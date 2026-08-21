package com.rmfacilities.app.ui.navigation

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Dashboard : AppDestination("dashboard")
    data object Employees : AppDestination("employees")
    data object EmployeeDetail : AppDestination("employees/{id}") {
        fun create(id: String) = "employees/$id"
    }
    data object Posts : AppDestination("posts")
    data object PostDetail : AppDestination("posts/{id}") {
        fun create(id: String) = "posts/$id"
    }
    data object Visits : AppDestination("visits")
    data object Occurrences : AppDestination("occurrences")
    data object Tasks : AppDestination("tasks")
    data object Reports : AppDestination("reports")
    data object Settings : AppDestination("settings")
}

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: String
)

val bottomNavItems = listOf(
    BottomNavItem("Início", AppDestination.Dashboard.route, "home"),
    BottomNavItem("Equipe", AppDestination.Employees.route, "people"),
    BottomNavItem("Postos", AppDestination.Posts.route, "business"),
    BottomNavItem("Visitas", AppDestination.Visits.route, "event"),
    BottomNavItem("Ocorrências", AppDestination.Occurrences.route, "warning")
)
