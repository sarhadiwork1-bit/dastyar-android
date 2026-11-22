package com.dastyar.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only action log. Never stores keys or message bodies. */
object AuditLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "audit.log")

    @Synchronized
    fun add(ctx: Context, line: String) {
        try {
            val f = file(ctx)
            val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("$ts  ${line.replace('\n', ' ').take(300)}\n")
            if (f.length() > 200_000) f.writeText(f.readLines().takeLast(500).joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun read(ctx: Context): List<String> =
        try { file(ctx).readLines().takeLast(150).reversed() } catch (_: Exception) { emptyList() }

    @Synchronized
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Exception) {} }
}
