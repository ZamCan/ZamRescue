package org.zamcan.zamrescue

import android.os.Build
import android.os.Bundle
import org.zamcan.zamrescue.recovery.RecoveryScreen
import org.zamcan.zamrescue.recovery.ManagedRecoveryInspector
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

private enum class AppPage { OVERVIEW, RECOVERY, DEVICE, PEERS, SECURITY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZamCanRescueApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZamCanRescueApp() {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF9BD8ED),
            secondary = Color(0xFF8DABF1),
            background = Color(0xFF0B1421),
            surface = Color(0xFF172638),
            onSurface = Color(0xFFEAF4FA),
            onBackground = Color(0xFFE2ECE4),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF176F9D),
            secondary = Color(0xFF536FB8),
            background = Color(0xFFF3F7FB),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF152536),
            onBackground = Color(0xFF17231B),
        )
    }
    var page by remember { mutableStateOf(AppPage.OVERVIEW) }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text("✦", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                }
                            }
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = page == AppPage.OVERVIEW,
                        onClick = { page = AppPage.OVERVIEW },
                        icon = { Text("01", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_overview)) },
                    )
                    NavigationBarItem(
                        selected = page == AppPage.RECOVERY,
                        onClick = { page = AppPage.RECOVERY },
                        icon = { Text("02", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text("Vault") },
                    )
                    NavigationBarItem(
                        selected = page == AppPage.DEVICE,
                        onClick = { page = AppPage.DEVICE },
                        icon = { Text("03", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_device)) },
                    )
                    NavigationBarItem(
                        selected = page == AppPage.PEERS,
                        onClick = { page = AppPage.PEERS },
                        icon = { Text("04", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_peers)) },
                    )
                    NavigationBarItem(
                        selected = page == AppPage.SECURITY,
                        onClick = { page = AppPage.SECURITY },
                        icon = { Text("05", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_security)) },
                    )
                }
            },
        ) { insets ->
            when (page) {
                AppPage.OVERVIEW -> OverviewScreen(insets, onOpenVault = { page = AppPage.RECOVERY }, onOpenPeers = { page = AppPage.PEERS })
                AppPage.RECOVERY -> RecoveryScreen(insets)
                AppPage.DEVICE -> DeviceScreen(insets)
                AppPage.PEERS -> PeerManagementScreen(insets)
                AppPage.SECURITY -> SecurityScreen(insets)
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    insets: PaddingValues,
    onOpenVault: () -> Unit,
    onOpenPeers: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(insets)
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Recovery center", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Protect your important files before an emergency.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("LOCAL RECOVERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Encrypted file vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Back up or restore a file you choose. Archives are password-protected and checked for tampering.")
                Button(onClick = onOpenVault, modifier = Modifier.fillMaxWidth()) {
                    Text("Open recovery vault")
                }
            }
        }
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("TRUSTED COMPANION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Manage trusted devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Verify identities and revoke enrollment. Remote backup and restore are not yet enabled.")
                Button(onClick = onOpenPeers, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage peers")
                }
            }
        }
        Text(
            "Readiness: not yet verified. Complete a backup and a restore test before relying on this app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun DeviceScreen(insets: PaddingValues) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val manufacturer = Build.MANUFACTURER.ifBlank { "Unknown" }.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL.ifBlank { "Unknown" }
    val version = Build.VERSION.RELEASE ?: "Unknown"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.device_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.device_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeviceProperty(stringResource(R.string.manufacturer), manufacturer)
                DeviceProperty(stringResource(R.string.model), model)
                DeviceProperty(stringResource(R.string.android_version), version)
                DeviceProperty(stringResource(R.string.api_level), Build.VERSION.SDK_INT.toString())
                DeviceProperty(stringResource(R.string.application_id), appContext.packageName)
            }
        }
    }
}

@Composable
private fun SecurityScreen(insets: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.security_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        StatusCard(
            title = stringResource(R.string.protocol_status_title),
            body = stringResource(R.string.protocol_status_body),
            accent = MaterialTheme.colorScheme.primary,
        )
        StatusCard(
            title = stringResource(R.string.security_title),
            body = stringResource(R.string.security_body),
            accent = MaterialTheme.colorScheme.secondary,
        )
        val context = LocalContext.current
        val eligibility = remember(context) { ManagedRecoveryInspector.inspect(context) }
        StatusCard(
            title = "Official Android recovery eligibility",
            body = eligibility.explanation,
            accent = MaterialTheme.colorScheme.primary,
        )
        StatusCard(
            title = stringResource(R.string.no_bypass_title),
            body = stringResource(R.string.no_bypass_body),
            accent = Color(0xFFB04737),
        )
    }
}

@Composable
private fun StatusCard(title: String, body: String, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(modifier = Modifier.size(width = 4.dp, height = 42.dp), shape = CircleShape, color = accent) {}
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
private fun DeviceProperty(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
