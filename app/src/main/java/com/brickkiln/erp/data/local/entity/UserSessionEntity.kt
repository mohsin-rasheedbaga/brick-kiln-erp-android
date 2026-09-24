package com.brickkiln.erp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val id: String = "current",
    val token: String,
    val userId: String,
    val username: String,
    val fullName: String,
    val roleId: String,
    val roleName: String,
    val departmentId: String?,
    val departmentName: String?,
    val permissions: String,  // JSON array of permission codes
    val loggedInAt: Long = System.currentTimeMillis(),
)
