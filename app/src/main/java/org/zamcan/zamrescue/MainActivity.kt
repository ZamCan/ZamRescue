package org.zamcan.zamrescue

import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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

private enum class AppPage { OVERVIEW, DEVICE, SECURITY }

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
            primary = Color(0xFF8FD5A7),
            secondary = Color(0xFF8BC8C0),
            background = Color(0xFF101612),
            surface = Color(0xFF18211B),
            onSurface = Color(0xFFE2ECE4),
            onBackground = Color(0xFFE2ECE4),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF176B45),
            secondary = Color(0xFF176C68),
            background = Color(0xFFF4F7F2),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF17231B),
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
                                    Text("Z", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                        selected = page == AppPage.DEVICE,
                        onClick = { page = AppPage.DEVICE },
                        icon = { Text("02", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_device)) },
                    )
                    NavigationBarItem(
                        selected = page == AppPage.SECURITY,
                        onClick = { page = AppPage.SECURITY },
                        icon = { Text("03", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(stringResource(R.string.tab_security)) },
                    )
                }
            },
        ) { insets ->
            when (page) {
                AppPage.OVERVIEW -> OverviewScreen(insets)
                AppPage.DEVICE -> DeviceScreen(insets)
                AppPage.SECURITY -> SecurityScreen(insets)
            }
        }
    }
}

@Composable
private fun OverviewScreen(insets: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.overview_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.overview_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
        )
        StatusCard(
            title = stringResource(R.string.status_title),
            body = stringResource(R.string.status_body),
            accent = MaterialTheme.colorScheme.primary,
        )
        StatusCard(
            title = stringResource(R.string.not_available_title),
            body = stringResource(R.string.not_available_body),
            accent = MaterialTheme.colorScheme.secondary,
        )
        Text(
            stringResource(R.string.foundation_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
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
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
