package com.brickkiln.erp.data.remote

import com.google.gson.annotations.SerializedName

/**
 * RPC request envelope.
 * The desktop ERP's /rpc endpoint expects { channel, args }.
 */
data class RpcRequest(
    val channel: String,
    val args: Map<String, Any?>,
)

/**
 * RPC response envelope — mirrors desktop's IpcResult.
 */
data class RpcResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: RpcError? = null,
)

data class RpcError(
    val code: String,
    val message: String,
    val details: Any? = null,
)

// ============== Auth ==============
data class LoginRequest(
    val username: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val user: UserDto,
)

data class UserDto(
    val id: String,
    val username: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("roleId") val roleId: String,
    @SerializedName("roleName") val roleName: String,
    @SerializedName("departmentId") val departmentId: String? = null,
    @SerializedName("departmentName") val departmentName: String? = null,
    @SerializedName("mustChangePassword") val mustChangePassword: Boolean = false,
    val permissions: List<String> = emptyList(),
)

// ============== Mobile Context ==============
data class MobileContextResponse(
    val user: UserDto,
    val department: DepartmentDto?,
    val workers: List<WorkerDto>,
    val workTypes: List<WorkTypeDto>,
    val kilns: List<KilnDto>,
    val brickCategories: List<BrickCategoryDto>,
    val recentEntries: List<ProductionEntryDto>,
    val todayStats: TodayStatsDto,
    val timestamp: String,
)

data class DepartmentDto(
    val id: String,
    val name: String,
    val code: String,
)

data class WorkerDto(
    val id: String,
    @SerializedName("worker_code") val workerCode: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("father_name") val fatherName: String? = null,
    @SerializedName("department_id") val departmentId: String,
    @SerializedName("work_type_id") val workTypeId: String? = null,
    @SerializedName("rate_per_1000") val ratePer1000: Double,
    @SerializedName("employment_type") val employmentType: String = "piece_rate",
    @SerializedName("daily_wage") val dailyWage: Double = 0.0,
    @SerializedName("monthly_salary") val monthlySalary: Double = 0.0,
    val status: String = "active",
)

data class WorkTypeDto(
    val id: String,
    val name: String,
    val code: String,
    @SerializedName("department_id") val departmentId: String? = null,
    @SerializedName("default_rate_per_1000") val defaultRatePer1000: Double,
    @SerializedName("is_active") val isActive: Boolean = true,
)

data class KilnDto(
    val id: String,
    val name: String,
    val code: String,
    val status: String = "active",
)

data class BrickCategoryDto(
    val id: String,
    val name: String,
    val code: String,
    @SerializedName("default_selling_rate") val defaultSellingRate: Double = 0.0,
)

data class ProductionEntryDto(
    val id: String? = null,
    val stage: String,
    val date: String,
    val quantity: Double,
    @SerializedName("rate_per_1000") val ratePer1000: Double,
    @SerializedName("labour_amount") val labourAmount: Double,
    @SerializedName("worker_id") val workerId: String,
    @SerializedName("worker_name") val workerName: String? = null,
    @SerializedName("worker_code") val workerCode: String? = null,
    @SerializedName("work_type_id") val workTypeId: String? = null,
    @SerializedName("department_id") val departmentId: String? = null,
    @SerializedName("batch_id") val batchId: String? = null,
    @SerializedName("kiln_id") val kilnId: String? = null,
    @SerializedName("kiln_name") val kilnName: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    val notes: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class TodayStatsDto(
    @SerializedName("total_qty") val totalQty: Double,
    @SerializedName("total_labour") val totalLabour: Double,
    @SerializedName("entry_count") val entryCount: Int,
)

data class SubmitProductionResponse(
    val id: String,
    val stage: String,
    val date: String,
    val quantity: Double,
    val ratePer1000: Double,
    val labourAmount: Double,
    val workerId: String,
    val workTypeId: String,
    val departmentId: String,
    val batchId: String? = null,
    val kilnId: String? = null,
    val categoryId: String? = null,
    val notes: String? = null,
    val enteredBy: String,
    val createdAt: String,
)

data class SyncStatusResponse(
    @SerializedName("entriesSynced24h") val entriesSynced24h: Int,
    @SerializedName("lastSyncAt") val lastSyncAt: String?,
    @SerializedName("todayQty") val todayQty: Double,
    @SerializedName("todayLabour") val todayLabour: Double,
    @SerializedName("serverTime") val serverTime: String,
)
