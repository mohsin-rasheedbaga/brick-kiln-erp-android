package com.brickkiln.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    val workerCode: String,
    val fullName: String,
    val fatherName: String?,
    val departmentId: String,
    val workTypeId: String?,
    val ratePer1000: Double,
    val employmentType: String,   // "piece_rate" | "salary"
    val dailyWage: Double,
    val monthlySalary: Double,
    val status: String,           // "active" | "inactive" | "left"
    val lastSyncedAt: Long = System.currentTimeMillis(),
)
