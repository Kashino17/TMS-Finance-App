package com.tms.banking.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tms.banking.TmsApp
import com.tms.banking.ui.account.AccountDetailScreen
import com.tms.banking.ui.add.AddTransactionScreen
import com.tms.banking.ui.categories.CategoriesScreen
import com.tms.banking.ui.home.HomeScreen
import com.tms.banking.ui.loans.LoansScreen
import com.tms.banking.ui.settings.SettingsScreen
import com.tms.banking.ui.theme.Background
import com.tms.banking.ui.theme.OnSurface
import com.tms.banking.ui.theme.Primary
import com.tms.banking.ui.theme.Surface

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Categories : Screen("categories", "Categories", Icons.Filled.PieChart)
    object Loans : Screen("loans", "Loans", Icons.Filled.AccountBalance)
    object Add : Screen("add", "Add", Icons.Filled.Add)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object AccountDetail : Screen("account/{accountId}", "Account", Icons.Filled.Home) {
        fun createRoute(accountId: Int) = "account/$accountId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Categories,
    Screen.Loans,
    Screen.Add,
    Screen.Settings
)

@Composable
fun TmsNavigation(app: TmsApp) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Surface,
                    contentColor = OnSurface
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                unselectedIconColor = OnSurface,
                                unselectedTextColor = OnSurface,
                                indicatorColor = Surface
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    app = app,
                    onAccountClick = { accountId ->
                        navController.navigate(Screen.AccountDetail.createRoute(accountId))
                    }
                )
            }
            composable(Screen.Categories.route) {
                CategoriesScreen(app = app)
            }
            composable(Screen.Loans.route) {
                LoansScreen(app = app)
            }
            composable(Screen.Add.route) {
                AddTransactionScreen(
                    app = app,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(app = app)
            }
            composable(
                route = Screen.AccountDetail.route,
                arguments = listOf(navArgument("accountId") { type = NavType.IntType })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getInt("accountId") ?: return@composable
                AccountDetailScreen(
                    app = app,
                    accountId = accountId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
