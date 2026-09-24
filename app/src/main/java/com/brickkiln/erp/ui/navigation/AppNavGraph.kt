package com.brickkiln.erp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.brickkiln.erp.BrickKilnApp
import com.brickkiln.erp.data.remote.ApiClient
import com.brickkiln.erp.data.repository.AuthRepository
import com.brickkiln.erp.data.repository.ProductionRepository
import com.brickkiln.erp.ui.home.HomeScreen
import com.brickkiln.erp.ui.login.LoginScreen
import com.brickkiln.erp.ui.login.LoginViewModel
import com.brickkiln.erp.ui.production.ProductionEntryScreen
import com.brickkiln.erp.ui.production.ProductionHistoryScreen
import com.brickkiln.erp.ui.settings.SettingsScreen
import com.brickkiln.erp.ui.worker.WorkerLookupScreen
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val PRODUCTION_ENTRY = "production_entry"
    const val PRODUCTION_HISTORY = "production_history"
    const val WORKER_LOOKUP = "worker_lookup"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val app = BrickKilnApp.instance

    // Repositories
    val apiClient = ApiClient(app.settingsRepository)
    val authRepo = AuthRepository(apiClient, app.database)
    val prodRepo = ProductionRepository(apiClient, app.database)

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(factory = LoginViewModel.factory(authRepo, app.settingsRepository, apiClient))
            LoginScreen(
                viewModel = vm,
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                productionRepo = prodRepo,
                onNewEntry = { navController.navigate(Routes.PRODUCTION_ENTRY) },
                onHistory = { navController.navigate(Routes.PRODUCTION_HISTORY) },
                onLookup = { navController.navigate(Routes.WORKER_LOOKUP) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = {
                    MainScope().launch {
                        authRepo.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.PRODUCTION_ENTRY) {
            ProductionEntryScreen(
                productionRepo = prodRepo,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.PRODUCTION_HISTORY) {
            ProductionHistoryScreen(
                database = BrickKilnApp.instance.database,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.WORKER_LOOKUP) {
            WorkerLookupScreen(
                database = BrickKilnApp.instance.database,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsRepository = app.settingsRepository,
                apiClient = apiClient,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
