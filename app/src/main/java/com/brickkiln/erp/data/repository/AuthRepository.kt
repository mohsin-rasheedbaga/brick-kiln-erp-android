package com.brickkiln.erp.data.repository

import com.brickkiln.erp.data.local.AppDatabase
import com.brickkiln.erp.data.local.entity.UserSessionEntity
import com.brickkiln.erp.data.local.entity.WorkerEntity
import com.brickkiln.erp.data.local.entity.WorkTypeEntity
import com.brickkiln.erp.data.local.entity.KilnEntity
import com.brickkiln.erp.data.local.entity.BrickCategoryEntity
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.remote.LoginResponse
import com.brickkiln.erp.data.remote.MobileContextResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AuthRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
) {

    private val gson = Gson()

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        val result = apiClient.callRpc(
            LoginResponse::class.java,
            channel = "auth:login",
            args = mapOf("username" to username, "password" to password),
        )
        if (result.isFailure) return result

        val login = result.getOrNull()!!
        // Persist session locally
        val session = UserSessionEntity(
            token = login.token,
            userId = login.user.id,
            username = login.user.username,
            fullName = login.user.fullName,
            roleId = login.user.roleId,
            roleName = login.user.roleName,
            departmentId = login.user.departmentId,
            departmentName = login.user.departmentName,
            permissions = gson.toJson(login.user.permissions),
        )
        database.userSessionDao().upsert(session)
        return Result.success(login)
    }

    suspend fun logout() {
        database.userSessionDao().clear()
    }

    suspend fun fetchMobileContext(): Result<MobileContextResponse> {
        val session = database.userSessionDao().get()
            ?: return Result.failure(Exception("Not logged in."))
        val result = apiClient.callRpc(
            MobileContextResponse::class.java,
            channel = "mobile:context",
            args = mapOf("token" to session.token),
        )
        if (result.isFailure) return result

        val ctx = result.getOrNull()!!

        // Cache workers locally
        val workers = ctx.workers.map {
            WorkerEntity(
                id = it.id,
                workerCode = it.workerCode,
                fullName = it.fullName,
                fatherName = it.fatherName,
                departmentId = it.departmentId,
                workTypeId = it.workTypeId,
                ratePer1000 = it.ratePer1000,
                employmentType = it.employmentType,
                dailyWage = it.dailyWage,
                monthlySalary = it.monthlySalary,
                status = it.status,
            )
        }
        database.workerDao().clear()
        database.workerDao().upsertAll(workers)

        // Cache work types
        database.masterDataDao().clearWorkTypes()
        database.masterDataDao().upsertWorkTypes(ctx.workTypes.map {
            WorkTypeEntity(
                id = it.id, name = it.name, code = it.code,
                departmentId = it.departmentId,
                defaultRatePer1000 = it.defaultRatePer1000,
                isActive = it.isActive,
            )
        })

        // Cache kilns
        database.masterDataDao().clearKilns()
        database.masterDataDao().upsertKilns(ctx.kilns.map {
            KilnEntity(id = it.id, name = it.name, code = it.code, status = it.status)
        })

        // Cache brick categories
        database.masterDataDao().clearBrickCategories()
        database.masterDataDao().upsertBrickCategories(ctx.brickCategories.map {
            BrickCategoryEntity(
                id = it.id, name = it.name, code = it.code,
                defaultSellingRate = it.defaultSellingRate,
            )
        })

        return Result.success(ctx)
    }
}
