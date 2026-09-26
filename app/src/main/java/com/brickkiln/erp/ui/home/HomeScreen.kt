package com.brickkiln.erp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.flow.collectLatest
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
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    val localRecent by db.productionEntryDao().observeRecent(session?.userId ?: "", 5).collectAsState(initial = emptyList())

    // Server-side entries (from desktop ERP, fetched on login or refresh)
    var serverRecent by remember { mutableStateOf<List<ServerEntry>>(emptyList()) }
    var serverTodayQty by remember { mutableStateOf(0.0) }
    var serverTodayLabour by remember { mutableStateOf(0.0) }
    var lastRefresh by remember { mutableStateOf(0L) }

    // Stats (local — entries this user has entered on mobile)
    var pendingCount by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }

    // Fetch server-side data when session changes
    LaunchedEffect(session?.userId) {
        val userId = session?.userId ?: return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stats = db.productionEntryDao().getStats(userId, today)
        pendingCount = stats.pendingCount
        // Fetch from server
        val apiClient = com.brickkiln.erp.data.remote.ApiClient(BrickKilnApp.instance.settingsRepository)
        val authRepo = com.brickkiln.erp.data.repository.AuthRepository(apiClient, db)
        val result = authRepo.fetchMobileContext()
        result.onSuccess { ctx ->
            serverRecent = ctx.recentEntries.map { e ->
                ServerEntry(
                    stage = e.stage,
                    workerName = e.workerName ?: "—",
                    quantity = e.quantity,
                    labourAmount = e.labourAmount,
                    date = e.date,
                    createdAt = e.createdAt ?: "",
                )
            }
            serverTodayQty = ctx.todayStats.totalQty
            serverTodayLabour = ctx.todayStats.totalLabour
            lastRefresh = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brick Kiln ERP") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewEntry,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Entry") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Welcome card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Assalamu Alaikum,",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = session?.fullName ?: "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${session?.roleName ?: ""} • ${session?.departmentName ?: "—"}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Today's stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Today's Qty",
                        value = formatNumber(serverTodayQty),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Today's Labour",
                        value = "Rs. ${formatNumber(serverTodayLabour)}",
                    )
                }
            }

            // Sync status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Pending Sync",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$pendingCount entries",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    syncing = true
                                    SyncScheduler.syncNow()
                                    kotlinx.coroutines.delay(2000)
                                    // Refresh server-side data after sync
                                    val apiClient = com.brickkiln.erp.data.remote.ApiClient(BrickKilnApp.instance.settingsRepository)
                                    val authRepo = com.brickkiln.erp.data.repository.AuthRepository(apiClient, db)
                                    val result = authRepo.fetchMobileContext()
                                    result.onSuccess { ctx ->
                                        serverRecent = ctx.recentEntries.map { e ->
                                            ServerEntry(
                                                stage = e.stage,
                                                workerName = e.workerName ?: "—",
                                                quantity = e.quantity,
                                                labourAmount = e.labourAmount,
                                                date = e.date,
                                                createdAt = e.createdAt ?: "",
                                            )
                                        }
                                        serverTodayQty = ctx.todayStats.totalQty
                                        serverTodayLabour = ctx.todayStats.totalLabour
                                        lastRefresh = System.currentTimeMillis()
                                    }
                                    val userId = session?.userId ?: return@launch
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    val stats = db.productionEntryDao().getStats(userId, today)
                                    pendingCount = stats.pendingCount
                                    syncing = false
                                }
                            },
                            enabled = !syncing,
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (syncing) "Syncing..." else "Sync Now")
                        }
                    }
                }
            }

            // Quick actions
            item {
                Text(
                    text = "Quick Actions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PersonSearch,
                        label = "Find Worker",
                        onClick = onLookup,
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.History,
                        label = "History",
                        onClick = onHistory,
                    )
                }
            }

            // Recent entries
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Entries (Department)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (lastRefresh > 0) {
                        Text(
                            text = "Updated ${SimpleDateFormat("HH:mm", Locale.US).format(Date(lastRefresh))}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            // Show server-side entries first (department-wide)
            if (serverRecent.isEmpty() && localRecent.isEmpty()) {
                item {
                    Text(
                        text = "No entries yet. Tap + below to add your first production entry.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(serverRecent) { entry ->
                ServerEntryRow(entry)
            }
            // Also show local entries that haven't synced yet (pending/failed)
            if (localRecent.isNotEmpty() && pendingCount > 0) {
                item {
                    Text(
                        text = "Pending Sync (your mobile entries)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(localRecent.filter { it.status == "PENDING" || it.status == "FAILED" }.take(5)) { entry ->
                    RecentEntryRow(entry)
                }
            }
        }
    }
}

private data class ServerEntry(
    val stage: String,
    val workerName: String,
    val quantity: Double,
    val labourAmount: Double,
    val date: String,
    val createdAt: String,
)

@Composable
private fun ServerEntryRow(entry: ServerEntry) {
    val stageLabel = when (entry.stage) {
        "raw_brick_making" -> "Raw Brick"
        "raw_brick_transport" -> "Transport+Loading"
        "baked_brick_unloading" -> "Unloading"
        else -> entry.stage
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${entry.workerName} • ${formatNumber(entry.quantity)} qty",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entry.date} • Rs. ${formatNumber(entry.labourAmount)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Synced",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentEntryRow(entry: ProductionEntryEntity) {
    val stageLabel = when (entry.stage) {
        "raw_brick_making" -> "Raw Brick"
        "raw_brick_transport" -> "Transport+Loading"
        "baked_brick_unloading" -> "Unloading"
        else -> entry.stage
    }
    val statusColor = when (entry.status) {
        "PENDING" -> MaterialTheme.colorScheme.error
        "SYNCED" -> MaterialTheme.colorScheme.primary
        "FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (entry.status) {
        "PENDING" -> "⏳ Pending"
        "SYNCED" -> "✓ Synced"
        "FAILED" -> "✗ Failed"
        else -> entry.status
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Qty: ${formatNumber(entry.quantity)} • Rs. ${formatNumber(entry.labourAmount)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entry.date}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Text(
                text = statusText,
                fontSize = 12.sp,
                color = statusColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatNumber(n: Double): String {
    return String.format(Locale.US, "%,.0f", n)
}
