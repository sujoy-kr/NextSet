package com.sujoy.nextset.ui

import android.app.NotificationManager
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*
import com.sujoy.nextset.notifications.SessionRuntime
import kotlinx.coroutines.delay

@Composable
fun ExecutionScreen(vm: ExecutionViewModel, runtime: SessionRuntime, onHome: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val compact = with(LocalDensity.current) { windowSize.height.toDp() } < 420.dp
    var end by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val workout = ui.workout
    val state = workout?.execution
    DisposableEffect(owner, view, state?.terminal) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
            view.keepScreenOn = event == Lifecycle.Event.ON_RESUME && state?.terminal == false
        }
        owner.lifecycle.addObserver(observer)
        view.keepScreenOn =
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                state?.terminal == false
        onDispose {
            view.keepScreenOn = false
            owner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = state?.terminal == false) { end = true }
    // An alert changes immediately; a pale-to-red fade would briefly put white text on white.
    val bg =
        if (state?.phase == Phase.REST_DONE) Color(0xFFA52127)
        else MaterialTheme.colorScheme.background
    val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = state?.phase != Phase.REST_DONE && lightBars
                isAppearanceLightNavigationBars = state?.phase != Phase.REST_DONE && lightBars
            }
        }
    }
    val foreground =
        if (state?.phase == Phase.REST_DONE) Color.White else MaterialTheme.colorScheme.onBackground
    fun perform(command: Command) {
        if (state != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            vm.action(state.revision, command)
    }
    Scaffold(
        containerColor = bg,
        contentColor = foreground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (ui.error != null)
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(ui.error!!)
                Button(onClick = vm::retry) { Text(stringResource(R.string.ui_retry)) }
                TextButton(onClick = onHome) { Text(stringResource(R.string.home)) }
            }
        else if (workout == null || state == null)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        else {
            var pressing by remember(state.revision) { mutableStateOf(false) }
            val holdProgress by
                animateFloatAsState(
                    if (pressing) 1f else 0f,
                    animationSpec = tween(if (pressing) 1000 else 0),
                    label = "Hold to complete",
                )
            val plan = remember(workout.session.snapshot) { workout.session.snapshot.sequence() }
            val step = plan.getOrNull(state.index)
            val upcoming =
                plan.drop(state.index + 1).filterIsInstance<WorkoutStep.Set>().firstOrNull()
            val activeStep = step as? WorkoutStep.Set
            val count =
                workout.session.snapshot.blocks
                    .flatMap { it.exercises }
                    .find { it.id == activeStep?.exerciseId }
                    ?.sets
            val onHold = {
                val audio = context.getSystemService(AudioManager::class.java)
                val dnd = context.getSystemService(NotificationManager::class.java)
                if (
                    audio.ringerMode != AudioManager.RINGER_MODE_SILENT &&
                        dnd.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
                )
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                perform(Command.HOLD)
            }
            Column(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    workout.session.snapshot.name,
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 20.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = foreground.copy(alpha = .75f),
                )
                Box(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (state.phase == Phase.EXERCISE || state.phase == Phase.REST_DONE)
                                Modifier.testTag("workout_surface")
                                    .oneSecondHold(state.revision, { pressing = it }, onHold)
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        onLongClick(label = "Complete set or acknowledge rest") {
                                            onHold()
                                            true
                                        }
                                        customActions =
                                            listOf(
                                                CustomAccessibilityAction(
                                                    "Complete set or acknowledge rest"
                                                ) {
                                                    onHold()
                                                    true
                                                }
                                            )
                                    }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pressing)
                        LinearProgressIndicator(
                            progress = { holdProgress },
                            modifier =
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                            color = foreground,
                        )
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 22.dp),
                    ) {
                        when (state.phase) {
                            Phase.EXERCISE -> {
                                Text(
                                    activeStep?.exerciseName.orEmpty(),
                                    style =
                                        MaterialTheme.typography.headlineLarge.copy(
                                            fontSize = 40.sp,
                                            lineHeight = 48.sp,
                                        ),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "SET ${activeStep?.number} OF $count",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Hold anywhere for 1 second\nto complete this set",
                                    textAlign = TextAlign.Center,
                                    color = foreground.copy(alpha = .75f),
                                )
                            }
                            Phase.REST,
                            Phase.REST_DONE -> {
                                RestCountdown(state, runtime, vm, foreground, compact)
                                if (state.phase == Phase.REST_DONE)
                                    Text(
                                        "REST COMPLETE",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                Text(
                                    upcoming?.let {
                                        "Up next · ${it.exerciseName}\nSet ${it.number}"
                                    } ?: "All sets done · Finish your workout",
                                    textAlign = TextAlign.Center,
                                    color = foreground.copy(alpha = .8f),
                                )
                                if (state.phase == Phase.REST_DONE)
                                    Text(
                                        "Hold for 1 second to continue",
                                        textAlign = TextAlign.Center,
                                    )
                            }
                            Phase.PAUSED -> {
                                Text(
                                    stringResource(R.string.stage2_paused),
                                    style = MaterialTheme.typography.headlineLarge,
                                )
                                Text(
                                    stringResource(R.string.stage2_your_rest_timer_is_frozen),
                                    textAlign = TextAlign.Center,
                                )
                                Button(onClick = { perform(Command.RESUME) }) {
                                    Text(stringResource(R.string.stage2_resume))
                                }
                            }
                            Phase.COMPLETED,
                            Phase.ABANDONED -> {
                                Icon(
                                    if (state.phase == Phase.COMPLETED) Icons.Default.CheckCircle
                                    else Icons.Default.StopCircle,
                                    null,
                                    Modifier.size(56.dp),
                                )
                                Text(
                                    if (state.phase == Phase.COMPLETED) "Workout complete"
                                    else "Workout ended",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    "${workout.session.completedSets.size} sets · ${elapsedLabel(state.elapsed)}",
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    stringResource(R.string.stage2_saved_to_your_history),
                                    color = foreground.copy(alpha = .7f),
                                )
                                Button(onClick = onHome) {
                                    Text(stringResource(R.string.stage2_back_to_home))
                                }
                            }
                            Phase.NOT_STARTED ->
                                Text(
                                    stringResource(R.string.stage2_ready),
                                    style = MaterialTheme.typography.headlineLarge,
                                )
                        }
                    }
                }
                if (!state.terminal)
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = { perform(Command.UNDO) }, enabled = state.index > 0) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                "Undo last set",
                                tint = foreground.copy(alpha = if (state.index > 0) .75f else .25f),
                            )
                        }
                        IconButton(
                            onClick = {
                                perform(
                                    if (state.phase == Phase.PAUSED) Command.RESUME
                                    else Command.PAUSE
                                )
                            }
                        ) {
                            Icon(
                                if (state.phase == Phase.PAUSED) Icons.Default.PlayArrow
                                else Icons.Default.Pause,
                                if (state.phase == Phase.PAUSED) "Resume workout"
                                else "Pause workout",
                                tint = foreground.copy(alpha = .75f),
                            )
                        }
                        IconButton(
                            onClick = { perform(Command.SKIP_REST) },
                            enabled = state.phase == Phase.REST || state.phase == Phase.REST_DONE,
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                "Skip remaining rest",
                                tint =
                                    foreground.copy(
                                        alpha =
                                            if (
                                                state.phase == Phase.REST ||
                                                    state.phase == Phase.REST_DONE
                                            )
                                                .75f
                                            else .25f
                                    ),
                            )
                        }
                        IconButton(onClick = { end = true }) {
                            Icon(
                                Icons.Default.Close,
                                "End workout",
                                tint = foreground.copy(alpha = .75f),
                            )
                        }
                    }
            }
        }
    }
    if (end)
        EndWorkoutDialog(
            onDismiss = { end = false },
            onEnd = {
                end = false
                perform(Command.END)
            },
            onHome = {
                end = false
                onHome()
            },
        )
}

private fun Modifier.oneSecondHold(
    revision: Long,
    onPress: (Boolean) -> Unit,
    onHold: () -> Unit,
): Modifier =
    pointerInput(revision) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onPress(true)
            try {
                val held =
                    withTimeoutOrNull(1_000) {
                        waitForUpOrCancellation()
                        false
                    } ?: true
                if (held) {
                    onHold()
                    waitForUpOrCancellation()
                }
            } finally {
                onPress(false)
            }
        }
    }

@Composable
private fun RestCountdown(
    state: ExecutionState,
    runtime: SessionRuntime,
    vm: ExecutionViewModel,
    color: Color,
    compact: Boolean,
) {
    var remaining by
        remember(state.revision, state.deadline) {
            mutableLongStateOf(state.remainingAt(runtime.clock.now()))
        }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(state.revision, state.deadline, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (state.phase == Phase.REST) {
                remaining = state.remainingAt(runtime.clock.now())
                if (remaining == 0L) {
                    vm.action(state.revision, Command.REFRESH)
                    break
                }
                delay(minOf(250, remaining))
            }
        }
    }
    val seconds = if (state.phase == Phase.REST_DONE) 0 else (remaining + 999) / 1000
    val display = "%02d:%02d".format(seconds / 60, seconds % 60)
    BasicText(
        text = display,
        modifier = Modifier.fillMaxWidth(),
        style =
            MaterialTheme.typography.headlineLarge.copy(
                color = color,
                fontSize = 72.sp,
                lineHeight = TextUnit.Unspecified,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
        maxLines = 1,
        softWrap = false,
        autoSize =
            TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = if (compact) 56.sp else 72.sp),
    )
}
