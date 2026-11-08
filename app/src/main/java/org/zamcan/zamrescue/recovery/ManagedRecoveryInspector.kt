package org.zamcan.zamrescue.recovery

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build

/**
 * Read-only inspection of official Android management eligibility.
 * Never attempts to change lock credentials or acquire administrator privileges.
 */
data class ManagedRecoveryEligibility(
    val deviceOwner: Boolean,
    val profileOwner: Boolean,
    val androidApi: Int,
    val supportedTokenApi: Boolean,
    val explanation: String,
)

object ManagedRecoveryInspector {
    fun inspect(context: Context): ManagedRecoveryEligibility {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val packageName = context.packageName
        val deviceOwner = manager.isDeviceOwnerApp(packageName)
        val profileOwner = manager.isProfileOwnerApp(packageName)
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val explanation = when {
            !deviceOwner && !profileOwner ->
                "Personal device: this app has no authority to reset the Android screen lock. Use official account and manufacturer recovery."
            !supported ->
                "This Android version does not support the required managed recovery token APIs."
            else ->
                "Management ownership detected. Token-based credential recovery requires separate advance provisioning, activation and OS policy checks; it is not enabled in this build."
        }
        return ManagedRecoveryEligibility(deviceOwner, profileOwner, Build.VERSION.SDK_INT, supported, explanation)
    }
}
