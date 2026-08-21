package com.rmfacilities.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rmfacilities.app.RMFacilitiesApp
import com.rmfacilities.app.ui.screens.*
import com.rmfacilities.app.viewmodel.*

@Composable
fun AppNavGraph(app: RMFacilitiesApp) {
    val navController = rememberNavController()
    val factory = AppViewModelFactory(app)

    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    val startDestination = if (authState.isAuthenticated) AppDestination.Dashboard.route else AppDestination.Login.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestination.Login.route) {
            LoginScreen(
                state = authState,
                onLogin = { email, senha -> authViewModel.login(email, senha) },
                onDismissError = { authViewModel.clearError() },
                onSuccess = {
                    navController.navigate(AppDestination.Dashboard.route) {
                        popUpTo(AppDestination.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AppDestination.Dashboard.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: DashboardViewModel = viewModel(factory = factory)
                DashboardScreen(vm, modifier = innerModifier)
            }
        }

        composable(AppDestination.Employees.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: EmployeesViewModel = viewModel(factory = factory)
                EmployeesScreen(vm, onOpenDetail = { navController.navigate(AppDestination.EmployeeDetail.create(it)) }, modifier = innerModifier)
            }
        }

        composable(
            route = AppDestination.EmployeeDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val vm: EmployeesViewModel = viewModel(factory = factory)
            EmployeeDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppDestination.Posts.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: PostsViewModel = viewModel(factory = factory)
                PostsScreen(vm, onOpenDetail = { navController.navigate(AppDestination.PostDetail.create(it)) }, modifier = innerModifier)
            }
        }

        composable(
            route = AppDestination.PostDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val vm: PostsViewModel = viewModel(factory = factory)
            PostDetailScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                vm = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppDestination.Visits.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: VisitsViewModel = viewModel(factory = factory)
                VisitsScreen(vm, modifier = innerModifier)
            }
        }

        composable(AppDestination.Occurrences.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: OccurrencesViewModel = viewModel(factory = factory)
                OccurrencesScreen(vm, modifier = innerModifier)
            }
        }

        composable(AppDestination.Tasks.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: TasksViewModel = viewModel(factory = factory)
                TasksScreen(vm, modifier = innerModifier)
            }
        }

        composable(AppDestination.Reports.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: ReportsViewModel = viewModel(factory = factory)
                ReportsScreen(vm, modifier = innerModifier)
            }
        }

        composable(AppDestination.Settings.route) {
            MainScaffold(navController = navController) { innerModifier ->
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    vm = vm,
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(AppDestination.Login.route) {
                            popUpTo(0)
                        }
                    },
                    modifier = innerModifier
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    navController: androidx.navigation.NavHostController,
    content: @Composable (Modifier) -> Unit
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("RM Supervisor") },
                actions = {
                    IconButton(onClick = { navController.navigate(AppDestination.Settings.route) { launchSingleTop = true } }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                            }
                        },
                        label = { Text(item.label) },
                        icon = { Icon(item.iconVector(), contentDescription = item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        content(Modifier.padding(paddingValues))
    }
}

private fun BottomNavItem.iconVector() = when (icon) {
    "people" -> Icons.Filled.People
    "business" -> Icons.Filled.Business
    "event" -> Icons.Filled.Event
    "warning" -> Icons.Filled.Warning
    else -> Icons.Filled.Home
}
