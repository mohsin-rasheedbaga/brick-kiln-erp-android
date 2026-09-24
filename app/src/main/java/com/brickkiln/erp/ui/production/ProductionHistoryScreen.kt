package com.brickkiln.erp.ui.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.local.entity.ProductionEntryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionHistoryScreen(
    database: AppDatabase,
    onBack: () -> Unit,
) {
    val session by database.userSessionDao().observe().collectAsState(initial = null)
    val entries by database.productionEntryDao().observeRecent(session?.userId ?: "", 100).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Production History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No entries yet.\nTap + on the home screen to add one.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    HistoryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: ProductionEntryEntity) {
    val stageLabel = when (entry.stage) {
        "raw_brick_making" -> "Raw Brick Making"
        "raw_brick_transport" -> "Transport + Loading"
        "baked_brick_unloading" -> "Baked Brick Unloading"
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
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stageLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Qty: ${String.format(Locale.US, "%,.0f", entry.quantity)} • " +
                       "Rate: ${String.format(Locale.US, "%,.0f", entry.ratePer1000)}/1000 • " +
                       "Labour: Rs. ${String.format(Locale.US, "%,.2f", entry.labourAmount)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${entry.date} • ${dateFormat.format(Date(entry.createdAt))}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (entry.syncError != null) {
                Text(
                    text = "Error: ${entry.syncError}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
