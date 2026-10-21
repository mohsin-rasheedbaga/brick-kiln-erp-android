package com.brickkiln.erp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.BrickKilnApp
import com.brickkiln.erp.data.local.entity.ProductionEntryEntity
import com.brickkiln.erp.data.repository.ProductionRepository
import com.brickkiln.erp.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    productionRepo: ProductionRepository,
    onNewEntry: () -> Unit,
    onHistory: () -> Unit,
    onLookup: () -> Unit,
    onSettings: () -> Unit,
    onFinancial: () -> Unit,
    onMyHistory: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    val recent by db.productionEntryDao().observeRecent(session?.userId ?: "", 5).collectAsState(initial = emptyList())
    val workerCount by produceState(0) { value = db.workerDao().count() }

    var todayQty by remember { mutableStateOf(0.0) }
    var todayLabour by remember { mutableStateOf(0.0) }
    var serverTodayQty by remember { mutableStateOf(0.0) }
    var serverTodayLabour by remember { mutableStateOf(0.0) }
    var pendingCount by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("Loading...") }

    val isAccountant = session?.roleId == "role-accountant" || session?.roleId == "role-sales"
    val isOperator = session?.roleId in listOf("role-raw-maker", "role-transport", "role-kiln-unload")

    LaunchedEffect(session?.userId) {
        val userId = session?.userId ?: return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stats = db.productionEntryDao().getStats(userId, today)
        todayQty = stats.todayQty
        todayLabour = stats.todayLabour
        pendingCount = stats.pendingCount

        syncStatus = "Syncing..."
        try {
            val apiClient = com.brickkiln.erp.data.remote.ApiClient(BrickKilnApp.instance.settingsRepository)
            val authRepo = com.brickkiln.erp.data.repository.AuthRepository(apiClient, db)
            val ctx = authRepo.fetchMobileContext()
            if (ctx != null) {
                syncStatus = "Synced: ${ctx.workers.size} workers"
                serverTodayQty = ctx.todayStats.totalQty
                serverTodayLabour = ctx.todayStats.totalLabour
            } else {
                syncStatus = "Sync failed: ${com.brickkiln.erp.data.repository.AuthRepository.lastError ?: "Unknown"}"
            }
        } catch (e: Exception) { syncStatus = "Error: ${e.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brick Kiln ERP v2.1.0") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Logout") }
                },
            )
        },
        floatingActionButton = {
            if (!isAccountant) {
                ExtendedFloatingActionButton(onClick = onNewEntry, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("New Entry") })
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Welcome card
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Assalamu Alaikum,", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(session?.fullName ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${session?.roleName ?: ""} - ${session?.departmentName ?: "None"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // Sync status
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = if (syncStatus.startsWith("Synced")) MaterialTheme.colorScheme.secondaryContainer
                    else if (syncStatus.startsWith("Sync f") || syncStatus.startsWith("Error")) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                )) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Data Sync Status", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(syncStatus, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = if (syncStatus.startsWith("Synced")) MaterialTheme.colorScheme.onSecondaryContainer
                            else if (syncStatus.startsWith("Sync f") || syncStatus.startsWith("Error")) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Workers in local DB: $workerCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Dashboard - today's work summary
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Today's Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("My Bricks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)); Text(String.format(Locale.US, "%,.0f", todayQty), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                            Column { Text("My Earnings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)); Text("Rs. ${String.format(Locale.US, "%,.0f", todayLabour)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                            Column { Text("Dept Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)); Text(String.format(Locale.US, "%,.0f", serverTodayQty), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                        }
                    }
                }
            }

            // Pending sync
            if (!isAccountant) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Pending Sync", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$pendingCount entries", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (pendingCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            Button(onClick = {
                                scope.launch {
                                    syncing = true; SyncScheduler.syncNow(); kotlinx.coroutines.delay(3000)
                                    val pending = db.productionEntryDao().getPending()
                                    for (entry in pending) { productionRepo.syncOne(entry) }
                                    val userId = session?.userId ?: return@launch
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    val stats = db.productionEntryDao().getStats(userId, today)
                                    todayQty = stats.todayQty; todayLabour = stats.todayLabour; pendingCount = stats.pendingCount; syncing = false
                                }
                            }, enabled = !syncing) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (syncing) "Syncing..." else "Sync Now")
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item { Text("Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAccountant) { ActionButton(Modifier.weight(1f), Icons.Default.AccountBalance, "Financial", onFinancial) }
                    ActionButton(Modifier.weight(1f), Icons.Default.Assessment, "My History", onMyHistory)
                    if (!isAccountant) { ActionButton(Modifier.weight(1f), Icons.Default.PersonSearch, "Find Worker", onLookup) }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isAccountant) { ActionButton(Modifier.weight(1f), Icons.Default.History, "History", onHistory) }
                    ActionButton(Modifier.weight(1f), Icons.Default.Lock, "Password", onChangePassword)
                }
            }

            // Recent entries
            if (!isAccountant) {
                item { Text("Recent Entries", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                items(recent) { entry -> RecentEntryRow(entry) }
            }
        }
    }
}

@Composable
private fun ActionButton(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier, shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(6.dp)); Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentEntryRow(entry: ProductionEntryEntity) {
    val stageLabel = when (entry.stage) { "raw_brick_making" -> "Raw Brick"; "raw_brick_transport" -> "Transport+Loading"; "baked_brick_unloading" -> "Unloading"; else -> entry.stage }
    val statusText = when (entry.status) { "PENDING" -> "Pending"; "SYNCED" -> "Synced"; "FAILED" -> "Failed"; else -> entry.status }
    val statusColor = when (entry.status) { "PENDING" -> MaterialTheme.colorScheme.error; "SYNCED" -> MaterialTheme.colorScheme.primary; "FAILED" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Qty: ${String.format(Locale.US, "%,.0f", entry.quantity)} - Rs. ${String.format(Locale.US, "%,.0f", entry.labourAmount)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
        }
    }
}
