package com.lifetrack.app.ui.routine

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifetrack.app.data.Repository
import com.lifetrack.app.data.ReminderMode
import com.lifetrack.app.data.RoutineItem
import com.lifetrack.app.data.RoutineKind
import com.lifetrack.app.reminders.RoutineScheduler
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.SegmentedPicker
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import java.time.LocalTime

/** Adding, changing and removing routine items, plus keeping their alarms in step. */
internal object RoutineEdits {

    /** A new item starts at the next whole hour as a notification; the sheet fills in the rest. */
    fun blank(now: LocalTime = LocalTime.now()): RoutineItem =
        RoutineItem(key = "", title = "", emoji = "✅", hour = (now.hour + 1) % 24, minute = 0)

    /** Saves [item] - a new one when its id is 0 - and re-arms its alarm. */
    suspend fun save(context: Context, repo: Repository, item: RoutineItem) {
        val app = context.applicationContext
        val id = if (item.id == 0L) repo.addRoutineItem(item) else item.id.also { repo.updateRoutineItem(item) }
        RoutineScheduler.reschedule(app, repo, id)
    }

    /** Disarms first, so nothing can ring for an item that is already gone. */
    suspend fun remove(context: Context, repo: Repository, item: RoutineItem) {
        RoutineScheduler.cancel(context.applicationContext, item.id)
        repo.removeRoutineItem(item)
    }
}

private val EMOJI_CHOICES = listOf("✅", "💊", "🧘", "🏃", "📖", "🎓", "💧", "🧹", "🛏", "🍎", "🙏", "💻")

/**
 * One routine item's usual time, mode, name and emoji - or a new item when [initial] has id 0.
 * [onRemove] is offered for existing items; it asks first, since the item's history goes with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RoutineItemSheet(
    initial: RoutineItem,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (RoutineItem) -> Unit,
    onRemove: ((RoutineItem) -> Unit)?
) {
    val context = LocalContext.current
    val isNew = initial.id == 0L
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    var confirmRemove by remember { mutableStateOf(false) }
    val time = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(
                if (isNew) "New routine item" else "${initial.emoji} ${initial.title}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                if (isNew) "Joins every day from now on. Tick it off in the Routine tab or from its notification."
                else "The usual time, every day. To move just tomorrow, use Plan tomorrow instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = draft.emoji,
                    onValueChange = { draft = draft.copy(emoji = it.take(8)) },
                    label = { Text("Icon") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(84.dp)
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it.take(40)) },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                EMOJI_CHOICES.forEach { e ->
                    FilterChip(
                        selected = draft.emoji == e,
                        onClick = { draft = draft.copy(emoji = e) },
                        label = { Text(e) }
                    )
                }
            }
            if (draft.kindType != RoutineKind.MANUAL) {
                Text(
                    "This one ticks itself (${autoHint(draft.kindType)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
            SectionLabel("How it arrives")
            SegmentedPicker(
                options = ReminderMode.entries.toList(),
                selected = draft.modeType,
                onSelect = { draft = draft.copy(mode = it.name) },
                label = { it.label },
                accent = accent,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            title = draft.title.trim(),
                            emoji = draft.emoji.trim().ifEmpty { "✅" },
                            hour = time.hour,
                            minute = time.minute
                        )
                    )
                },
                enabled = draft.title.isNotBlank(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (isNew) "Add to routine" else "Save", style = MaterialTheme.typography.titleMedium) }
            if (!isNew && onRemove != null) {
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove from routine", color = accents().negative)
                }
            }
        }
    }

    if (confirmRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${initial.title}?") },
            text = {
                Text(
                    "It stops reminding you and leaves the tracker, along with its done/missed history.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove(initial) }) {
                    Text("Remove", color = accents().negative)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

private fun autoHint(kind: RoutineKind): String = when (kind) {
    RoutineKind.WAKE -> "dismissing the alarm"
    RoutineKind.MEAL -> "logging that meal"
    RoutineKind.WALK -> "1,000 steps in the two hours after"
    RoutineKind.PLAN -> "saving tomorrow's plan"
    RoutineKind.MANUAL -> "a tap"
}
