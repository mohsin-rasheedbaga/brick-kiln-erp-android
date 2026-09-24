package com.brickkiln.erp.ui.worker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.local.entity.WorkerEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerLookupScreen(
    database: AppDatabase,
    onBack: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WorkerEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<WorkerEntity?>(null) }

    LaunchedEffect(Unit) {
        // Load all on first show
        scope.launch {
            results = database.workerDao().search("")
        }
    }

    LaunchedEffect(search) {
        scope.launch {
            results = database.workerDao().search(search.trim())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Worker") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search by name or code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(12.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No workers found.\nMake sure you've logged in and synced master data.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results) { worker ->
                        WorkerCard(worker = worker, onClick = { selected = worker })
                    }
                }
            }
        }
    }

    selected?.let { worker ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            },
            title = { Text(worker.fullName) },
            text = {
                Column {
                    DetailRow("Worker Code", worker.workerCode)
                    DetailRow("Employment Type", worker.employmentType)
                    DetailRow("Rate / 1000", "Rs. ${String.format(java.util.Locale.US, "%,.0f", worker.ratePer1000)}")
                    if (worker.employmentType == "salary") {
                        DetailRow("Monthly Salary", "Rs. ${String.format(java.util.Locale.US, "%,.0f", worker.monthlySalary)}")
                    } else {
                        DetailRow("Daily Wage", "Rs. ${String.format(java.util.Locale.US, "%,.0f", worker.dailyWage)}")
                    }
                    if (worker.fatherName != null) {
                        DetailRow("Father", worker.fatherName)
                    }
                }
            },
        )
    }
}

@Composable
private fun WorkerCard(worker: WorkerEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(worker.fullName, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${worker.workerCode} • Rs. ${String.format(java.util.Locale.US, "%,.0f", worker.ratePer1000)}/1000",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (worker.employmentType == "salary") "Monthly" else "Piece Rate",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
