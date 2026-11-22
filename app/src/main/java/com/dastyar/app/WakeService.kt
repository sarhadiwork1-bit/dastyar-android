package com.dastyar.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Foreground service: listens offline for the wake word, then runs one voice command. */
class WakeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speech: SpeechService? = null
    private var started = false
    @Volatile private var busy = false
    @Volatile private var lastWakeAt = 0L
    @Volatile private var restartScheduled = false
    private var restartAttempts = 0

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "com.dastyar.app.STOP"
        private const val WAKE_COOLDOWN_MS = 2500L
        private const val MAX_RESTART_DELAY_MS = 30000L
        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, WakeService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WakeService::class.java)) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, WakeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "wake")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("${Prefs.assistantName} فعال است")
            .setContentText("منتظر کلمه «${Prefs.wakeWord}»")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "توقف", stopPi).build())
            .build()
        ServiceCompat.startForeground(
            this, 1, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        )
        running = true
        if (!started) {
            started = true
            scope.launch {
                try {
                    val model = withContext(Dispatchers.Default) { ModelManager.load(this@WakeService) }
                    if (model == null) {
                        AuditLog.add(this@WakeService, "wake service: voice model missing")
                        stopSelf()
                    } else startSpeech(model)
                } catch (e: Exception) {
                    AuditLog.add(this@WakeService, "wake model init failed: ${e.javaClass.simpleName}")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startSpeech(model: Model) {
        if (!running || busy || speech != null) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AuditLog.add(this, "wake service stopped: microphone permission missing")
            stopSelf()
            return
        }
        try {
            val s = SpeechService(Recognizer(model, 16000f), 16000f)
            speech = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(h: String?) { check(h, "partial") }
                override fun onResult(h: String?) { check(h, "text") }
                override fun onFinalResult(h: String?) { check(h, "text") }
                override fun onError(e: Exception?) {
                    AuditLog.add(this@WakeService, "wake recognition error: ${e?.javaClass?.simpleName ?: "unknown"}")
                    releaseRecognizer()
                    scheduleRecognizerRestart(model)
                }
                override fun onTimeout() {
                    releaseRecognizer()
                    scheduleRecognizerRestart(model)
                }
            })
        } catch (e: Exception) {
            AuditLog.add(this, "mic start failed: ${e.javaClass.simpleName}")
            releaseRecognizer()
            scheduleRecognizerRestart(model)
        }
    }

    private fun releaseRecognizer() {
        val old = speech
        speech = null
        try { old?.stop() } catch (_: Exception) {}
        try { old?.shutdown() } catch (_: Exception) {}
    }

    private fun check(json: String?, key: String) {
        if (busy || !running || json.isNullOrBlank()) return
        val txt = try { JSONObject(json).optString(key) } catch (_: Exception) { return }
        if (txt.isBlank() || !Fuzzy.wakeMatches(txt)) return

        // Partial results can repeat the same phrase many times; debounce them.
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAt < WAKE_COOLDOWN_MS) return
        lastWakeAt = now
        busy = true
        restartAttempts = 0
        AuditLog.add(this, "wake word detected")
        scope.launch { session() }
    }

    private fun scheduleRecognizerRestart(model: Model) {
        if (!running || busy || restartScheduled) return
        restartScheduled = true
        restartAttempts = (restartAttempts + 1).coerceAtMost(6)
        val delayMs = (1000L shl (restartAttempts - 1)).coerceAtMost(MAX_RESTART_DELAY_MS)
        scope.launch {
            delay(delayMs)
            restartScheduled = false
            if (running && !busy && speech == null) startSpeech(model)
        }
    }

    private suspend fun session() {
        releaseRecognizer()
        try {
            Agent.handleVoice(this)
        } catch (e: Exception) {
            AuditLog.add(this, "session error: ${e.message}")
        }
        delay(900)
        busy = false
        ModelManager.cached?.let { if (running && speech == null) startSpeech(it) }
    }

    override fun onDestroy() {
        running = false
        busy = false
        restartScheduled = false
        scope.cancel()
        try { speech?.stop() } catch (_: Exception) {}
        try { speech?.shutdown() } catch (_: Exception) {}
        speech = null
        super.onDestroy()
    }
}
