package com.brickkiln.erp.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.BrickKilnApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHistoryScreen(onBack: () -> Unit) {
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    val recent by db.productionEntryDao().observeRecent(session?.userId ?: "", 100).collectAsState(initial = emptyList())

    var todayQty by remember { mutableStateOf(0.0) }
    var todayLabour by remember { mutableStateOf(0.0) }

    LaunchedEffect(session?.userId) {
        val userId = session?.userId ?: return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        val stats = db.productionEntryDao().getStats(userId, today)
        todayQty = stats.todayQty
        todayLabour = stats.todayLabour
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("My Work History") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Today's Work", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bricks: ${String.format(Locale.US, "%,.0f", todayQty)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Earnings: Rs. ${String.format(Locale.US, "%,.2f", todayLabour)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item { Text("All My Entries", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (recent.isEmpty()) {
                item { Text("No entries yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(recent) { entry ->
                    val stageLabel = when (entry.stage) {
                        "raw_brick_making" -> "Raw Brick"
                        "raw_brick_transport" -> "Transport+Loading"
                        "baked_brick_unloading" -> "Unloading"
                        else -> entry.stage
                    }
                    val statusText = when (entry.status) { "PENDING" -> "Pending"; "SYNCED" -> "Synced"; "FAILED" -> "Failed"; else -> entry.status }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Qty: ${String.format(Locale.US, "%,.0f", entry.quantity)} - Rs. ${String.format(Locale.US, "%,.2f", entry.labourAmount)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            Text(statusText, fontSize = 12.sp, color = if (entry.status == "SYNCED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Change Password") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = currentPassword, onValueChange = { currentPassword = it },
                label = { Text("Current Password") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }}
            )
            OutlinedTextField(
                value = newPassword, onValueChange = { newPassword = it },
                label = { Text("New Password") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
            )
            OutlinedTextField(
                value = confirmPassword, onValueChange = { confirmPassword = it },
                label = { Text("Confirm New Password") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }
            Button(
                onClick = {
                    if (currentPassword.isBlank()) { error = "Enter current password"; return@Button }
                    if (newPassword.length < 6) { error = "New password must be at least 6 characters"; return@Button }
                    if (newPassword != confirmPassword) { error = "Passwords do not match"; return@Button }
                    error = null; message = null; saving = true
                    scope.launch {
                        try {
                            val apiClient = com.brickkiln.erp.data.remote.ApiClient(BrickKilnApp.instance.settingsRepository)
                            val sess = session ?: return@launch
                            val result = apiClient.callRpc(
                                com.brickkiln.erp.data.remote.LoginResponse::class.java,
                                channel = "auth:change-password",
                                args = mapOf("token" to sess.token, "currentPassword" to currentPassword, "newPassword" to newPassword),
                            )
                            if (result.isSuccess) {
                                message = "Password changed successfully!"
                                currentPassword = ""; newPassword = ""; confirmPassword = ""
                            } else {
                                error = result.exceptionOrNull()?.message ?: "Failed to change password"
                            }
                        } catch (e: Exception) { error = e.message ?: "Error" }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text("Change Password", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
