package com.brickkiln.erp.data.repository

import android.util.Log
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

class AuthRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
) {

    private val gson = Gson()
    private val TAG = "AuthRepository"

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        Log.i(TAG, "login: attempting login for '$username'")
        val result = apiClient.callRpc(
            LoginResponse::class.java,
            channel = "auth:login",
            args = mapOf("username" to username, "password" to password),
        )
        if (result.isFailure) {
            Log.e(TAG, "login: failed — ${result.exceptionOrNull()?.message}")
            return result
        }

        val login = result.getOrNull()!!
        Log.i(TAG, "login: success — userId=${login.user.id}, roleId=${login.user.roleId}, deptId=${login.user.departmentId}")

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

        // IMMEDIATELY fetch mobile context to cache workers, work types, etc.
        // This is CRITICAL — without this, the user won't see any workers.
        Log.i(TAG, "login: fetching mobile context immediately...")
        val ctx = fetchMobileContext()
        if (ctx != null) {
            Log.i(TAG, "login: mobile context fetched — ${ctx.workers.size} workers cached")
        } else {
            Log.e(TAG, "login: FAILED to fetch mobile context — workers will NOT be available!")
        }

        return Result.success(login)
    }

    suspend fun logout() {
        database.userSessionDao().clear()
    }

    /**
     * Fetch mobile context (workers, work types, kilns, etc.) from the desktop ERP.
     * Caches everything in Room for offline use.
     * Returns the context response, or null on failure.
     */
    suspend fun fetchMobileContext(): MobileContextResponse? {
        val session = database.userSessionDao().get()
        if (session == null) {
            Log.e(TAG, "fetchMobileContext: no session — user not logged in")
            return null
        }

        Log.i(TAG, "fetchMobileContext: calling mobile:context...")
        val result = apiClient.callRpc(
            MobileContextResponse::class.java,
            channel = "mobile:context",
            args = mapOf("token" to session.token),
        )

        if (result.isFailure) {
            Log.e(TAG, "fetchMobileContext: FAILED — ${result.exceptionOrNull()?.message}")
            return null
        }

        val ctx = result.getOrNull()!!
        Log.i(TAG, "fetchMobileContext: SUCCESS — got ${ctx.workers.size} workers, ${ctx.workTypes.size} work types, ${ctx.kilns.size} kilns, ${ctx.brickCategories.size} categories")

        if (ctx.workers.isEmpty()) {
            Log.w(TAG, "fetchMobileContext: WARNING — server returned 0 workers!")
        } else {
            // Log first worker for debugging
            val w = ctx.workers[0]
            Log.i(TAG, "fetchMobileContext: first worker — id=${w.id}, code=${w.workerCode}, name=${w.fullName}")
        }

        // Cache workers locally — clear ALL then insert fresh
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
        if (workers.isNotEmpty()) {
            database.workerDao().upsertAll(workers)
            Log.i(TAG, "fetchMobileContext: cached ${workers.size} workers in local DB")
        }

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

        return ctx
    }
}
