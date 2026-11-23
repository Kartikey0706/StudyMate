package com.example.studymate

import android.Manifest
import android.R
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("task_title") ?: "Study Task"
        val subject = intent.getStringExtra("task_subject") ?: "General"
        val timing = intent.getStringExtra("reminder_timing") ?: "due"

        val message = if (timing == "before") {
            "$title ($subject) is due tomorrow."
        } else {
            "$title ($subject) is due today."
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, "task_reminders")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("Task Reminder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(intent.getLongExtra("task_id", 0L).toInt(), notification)
        }
    }
}
