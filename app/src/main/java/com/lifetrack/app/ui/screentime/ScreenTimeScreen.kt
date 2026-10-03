package com.lifetrack.app.ui.screentime

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifetrack.app.data.Dates
import com.lifetrack.app.data.TrackedApp
import com.lifetrack.app.screentime.LimitGuardService
import com.lifetrack.app.ui.settings.AccessNeededCard
import com.lifetrack.app.screentime.UsageSync
import com.lifetrack.app.ui.appViewModel
import com.lifetrack.app.ui.components.BarChart
import com.lifetrack.app.ui.components.BreakdownList
import com.lifetrack.app.ui.components.CardGap
import com.lifetrack.app.ui.components.DeltaChip
import com.lifetrack.app.ui.components.DonutChart
import com.lifetrack.app.ui.components.EmptyState
import com.lifetrack.app.ui.components.HeroPanel
import com.lifetrack.app.ui.components.LifeCard
import com.lifetrack.app.ui.components.OverflowBar
import com.lifetrack.app.ui.components.ScreenHeader
import com.lifetrack.app.ui.components.ScreenPadding
import com.lifetrack.app.ui.components.SectionLabel
import com.lifetrack.app.ui.components.Slice
import com.lifetrack.app.ui.components.toDailyPoints
import com.lifetrack.app.ui.theme.MetricStyle
import com.lifetrack.app.ui.theme.Space
import com.lifetrack.app.ui.theme.accents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the day actually went. Reached from Home rather than a tab, so it owns a back arrow.
 *
 * The five starter apps are always here and cannot be removed — only apps the user added get a
 * remove action, which is why the row checks [TrackedApp.seeded] instead of offering a button
 * the repository would refuse.
 */
@Composable
fun ScreenTimeScreen(
    onOpenSettings: () -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit,
    vm: ScreenTimeViewModel = appViewModel { ScreenTimeViewModel(it) }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val a = accents()

    // Switched on in another app's screen (Accessibility), so re-read it on every return.
    var guardOn by remember { mutableStateOf(LimitGuardService.isEnabled(context)) }

    // The OS keeps usage data to itself; nothing is stored until we snapshot, so every resume does.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh(context)
                guardOn = LimitGuardService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showAdd by remember { mutableStateOf(false) }
    var limitFor by remember { mutableStateOf<TrackedApp?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }

    val heroAccent = if (state.anyOver) a.negative else a.screen
    val palette = listOf(a.screen, a.activity, a.habits, a.calories, a.alarms, a.caution)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(Space.cards)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(Space.xs))
                ScreenHeader(
                    title = "Screen time",
                    subtitle = Dates.label(state.day),
                    modifier = Modifier.weight(1f),
                    trailing = {
                        FilledTonalButton(onClick = { showAdd = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text("Add app")
                        }
                    }
                )
            }
        }

        if (!state.permission) {
            item { AccessNeededCard("Usage access is off, so app times can't be read.", onOpenSettings) }
        }

        if (!guardOn) {
            item {
                AccessNeededCard("Limits only turn the bar red until Limit blocking is on.", onOpenSettings)
            }
        }

        item { TodayHero(state, heroAccent) }

        if (state.rows.isEmpty()) {
            item {
                EmptyState(
                    "📱",
                    "Nothing tracked yet",
                    "Add an app and LifeTrack starts keeping a daily record of how long you spend in it."
                )
            }
        } else {
            item { SplitCard(state = state, palette = palette, onOpenApp = onOpenApp) }

            item {
                LifeCard {
                    SectionLabel("Last 7 days")
                    Spacer(Modifier.height(Space.md))
                    BarChart(
                        points = state.weekly.toDailyPoints(state.weekFrom, state.day),
                        accent = a.screen,
                        goal = state.totalLimit.takeIf { it > 0 }?.toDouble(),
                        goalMeansGood = false,
                        valueLabel = { Dates.formatMinutes(it.toInt()) },
                        selectedKey = selectedDay,
                        onSelect = { selectedDay = if (it.key == selectedDay) null else it.key }
                    )
                    if (!state.hasHistory) {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            "History builds from today — open this screen once a day and the week fills in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Tracked apps", Modifier.weight(1f))
                    Text(
                        "${state.rows.size} apps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.rows, key = { it.packageName }) { row ->
                AppCard(
                    row = row,
                    onOpen = { onOpenApp(row.packageName) },
                    onLimit = { limitFor = row.app },
                    onRemove = if (row.app.seeded) null else ({ vm.remove(row.app) })
                )
            }
        }

        item { CardGap() }
    }

    if (showAdd) {
        AddAppDialog(
            tracked = state.rows.map { it.packageName }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { pkg, label ->
                vm.addApp(context, pkg, label)
                showAdd = false
            }
        )
    }

    limitFor?.let { app ->
        LimitDialog(
            label = app.label,
            current = app.dailyLimitMin,
            onDismiss = { limitFor = null },
            onSave = {
                vm.setLimit(app, it)
                limitFor = null
            }
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun TodayHero(state: ScreenTimeState, accent: Color) {
    HeroPanel(accent = accent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Time on screen today", Modifier.weight(1f))
            // Less screen time is the win, so a drop has to read green.
            if (state.hasYesterday) DeltaChip(delta = state.deltaPct, higherIsBetter = false)
        }
        Spacer(Modifier.height(Space.xs))
        Text(Dates.formatMinutes(state.totalToday), style = MetricStyle, color = accent)
        Text(
            if (state.hasYesterday) "vs ${Dates.formatMinutes(state.totalYesterday)} yesterday"
            else "No comparison yet — yesterday was not recorded",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.totalLimit > 0) {
            Spacer(Modifier.height(Space.md))
            OverflowBar(state.totalToday.toFloat(), state.totalLimit.toFloat())
            Spacer(Modifier.height(Space.sm))
            Text(
                when {
                    state.anyOver ->
                        "${state.overCount} app${if (state.overCount == 1) "" else "s"} over limit"
                    state.totalToday == 0 ->
                        "Combined limit ${Dates.formatMinutes(state.totalLimit)}"
                    else ->
                        "${Dates.formatMinutes((state.totalLimit - state.totalToday).coerceAtLeast(0))} " +
                            "left of ${Dates.formatMinutes(state.totalLimit)}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.anyOver) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SplitCard(state: ScreenTimeState, palette: List<Color>, onOpenApp: (String) -> Unit) {
    val used = state.rows.filter { it.minutes > 0 }
    LifeCard {
        SectionLabel("Where it went")
        Spacer(Modifier.height(Space.md))
        if (used.isEmpty()) {
            Text(
                "Nothing recorded yet today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Slice labels carry the package so a tap can open the right detail screen.
            val slices = used.mapIndexed { i, row ->
                Slice(row.app.label, row.minutes.toDouble(), palette[i % palette.size])
            }
            val packageForLabel = used.associate { it.app.label to it.packageName }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = slices,
                    centerValue = Dates.formatMinutes(state.totalToday),
                    centerLabel = "today",
                    size = 132.dp,
                    stroke = 16.dp
                )
                Spacer(Modifier.width(Space.lg))
                BreakdownList(
                    slices = slices,
                    modifier = Modifier.weight(1f),
                    valueLabel = { Dates.formatMinutes(it.toInt()) },
                    onClick = { slice -> packageForLabel[slice.label]?.let(onOpenApp) }
                )
            }
        }
    }
}

@Composable
private fun AppCard(
    row: AppRow,
    onOpen: () -> Unit,
    onLimit: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val a = accents()
    val label = rememberAppLabel(row.packageName, row.app.label)
    val accent = if (row.over) a.negative else a.screen

    LifeCard(onClick = onOpen, accent = if (row.over) a.negative else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(row.packageName, label, accent)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    if (row.limitMin > 0)
                        "${Dates.formatMinutes(row.minutes)} of ${Dates.formatMinutes(row.limitMin)}"
                    else Dates.formatMinutes(row.minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (row.limitMin > 0) {
                Text(
                    if (row.over) "over by ${Dates.formatMinutes(row.minutes - row.limitMin)}"
                    else "${Dates.formatMinutes(row.remainingMin)} left",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (row.over) a.negative else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        OverflowBar(row.minutes.toFloat(), row.limitMin.toFloat().coerceAtLeast(1f))
        Row(
            Modifier.fillMaxWidth().padding(top = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onLimit) { Text("Limit") }
            Spacer(Modifier.weight(1f))
            if (onRemove != null) {
                TextButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text("Remove")
                }
            }
        }
    }
}

/** The real launcher icon where the app is installed, a lettered badge where it is not. */
@Composable
internal fun AppIcon(packageName: String, label: String, accent: Color, size: Dp = 44.dp) {
    val icon by rememberAppIcon(packageName)
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(percent = 30))
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 30))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

// ---------------------------------------------------------------- dialogs

/**
 * Picks from the apps actually installed, so nobody has to know that YouTube is
 * `com.google.android.youtube`. Typing a package by hand stays as a fallback for the odd app
 * with no launcher entry, and for devices that refuse to list anything.
 */
@Composable
private fun AddAppDialog(
    tracked: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    val context = LocalContext.current
    val a = accents()
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var manualPkg by remember { mutableStateOf("") }

    val apps by produceState(initialValue = emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.IO) { InstalledApps.launchable(context.applicationContext) }
    }
    val visible = remember(apps, query, tracked) {
        apps.asSequence()
            .filter { it.packageName !in tracked }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .toList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp)
        ) {
            Column(Modifier.padding(Space.lg)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Add an app", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Text(
                    "New apps start at a ${ScreenTimeViewModel.DEFAULT_LIMIT_MIN} minute daily limit — " +
                        "change it any time from the app's card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search installed apps") }
                )
                Spacer(Modifier.height(Space.sm))

                when {
                    apps.isEmpty() -> Text(
                        "No installed apps could be listed on this device. Add the package name below instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.md)
                    )

                    visible.isEmpty() -> Text(
                        "Nothing matches that search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Space.md)
                    )

                    else -> LazyColumn(
                        Modifier.weight(1f, fill = false).heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        items(visible, key = { it.packageName }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onAdd(app.packageName, app.label) }
                                    .padding(vertical = Space.sm, horizontal = Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app.packageName, app.label, a.screen, size = 36.dp)
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Filled.Add, contentDescription = null, tint = a.screen)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.sm))
                if (!manual) {
                    TextButton(onClick = { manual = true }) { Text("Add by package name") }
                } else {
                    OutlinedTextField(
                        value = manualPkg,
                        onValueChange = { manualPkg = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        label = { Text("Package name") },
                        placeholder = { Text("com.example.app") }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { manual = false }) { Text("Cancel") }
                        TextButton(
                            enabled = manualPkg.isNotBlank(),
                            onClick = {
                                val pkg = manualPkg.trim()
                                onAdd(pkg, InstalledApps.label(context, pkg, pkg.substringAfterLast('.')))
                            }
                        ) { Text("Add") }
                    }
                }
            }
        }
    }
}

/** Shared with the detail screen, so one place decides what a sane limit looks like. */
@Composable
internal fun LimitDialog(
    label: String,
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var minutes by remember(current) { mutableStateOf(current.coerceAtLeast(0).toString()) }
    val parsed = minutes.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily limit") },
        text = {
            Column {
                Text(
                    "How long in $label is reasonable on a normal day?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.md))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { preset ->
                        FilterChip(
                            selected = parsed == preset,
                            onClick = { minutes = preset.toString() },
                            label = {
                                Text(
                                    Dates.formatMinutes(preset),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { entry -> minutes = entry.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { Text("min") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed >= ScreenTimeViewModel.MIN_LIMIT_MIN,
                onClick = { onSave(parsed) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

