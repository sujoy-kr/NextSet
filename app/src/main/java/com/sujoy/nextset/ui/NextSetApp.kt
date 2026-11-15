package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.sujoy.nextset.NextSetApplication
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs =
    listOf(
        Tab("home", R.string.home, Icons.Default.Home),
        Tab("workouts", R.string.workouts, Icons.Default.FitnessCenter),
        Tab("schedule", R.string.week, Icons.Default.CalendarMonth),
        Tab("history", R.string.history, Icons.Default.History),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextSetApp(application: NextSetApplication) {
    val vm: AppViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        AppViewModel(
                            application.workouts,
                            application.sessions,
                            application.settings,
                        )
                    }
                }
        )
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val mainRoute = route in tabs.map { it.route } || route == "settings"
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    val requested by application.requestedSession.collectAsStateWithLifecycle()
    val requestedHome by application.requestedHome.collectAsStateWithLifecycle()
    LaunchedEffect(requested, requestedHome, state.loading, state.error) {
        if (state.loading || state.error != null) return@LaunchedEffect
        if (requestedHome) {
            nav.navigate("home") {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
            application.requestedHome.value = false
        }
        if (requested != null) {
            nav.navigate("session/$requested") { launchSingleTop = true }
            application.requestedSession.value = null
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    NextSetTheme(state.settings.theme) {
        Scaffold(
            contentWindowInsets =
                if (route == "session/{id}") WindowInsets(0, 0, 0, 0)
                else ScaffoldDefaults.contentWindowInsets,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (mainRoute)
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        navigationIcon = {
                            if (route == "settings")
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                        },
                        actions = {
                            if (route != "settings")
                                IconButton(
                                    onClick = {
                                        nav.navigate("settings") { launchSingleTop = true }
                                    }
                                ) {
                                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                                }
                        },
                    )
            },
            bottomBar = {
                if (mainRoute)
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    // Settings is a child of the current tab, not a saved tab
                                    // stack.
                                    if (route == "settings") nav.popBackStack()
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
            },
        ) { padding ->
            if (state.loading)
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            else if (state.error != null)
                Column(Modifier.padding(padding).padding(24.dp)) {
                    Text(
                        stringResource(R.string.ui_local_storage_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(state.error!!)
                    Button(onClick = vm::retry) { Text(stringResource(R.string.ui_retry)) }
                }
            else
                Box(
                    Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                ) {
                    NavHost(
                        nav,
                        startDestination = "home",
                        modifier =
                            (if (route == "session/{id}") Modifier
                                else Modifier.widthIn(max = 840.dp))
                                .fillMaxSize(),
                    ) {
                        composable("home") {
                            HomeScreen(
                                state,
                                { nav.navigate("edit/new") },
                                { nav.navigate("schedule") },
                                { nav.navigate("prepare/$it") },
                                { nav.navigate("session/$it") },
                            )
                        }
                        composable("workouts") {
                            WorkoutsScreen(
                                state.templates,
                                { nav.navigate("edit/$it") },
                                vm::duplicate,
                                vm::delete,
                                vm::reorder,
                                { nav.navigate("prepare/$it") },
                            )
                        }
                        composable("schedule") {
                            ScheduleScreen(state, vm::assign, { nav.navigate("edit/new") })
                        }
                        composable("history") { HistoryScreen(state.sessions) }
                        composable("settings") {
                            SettingsScreen(state.settings, vm::theme, vm::defaultRest) {
                                ReminderSettings(
                                    state.settings,
                                    state.sessions,
                                    application.alarms,
                                    vm::reminders,
                                    vm::reminderMinute,
                                )
                            }
                        }
                        composable("edit/{id}") { entry ->
                            val editor: EditorViewModel =
                                viewModel(
                                    viewModelStoreOwner = entry,
                                    factory =
                                        viewModelFactory {
                                            initializer {
                                                EditorViewModel(
                                                    application.workouts,
                                                    createSavedStateHandle(),
                                                )
                                            }
                                        },
                                )
                            EditorScreen(editor, state.settings.defaultRest) { nav.popBackStack() }
                        }
                        composable("prepare/{id}") { entry ->
                            PrepareScreen(
                                state.templates.find { it.id == entry.arguments?.getString("id") },
                                application.alarms,
                                state.sessions.any { it.status == SessionStatus.ACTIVE },
                                opening,
                                { nav.popBackStack() },
                                { nav.navigate("edit/$it") },
                                {
                                    if (!opening) {
                                        opening = true
                                        scope.launch {
                                            try {
                                                val id =
                                                    application.runtime.start(
                                                        entry.arguments?.getString("id").orEmpty()
                                                    )
                                                nav.navigate("session/$id") {
                                                    popUpTo("prepare/{id}") { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                snackbar.showSnackbar(
                                                    e.message ?: "Couldn't start workout."
                                                )
                                            } finally {
                                                opening = false
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        composable("session/{id}") { entry ->
                            val execution: ExecutionViewModel =
                                viewModel(
                                    viewModelStoreOwner = entry,
                                    factory =
                                        viewModelFactory {
                                            initializer {
                                                ExecutionViewModel(
                                                    application.runtime,
                                                    entry.arguments?.getString("id").orEmpty(),
                                                )
                                            }
                                        },
                                )
                            ExecutionScreen(execution, application.runtime) {
                                nav.navigate("home") {
                                    popUpTo(nav.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }
        }
    }
}
