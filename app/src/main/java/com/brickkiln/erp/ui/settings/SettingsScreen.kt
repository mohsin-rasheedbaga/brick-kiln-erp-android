package com.brickkiln.erp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.ProductionRepository
import com.brickkiln.erp.data.repository.ServerSettings
import com.brickkiln.erp.data.repository.SettingsRepository
import com.brickkiln.erp.data.remote.SyncStatusResponse
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    apiClient: ApiClient,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState(initial = ServerSettings())

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8765") }
    var accessCode by remember { mutableStateOf("") }
    var cloudUrl by remember { mutableStateOf("") }
    var lanStatus by remember { mutableStateOf<String?>(null) }
    var cloudStatus by remember { mutableStateOf<String?>(null) }
    var testingLan by remember { mutableStateOf(false) }
    var testingCloud by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings) {
        host = settings.serverHost
        port = settings.serverPort.toString()
        accessCode = settings.accessCode
        cloudUrl = settings.cloudUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // LAN server
            Section(title = "Local Wi-Fi Server (LAN)") {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Server IP Address") },
                    placeholder = { Text("e.g. 192.168.1.10") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = accessCode,
                    onValueChange = { accessCode = it },
                    label = { Text("Access Code (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                testingLan = true
                                lanStatus = null
                                // Save first so ApiClient uses the new settings
                                settingsRepository.update(ServerSettings(host, port.toIntOrNull() ?: 8765, accessCode, cloudUrl))
                                val ok = apiClient.checkLanReachable()
                                lanStatus = if (ok) "✓ Server reachable" else "✗ Cannot reach server"
                                testingLan = false
                            }
                        },
                        enabled = !testingLan && host.isNotBlank(),
                    ) {
                        if (testingLan) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(if (lanStatus?.startsWith("✓") == true) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Test LAN Connection")
                    }
                    lanStatus?.let {
                        Spacer(Modifier.width(12.dp))
                        Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()

            // Cloud relay (optional)
            Section(title = "Cloud Relay (Internet)") {
                Text(
                    text = "Optional — enables out-of-Wi-Fi usage. " +
                           "Get the URL from your ERP administrator.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cloudUrl,
                    onValueChange = { cloudUrl = it },
                    label = { Text("Cloud Relay URL (optional)") },
                    placeholder = { Text("https://brick-kiln-relay.supabase.co") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                testingCloud = true
                                cloudStatus = null
                                settingsRepository.update(ServerSettings(host, port.toIntOrNull() ?: 8765, accessCode, cloudUrl))
                                val ok = apiClient.checkCloudReachable()
                                cloudStatus = if (ok) "✓ Cloud reachable" else "✗ Cloud not reachable"
                                testingCloud = false
                            }
                        },
                        enabled = !testingCloud && cloudUrl.isNotBlank(),
                    ) {
                        if (testingCloud) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(if (cloudStatus?.startsWith("✓") == true) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Test Cloud")
                    }
                    cloudStatus?.let {
                        Spacer(Modifier.width(12.dp))
                        Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider()

            // Save
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        settingsRepository.update(ServerSettings(host, port.toIntOrNull() ?: 8765, accessCode, cloudUrl))
                        saveMsg = "Settings saved. Please re-login to apply."
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !saving,
            ) {
                Text("Save Settings", fontWeight = FontWeight.SemiBold)
            }
            saveMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(content = content)
    }
}
