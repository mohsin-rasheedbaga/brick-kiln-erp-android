package com.brickkiln.erp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
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
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    val recent by db.productionEntryDao().observeRecent(session?.userId ?: "", 10).collectAsState(initial = emptyList())

    // DIAGNOSTIC: Track worker count and sync status — VISIBLE on screen
    val workerCount by produceState(0) {
        value = db.workerDao().count()
    }

    var todayQty by remember { mutableStateOf(0.0) }
    var todayLabour by remember { mutableStateOf(0.0) }
    var pendingCount by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    // THIS IS THE KEY — sync status visible to user
    var syncStatus by remember { mutableStateOf("Loading...") }
    var syncError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session?.userId) {
        val userId = session?.userId ?: return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val stats = db.productionEntryDao().getStats(userId, today)
        todayQty = stats.todayQty
        todayLabour = stats.todayLabour
        pendingCount = stats.pendingCount

        // Fetch context from server
        syncStatus = "Syncing from server..."
        syncError = null
        try {
            val apiClient = com.brickkiln.erp.data.remote.ApiClient(BrickKilnApp.instance.settingsRepository)
            val authRepo = com.brickkiln.erp.data.repository.AuthRepository(apiClient, db)
            val ctx = authRepo.fetchMobileContext()
            if (ctx != null) {
                syncStatus = "✓ Synced: ${ctx.workers.size} workers, ${ctx.workTypes.size} work types"
                todayQty = ctx.todayStats.totalQty
                todayLabour = ctx.todayStats.totalLabour
            } else {
                syncError = com.brickkiln.erp.data.repository.AuthRepository.lastError
                    ?: "fetchMobileContext returned null"
                syncStatus = "✗ Sync failed"
            }
        } catch (e: Exception) {
            syncError = e.message ?: e.toString()
            syncStatus = "✗ Sync error"
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

            // DIAGNOSTIC CARD — shows sync status + worker count
            // This is CRITICAL — user can see if sync worked or failed
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (syncStatus.startsWith("✓"))
                            MaterialTheme.colorScheme.secondaryContainer
                        else if (syncStatus.startsWith("✗"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Data Sync Status",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = syncStatus,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (syncStatus.startsWith("✓"))
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else if (syncStatus.startsWith("✗"))
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        syncError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Error: $err",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Workers in local DB: $workerCount",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val s = BrickKilnApp.instance.settingsRepository
                        // Show server IP
                        Text(
                            text = "Server: (check settings)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                        value = formatNumber(todayQty),
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Today's Labour",
                        value = "Rs. ${formatNumber(todayLabour)}",
                    )
                }
            }

            // Sync status + button
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
                                    syncing = false
                                    // Re-trigger LaunchedEffect by toggling
                                    syncStatus = "Re-syncing..."
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
                Text(
                    text = "Recent Entries",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(recent) { entry ->
                RecentEntryRow(entry)
            }
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
