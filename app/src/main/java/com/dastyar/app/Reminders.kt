package com.dastyar.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object Reminders {
    @Synchronized
    fun add(ctx: Context, text: String, at: Long): String {
        val arr = JSONArray(Prefs.reminders)
        val id = UUID.randomUUID().toString().take(8)
        arr.put(JSONObject().put("id", id).put("text", text).put("at", at))
        Prefs.reminders = arr.toString()
        schedule(ctx, id, text, at)
        return id
    }

    fun schedule(ctx: Context, id: String, text: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, id, text)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(ctx: Context, id: String, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    @Synchronized
    fun remove(id: String) {
        val arr = JSONArray(Prefs.reminders)
        val out = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("id") != id) out.put(arr.getJSONObject(i))
        Prefs.reminders = out.toString()
    }

    @Synchronized
    fun rescheduleAll(ctx: Context) {
        val arr = JSONArray(Prefs.reminders)
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("at") > now) {
                keep.put(o)
                schedule(ctx, o.getString("id"), o.getString("text"), o.getLong("at"))
            }
        }
        Prefs.reminders = keep.toString()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val text = i.getStringExtra("text") ?: return
        val id = i.getStringExtra("id") ?: ""
        Reminders.remove(id)
        val n = Notification.Builder(ctx, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("یادآوری")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id.hashCode(), n)
        val pr = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                withTimeoutOrNull(8000) { Speaker.speak(ctx.applicationContext, "یادآوری: $text") }
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Reminders.rescheduleAll(ctx)
    }
}
