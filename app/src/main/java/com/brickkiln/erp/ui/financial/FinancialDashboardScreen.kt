package com.brickkiln.erp.ui.financial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.BrickKilnApp
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.AuthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDashboardScreen(onBack: () -> Unit) {
    val db = BrickKilnApp.instance.database
    val session by db.userSessionDao().observe().collectAsState(initial = null)
    var todayQty by remember { mutableStateOf(0.0) }
    var todayLabour by remember { mutableStateOf(0.0) }
    var workerCount by remember { mutableStateOf(0) }
    var recentEntries by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var syncStatus by remember { mutableStateOf("Loading...") }

    LaunchedEffect(session?.userId) {
        loading = true
        try {
            val apiClient = ApiClient(BrickKilnApp.instance.settingsRepository)
            val authRepo = AuthRepository(apiClient, db)
            val ctx = authRepo.fetchMobileContext()
            if (ctx != null) {
                todayQty = ctx.todayStats.totalQty
                todayLabour = ctx.todayStats.totalLabour
                workerCount = ctx.workers.size
                recentEntries = ctx.recentEntries.map { e ->
                    RecentEntry(e.stage, e.workerName ?: "—", e.quantity, e.labourAmount, e.date)
                }
                syncStatus = "Synced: ${ctx.workers.size} workers"
            } else { syncStatus = "Sync failed" }
        } catch (e: Exception) { syncStatus = "Error: ${e.message}" }
        loading = false
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Financial Dashboard") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        if (loading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                        containerColor = if (syncStatus.startsWith("Synced")) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Data Sync Status", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(syncStatus, fontSize = 13.sp)
                        }
                    }
                }
                item { Text("Today's Production", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(Modifier.weight(1f), "Total Quantity", String.format(java.util.Locale.US, "%,.0f", todayQty))
                        StatCard(Modifier.weight(1f), "Total Labour Cost", "Rs. ${String.format(java.util.Locale.US, "%,.0f", todayLabour)}")
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Total Workers", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("$workerCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { Text("Recent Production Entries", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                if (recentEntries.isEmpty()) {
                    item { Text("No entries yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(recentEntries) { entry ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(when(entry.stage) { "raw_brick_making" -> "Raw Brick"; "raw_brick_transport" -> "Transport+Loading"; "baked_brick_unloading" -> "Unloading"; else -> entry.stage }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${entry.workerName} - ${String.format(java.util.Locale.US, "%,.0f", entry.quantity)} qty", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(entry.date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Rs. ${String.format(java.util.Locale.US, "%,.0f", entry.labourAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class RecentEntry(val stage: String, val workerName: String, val quantity: Double, val labourAmount: Double, val date: String)

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
