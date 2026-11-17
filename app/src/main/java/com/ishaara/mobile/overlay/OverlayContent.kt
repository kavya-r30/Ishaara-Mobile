package com.ishaara.mobile.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ishaara.mobile.core.PanelState
import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.TaskStatus
import com.ishaara.mobile.ui.components.Chip
import com.ishaara.mobile.ui.components.IshaaraOrb
import com.ishaara.mobile.ui.components.PrimaryButton
import com.ishaara.mobile.ui.components.RoundIconButton
import com.ishaara.mobile.ui.components.TaskChecklist
import com.ishaara.mobile.ui.theme.Ishaara
import com.ishaara.mobile.ui.theme.SignalColors
import kotlinx.coroutines.delay

enum class CallState { IDLE, RINGING, IN_CALL }

data class OverlayActions(
    val compose: (listen: Boolean) -> Unit,
    val reply: (listen: Boolean) -> Unit,
    val stop: () -> Unit,
    val answer: (String) -> Unit,
    val dismiss: () -> Unit,
    val callCommand: (String) -> Unit,
)

private sealed interface Body {
    data class Agent(val state: PanelState) : Body
    data class Call(val call: CallState) : Body
}

/**
 * The card lives in a window whose height only changes between animations, because resizing a window
 * every frame stutters. [onWindowHeight] gets 0 once the card has fully gone.
 */
@Composable
fun OverlayPanel(
    state: PanelState,
    call: CallState,
    hidden: Boolean,
    bottomInset: Dp,
    actions: OverlayActions,
    onWindowHeight: (Int) -> Unit,
) {
    val current: Body? = when {
        hidden -> null
        state != PanelState.Idle -> Body.Agent(state)
        call != CallState.IDLE -> Body.Call(call)
        else -> null
    }
    var shown by remember { mutableStateOf(current) }
    if (current != null) shown = current

    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = current != null
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState) onWindowHeight(0)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                fadeIn(tween(160)) +
                scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f),
            exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)),
        ) {
            shown?.let { body ->
                Card(bottomInset, onWindowHeight) {
                    // Keyed by kind, so a new step updates in place and only a new kind of card fades in.
                    key(body.kind()) {
                        val alpha = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { alpha.animateTo(1f, tween(220)) }
                        Box(Modifier.graphicsLayer { this.alpha = alpha.value }) {
                            when (body) {
                                is Body.Call -> CallCard(body.call, actions)
                                is Body.Agent -> when (val agent = body.state) {
                                    is PanelState.Running -> RunningBody(agent, actions.stop)
                                    is PanelState.Asking -> AskingBody(agent, actions)
                                    is PanelState.Result -> ResultBody(agent, actions)
                                    PanelState.Idle -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Body.kind(): Any = when (this) {
    is Body.Call -> Body.Call::class
    is Body.Agent -> state::class
}

@Composable
private fun Card(bottomInset: Dp, onWindowHeight: (Int) -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val shape = MaterialTheme.shapes.large
    val margin = 10.dp
    val shadowRoom = with(density) { (bottomInset + margin + 28.dp).roundToPx() }
    var natural by remember { mutableIntStateOf(0) }
    val height = remember { Animatable(0f) }

    LaunchedEffect(natural) {
        val target = natural.toFloat()
        when {
            target <= 0f -> Unit
            height.value <= 0f -> {
                height.snapTo(target)
                onWindowHeight(natural + shadowRoom)
            }
            else -> {
                if (target > height.value) onWindowHeight(natural + shadowRoom)
                height.animateTo(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium))
                onWindowHeight(natural + shadowRoom)
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = margin, end = margin, bottom = bottomInset + margin)
            .layout { measurable, constraints ->
                val h = height.value.toInt()
                val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            .background(Ishaara.colors.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .onSizeChanged { natural = it.height }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            content()
        }
    }
}

/** Looks like a text box. Tapping opens the real one, because overlay windows can't use the keyboard. */
@Composable
private fun Composer(hint: String, onType: () -> Unit, onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Ishaara.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .clickable(onClick = onType)
            .padding(start = 18.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hint, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        RoundIconButton(Icons.Rounded.Mic, "Speak", filled = true, size = 38.dp, onClick = onSpeak)
    }
}

/** The mark with a ring turning around it: Ishaara is working. */
@Composable
private fun WorkingOrb() {
    val angle by rememberInfiniteTransition(label = "orbRing").animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "orbRing",
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(angle)) {
            val stroke = 2.dp.toPx()
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent) + SignalColors),
                0f, 300f, false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        IshaaraOrb(26.dp)
    }
}

/** One segment per task, so progress reads at a glance without taking up the screen. */
@Composable
private fun TaskProgress(tasks: List<AgentTask>, modifier: Modifier = Modifier) {
    val colors = Ishaara.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tasks.forEach { task ->
            val segment = Modifier.weight(1f).height(3.dp).clip(CircleShape)
            when (task.status) {
                TaskStatus.DONE -> Box(segment.background(colors.success))
                TaskStatus.FAILED -> Box(segment.background(colors.danger))
                TaskStatus.PENDING -> Box(segment.background(colors.surfaceRaised))
                TaskStatus.RUNNING, TaskStatus.RETRYING -> Box(segment.background(colors.signal))
            }
        }
    }
}

@Composable
private fun RunningBody(state: PanelState.Running, onStop: () -> Unit) {
    val colors = Ishaara.colors
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.tasks.size) { expanded = state.tasks.size > 1 }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.3f
    val multiple = state.tasks.size > 1
    val current = state.tasks.firstOrNull { it.status == TaskStatus.RUNNING || it.status == TaskStatus.RETRYING }
    val index = current?.let { state.tasks.indexOf(it) + 1 } ?: state.tasks.count { it.status == TaskStatus.DONE }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WorkingOrb()
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, null, enabled = multiple) { expanded = !expanded },
            ) {
                Text(
                    when {
                        state.tasks.isEmpty() -> "Thinking"
                        expanded && multiple -> "Step ${index.coerceAtLeast(1)} of ${state.tasks.size}"
                        else -> current?.title ?: state.tasks.last().title
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when {
                    state.tasks.isEmpty() || (expanded && multiple) -> null
                    current?.status == TaskStatus.RETRYING -> "Trying again"
                    else -> state.step
                }
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (multiple) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    if (expanded) "Hide tasks" else "Show tasks",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { expanded = !expanded },
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
            RoundIconButton(Icons.Rounded.Close, "Stop", size = 36.dp, onClick = onStop)
        }
        if (multiple) TaskProgress(state.tasks, Modifier.padding(top = 12.dp))
        if (expanded && multiple) {
            TaskChecklist(
                state.tasks,
                state.step,
                modifier = Modifier
                    .padding(top = 14.dp, start = 2.dp, bottom = 2.dp)
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskingBody(state: PanelState.Asking, actions: OverlayActions) {
    val colors = Ishaara.colors
    // Collapsible, because the question may ask the user to do something on the screen underneath.
    var collapsed by remember(state.question) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = if (collapsed) Alignment.CenterVertically else Alignment.Top) {
            IshaaraOrb(30.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                state.question,
                Modifier
                    .weight(1f)
                    .padding(top = if (collapsed) 0.dp else 3.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { collapsed = !collapsed },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = if (collapsed) 1 else 5,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (collapsed) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                if (collapsed) "Show question" else "Hide question",
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = if (collapsed) 0.dp else 6.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { collapsed = !collapsed },
            )
            RoundIconButton(Icons.Rounded.Close, "Stop", size = 36.dp, onClick = actions.stop)
        }
        if (!collapsed) {
            if (state.options.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.options.forEachIndexed { index, option ->
                        if (index == 0) {
                            PrimaryButton(option) { actions.answer(option) }
                        } else {
                            Chip(option) { actions.answer(option) }
                        }
                    }
                }
            }
            Composer("Type your answer", { actions.reply(false) }, { actions.reply(true) })
        }
    }
}

@Composable
private fun ResultBody(state: PanelState.Result, actions: OverlayActions) {
    val colors = Ishaara.colors
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.35f
    LaunchedEffect(state) {
        delay(30000)
        actions.dismiss()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (state.success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                if (state.success) "Done" else "Failed",
                Modifier.padding(top = 1.dp).size(24.dp),
                tint = if (state.success) colors.success else colors.danger,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                state.summary,
                Modifier
                    .weight(1f)
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.Close, "Close", size = 32.dp, onClick = actions.dismiss)
        }
        if (state.tasks.size > 1) TaskProgress(state.tasks)
        Composer("Ask Ishaara", { actions.compose(false) }, { actions.compose(true) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallCard(call: CallState, actions: OverlayActions) {
    val colors = Ishaara.colors
    val command = actions.callCommand
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IshaaraOrb(30.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (call == CallState.RINGING) "Incoming call" else "On a call",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (call == CallState.RINGING) {
                PrimaryButton("Answer") { command("Answer the phone call") }
                Chip("Decline") { command("Reject the phone call") }
            } else {
                Chip("Speaker") { command("Turn on speaker on this call") }
                Chip("Hold") { command("Put this call on hold") }
                Chip("Mute") { command("Mute this call") }
                PrimaryButton("End") { command("End this call") }
            }
            Chip("Type") { actions.compose(false) }
        }
    }
}
