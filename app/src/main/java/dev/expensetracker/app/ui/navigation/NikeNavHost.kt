package dev.expensetracker.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.expensetracker.app.data.ExpenseRepository
import dev.expensetracker.app.ui.accounts.AccountsScreen
import dev.expensetracker.app.ui.budget.BudgetEditorScreen
import dev.expensetracker.app.ui.categories.CategoryManagerScreen
import dev.expensetracker.app.ui.home.HomeScreen
import dev.expensetracker.app.ui.review.ReviewScreen
import dev.expensetracker.app.ui.settings.SettingsScreen
import dev.expensetracker.app.ui.spends.SpendsScreen
import dev.expensetracker.app.ui.theme.AccentPrimary
import dev.expensetracker.app.ui.theme.AccentTheme
import dev.expensetracker.app.ui.theme.BackgroundBase
import dev.expensetracker.app.ui.theme.SurfaceCard
import dev.expensetracker.app.ui.theme.TextSecondary
import dev.expensetracker.app.ui.transaction.TransactionDetailScreen
import java.time.YearMonth

/** Exactly four top-level destinations, per blueprints/design-system-and-navigation.md. */
sealed class ExpenseDestination(val route: String, val label: String) {
    data object Home : ExpenseDestination("home", "Home")
    data object Spends : ExpenseDestination("spends", "Spends")
    data object Accounts : ExpenseDestination("accounts", "Accounts")
    data object Settings : ExpenseDestination("settings", "Settings")
}

/**
 * Full-screen routes layered on the same host. They are not bottom-nav destinations, so the bar
 * hides while they are open and the user returns to wherever they came from.
 */
object DetailRoutes {
    const val TRANSACTION = "transaction/{transactionId}"
    const val REVIEW = "review"
    const val CATEGORIES = "categories?create={create}"
    const val BUDGET = "budget/{month}/{categoryId}"

    fun transaction(id: Long) = "transaction/$id"
    fun categories(startInCreateMode: Boolean = false) = "categories?create=$startInCreateMode"
    fun budget(month: YearMonth, categoryId: String) = "budget/$month/$categoryId"
}

private val destinations = listOf(
    ExpenseDestination.Home,
    ExpenseDestination.Spends,
    ExpenseDestination.Accounts,
    ExpenseDestination.Settings,
)

private val topLevelRoutes = destinations.map { it.route }.toSet()

/**
 * [onSelectAccent] defaults to a no-op only so Compose tests can render the shell without a
 * preferences store. A real call site that forgets it leaves the theme picker inert with no
 * compile error, which is the same trap `onRunBackfill` documents in
 * blueprints/permissions-and-onboarding.md — check both when this signature changes.
 */
@Composable
fun ExpenseNavHost(
    repository: ExpenseRepository,
    onRunBackfill: () -> Unit = {},
    selectedAccent: AccentTheme = AccentTheme.DEFAULT,
    onSelectAccent: (AccentTheme) -> Unit = {},
) {
    val navController = rememberNavController()

    // The month filter is hoisted here rather than owned per screen, so Home and Spends can never
    // disagree about which month is on screen. Held as its ISO string because that is trivially
    // saveable, which is what makes the choice survive rotation and process death.
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedMonth = remember(monthKey) { parseYearMonth(monthKey) ?: YearMonth.now() }
    val onSelectMonth: (YearMonth) -> Unit = { monthKey = it.toString() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in topLevelRoutes

    Scaffold(
        containerColor = BackgroundBase,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                NavigationBar(containerColor = SurfaceCard) {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            // The description sits on the item rather than on the icon so that the
                            // tab is one node that both names itself and carries the click, instead
                            // of a label and an icon that a reader would announce twice.
                            modifier = Modifier.semantics { contentDescription = destination.label },
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(iconFor(destination), contentDescription = null) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentPrimary,
                                selectedTextColor = AccentPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = SurfaceCard,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ExpenseDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ExpenseDestination.Home.route) {
                HomeScreen(
                    repository = repository,
                    selectedMonth = selectedMonth,
                    onSelectMonth = onSelectMonth,
                    onOpenTransaction = { navController.navigate(DetailRoutes.transaction(it)) },
                    onOpenReview = { navController.navigate(DetailRoutes.REVIEW) },
                    onOpenSpends = {
                        navController.navigate(ExpenseDestination.Spends.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(ExpenseDestination.Spends.route) {
                SpendsScreen(
                    repository = repository,
                    selectedMonth = selectedMonth,
                    onSelectMonth = onSelectMonth,
                    onOpenTransaction = { navController.navigate(DetailRoutes.transaction(it)) },
                    onOpenReview = { navController.navigate(DetailRoutes.REVIEW) },
                    onEditBudget = { navController.navigate(DetailRoutes.budget(selectedMonth, it)) },
                )
            }

            composable(ExpenseDestination.Accounts.route) {
                AccountsScreen(repository = repository, selectedMonth = selectedMonth)
            }

            composable(ExpenseDestination.Settings.route) {
                SettingsScreen(
                    repository = repository,
                    onRunBackfill = onRunBackfill,
                    onOpenCategories = { navController.navigate(DetailRoutes.categories()) },
                    selectedAccent = selectedAccent,
                    onSelectAccent = onSelectAccent,
                )
            }

            composable(
                route = DetailRoutes.TRANSACTION,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            ) { entry ->
                TransactionDetailScreen(
                    repository = repository,
                    transactionId = entry.arguments?.getLong("transactionId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onCreateCategory = {
                        navController.navigate(DetailRoutes.categories(startInCreateMode = true))
                    },
                )
            }

            composable(DetailRoutes.REVIEW) {
                ReviewScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onOpenTransaction = { navController.navigate(DetailRoutes.transaction(it)) },
                )
            }

            composable(
                route = DetailRoutes.CATEGORIES,
                arguments = listOf(
                    navArgument("create") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                CategoryManagerScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    startInCreateMode = entry.arguments?.getBoolean("create") == true,
                )
            }

            composable(
                route = DetailRoutes.BUDGET,
                arguments = listOf(
                    navArgument("month") { type = NavType.StringType },
                    navArgument("categoryId") { type = NavType.StringType },
                ),
            ) { entry ->
                val month = entry.arguments?.getString("month")?.let(::parseYearMonth) ?: YearMonth.now()
                BudgetEditorScreen(
                    repository = repository,
                    month = month,
                    categoryId = entry.arguments?.getString("categoryId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun iconFor(destination: ExpenseDestination) = when (destination) {
    ExpenseDestination.Home -> Icons.Filled.Home
    ExpenseDestination.Spends -> Icons.Filled.PieChart
    ExpenseDestination.Accounts -> Icons.Filled.AccountBalanceWallet
    ExpenseDestination.Settings -> Icons.Filled.Settings
}

/** A route argument is attacker-free but still user-visible text; a bad value falls back. */
private fun parseYearMonth(raw: String): YearMonth? = runCatching { YearMonth.parse(raw) }.getOrNull()
