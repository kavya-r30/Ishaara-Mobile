package com.ishaara.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ishaara.mobile.core.model.AgentTask
import com.ishaara.mobile.core.model.TaskStatus
import com.ishaara.mobile.data.HistoryEntry
import com.ishaara.mobile.ui.components.GroupCard
import com.ishaara.mobile.ui.components.IconBadge
import com.ishaara.mobile.ui.components.IshaaraOrb
import com.ishaara.mobile.ui.components.ListRow
import com.ishaara.mobile.ui.components.RoundIconButton
import com.ishaara.mobile.ui.components.RowDivider
import com.ishaara.mobile.ui.components.SectionLabel
import com.ishaara.mobile.ui.components.TaskChecklist
import com.ishaara.mobile.ui.theme.Ishaara
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private class Suggestion(val icon: ImageVector, val label: String, val command: String)

private val SUGGESTIONS = listOf(
    Suggestion(Icons.Rounded.Alarm, "Set an alarm\nfor 6:30", "Set an alarm for 6:30 tomorrow morning"),
    Suggestion(Icons.Rounded.PlayArrow, "Play lo-fi beats\non YouTube", "Play lo-fi beats on YouTube"),
    Suggestion(Icons.Rounded.PhotoCamera, "Take a\nphoto", "Open the camera and take a photo"),
    Suggestion(Icons.Rounded.FlashlightOn, "Turn on the\nflashlight", "Turn on the flashlight"),
    Suggestion(Icons.Rounded.Call, "Call\nsomeone", "Call my most recent contact"),
    Suggestion(Icons.Rounded.Download, "Install an\napp", "Open the Play Store"),
)

@Composable
fun HomeScreen(
    ready: Boolean,
    history: List<HistoryEntry>,
    onSetup: () -> Unit,
    onSettings: () -> Unit,
    onOpenBar: () -> Unit,
    onSpeak: () -> Unit,
    onExample: (String) -> Unit,
) {
    val colors = Ishaara.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IshaaraOrb(32.dp)
                Spacer(Modifier.width(10.dp))
                Text("Ishaara", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Settings", tint = colors.textSecondary)
                }
            }
        }
        item {
            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 16.dp)) {
                Text(greeting(), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                Spacer(Modifier.height(2.dp))
                Text("What should I do?", style = MaterialTheme.typography.displaySmall, color = colors.textPrimary)
            }
        }
        if (!ready) {
            item {
                GroupCard {
                    ListRow(
                        title = "Finish setting up",
                        subtitle = "Ishaara needs a few permissions to work.",
                        leading = { IshaaraOrb(40.dp) },
                        trailing = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary)
                        },
                        onClick = onSetup,
                    )
                }
            }
        } else {
            item { Composer(onOpenBar, onSpeak) }
            item { SectionLabel("Try asking", Modifier.padding(top = 10.dp)) }
            val rows = SUGGESTIONS.chunked(2)
            rows.forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { s ->
                            SuggestionTile(s, Modifier.weight(1f)) { onExample(s.command) }
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionLabel("Recent", Modifier.padding(top = 10.dp)) }
            item {
                GroupCard {
                    history.take(8).forEachIndexed { index, entry ->
                        if (index > 0) RowDivider(startInset = 50.dp)
                        HistoryRow(entry)
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun Composer(onOpen: () -> Unit, onSpeak: () -> Unit) {
    val colors = Ishaara.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ask Ishaara", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
        RoundIconButton(Icons.Rounded.Mic, "Speak", filled = true, onClick = onSpeak)
    }
}

@Composable
private fun SuggestionTile(suggestion: Suggestion, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Ishaara.colors
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(suggestion.icon, size = 36.dp)
        Text(suggestion.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, minLines = 2)
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val colors = Ishaara.colors
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 1.dp)) {
                Icon(
                    if (entry.success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    if (entry.success) "Done" else "Failed",
                    Modifier.size(20.dp),
                    tint = if (entry.success) colors.success else colors.danger,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.command,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expanded) {
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (entry.tasks.size > 1) {
                        TaskChecklist(
                            entry.tasks.mapIndexed { i, t -> AgentTask(i, t.title, "", false, TaskStatus.valueOf(t.status)) },
                            null,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.timeMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
