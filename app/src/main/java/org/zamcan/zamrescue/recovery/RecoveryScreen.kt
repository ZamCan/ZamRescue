package org.zamcan.zamrescue.recovery

import android.net.Uri
import java.io.File
import java.io.PushbackInputStream
import java.io.DataInputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit Storage Access Framework consent for every source and destination. */
@Composable
fun RecoveryScreen(insets: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("backup") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Select a file to begin. No files are accessed automatically.") }

    val destinationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { destination ->
        val selected = source
        if (destination == null || selected == null) {
            status = "Operation cancelled."
        } else {
            val chosenMode = mode
            val secret = password.toCharArray()
            password = ""
            busy = true
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        try {
                            if (chosenMode == "backup") {
                                context.contentResolver.openInputStream(selected).use { input ->
                                    requireNotNull(input) { "Cannot read selected file" }
                                    context.contentResolver.openOutputStream(destination, "wt").use { output ->
                                        requireNotNull(output) { "Cannot write destination" }
                                        StreamingRecoveryVault.encrypt(input, output, secret)
                                    }
                                }
                            } else {
                                // Authenticate the complete archive in private temporary storage.
                                // Never expose partially decrypted data in a user-visible document.
                                val temporary = File.createTempFile("zamrescue-restore-", ".tmp", context.cacheDir)
                                try {
                                    context.contentResolver.openInputStream(selected).use { input ->
                                        requireNotNull(input) { "Cannot read selected archive" }
                                        val peek = PushbackInputStream(input, 4)
                                        val magic = ByteArray(4)
                                        DataInputStream(peek).readFully(magic)
                                        peek.unread(magic)
                                        temporary.outputStream().use { output ->
                                            when (DataInputStream(magic.inputStream()).readInt()) {
                                                0x5A525631 -> RecoveryVault.decrypt(peek, output, secret)
                                                0x5A525632 -> StreamingRecoveryVault.decrypt(peek, output, secret)
                                                else -> error("Unsupported archive format")
                                            }
                                        }
                                    }
                                    context.contentResolver.openOutputStream(destination, "wt").use { output ->
                                        requireNotNull(output) { "Cannot write destination" }
                                        temporary.inputStream().use { it.copyTo(output) }
                                    }
                                } finally {
                                    temporary.delete()
                                }
                            }
                        } catch (error: Exception) {
                            // CreateDocument may have already created a partial file.
                            // Never leave a failed archive or restore visible as a valid result.
                            try { context.contentResolver.delete(destination, null, null) } catch (_: Exception) {}
                            throw error
                        }
                    }
                    if (chosenMode == "backup") "Encrypted backup saved." else "Archive authenticated and file restored."
                } catch (_: Exception) {
                    "Operation failed. Check the password, file size, format, and destination. Any partial output is deleted when the document provider permits it."
                } finally {
                    secret.fill(0.toChar())
                    busy = false
                    source = null
                }
            }
        }
    }
    val sourcePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { selected ->
        source = selected
        if (selected != null) {
            destinationPicker.launch(if (mode == "backup") "ZamRescue-backup-v2.zrv" else "ZamRescue-restored-file")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Recovery vault", style = MaterialTheme.typography.headlineMedium)
        Text("Protect and restore individual files with AES-256-GCM encryption and password-derived keys. All operations stay on this device.")
        Card {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Private backup", style = MaterialTheme.typography.titleLarge)
                Text("Choose an accessible file, then select where to save its encrypted, streaming .zrv archive.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(256) },
                    label = { Text("Vault password (12+ characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && password.length >= 12,
                    onClick = { mode = "backup"; sourcePicker.launch(arrayOf("*/*")) }
                ) { Text("Create encrypted backup") }
                Button(
                    enabled = !busy && password.isNotEmpty(),
                    onClick = { mode = "restore"; sourcePicker.launch(arrayOf("*/*")) }
                ) { Text("Restore from .zrv archive") }
            }
        }
        Text(status, color = MaterialTheme.colorScheme.primary)
        Text("This feature cannot recover deleted files, access locked devices, or restore other apps' private data. Keep your password safe: it cannot be reset.", style = MaterialTheme.typography.bodySmall)
    }
}
