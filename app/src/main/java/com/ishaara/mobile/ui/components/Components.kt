package com.ishaara.mobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.ishaara.mobile.R
import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.TaskStatus
import com.ishaara.mobile.ui.theme.BrandDark
import com.ishaara.mobile.ui.theme.BrandLight
import com.ishaara.mobile.ui.theme.Ishaara
import com.ishaara.mobile.ui.theme.SignalColors

/** The logo: the raised hand on its deep green tile, as on the app icon. */
@Composable
fun IshaaraOrb(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(Brush.verticalGradient(listOf(BrandLight, BrandDark))),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ishaara_mark), "Ishaara", Modifier.size(size))
    }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Ishaara.colors.surface)
            .animateContentSize(),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Ishaara.colors.textSecondary,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
fun RowDivider(startInset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(1.dp)
            .background(Ishaara.colors.divider),
    )
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = Ishaara.colors
    Box(
        modifier.size(size).background(colors.surfaceRaised, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = colors.textPrimary)
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = Ishaara.colors
    val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .then(tap)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Ishaara.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Ishaara.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

/** A round icon action. Filled is the one main action on a surface; otherwise it sits back. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val colors = Ishaara.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) colors.primary else colors.surfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size * 0.5f), tint = if (filled) colors.onPrimary else colors.textPrimary)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    val colors = Ishaara.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
    }
}

@Composable
fun TaskStatusIcon(status: TaskStatus, modifier: Modifier = Modifier) {
    val colors = Ishaara.colors
    when (status) {
        TaskStatus.PENDING -> Canvas(modifier.size(20.dp)) {
            drawCircle(colors.textSecondary.copy(alpha = 0.5f), radius = this.size.width * 0.4f, style = Stroke(1.5.dp.toPx()))
        }
        TaskStatus.RUNNING -> {
            val angle by rememberInfiniteTransition(label = "ring").animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "ring",
            )
            Canvas(modifier.size(20.dp).rotate(angle)) {
                val stroke = 2.dp.toPx()
                drawArc(
                    Brush.sweepGradient(SignalColors),
                    0f, 280f, false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        TaskStatus.RETRYING -> Icon(Icons.Rounded.Autorenew, "Retrying", modifier.size(20.dp), tint = SignalColors[1])
        TaskStatus.DONE -> {
            val scale = remember { Animatable(0.5f) }
            LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            Icon(Icons.Rounded.CheckCircle, "Done", modifier.size(20.dp).scale(scale.value), tint = colors.success)
        }
        TaskStatus.FAILED -> Icon(Icons.Rounded.Cancel, "Failed", modifier.size(20.dp), tint = colors.danger)
    }
}

/** The live checklist. [step] is shown under the running task. */
@Composable
fun TaskChecklist(tasks: List<AgentTask>, step: String?, modifier: Modifier = Modifier, maxAttempts: Int = 3) {
    val colors = Ishaara.colors
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tasks.forEach { task ->
            val secondary = when (task.status) {
                TaskStatus.RUNNING -> step
                TaskStatus.RETRYING -> "Attempt ${task.attempt} of $maxAttempts"
                TaskStatus.FAILED -> task.note
                else -> null
            }
            Row(verticalAlignment = Alignment.Top) {
                TaskStatusIcon(task.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (task.status == TaskStatus.PENDING) colors.textSecondary else colors.textPrimary,
                    )
                    if (!secondary.isNullOrBlank()) {
                        Text(
                            secondary,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Five rounded bars driven by microphone level, 0 to 1. */
@Composable
fun Waveform(level: Float, modifier: Modifier = Modifier) {
    val brush = Ishaara.colors.signal
    val weights = listOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
    Row(modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        weights.forEach { weight ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(5.dp + 19.dp * (level * weight).coerceIn(0f, 1f))
                    .background(brush, RoundedCornerShape(2.dp)),
            )
        }
    }
}
