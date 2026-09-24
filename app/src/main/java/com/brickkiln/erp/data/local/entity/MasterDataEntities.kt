package com.brickkiln.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_types")
data class WorkTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val departmentId: String?,
    val defaultRatePer1000: Double,
    val isActive: Boolean = true,
)

@Entity(tableName = "kilns")
data class KilnEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val status: String,
)

@Entity(tableName = "brick_categories")
data class BrickCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val defaultSellingRate: Double,
)
