package com.lifetrack.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.MainActivity
import kotlinx.coroutines.flow.StateFlow
import com.lifetrack.app.data.Metric
import com.lifetrack.app.ui.activity.ActivityDetailScreen
import com.lifetrack.app.ui.all.AllScreen
import com.lifetrack.app.ui.all.SectionLink
import com.lifetrack.app.ui.theme.accents
import com.lifetrack.app.ui.activity.ActivityScreen
import com.lifetrack.app.ui.alarms.AlarmsScreen
import com.lifetrack.app.ui.calories.CaloriesDetailScreen
import com.lifetrack.app.ui.calories.CaloriesScreen
import com.lifetrack.app.ui.habits.HabitDetailScreen
import com.lifetrack.app.ui.habits.HabitsScreen
import com.lifetrack.app.ui.home.HomeScreen
import com.lifetrack.app.ui.profile.ProfileScreen
import com.lifetrack.app.ui.routine.PlanScreen
import com.lifetrack.app.ui.routine.RoutineScreen
import com.lifetrack.app.ui.settings.FirstRun
import com.lifetrack.app.ui.settings.SettingsScreen
import com.lifetrack.app.ui.screentime.AppDetailScreen
import com.lifetrack.app.ui.screentime.ScreenTimeScreen

/**
 * Three tabs: Home, All, and Settings - the one place any access is asked for. All is every section as a round button. The sections themselves are
 * ordinary destinations pushed from either tab, and the bar stays visible on them (with All
 * lit) so getting back is always one tap. Screen time is on the grid too, as well as its Home card.
 */
sealed class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home : Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object All : Tab("all", "All", Icons.Outlined.Apps, Icons.Filled.Apps)
    data object Settings : Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Home, Tab.All, Tab.Settings)

/** The five sections the All tab opens. Routes are unchanged, so deep links keep working. */
object Sections {
    const val CALORIES = MainActivity.ROUTE_CALORIES
    const val ACTIVITY = "activity"
    const val HABITS = "habits"
    const val ROUTINE = MainActivity.ROUTE_ROUTINE
    const val ALARMS = "alarms"
    const val SCREEN_TIME = Routes.SCREEN_TIME
    val routes = listOf(CALORIES, ACTIVITY, HABITS, ROUTINE, ALARMS, SCREEN_TIME)
}

object Routes {
    const val PROFILE = "profile"
    const val CALORIES_DETAIL = "calories/detail"
    const val SCREEN_TIME = "screentime"
    const val APP_DETAIL = "screentime/{package}"
    const val HABIT_DETAIL = "habits/{habitId}"
    const val ACTIVITY_DETAIL = "activity/{metric}"
    const val PLAN = MainActivity.ROUTE_PLAN

    fun appDetail(pkg: String) = "screentime/$pkg"
    fun habitDetail(id: Long) = "habits/$id"
    fun activityDetail(metric: Metric) = "activity/${metric.key}"
}

@Composable
fun LifeTrackNav(pendingRoute: StateFlow<String?>, onRouteHandled: () -> Unit) {
    val context = LocalContext.current
    // Read once: a fresh install starts on Settings, so access is chosen before anything asks.
    var firstRun by remember { mutableStateOf(FirstRun.needed(context)) }
    val start = remember { if (firstRun) Tab.Settings.route else Tab.Home.route }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    /**
     * Tabs reset to their root: Home is Home, All is the grid. Everything pops back to Home -
     * not to the graph's start destination, which on a fresh install is Settings and is gone
     * from the stack once setup pops it, so popping to it would pop nothing and tabs would
     * pile up. Until setup is finished Home was never pushed, and Settings is the bottom.
     */
    fun switchTab(route: String) = nav.navigate(route) {
        val homeOnStack = runCatching { nav.getBackStackEntry(Tab.Home.route) }.isSuccess
        if (homeOnStack) popUpTo(Tab.Home.route) else popUpTo(nav.graph.findStartDestination().id)
        launchSingleTop = true
    }

    /** A section opens on top of whichever tab you were on, so Back returns there. */
    fun openSection(route: String) = nav.navigate(route) { launchSingleTop = true }

    // A tapped notification: sections and the plan are pushed on top of the current tab.
    val deepLink by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink) {
        val route = deepLink ?: return@LaunchedEffect
        if (route in Sections.routes) openSection(route) else nav.navigate(route)
        onRouteHandled()
    }

    // The bar shows on both tabs and on the five sections; detail screens get the full height.
    val route = current?.route
    val onRoot = tabs.any { it.route == route } || route in Sections.routes
    fun selected(tab: Tab) = when (tab) {
        Tab.Home -> route == Tab.Home.route
        Tab.Settings -> route == Tab.Settings.route
        Tab.All -> route != Tab.Home.route && route != Tab.Settings.route
    }
    val openSettings: () -> Unit = { switchTab(Tab.Settings.route) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onRoot) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        val selected = selected(tab)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            nav,
            startDestination = start,
            modifier = Modifier.padding(padding),
            // Detail screens slide in from the right; tab switches cross-fade.
            enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(240)) }
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenCalories = { openSection(Sections.CALORIES) },
                    onOpenActivity = { openSection(Sections.ACTIVITY) },
                    onOpenHabits = { openSection(Sections.HABITS) },
                    onOpenRoutine = { openSection(Sections.ROUTINE) },
                    onOpenScreenTime = { nav.navigate(Routes.SCREEN_TIME) },
                    onOpenAlarms = { openSection(Sections.ALARMS) },
                    onOpenProfile = { nav.navigate(Routes.PROFILE) }
                )
            }

            composable(Tab.All.route) {
                val a = accents()
                AllScreen(
                    sections = listOf(
                        SectionLink(Sections.CALORIES, "Calories", "Log meals", Icons.Filled.Restaurant, a.calories),
                        SectionLink(Sections.ACTIVITY, "Activity", "Steps & sleep", Icons.Filled.DirectionsWalk, a.activity),
                        SectionLink(Sections.HABITS, "Habits", "Timers & counts", Icons.Filled.Autorenew, a.habits),
                        SectionLink(Sections.ROUTINE, "Routine", "Hit or miss", Icons.Filled.TaskAlt, a.positive),
                        SectionLink(Sections.ALARMS, "Alarms", "Routine & alarms", Icons.Filled.Alarm, a.alarms),
                        SectionLink(Sections.SCREEN_TIME, "Screen time", "Apps & limits", Icons.Filled.PhoneAndroid, a.screen)
                    ),
                    onOpen = ::openSection
                )
            }

            composable(Tab.Settings.route) {
                SettingsScreen(
                    firstRun = firstRun,
                    onFinishSetup = {
                        FirstRun.done(context)
                        firstRun = false
                        nav.navigate(Tab.Home.route) {
                            popUpTo(nav.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenProfile = { nav.navigate(Routes.PROFILE) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(onBack = { nav.popBackStack() })
            }

            composable(Sections.CALORIES) {
                CaloriesScreen(onOpenDetail = { nav.navigate(Routes.CALORIES_DETAIL) })
            }
            composable(Routes.CALORIES_DETAIL) {
                CaloriesDetailScreen(onBack = { nav.popBackStack() })
            }

            composable(Sections.ACTIVITY) {
                ActivityScreen(onOpenMetric = { nav.navigate(Routes.activityDetail(it)) }, onOpenSettings = openSettings)
            }
            composable(
                Routes.ACTIVITY_DETAIL,
                arguments = listOf(navArgument("metric") { type = NavType.StringType })
            ) { entry ->
                val metric = Metric.from(entry.arguments?.getString("metric")) ?: Metric.Steps
                ActivityDetailScreen(metric = metric, onBack = { nav.popBackStack() })
            }

            composable(Sections.HABITS) {
                HabitsScreen(onOpenHabit = { nav.navigate(Routes.habitDetail(it)) })
            }
            composable(
                Routes.HABIT_DETAIL,
                arguments = listOf(navArgument("habitId") { type = NavType.LongType })
            ) { entry ->
                HabitDetailScreen(
                    habitId = entry.arguments?.getLong("habitId") ?: 0L,
                    onBack = { nav.popBackStack() }
                )
            }

            composable(Sections.ROUTINE) { RoutineScreen(onPlan = { nav.navigate(Routes.PLAN) }) }
            composable(Routes.PLAN) { PlanScreen(onBack = { nav.popBackStack() }) }

            composable(Sections.ALARMS) { AlarmsScreen(onPlan = { nav.navigate(Routes.PLAN) }, onOpenSettings = openSettings) }

            composable(Routes.SCREEN_TIME) {
                ScreenTimeScreen(
                    onOpenSettings = openSettings,
                    onOpenApp = { nav.navigate(Routes.appDetail(it)) },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.APP_DETAIL,
                arguments = listOf(navArgument("package") { type = NavType.StringType })
            ) { entry ->
                AppDetailScreen(
                    packageName = entry.arguments?.getString("package").orEmpty(),
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
