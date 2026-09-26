package com.brickkiln.erp.ui.production

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brickkiln.erp.BrickKilnApp
import com.brickkiln.erp.data.local.entity.WorkerEntity
import com.brickkiln.erp.data.repository.ProductionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class StageOption(val value: String, val label: String)

private val ALL_STAGES = listOf(
    StageOption("raw_brick_making", "Raw Brick Making (کچی اینٹ بنانا)"),
    StageOption("raw_brick_transport", "Transport + Loading (بھٹے تک + بھٹے میں جوڑنا)"),
    StageOption("baked_brick_unloading", "Baked Brick Unloading (پکی اینٹ نکالنا)"),
)

/**
 * Returns the stages this user is allowed to enter data for.
 *
 * Role-based restrictions — same logic as the desktop ERP:
 *   - role-raw-maker (Raw Brick Operator) → ONLY raw_brick_making
 *   - role-transport (Transport Operator) → ONLY raw_brick_transport
 *   - role-kiln-unload (Unloading Operator) → ONLY baked_brick_unloading
 *   - role-super-admin, role-admin, role-manager, role-accountant → ALL stages
 *   - role-sales → NO stages (sales people don't enter production)
 *
 * Also map by department ID (for legacy users who may have department set but not role):
 *   - dept-raw-brick → raw_brick_making
 *   - dept-transport → raw_brick_transport
 *   - dept-unloading → baked_brick_unloading
 */
private fun allowedStagesForUser(roleId: String?, departmentId: String?): List<StageOption> {
    // First check role
    when (roleId) {
        "role-raw-maker" -> return listOf(ALL_STAGES[0]) // Raw Brick Making only
        "role-transport" -> return listOf(ALL_STAGES[1]) // Transport + Loading only
        "role-kiln-unload" -> return listOf(ALL_STAGES[2]) // Unloading only
        "role-super-admin", "role-admin", "role-manager", "role-accountant" -> return ALL_STAGES
        "role-sales" -> return emptyList()
    }
    // Fall back to department
    when (departmentId) {
        "dept-raw-brick" -> return listOf(ALL_STAGES[0])
        "dept-transport" -> return listOf(ALL_STAGES[1])
        "dept-unloading" -> return listOf(ALL_STAGES[2])
        "dept-sales" -> return emptyList()
        "dept-accounts", "dept-accountant" -> return ALL_STAGES // accountant sees all
    }
    // Default: all stages (super-admin etc.)
    return ALL_STAGES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionEntryScreen(
    productionRepo: ProductionRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val db = BrickKilnApp.instance.database
    val workers by db.workerDao().observeAll().collectAsState(initial = emptyList())
    val session by db.userSessionDao().observe().collectAsState(initial = null)

    // Determine allowed stages based on user's role / department
    val allowedStages = remember(session?.roleId, session?.departmentId) {
        allowedStagesForUser(session?.roleId, session?.departmentId)
    }

    // Default to first allowed stage (or empty if no stages)
    var selectedStage by remember(allowedStages) {
        mutableStateOf(allowedStages.firstOrNull()?.value ?: "")
    }
    var selectedWorkerId by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showWorkerPicker by remember { mutableStateOf(false) }

    val selectedWorker = workers.find { it.id == selectedWorkerId }
    val workTypes by produceState<List<com.brickkiln.erp.data.local.entity.WorkTypeEntity>>(emptyList()) {
        value = db.masterDataDao().getWorkTypes()
    }
    val kilns by produceState<List<com.brickkiln.erp.data.local.entity.KilnEntity>>(emptyList()) {
        value = db.masterDataDao().getKilns()
    }
    val categories by produceState<List<com.brickkiln.erp.data.local.entity.BrickCategoryEntity>>(emptyList()) {
        value = db.masterDataDao().getBrickCategories()
    }

    var selectedKilnId by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }

    // Auto-fill rate when worker selected
    LaunchedEffect(selectedWorkerId) {
        val w = selectedWorker
        if (w != null && rate.isBlank()) {
            rate = w.ratePer1000.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Production Entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Stage selector — only shows stages the user is allowed to enter
            Text("Stage / مرحلہ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (allowedStages.isEmpty()) {
                Text(
                    text = "Your account does not have permission to enter production data.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (allowedStages.size == 1) {
                // Single stage — show as info card (no radio needed)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "✓ ${allowedStages[0].label}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            } else {
                // Multiple stages — show as radio buttons
                allowedStages.forEach { stage ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedStage == stage.value,
                            onClick = { selectedStage = stage.value },
                        )
                        Text(stage.label, fontSize = 14.sp)
                    }
                }
            }

            HorizontalDivider()

            // Worker picker
            Text("Worker / ورکر", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                onClick = { showWorkerPicker = true },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (selectedWorker != null) {
                        Text(selectedWorker.fullName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${selectedWorker.workerCode} • Rate: Rs. ${selectedWorker.ratePer1000}/1000",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Tap to select worker", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Quantity + Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity / مقدار") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Rate / 1000") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            // Kiln selector (for transport + unloading)
            if (selectedStage == "raw_brick_transport" || selectedStage == "baked_brick_unloading") {
                Text("Kiln / بھٹا", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                KilnDropdown(
                    kilns = kilns,
                    selectedId = selectedKilnId,
                    onSelect = { selectedKilnId = it },
                )
            }

            // Category (for unloading)
            if (selectedStage == "baked_brick_unloading") {
                Text("Brick Category / قسم", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CategoryDropdown(
                    categories = categories,
                    selectedId = selectedCategoryId,
                    onSelect = { selectedCategoryId = it },
                )
            }

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // Computed labour preview
            val qty = quantity.toDoubleOrNull() ?: 0.0
            val rateVal = rate.toDoubleOrNull() ?: 0.0
            val labour = if (qty > 0 && rateVal > 0) (qty / 1000.0) * rateVal else 0.0
            if (labour > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Labour Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = "Rs. ${String.format(Locale.US, "%,.2f", labour)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (selectedStage.isBlank()) {
                        error = "Your account does not have permission to enter production data. Contact your administrator."
                        return@Button
                    }
                    if (selectedWorkerId.isBlank()) {
                        error = "Please select a worker."
                        return@Button
                    }
                    if (qty <= 0) {
                        error = "Quantity must be greater than zero."
                        return@Button
                    }
                    if (rateVal <= 0) {
                        error = "Rate must be greater than zero."
                        return@Button
                    }
                    scope.launch {
                        saving = true
                        error = null
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val workTypeId = selectedWorker?.workTypeId
                            ?: workTypes.firstOrNull()?.id
                        try {
                            productionRepo.createLocalEntry(
                                stage = selectedStage,
                                workerId = selectedWorkerId,
                                quantity = qty,
                                ratePer1000 = rateVal,
                                workTypeId = workTypeId,
                                kilnId = selectedKilnId.ifBlank { null },
                                categoryId = selectedCategoryId.ifBlank { null },
                                date = today,
                                notes = notes.ifBlank { null },
                            )
                            success = true
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Entry", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (success) {
                AlertDialog(
                    onDismissRequest = onDone,
                    confirmButton = {
                        TextButton(onClick = onDone) { Text("OK") }
                    },
                    title = { Text("Entry Saved") },
                    text = {
                        Text("Production entry saved locally. It will sync to the server automatically when network is available.")
                    },
                )
            }
        }
    }

    if (showWorkerPicker) {
        WorkerPickerDialog(
            workers = workers,
            onPick = { id ->
                selectedWorkerId = id
                showWorkerPicker = false
            },
            onDismiss = { showWorkerPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KilnDropdown(
    kilns: List<com.brickkiln.erp.data.local.entity.KilnEntity>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = kilns.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.name ?: "— None —",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("— None —") }, onClick = { onSelect(""); expanded = false })
            kilns.forEach { k ->
                DropdownMenuItem(text = { Text(k.name) }, onClick = { onSelect(k.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<com.brickkiln.erp.data.local.entity.BrickCategoryEntity>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.find { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            placeholder = { Text("Select category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}

@Composable
private fun WorkerPickerDialog(
    workers: List<WorkerEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = workers.filter {
        it.fullName.contains(search, ignoreCase = true) ||
        it.workerCode.contains(search, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Select Worker") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search by name or code") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered) { w ->
                        ListItem(
                            headlineContent = { Text(w.fullName) },
                            supportingContent = { Text("${w.workerCode} • Rs. ${w.ratePer1000}/1000") },
                            modifier = Modifier.clickable { onPick(w.id) },
                        )
                    }
                }
            }
        },
    )
}
