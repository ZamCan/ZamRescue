package org.zamcan.zamrescue.transport.nsd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import java.io.Closeable
import java.net.ServerSocket
import java.security.SecureRandom

/** A discovered service is an untrusted address hint, never a peer identity or authorization. */
data class DiscoveredRescueService(
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val protocolVersion: Int?,
)

enum class NsdOperation { DISCOVERY, RESOLUTION, ADVERTISEMENT }

interface NsdDiscoveryObserver {
    fun onDiscoveryStarted() = Unit
    fun onServiceResolved(service: DiscoveredRescueService)
    fun onServiceLost(serviceName: String) = Unit
    fun onDiscoveryStopped() = Unit
    fun onFailure(operation: NsdOperation, errorCode: Int?) = Unit
}

interface NsdAdvertisementObserver {
    fun onAdvertisingStarted(serviceName: String) = Unit
    fun onAdvertisingStopped() = Unit
    fun onFailure(errorCode: Int?) = Unit
}

/**
 * Foreground-owned DNS-SD discovery for ZamCan Rescue services. Discovery is user-started and
 * must be stopped when its screen/activity stops. It does not open sockets or authenticate peers.
 *
 * Advertisement is available only when the caller supplies a live, bound TCP server socket. This
 * prevents publishing a service for a port on which the app is not actually listening.
 */
class AndroidNsdPeerDiscovery(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveryObserver: NsdDiscoveryObserver? = null
    private var discoveryGeneration = 0L
    private val resolvingNames = mutableSetOf<String>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private var advertisementListener: NsdManager.RegistrationListener? = null
    private var advertisementObserver: NsdAdvertisementObserver? = null
    private var advertisementName: String? = null
    private var advertisementRequested = false
    private var closed = false

    /** Returns false when a discovery is already active. Results arrive asynchronously. */
    fun startDiscovery(observer: NsdDiscoveryObserver): Boolean {
        val listener: NsdManager.DiscoveryListener
        val generation: Long
        synchronized(lock) {
            check(!closed) { "Discovery has been closed" }
            if (discoveryListener != null) return false
            generation = ++discoveryGeneration
            discoveryObserver = observer
            listener = createDiscoveryListener(observer, generation)
            discoveryListener = listener
            try {
                acquireLegacyMulticastLockIfNeeded()
                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: SecurityException) {
                clearDiscoveryLocked(listener)
                val failureGeneration = discoveryGeneration
                dispatchIfGeneration(failureGeneration) { observer.onFailure(NsdOperation.DISCOVERY, null) }
                return false
            } catch (e: RuntimeException) {
                clearDiscoveryLocked(listener)
                val failureGeneration = discoveryGeneration
                dispatchIfGeneration(failureGeneration) { observer.onFailure(NsdOperation.DISCOVERY, null) }
                return false
            }
        }
        return true
    }

    fun stopDiscovery() {
        val listener = synchronized(lock) {
            val active = discoveryListener ?: return
            discoveryListener = null
            discoveryObserver = null
            discoveryGeneration++
            resolvingNames.clear()
            releaseMulticastLockLocked()
            active
        }
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // NSD can report a start failure before a caller stops the listener.
        } catch (_: RuntimeException) {
            // The request is already detached locally; the transport must still fail closed.
        }
    }

    /**
     * Advertises only a real, bound listener. Stop advertising before closing the socket and when
     * the invitation expires or the user leaves the foreground session.
     */
    fun startAdvertising(socket: ServerSocket, observer: NsdAdvertisementObserver): Boolean {
        require(socket.isBound && !socket.isClosed) { "A live bound server socket is required" }
        require(socket.localPort in 1..65_535) { "The server socket must have a valid port" }

        val serviceName = newEphemeralServiceName()
        val serviceInfo = NsdServiceInfo().apply {
            setServiceName(serviceName)
            serviceType = SERVICE_TYPE
            setPort(socket.localPort)
            setAttribute("v", PROTOCOL_VERSION.toString().toByteArray(Charsets.US_ASCII))
        }
        val listener = createRegistrationListener(observer)
        synchronized(lock) {
            check(!closed) { "Discovery has been closed" }
            if (advertisementListener != null) return false
            advertisementListener = listener
            advertisementObserver = observer
            advertisementName = serviceName
            advertisementRequested = true
            try {
                nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: SecurityException) {
                clearAdvertisementLocked(listener)
                dispatch { observer.onFailure(null) }
                return false
            } catch (e: RuntimeException) {
                clearAdvertisementLocked(listener)
                dispatch { observer.onFailure(null) }
                return false
            }
        }
        return true
    }

    fun stopAdvertising() {
        val listener = synchronized(lock) {
            val active = advertisementListener ?: return
            advertisementRequested = false
            active
        }
        try {
            nsdManager.unregisterService(listener)
        } catch (_: IllegalArgumentException) {
            // Registration may still be in flight; onServiceRegistered will retry unregistration.
        } catch (_: RuntimeException) {
            synchronized(lock) { clearAdvertisementLocked(listener) }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        stopDiscovery()
        stopAdvertising()
    }

    private fun createDiscoveryListener(
        observer: NsdDiscoveryObserver,
        generation: Long,
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            dispatchIfCurrent(this, generation) { observer.onDiscoveryStarted() }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (!isCurrentDiscovery(this, generation)) return
            if (normalizeServiceType(serviceInfo.serviceType) != SERVICE_TYPE) return
            val name = serviceInfo.serviceName ?: return
            synchronized(lock) {
                if (!isCurrentDiscoveryLocked(this, generation) || !resolvingNames.add(name)) return
            }
            resolveService(serviceInfo, name, observer, generation, this)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName ?: return
            synchronized(lock) {
                if (!isCurrentDiscoveryLocked(this, generation)) return
                resolvingNames.remove(name)
            }
            dispatchIfCurrent(this, generation) { observer.onServiceLost(name) }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            val stoppedGeneration = synchronized(lock) {
                if (!isCurrentDiscoveryLocked(this, generation)) return
                clearDiscoveryLocked(this)
                discoveryGeneration
            }
            dispatchIfGeneration(stoppedGeneration) { observer.onDiscoveryStopped() }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            val failureGeneration = synchronized(lock) {
                if (!isCurrentDiscoveryLocked(this, generation)) return
                clearDiscoveryLocked(this)
                discoveryGeneration
            }
            try {
                nsdManager.stopServiceDiscovery(this)
            } catch (_: RuntimeException) {
                // A failed start is already inactive on the framework side.
            }
            dispatchIfGeneration(failureGeneration) { observer.onFailure(NsdOperation.DISCOVERY, errorCode) }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            val failureGeneration = synchronized(lock) {
                if (discoveryGeneration != generation + 1 || discoveryListener != null || closed) return
                discoveryGeneration
            }
            dispatchIfGeneration(failureGeneration) { observer.onFailure(NsdOperation.DISCOVERY, errorCode) }
        }
    }

    private fun resolveService(
        found: NsdServiceInfo,
        name: String,
        observer: NsdDiscoveryObserver,
        generation: Long,
        owner: NsdManager.DiscoveryListener,
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { if (isCurrentDiscoveryLocked(owner, generation)) resolvingNames.remove(name) }
                dispatchIfCurrent(owner, generation) { observer.onFailure(NsdOperation.RESOLUTION, errorCode) }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                synchronized(lock) {
                    if (!isCurrentDiscoveryLocked(owner, generation)) return
                    resolvingNames.remove(name)
                }
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                if (port !in 1..65_535) return
                val version = serviceInfo.attributes?.get("v")
                    ?.let { String(it, Charsets.US_ASCII) }
                    ?.toIntOrNull()
                dispatchIfCurrent(owner, generation) {
                    observer.onServiceResolved(
                        DiscoveredRescueService(serviceInfo.serviceName ?: name, host, port, version)
                    )
                }
            }
        }
        try {
            nsdManager.resolveService(found, listener)
        } catch (_: RuntimeException) {
            synchronized(lock) { if (isCurrentDiscoveryLocked(owner, generation)) resolvingNames.remove(name) }
            dispatchIfCurrent(owner, generation) { observer.onFailure(NsdOperation.RESOLUTION, null) }
        }
    }

    private fun createRegistrationListener(observer: NsdAdvertisementObserver) =
        object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                val unregisterImmediately = synchronized(lock) {
                    if (advertisementListener !== this) {
                        true
                    } else if (!advertisementRequested) {
                        true
                    } else {
                        advertisementName = serviceInfo.serviceName
                        false
                    }
                }
                if (unregisterImmediately) {
                    try {
                        nsdManager.unregisterService(this)
                    } catch (_: RuntimeException) {
                        synchronized(lock) { clearAdvertisementLocked(this) }
                    }
                } else {
                    dispatch { observer.onAdvertisingStarted(serviceInfo.serviceName ?: "") }
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { clearAdvertisementLocked(this) }
                dispatch { observer.onFailure(errorCode) }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { clearAdvertisementLocked(this) }
                dispatch { observer.onAdvertisingStopped() }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { clearAdvertisementLocked(this) }
                dispatch { observer.onFailure(errorCode) }
            }
        }

    private fun acquireLegacyMulticastLockIfNeeded() {
        if (!requiresApplicationMulticastLock()) return
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        val active = wifi.createMulticastLock("zamcan-rescue-nsd")
        active.setReferenceCounted(false)
        active.acquire()
        multicastLock = active
    }

    private fun releaseMulticastLockLocked() {
        val active = multicastLock ?: return
        multicastLock = null
        if (active.isHeld) {
            try {
                active.release()
            } catch (_: RuntimeException) {
                // It is already released by the platform or another lifecycle path.
            }
        }
    }

    private fun clearDiscoveryLocked(listener: NsdManager.DiscoveryListener) {
        if (discoveryListener !== listener) return
        discoveryListener = null
        discoveryObserver = null
        resolvingNames.clear()
        discoveryGeneration++
        releaseMulticastLockLocked()
    }

    private fun clearAdvertisementLocked(listener: NsdManager.RegistrationListener) {
        if (advertisementListener !== listener) return
        advertisementListener = null
        advertisementObserver = null
        advertisementName = null
        advertisementRequested = false
    }

    private fun isCurrentDiscovery(listener: NsdManager.DiscoveryListener, generation: Long): Boolean =
        synchronized(lock) { isCurrentDiscoveryLocked(listener, generation) }

    private fun isCurrentDiscoveryLocked(listener: NsdManager.DiscoveryListener, generation: Long): Boolean =
        !closed && discoveryListener === listener && discoveryGeneration == generation

    private fun dispatch(block: () -> Unit) {
        mainHandler.post { block() }
    }

    private fun dispatchIfCurrent(
        owner: NsdManager.DiscoveryListener,
        generation: Long,
        block: () -> Unit,
    ) {
        mainHandler.post {
            if (isCurrentDiscovery(owner, generation)) block()
        }
    }

    private fun dispatchIfGeneration(generation: Long, block: () -> Unit) {
        mainHandler.post {
            val stillCurrent = synchronized(lock) { !closed && discoveryGeneration == generation }
            if (stillCurrent) block()
        }
    }

    private fun requiresApplicationMulticastLock(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return try {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) < T_MULTICAST_AUTO_MANAGEMENT_EXTENSION
        } catch (_: RuntimeException) {
            true
        }
    }

    private fun newEphemeralServiceName(): String {
        val suffix = ByteArray(4).also(SecureRandom()::nextBytes).joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        return "ZamCanRescue-$suffix"
    }

    private fun normalizeServiceType(value: String?): String = value.orEmpty().trim().trimEnd('.') + "."

    companion object {
        const val SERVICE_TYPE = "_zamrescue._tcp."
        const val PROTOCOL_VERSION = 1
        private const val T_MULTICAST_AUTO_MANAGEMENT_EXTENSION = 7
    }
}
