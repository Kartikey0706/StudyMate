package com.example.studymate

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.studymate.ui.theme.StudyMateTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Task(
    val title: String,
    val subject: String,
    val completed: Boolean = false,
    val priority: String = "Medium",
    val dueDate: String = "",
    val id: Long = System.nanoTime(),
    val xpAwarded: Boolean = false,
    val focusSessions: Int = 0,
    val focusMinutes: Int = 0,
    val reminder: String = "Off"
)

private const val PREFS_NAME = "StudyMatePrefs"
private const val TASKS_KEY = "tasks"

fun saveTasks(context: Context, tasks: List<Task>) {
    val jsonArray = JSONArray()

    tasks.forEach { task ->
        val obj = JSONObject()
        obj.put("id", task.id)
        obj.put("title", task.title)
        obj.put("subject", task.subject)
        obj.put("completed", task.completed)
        obj.put("priority", task.priority)
        obj.put("dueDate", task.dueDate)
        obj.put("xpAwarded", task.xpAwarded)
        obj.put("focusSessions", task.focusSessions)
        obj.put("focusMinutes", task.focusMinutes)
        obj.put("reminder", task.reminder)
        jsonArray.put(obj)
    }

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(TASKS_KEY, jsonArray.toString())
        .apply()
}

fun loadTasks(context: Context): MutableList<Task> {
    val savedData = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    ).getString(TASKS_KEY, null) ?: return mutableListOf()

    val jsonArray = JSONArray(savedData)
    val taskList = mutableListOf<Task>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)

        taskList.add(
            Task(
                id = obj.optLong("id", System.nanoTime() + i),
                title = obj.getString("title"),
                subject = obj.getString("subject"),
                completed = obj.getBoolean("completed"),
                priority = obj.optString("priority", "Medium"),
                dueDate = obj.optString("dueDate", ""),
                xpAwarded = obj.optBoolean("xpAwarded", false),
                focusSessions = obj.optInt("focusSessions", 0),
                focusMinutes = obj.optInt("focusMinutes", 0),
                reminder = obj.optString("reminder", "Off")
            )
        )
    }

    return taskList
}

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "task_reminders"
        val channelName = "Task Due Reminders"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Reminders for pending study tasks"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun scheduleTaskReminder(context: Context, task: Task) {
    if (task.completed || task.dueDate.isBlank() || task.reminder == "Off") {
        cancelTaskReminder(context, task.id)
        return
    }

    try {
        val parts = task.dueDate.split("-")
        if (parts.size != 3) {
            cancelTaskReminder(context, task.id)
            return
        }

        val year = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val day = parts[2].toInt()

        val calendar = Calendar.getInstance().apply {
            set(year, month, day, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (task.reminder == "One day before") {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val triggerTime = calendar.timeInMillis
        val currentTime = System.currentTimeMillis()

        if (triggerTime <= currentTime) {
            cancelTaskReminder(context, task.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_subject", task.subject)
            putExtra("reminder_timing", if (task.reminder == "One day before") "before" else "due")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    } catch (_: Exception) {
        cancelTaskReminder(context, task.id)
    }
}

fun cancelTaskReminder(context: Context, taskId: Long) {
    try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    } catch (_: Exception) {
        // Ignore
    }
}

private fun todayString(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return formatter.format(Date())
}

private fun yesterdayString(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return formatter.format(calendar.time)
}

private fun daysUntil(dueDate: String): Int? {
    return try {
        val parts = dueDate.split("-")
        if (parts.size != 3) return null

        val dueYear = parts[0].toInt()
        val dueMonth = parts[1].toInt() - 1
        val dueDay = parts[2].toInt()

        val localToday = Calendar.getInstance()

        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localToday.get(Calendar.YEAR),
                localToday.get(Calendar.MONTH),
                localToday.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
        }

        val dueUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(dueYear, dueMonth, dueDay, 0, 0, 0)
        }

        val oneDayMillis = 24L * 60L * 60L * 1000L
        ((dueUtc.timeInMillis - todayUtc.timeInMillis) / oneDayMillis).toInt()
    } catch (_: Exception) {
        null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)

        setContent {
            val prefs = remember {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            }

            var themeMode by remember {
                mutableStateOf(prefs.getString("themeMode", "System") ?: "System")
            }

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> systemDark
            }

            StudyMateTheme(darkTheme = useDarkTheme) {
                StudyMateApp(
                    themeMode = themeMode,
                    onThemeModeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putString("themeMode", newMode).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMateApp(
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val tasks = remember {
        mutableStateListOf<Task>().apply {
            addAll(loadTasks(context))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var totalXp by remember { mutableStateOf(prefs.getInt("totalXp", 0)) }
    var streak by remember { mutableStateOf(prefs.getInt("studyStreak", 0)) }
    var lastStudyDate by remember {
        mutableStateOf(prefs.getString("lastStudyDate", "") ?: "")
    }

    var showTaskDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFocusComplete by remember { mutableStateOf(false) }
    var showTaskSelectDialog by remember { mutableStateOf(false) }

    var taskTitle by remember { mutableStateOf("") }
    var taskSubject by remember { mutableStateOf("") }
    var taskPriority by remember { mutableStateOf("Medium") }
    var taskDueDate by remember { mutableStateOf("") }
    var taskReminder by remember { mutableStateOf("Off") }
    var selectedFilter by remember { mutableStateOf("All") }

    // Edit task state
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editSubject by remember { mutableStateOf("") }
    var editPriority by remember { mutableStateOf("Medium") }
    var editDueDate by remember { mutableStateOf("") }
    var editReminder by remember { mutableStateOf("Off") }

    var focusSeconds by remember { mutableStateOf(25 * 60) }
    var focusRunning by remember { mutableStateOf(false) }
    var focusSessionProcessed by remember { mutableStateOf(false) }
    var selectedFocusTaskId by remember { mutableStateOf<Long?>(null) }

    // Safely handle case where selected task was deleted
    if (selectedFocusTaskId != null && tasks.none { it.id == selectedFocusTaskId }) {
        selectedFocusTaskId = null
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(focusRunning) {
        if (focusRunning) {
            while (focusRunning && focusSeconds > 0) {
                delay(1000)
                if (focusSeconds > 0) {
                    focusSeconds -= 1
                }
            }

            if (focusSeconds == 0 && focusRunning && !focusSessionProcessed) {
                focusSessionProcessed = true
                focusRunning = false

                if (selectedFocusTaskId != null) {
                    val index = tasks.indexOfFirst { it.id == selectedFocusTaskId }
                    if (index != -1) {
                        val task = tasks[index]
                        tasks[index] = task.copy(
                            focusSessions = task.focusSessions + 1,
                            focusMinutes = task.focusMinutes + 25
                        )

                        totalXp += 15

                        val today = todayString()
                        if (lastStudyDate != today) {
                            streak = if (lastStudyDate == yesterdayString()) {
                                streak + 1
                            } else {
                                1
                            }
                            lastStudyDate = today
                        }

                        prefs.edit()
                            .putInt("totalXp", totalXp)
                            .putInt("studyStreak", streak)
                            .putString("lastStudyDate", lastStudyDate)
                            .apply()

                        saveTasks(context, tasks)
                    }
                }

                showFocusComplete = true
            }
        }
    }

    val completedTasks = tasks.count { it.completed }
    val pendingTasks = tasks.size - completedTasks
    val taskProgress = if (tasks.isEmpty()) {
        0f
    } else {
        completedTasks.toFloat() / tasks.size.toFloat()
    }

    val visibleTasks = when (selectedFilter) {
        "Pending" -> tasks.filter { !it.completed }
        "Completed" -> tasks.filter { it.completed }
        else -> tasks.toList()
    }

    val subjectProgress = tasks
        .groupBy { it.subject.ifBlank { "General" } }
        .toList()
        .sortedBy { it.first.lowercase(Locale.getDefault()) }

    val nextDeadline = tasks
        .filter { !it.completed && it.dueDate.isNotBlank() }
        .minByOrNull { it.dueDate }

    val level = totalXp / 100 + 1
    val xpWithinLevel = totalXp % 100
    val levelProgress = xpWithinLevel / 100f

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "StudyMate",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dark Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = themeMode == "Dark",
                        onCheckedChange = { checked ->
                            onThemeModeChange(if (checked) "Dark" else "Light")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("About StudyMate") },
                    selected = false,
                    onClick = {
                        showAboutDialog = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "StudyMate",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Student Task Manager",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        taskTitle = ""
                        taskSubject = ""
                        taskPriority = "Medium"
                        taskDueDate = ""
                        taskReminder = "Off"
                        showTaskDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Task"
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "Good evening, Kartikey 👋",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stay organized. Stay productive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            title = "Total",
                            value = tasks.size.toString(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Pending",
                            value = pendingTasks.toString(),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "Done",
                            value = completedTasks.toString(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    ProgressCard(
                        title = "Your Progress",
                        caption = "$completedTasks of ${tasks.size} tasks completed",
                        progress = taskProgress
                    )
                }

                item {
                    StreakXpCard(
                        streak = streak,
                        totalXp = totalXp,
                        level = level,
                        levelProgress = levelProgress,
                        xpWithinLevel = xpWithinLevel
                    )
                }

                if (nextDeadline != null) {
                    val remainingDays = daysUntil(nextDeadline.dueDate)
                    if (remainingDays != null) {
                        item {
                            UpcomingDeadlineCard(
                                task = nextDeadline,
                                daysLeft = remainingDays
                            )
                        }
                    }
                }

                item {
                    FocusCard(
                        seconds = focusSeconds,
                        isRunning = focusRunning,
                        selectedTask = tasks.find { it.id == selectedFocusTaskId },
                        onStartPause = {
                            if (focusSeconds > 0) {
                                focusRunning = !focusRunning
                            }
                        },
                        onReset = {
                            focusRunning = false
                            focusSeconds = 25 * 60
                            focusSessionProcessed = false
                        },
                        onSelectTask = {
                            showTaskSelectDialog = true
                        },
                        onClearTask = {
                            selectedFocusTaskId = null
                        }
                    )
                }

                if (subjectProgress.isNotEmpty()) {
                    item {
                        Text(
                            text = "Subject Progress",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(
                        items = subjectProgress,
                        key = { it.first }
                    ) { (subject, subjectTasks) ->
                        val done = subjectTasks.count { it.completed }
                        val ratio = done.toFloat() / subjectTasks.size.toFloat()

                        ProgressCard(
                            title = subject,
                            caption = "$done of ${subjectTasks.size} tasks completed",
                            progress = ratio
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Tasks",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${visibleTasks.size} tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Pending", "Completed").forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) }
                            )
                        }
                    }
                }

                if (visibleTasks.isEmpty()) {
                    item {
                        EmptyTasksCard(
                            message = when {
                                tasks.isEmpty() ->
                                    "Tap + to add your first academic task."
                                selectedFilter == "Pending" ->
                                    "No pending tasks."
                                else ->
                                    "No completed tasks yet."
                            },
                            onAddTask = if (tasks.isEmpty()) {
                                {
                                    taskTitle = ""
                                    taskSubject = ""
                                    taskPriority = "Medium"
                                    taskDueDate = ""
                                    taskReminder = "Off"
                                    showTaskDialog = true
                                }
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    items(
                        items = visibleTasks,
                        key = { it.id }
                    ) { task ->
                        TaskCard(
                            task = task,
                            onToggle = {
                                val index = tasks.indexOfFirst { it.id == task.id }
                                if (index != -1) {
                                    val shouldAwardXp =
                                        !task.completed && !task.xpAwarded

                                    val updatedTask = task.copy(
                                        completed = !task.completed,
                                        xpAwarded = task.xpAwarded || shouldAwardXp
                                    )
                                    tasks[index] = updatedTask

                                    if (updatedTask.completed) {
                                        cancelTaskReminder(context, updatedTask.id)
                                    } else {
                                        scheduleTaskReminder(context, updatedTask)
                                    }

                                    if (shouldAwardXp) {
                                        totalXp += if (task.priority == "High") 20 else 10

                                        val today = todayString()
                                        if (lastStudyDate != today) {
                                            streak = if (lastStudyDate == yesterdayString()) {
                                                streak + 1
                                            } else {
                                                1
                                            }
                                            lastStudyDate = today
                                        }

                                        prefs.edit()
                                            .putInt("totalXp", totalXp)
                                            .putInt("studyStreak", streak)
                                            .putString("lastStudyDate", lastStudyDate)
                                            .apply()
                                    }

                                    saveTasks(context, tasks)
                                }
                            },
                            onFocus = {
                                selectedFocusTaskId = task.id
                                focusSeconds = 25 * 60
                                focusRunning = true
                                focusSessionProcessed = false
                            },
                            onEdit = {
                                editingTask = task
                                editTitle = task.title
                                editSubject = task.subject
                                editPriority = task.priority
                                editDueDate = task.dueDate
                                editReminder = task.reminder
                            },
                            onDelete = {
                                if (selectedFocusTaskId == task.id) {
                                    selectedFocusTaskId = null
                                }
                                cancelTaskReminder(context, task.id)
                                tasks.removeAll { it.id == task.id }
                                saveTasks(context, tasks)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About StudyMate") },
            text = {
                Column {
                    Text("A simple Student Task Manager Android application.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Developed by: Kartikey Singh Suryavanshi")
                    Text("Course: BCA (Data Science & AI)")
                    Text("Technology: Kotlin + Jetpack Compose")
                    Text("Version: 1.0")
                    Text("Also made with mehnat & khoob saara pyaaarrrrr ")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showTaskSelectDialog) {
        AlertDialog(
            onDismissRequest = { showTaskSelectDialog = false },
            title = { Text("Select Task for Focus") },
            text = {
                val pendingTasksList = tasks.filter { !it.completed }
                if (pendingTasksList.isEmpty()) {
                    Text("No pending tasks available. Add a task first!")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingTasksList, key = { it.id }) { t ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFocusTaskId = t.id
                                        showTaskSelectDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedFocusTaskId == t.id)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = t.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = t.subject,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskSelectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingTask != null) {
        AlertDialog(
            onDismissRequest = { editingTask = null },
            title = { Text("Edit Task") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Task Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Low", "Medium", "High").forEach { priority ->
                            FilterChip(
                                selected = editPriority == priority,
                                onClick = { editPriority = priority },
                                label = { Text(priority) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val calendar = Calendar.getInstance()
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    editDueDate = String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        year,
                                        month + 1,
                                        day
                                    )
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (editDueDate.isBlank()) {
                                "Choose due date"
                            } else {
                                "Due: ${formatDueDate(editDueDate)}"
                            }
                        )
                    }

                    if (editDueDate.isNotBlank()) {
                        TextButton(
                            onClick = { editDueDate = "" },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear date")
                        }
                    }

                    if (editDueDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reminder",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Off", "On due date", "One day before").forEach { rem ->
                                FilterChip(
                                    selected = editReminder == rem,
                                    onClick = { editReminder = rem },
                                    label = { Text(rem) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editTitle.isNotBlank(),
                    onClick = {
                        val index = tasks.indexOfFirst { it.id == editingTask?.id }
                        if (index != -1) {
                            val existing = tasks[index]
                            val updated = existing.copy(
                                title = editTitle.trim(),
                                subject = editSubject.ifBlank { "General" }.trim(),
                                priority = editPriority,
                                dueDate = editDueDate,
                                reminder = if (editDueDate.isBlank()) "Off" else editReminder
                            )
                            tasks[index] = updated
                            saveTasks(context, tasks)
                            scheduleTaskReminder(context, updated)
                        }
                        editingTask = null
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTask = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTaskDialog) {
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text("Add New Task") },
            text = {
                Column {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("Task Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = taskSubject,
                        onValueChange = { taskSubject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Low", "Medium", "High").forEach { priority ->
                            FilterChip(
                                selected = taskPriority == priority,
                                onClick = { taskPriority = priority },
                                label = { Text(priority) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val calendar = Calendar.getInstance()
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    taskDueDate = String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        year,
                                        month + 1,
                                        day
                                    )
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (taskDueDate.isBlank()) {
                                "Choose due date"
                            } else {
                                "Due: ${formatDueDate(taskDueDate)}"
                            }
                        )
                    }

                    if (taskDueDate.isNotBlank()) {
                        TextButton(
                            onClick = { taskDueDate = "" },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear date")
                        }
                    }

                    if (taskDueDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reminder",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Off", "On due date", "One day before").forEach { rem ->
                                FilterChip(
                                    selected = taskReminder == rem,
                                    onClick = { taskReminder = rem },
                                    label = { Text(rem) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = taskTitle.isNotBlank(),
                    onClick = {
                        val newTask = Task(
                            title = taskTitle.trim(),
                            subject = taskSubject.ifBlank { "General" }.trim(),
                            priority = taskPriority,
                            dueDate = taskDueDate,
                            reminder = if (taskDueDate.isBlank()) "Off" else taskReminder
                        )
                        tasks.add(newTask)
                        saveTasks(context, tasks)
                        scheduleTaskReminder(context, newTask)
                        showTaskDialog = false
                    }
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFocusComplete) {
        val focusTask = tasks.find { it.id == selectedFocusTaskId }
        AlertDialog(
            onDismissRequest = { showFocusComplete = false },
            title = { Text("Focus session complete 🎉") },
            text = {
                Column {
                    Text("Great work! 25 focus minutes logged.")
                    if (focusTask != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Recorded against: ${focusTask.title}")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Earned +15 XP!")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFocusComplete = false
                        focusSeconds = 25 * 60
                        focusSessionProcessed = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ProgressCard(
    title: String,
    caption: String,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StreakXpCard(
    streak: Int,
    totalXp: Int,
    level: Int,
    levelProgress: Float,
    xpWithinLevel: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🔥 $streak day streak",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Complete a task each day to keep it going",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "⭐ $totalXp XP",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Level $level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = levelProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$xpWithinLevel / 100 XP to next level",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun UpcomingDeadlineCard(
    task: Task,
    daysLeft: Int
) {
    val scheme = MaterialTheme.colorScheme

    val statusText = when {
        daysLeft < 0 -> "Overdue by ${-daysLeft} day(s)"
        daysLeft == 0 -> "Due today"
        daysLeft == 1 -> "Due tomorrow"
        else -> "$daysLeft days left"
    }

    val cardColor = if (daysLeft < 0) {
        scheme.errorContainer
    } else {
        scheme.tertiaryContainer
    }

    val contentColor = if (daysLeft < 0) {
        scheme.onErrorContainer
    } else {
        scheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Next deadline",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
            Text(
                text = "${task.subject} • ${formatDueDate(task.dueDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

@Composable
fun FocusCard(
    seconds: Int,
    isRunning: Boolean,
    selectedTask: Task?,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onSelectTask: () -> Unit,
    onClearTask: () -> Unit
) {
    val totalFocusSeconds = 25 * 60
    val progress = seconds.toFloat() / totalFocusSeconds.toFloat()
    val timerText = String.format(
        Locale.US,
        "%02d:%02d",
        seconds / 60,
        seconds % 60
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Focus session",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "A little focus goes a long way",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTask != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎯 ${selectedTask.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${selectedTask.subject} • ${selectedTask.focusSessions} sessions (${selectedTask.focusMinutes}m logged)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onClearTask) {
                                Text("Change")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                OutlinedButton(
                    onClick = onSelectTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Select task to focus on (Optional)")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartPause,
                    modifier = Modifier.weight(1f),
                    enabled = seconds > 0
                ) {
                    Text(
                        when {
                            seconds == 0 -> "Finished"
                            isRunning -> "Pause"
                            seconds == totalFocusSeconds -> "Start Focus"
                            else -> "Resume"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun EmptyTasksCard(
    message: String,
    onAddTask: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconModifier = if (onAddTask != null) {
                Modifier
                    .size(56.dp)
                    .clickable(
                        onClick = onAddTask,
                        role = Role.Button
                    )
                    .padding(8.dp)
            } else {
                Modifier.size(42.dp)
            }

            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (onAddTask != null) "Add task" else null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No tasks here",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    onToggle: () -> Unit,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val cardColor = if (task.completed) {
        scheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        scheme.surface
    }

    val (priorityBg, priorityFg) = when (task.priority) {
        "High" -> Color(0xFFFDE8E8) to Color(0xFFC53030)
        "Low" -> Color(0xFFDEF7EC) to Color(0xFF03543F)
        else -> Color(0xFFFEF3C7) to Color(0xFFB45309)
    }

    val resolvedPriorityBg = if (isSystemInDarkTheme()) {
        when (task.priority) {
            "High" -> scheme.errorContainer
            "Low" -> scheme.secondaryContainer
            else -> scheme.tertiaryContainer
        }
    } else {
        priorityBg
    }

    val resolvedPriorityFg = if (isSystemInDarkTheme()) {
        when (task.priority) {
            "High" -> scheme.onErrorContainer
            "Low" -> scheme.onSecondaryContainer
            else -> scheme.onTertiaryContainer
        }
    } else {
        priorityFg
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (task.completed) scheme.onSurfaceVariant else scheme.onSurface,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (task.subject.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.subject,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }

                if (task.completed) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.primary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                modifier = Modifier.size(14.dp),
                                tint = scheme.primary
                            )
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row (Priority badge & Due date & Reminder info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = resolvedPriorityBg
                ) {
                    Text(
                        text = task.priority,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = resolvedPriorityFg
                    )
                }

                if (task.dueDate.isNotBlank()) {
                    Text(
                        text = "•  Due ${formatDueDate(task.dueDate)}",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (task.reminder != "Off") {
                    Text(
                        text = "•  🔔 ${task.reminder}",
                        color = scheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (task.focusSessions > 0) {
                    Text(
                        text = "•  🎯 ${task.focusSessions} (${task.focusMinutes}m)",
                        color = scheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (task.completed) "Mark Pending" else "Mark Done",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (!task.completed) {
                    OutlinedButton(
                        onClick = onFocus,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Focus", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.error
                    )
                }
            }
        }
    }
}

fun formatDueDate(date: String): String {
    val parts = date.split("-")
    if (parts.size != 3) return date

    return try {
        val year = parts[0]
        val month = parts[1].toInt()
        val day = parts[2]
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        "$day ${monthNames[month - 1]} $year"
    } catch (_: Exception) {
        date
    }
}
