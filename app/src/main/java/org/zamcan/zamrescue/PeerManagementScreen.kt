package org.zamcan.zamrescue

import org.zamcan.zamrescue.transport.LivePeerSession
import androidx.compose.material3.OutlinedButton
import android.provider.OpenableColumns
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zamcan.zamrescue.identity.AndroidKeystoreIdentityProvider
import org.zamcan.zamrescue.identity.DeviceIdentity
import org.zamcan.zamrescue.identity.KeySecurityLevel
import org.zamcan.zamrescue.identity.PeerCandidate
import org.zamcan.zamrescue.identity.PeerEnrollmentError
import org.zamcan.zamrescue.identity.PeerEnrollmentException
import org.zamcan.zamrescue.identity.PeerEnrollmentRepository
import org.zamcan.zamrescue.identity.TrustedPeerRecord
import org.zamcan.zamrescue.transport.nsd.AndroidNsdPeerDiscovery
import org.zamcan.zamrescue.transport.nsd.DiscoveredRescueService
import org.zamcan.zamrescue.transport.nsd.NsdDiscoveryObserver
import org.zamcan.zamrescue.transport.nsd.NsdOperation

private enum class IdentityUiState { LOADING, ABSENT, READY, CREATING, ERROR }

@Composable
internal fun PeerManagementScreen(insets: PaddingValues) {
    val context = LocalContext.current.applicationContext
    val identityProvider = remember { AndroidKeystoreIdentityProvider() }
    val peerRepository = remember(context) { PeerEnrollmentRepository(context, identityProvider) }
    val discovery = remember(context) { AndroidNsdPeerDiscovery(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val liveSession = remember(context) { LivePeerSession(context) }
    var selectedArchive by remember { mutableStateOf<Uri?>(null) }
    val chooseArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        selectedArchive = it
    }
    var probeHost by remember { mutableStateOf("") }
    var probePort by remember { mutableStateOf("42761") }
    var selectedPeer by remember { mutableStateOf("") }
    var probeBusy by remember { mutableStateOf(false) }
    var probeStatus by remember { mutableStateOf("No active peer session") }
    var identity by remember { mutableStateOf<DeviceIdentity?>(null) }
    var identityState by remember { mutableStateOf(IdentityUiState.LOADING) }
    var peerRecords by remember { mutableStateOf<List<TrustedPeerRecord>>(emptyList()) }
    var publicKeyInput by remember { mutableStateOf("") }
    var peerNameInput by remember { mutableStateOf("") }
    var fingerprintConfirmation by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<PeerCandidate?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var peerPendingRevocation by remember { mutableStateOf<TrustedPeerRecord?>(null) }
    var discoveryActive by remember { mutableStateOf(false) }
    var discoveryFailed by remember { mutableStateOf(false) }
    var discoveredServices by remember { mutableStateOf<List<DiscoveredRescueService>>(emptyList()) }

    LaunchedEffect(identityProvider, peerRepository) {
        try {
            val loadedIdentity = withContext(Dispatchers.IO) { identityProvider.load() }
            identity = loadedIdentity
            identityState = if (loadedIdentity == null) IdentityUiState.ABSENT else IdentityUiState.READY
            peerRecords = withContext(Dispatchers.IO) { peerRepository.peers() }
        } catch (_: Exception) {
            identityState = IdentityUiState.ERROR
            feedback = null
        }
    }

    val observer = remember(discovery) {
        object : NsdDiscoveryObserver {
            override fun onDiscoveryStarted() {
                discoveryActive = true
                discoveryFailed = false
            }

            override fun onServiceResolved(service: DiscoveredRescueService) {
                discoveredServices = (discoveredServices.filterNot { it.serviceName == service.serviceName } + service)
            }

            override fun onServiceLost(serviceName: String) {
                discoveredServices = discoveredServices.filterNot { it.serviceName == serviceName }
            }

            override fun onDiscoveryStopped() {
                discoveryActive = false
            }

            override fun onFailure(operation: NsdOperation, errorCode: Int?) {
                discoveryActive = false
                discoveryFailed = true
            }
        }
    }

    DisposableEffect(discovery, lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                discovery.stopDiscovery()
                discoveryActive = false
                discoveredServices = emptyList()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            discovery.close()
            liveSession.close()
        }
    }

    val genericError = stringResource(R.string.peer_operation_error)
    val clipboardLabel = stringResource(R.string.identity_clipboard_label)
    val title = stringResource(R.string.peers_title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionCard(title = stringResource(R.string.identity_section_title)) {
            when (identityState) {
                IdentityUiState.LOADING -> Text(stringResource(R.string.identity_loading))
                IdentityUiState.ABSENT -> {
                    Text(stringResource(R.string.identity_not_created))
                    Button(
                        onClick = {
                            scope.launch {
                                identityState = IdentityUiState.CREATING
                                try {
                                    val created = withContext(Dispatchers.IO) { identityProvider.create() }
                                    identity = created
                                    identityState = IdentityUiState.READY
                                    feedback = context.getString(R.string.identity_created)
                                } catch (_: Exception) {
                                    identityState = IdentityUiState.ERROR
                                    feedback = genericError
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.identity_create))
                    }
                }
                IdentityUiState.CREATING -> Text(stringResource(R.string.identity_creating))
                IdentityUiState.ERROR -> Text(stringResource(R.string.identity_error))
                IdentityUiState.READY -> identity?.let { current ->
                    Text(stringResource(R.string.identity_storage_note))
                    Text(
                        stringResource(R.string.identity_security_level, securityLevelText(current.securityLevel)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(stringResource(R.string.identity_fingerprint_label), style = MaterialTheme.typography.labelLarge)
                    Text(current.fingerprintHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val encoded = Base64.encodeToString(current.publicKeyEncoded, Base64.NO_WRAP)
                            clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, encoded))
                            feedback = context.getString(R.string.identity_public_key_copied)
                        },
                    ) {
                        Text(stringResource(R.string.identity_copy_public_key))
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.peer_enrollment_title)) {
            Text(stringResource(R.string.peer_enrollment_instructions))
            OutlinedTextField(
                value = peerNameInput,
                onValueChange = { peerNameInput = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.peer_name_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = publicKeyInput,
                onValueChange = {
                    publicKeyInput = it.take(1_024)
                    candidate = null
                    fingerprintConfirmation = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.peer_public_key_label)) },
                supportingText = { Text(stringResource(R.string.peer_public_key_help)) },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                enabled = identity != null && publicKeyInput.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            val bytes = Base64.decode(publicKeyInput.trim(), Base64.DEFAULT)
                            candidate = withContext(Dispatchers.IO) { peerRepository.preview(bytes) }
                            fingerprintConfirmation = ""
                            feedback = null
                        } catch (_: Exception) {
                            candidate = null
                            feedback = context.getString(R.string.peer_invalid_public_key)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.peer_preview_fingerprint))
            }
            candidate?.let { pending ->
                Text(stringResource(R.string.peer_candidate_fingerprint_label), style = MaterialTheme.typography.labelLarge)
                Text(pending.fingerprintHex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.peer_fingerprint_confirmation_help))
                OutlinedTextField(
                    value = fingerprintConfirmation,
                    onValueChange = { fingerprintConfirmation = it.take(96) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.peer_confirm_fingerprint_label)) },
                    singleLine = true,
                )
                Button(
                    enabled = identity != null && fingerprintConfirmation.isNotBlank() && peerNameInput.isNotBlank(),
                    onClick = {
                        val confirmedFingerprint = fingerprintConfirmation
                        val peerLabel = peerNameInput
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    peerRepository.enroll(pending, confirmedFingerprint, peerLabel)
                                }
                                peerRecords = withContext(Dispatchers.IO) { peerRepository.peers() }
                                publicKeyInput = ""
                                peerNameInput = ""
                                fingerprintConfirmation = ""
                                candidate = null
                                feedback = context.getString(R.string.peer_enrolled)
                            } catch (e: PeerEnrollmentException) {
                                feedback = context.getString(
                                    if (e.error == PeerEnrollmentError.FINGERPRINT_MISMATCH) {
                                        R.string.peer_fingerprint_mismatch
                                    } else {
                                        R.string.peer_operation_error
                                    }
                                )
                            } catch (_: Exception) {
                                feedback = genericError
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.peer_enroll_button))
                }
            }
        }

        SectionCard(title = "Live peer verification") {
            Text("On both phones, enroll and verify the other's public key first. Keep the receiver screen open and connect over the same reachable network. This tests encrypted identity authentication only.")
            OutlinedTextField(
                value = probeHost,
                onValueChange = { probeHost = it.take(253) },
                label = { Text("Receiver IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = probePort,
                onValueChange = { probePort = it.filter(Char::isDigit).take(5) },
                label = { Text("TCP port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            peerRecords.filterNot { it.isRevoked }.forEach { peer ->
                OutlinedButton(onClick = { selectedPeer = peer.fingerprintHex }) {
                    Text((if (selectedPeer == peer.fingerprintHex) "✓ " else "") + peer.displayName)
                }
            }
            Button(
                enabled = !probeBusy && identity != null && peerRecords.any { !it.isRevoked },
                onClick = {
                    probeBusy = true
                    probeStatus = "Listening for one authenticated peer (up to 60 seconds)…"
                    scope.launch {
                        try {
                            probeStatus = withContext(Dispatchers.IO) {
                                liveSession.receiveProbe(probePort.toIntOrNull() ?: 42761)
                            }
                        } catch (e: Exception) {
                            probeStatus = "Listening ended: " + (e.message ?: "connection failed")
                        } finally { probeBusy = false }
                    }
                },
            ) { Text("Listen for peer") }
            Button(
                enabled = !probeBusy && identity != null && selectedPeer.isNotBlank() && probeHost.isNotBlank(),
                onClick = {
                    probeBusy = true
                    scope.launch {
                        try {
                            probeStatus = withContext(Dispatchers.IO) {
                                liveSession.sendProbe(probeHost.trim(), probePort.toIntOrNull() ?: 42761, selectedPeer)
                            }
                        } catch (e: Exception) {
                            probeStatus = "Connection failed: " + (e.message ?: "authentication rejected")
                        } finally { probeBusy = false }
                    }
                },
            ) { Text("Verify encrypted connection") }
            OutlinedButton(onClick = { liveSession.close(); probeStatus = "Listening cancelled" }) {
                Text("Stop listening")
            }
            Text("Transfer only an encrypted .zrv archive. The receiving phone must explicitly start receiving. Incoming files remain in private app storage and are never automatically decrypted.")
            OutlinedButton(onClick = { chooseArchive.launch(arrayOf("*/*")) }) {
                Text(if (selectedArchive == null) "Select encrypted archive" else "Archive selected")
            }
            Button(
                enabled = !probeBusy && identity != null && peerRecords.any { !it.isRevoked },
                onClick = {
                    probeBusy = true
                    probeStatus = "Waiting for encrypted archive…"
                    scope.launch {
                        try {
                            val received = withContext(Dispatchers.IO) {
                                liveSession.receiveArchive(probePort.toIntOrNull() ?: 42761)
                            }
                            probeStatus = "Encrypted archive received privately: " + received.name +
                                " (" + received.length() + " bytes). No automatic restore."
                        } catch (e: Exception) {
                            probeStatus = "Receive failed: " + (e.message ?: "connection rejected")
                        } finally { probeBusy = false }
                    }
                },
            ) { Text("Receive encrypted archive") }
            Button(
                enabled = !probeBusy && identity != null && selectedArchive != null &&
                    selectedPeer.isNotBlank() && probeHost.isNotBlank(),
                onClick = {
                    val archive = selectedArchive ?: return@Button
                    probeBusy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val length = context.contentResolver.query(
                                    archive, arrayOf(OpenableColumns.SIZE), null, null, null
                                )?.use { cursor ->
                                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                                } ?: error("Provider must report archive size")
                                require(length >= 0) { "Invalid archive size" }
                                context.contentResolver.openInputStream(archive).use { input ->
                                    requireNotNull(input) { "Cannot open archive" }
                                    liveSession.sendArchive(
                                        probeHost.trim(), probePort.toIntOrNull() ?: 42761,
                                        selectedPeer, input, length
                                    )
                                }
                            }
                            probeStatus = "Encrypted archive delivered and acknowledged"
                        } catch (e: Exception) {
                            probeStatus = "Transfer failed: " + (e.message ?: "connection rejected")
                        } finally { probeBusy = false }
                    }
                },
            ) { Text("Send encrypted archive") }
            Text(probeStatus)
        }

        feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        SectionCard(title = stringResource(R.string.trusted_peers_title)) {
            if (peerRecords.isEmpty()) {
                Text(stringResource(R.string.trusted_peers_empty))
            } else {
                peerRecords.forEach { peer ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(peer.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(peer.fingerprintHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (peer.isRevoked) {
                            Text(stringResource(R.string.peer_revoked_label), color = MaterialTheme.colorScheme.error)
                        } else {
                            Button(
                                onClick = { peerPendingRevocation = peer },
                            ) {
                                Text(stringResource(R.string.peer_revoke_button))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        SectionCard(title = stringResource(R.string.nsd_section_title)) {
            Text(stringResource(R.string.nsd_discovery_explanation))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !discoveryActive,
                    onClick = {
                        discoveredServices = emptyList()
                        discoveryFailed = false
                        discoveryActive = discovery.startDiscovery(observer)
                    },
                ) {
                    Text(stringResource(R.string.nsd_start_button))
                }
                Button(
                    enabled = discoveryActive,
                    onClick = {
                        discovery.stopDiscovery()
                        discoveryActive = false
                    },
                ) {
                    Text(stringResource(R.string.nsd_stop_button))
                }
            }
            if (discoveryActive) Text(stringResource(R.string.nsd_scanning))
            if (discoveryFailed) Text(stringResource(R.string.nsd_error), color = MaterialTheme.colorScheme.error)
            if (discoveryActive && discoveredServices.isEmpty()) Text(stringResource(R.string.nsd_empty))
            discoveredServices.forEach { service ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(service.serviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${service.hostAddress}:${service.port}", fontFamily = FontFamily.Monospace)
                    Text(
                        stringResource(
                            R.string.nsd_protocol_version,
                            service.protocolVersion?.toString() ?: stringResource(R.string.nsd_version_unknown),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    peerPendingRevocation?.let { peer ->
        AlertDialog(
            onDismissRequest = { peerPendingRevocation = null },
            title = { Text(stringResource(R.string.peer_revoke_dialog_title)) },
            text = { Text(stringResource(R.string.peer_revoke_dialog_body, peer.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        peerPendingRevocation = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { peerRepository.revoke(peer.fingerprintHex) }
                                peerRecords = withContext(Dispatchers.IO) { peerRepository.peers() }
                                feedback = context.getString(R.string.peer_revoked_feedback)
                            } catch (_: Exception) {
                                feedback = genericError
                            }
                        }
                    },
                ) { Text(stringResource(R.string.peer_revoke_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { peerPendingRevocation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun securityLevelText(level: KeySecurityLevel): String = when (level) {
    KeySecurityLevel.SOFTWARE -> stringResource(R.string.key_level_software)
    KeySecurityLevel.TRUSTED_ENVIRONMENT -> stringResource(R.string.key_level_tee)
    KeySecurityLevel.STRONGBOX -> stringResource(R.string.key_level_strongbox)
    KeySecurityLevel.UNKNOWN -> stringResource(R.string.key_level_unknown)
}
