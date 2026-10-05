package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.core.habits.Habit
import com.example.core.habits.HabitAlarmScheduler
import com.example.core.habits.HabitFrequency
import com.example.core.habits.HabitStore
import com.example.core.habits.computeStreak
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val Navy = Color(0xFF0B3954)
private val PageBg = Color(0xFFF6F8FA)
private val Ink = Color(0xFF10231F)
private val InkMuted = Color(0xFF5B6F69)
private val Border = Color(0xFFE1E9E5)
private val Teal = Color(0xFF0FB89A)
private val StreakBg = Color(0xFFFDE4E4)
private val StreakFg = Color(0xFFE2574C)
private val Track = Color(0xFFEFF3F6)
private val IconBox = Color(0xFFE9EEF2)

/** Icons offered when creating a habit (same order as the design). */
private val HabitIcons: List<Pair<String, ImageVector>> = listOf(
    "star" to Icons.Outlined.StarBorder,
    "prayer" to Icons.Outlined.SelfImprovement,
    "moon" to Icons.Outlined.Bedtime,
    "book" to Icons.Outlined.MenuBook,
    "workout" to Icons.Outlined.FitnessCenter,
    "walk" to Icons.Outlined.DirectionsWalk,
    "water" to Icons.Outlined.WaterDrop,
    "food" to Icons.Outlined.Restaurant,
    "alarm" to Icons.Outlined.Alarm,
    "heart" to Icons.Outlined.FavoriteBorder,
    "work" to Icons.Outlined.WorkOutline
)

private fun iconFor(key: String): ImageVector =
    HabitIcons.firstOrNull { it.first == key }?.second ?: Icons.Outlined.StarBorder

private class HabitDraft(
    val title: String = "",
    val description: String = "",
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val reminder: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val iconKey: String = "star"
)

private class HabitTemplate(
    val title: String,
    val description: String,
    val iconKey: String,
    val hour: Int,
    val minute: Int
)

private val Templates = listOf(
    HabitTemplate("Screen-free Bedtime", "No phones 30m before sleep", "moon", 22, 0),
    HabitTemplate("Book Reading with Kids", "Read a story or book together", "book", 20, 0),
    HabitTemplate("Morning Workout / Walk", "20-30m physical exercise or walk", "walk", 6, 30),
    HabitTemplate("Drink 2L Water", "Stay hydrated throughout the day", "water", 10, 0),
    HabitTemplate("Family Dinner Time", "Meal together with no screens", "heart", 20, 30),
    HabitTemplate("Prayer & Gratitude", "Daily prayer & reflection", "prayer", 5, 0)
)

private fun formatTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))

private fun frequencyLabel(f: HabitFrequency): String = when (f) {
    HabitFrequency.DAILY -> "Daily"
    HabitFrequency.WEEKLY -> "Weekly"
    HabitFrequency.MONTHLY -> "Monthly"
}

/** The Habits screen is always light, so keep dark status-bar icons while it is visible (restored on exit). */
@Composable
private fun StatusBarDarkIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = InkMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Track)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * [onFormVisibilityChange] tells the host when the full-screen "Add habit" form is open,
 * so it can hide its floating navigation bar and give the form the whole screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitsTabContent(onFormVisibilityChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { HabitStore(context) }
    var habits by remember { mutableStateOf(store.load()) }
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var draft by remember { mutableStateOf<HabitDraft?>(null) }
    var toDelete by remember { mutableStateOf<Habit?>(null) }

    val dueHabits = habits.filter { it.isDueOn(selected) }
    val doneCount = dueHabits.count { it.isDone(selected) }
    val percent = if (dueHabits.isEmpty()) 0 else doneCount * 100 / dueHabits.size
    val streak = computeStreak(habits, today)

    val formOpen = draft != null
    val latestFormCallback by rememberUpdatedState(onFormVisibilityChange)
    LaunchedEffect(formOpen) { latestFormCallback(formOpen) }
    DisposableEffect(Unit) { onDispose { latestFormCallback(false) } }

    StatusBarDarkIcons()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        // ---------- Header (white fills the status bar area; the text sits below it) ----------
        Box(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Habit Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text("Daily routines & consistency", fontSize = 13.sp, color = InkMuted)
                    }
                    Button(
                        onClick = { draft = HabitDraft() },
                        modifier = Modifier.height(44.dp).testTag("habit_add_button"),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- Week strip ----------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .border(1.dp, Border, RoundedCornerShape(22.dp))
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    (-3L..3L).map { today.plusDays(it) }.forEach { day ->
                        val isSel = day == selected
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) Navy else Color.Transparent)
                                .clickable { selected = day }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                color = if (isSel) Color.White.copy(alpha = 0.8f) else InkMuted
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                day.dayOfMonth.toString(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            day != today -> Color.Transparent
                                            isSel -> Color.White
                                            else -> Teal
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // ---------- Streak / progress ----------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Navy, Color(0xFF15607F))))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFFFB454),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("$streak Day Streak", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${habits.size} Active Habits",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$percent%", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Done",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 7.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$doneCount of ${dueHabits.size} completed",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 7.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent / 100f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color(0xFF3DDBB8))
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = when {
                            habits.isEmpty() -> "Start your journey with a positive habit today!"
                            dueHabits.isEmpty() -> "Nothing scheduled for this day."
                            doneCount == dueHabits.size -> "Amazing! Every habit is complete — keep the streak going!"
                            doneCount > 0 -> "Great progress — keep going!"
                            else -> "Tick off your first habit to get started."
                        },
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // ---------- Checklist ----------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "HABITS CHECKLIST",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF3D5163),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (selected == today) "Today"
                        else selected.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InkMuted
                    )
                }
            }
            if (habits.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(1.dp, Border, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(IconBox),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = Navy,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("No habits yet", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap the \"+ Add\" button above or choose an idea below to build positive daily habits.",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = InkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (dueHabits.isEmpty()) {
                item {
                    Text(
                        "No habits are scheduled for this day.",
                        fontSize = 14.sp,
                        color = InkMuted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                dueHabits.forEach { habit ->
                    item(key = habit.id) {
                        val done = habit.isDone(selected)
                        val canToggle = !selected.isAfter(today)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .border(1.dp, if (done) Teal.copy(alpha = 0.45f) else Border, RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (canToggle) habits = store.setDone(habit.id, selected, !done)
                                    },
                                    onLongClick = { toDelete = habit }
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(IconBox),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(iconFor(habit.iconKey), contentDescription = null, tint = Navy)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    habit.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (done) InkMuted else Ink,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (habit.description.isNotBlank()) {
                                    Text(
                                        habit.description,
                                        fontSize = 13.sp,
                                        color = InkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MetaChip(frequencyLabel(habit.frequency))
                                    if (habit.reminderEnabled) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = InkMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            formatTime(habit.hour, habit.minute),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = InkMuted
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (done) Teal else Color.Transparent)
                                    .border(2.dp, if (done) Teal else Border, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Habit Builder Ideas ----------
            item {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Habit Builder Ideas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap any template to add to your daily routine", fontSize = 13.sp, color = InkMuted)
                }
            }
            Templates.chunked(2).forEach { pair ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { t ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                                    .clickable {
                                        draft = HabitDraft(
                                            title = t.title,
                                            description = t.description,
                                            frequency = HabitFrequency.DAILY,
                                            reminder = true,
                                            hour = t.hour,
                                            minute = t.minute,
                                            iconKey = t.iconKey
                                        )
                                    }
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(IconBox),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(iconFor(t.iconKey), contentDescription = null, tint = Navy)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(CircleShape).background(Teal.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Teal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    t.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    t.description,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = InkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen form drawn inside the app window (not a Dialog), so it gets the real status-bar and
    // navigation-bar insets: header fills the status bar, and the button always sits above the system buttons.
    draft?.let { d ->
        AddHabitScreen(
            initial = d,
            onClose = { draft = null },
            onCreate = { result ->
                val habit = Habit(
                    id = UUID.randomUUID().toString(),
                    title = result.title.trim(),
                    description = result.description.trim(),
                    frequency = result.frequency,
                    reminderEnabled = result.reminder,
                    hour = result.hour,
                    minute = result.minute,
                    iconKey = result.iconKey,
                    createdAt = LocalDate.now(),
                    completedDates = emptySet()
                )
                habits = store.add(habit)
                HabitAlarmScheduler.schedule(context, habit)
                draft = null
            }
        )
    }
    }

    toDelete?.let { h ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete habit?") },
            text = { Text("\"${h.title}\" and its history will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    HabitAlarmScheduler.cancel(context, h.id)
                    habits = store.delete(h.id)
                    toDelete = null
                }) { Text("Delete", color = StreakFg) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHabitScreen(
    initial: HabitDraft,
    onClose: () -> Unit,
    onCreate: (HabitDraft) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var frequency by remember { mutableStateOf(initial.frequency) }
    var reminder by remember { mutableStateOf(initial.reminder) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var iconKey by remember { mutableStateOf(initial.iconKey) }
    var showTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Ask for what a full-screen alarm needs the moment the reminder is switched on.
    fun prepareAlarmPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedBorderColor = Navy,
        unfocusedBorderColor = Border
    )

    Surface(modifier = Modifier.fillMaxSize(), color = PageBg) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // ---------- Top bar: white fills the status bar area, title stays below it ----------
            Box(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(60.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Track)
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Ink,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            "Add New Habit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                FieldLabel("Habit Title *")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
                    modifier = Modifier.fillMaxWidth().testTag("habit_title_field"),
                    singleLine = true,
                    isError = titleError,
                    supportingText = if (titleError) {
                        { Text("Please enter a habit title") }
                    } else null,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Ink),
                    keyboardOptions = KeyboardOptions.Default,
                    colors = fieldColors
                )
                Spacer(Modifier.height(20.dp))

                FieldLabel("Description")
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().testTag("habit_description_field"),
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = Ink),
                    colors = fieldColors
                )
                Spacer(Modifier.height(20.dp))

                FieldLabel("Frequency")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Track)
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .padding(5.dp)
                ) {
                    HabitFrequency.values().forEach { f ->
                        val sel = f == frequency
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (sel) Modifier.shadow(2.dp, RoundedCornerShape(12.dp)) else Modifier
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Color.White else Color.Transparent)
                                .clickable { frequency = f }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                frequencyLabel(f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Navy else Color(0xFF5F7A93)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                FieldLabel("Reminder Alarm")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Wakes the screen with a full alert when it's time, e.g. a prayer time reminder.",
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = InkMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = reminder,
                            onCheckedChange = {
                                reminder = it
                                if (it) prepareAlarmPermissions()
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Teal,
                                checkedThumbColor = Color.White,
                                checkedBorderColor = Teal
                            ),
                            modifier = Modifier.testTag("habit_reminder_switch")
                        )
                    }
                    if (reminder) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = "Pick time",
                                tint = Teal,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Time",
                                fontSize = 14.sp,
                                color = InkMuted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatTime(hour, minute),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Navy
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                FieldLabel("Select Icon")
                val perRow = 6
                HabitIcons.chunked(perRow).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (key, vector) ->
                            val sel = key == iconKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) Navy else Color.White)
                                    .border(
                                        1.dp,
                                        if (sel) Navy else Border,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { iconKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(vector, contentDescription = null, tint = if (sel) Color.White else Navy)
                            }
                        }
                        repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- Bottom bar: always above the system navigation buttons ----------
            Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                onCreate(
                                    HabitDraft(
                                        title = title,
                                        description = description,
                                        frequency = frequency,
                                        reminder = reminder,
                                        hour = hour,
                                        minute = minute,
                                        iconKey = iconKey
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("habit_create_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                    ) {
                        Text("Create Habit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        HabitTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}
