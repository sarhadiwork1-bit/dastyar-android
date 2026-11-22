package com.dastyar.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    val azureFaVoices = listOf("fa-IR-DilaraNeural" to "زن (دلارا)", "fa-IR-FaridNeural" to "مرد (فرید)")
    val azureEnVoices = listOf(
        "en-US-JennyNeural" to "Female – Jenny", "en-US-GuyNeural" to "Male – Guy",
        "en-GB-SoniaNeural" to "Female – Sonia (UK)", "en-GB-RyanNeural" to "Male – Ryan (UK)"
    )

    private fun isPersian(s: String) = s.any { it in '\u0600'..'\u06FF' }

    suspend fun speak(ctx: Context, text: String) {
        val clean = text.replace(Regex("[*_#`>]+"), " ").trim()
        if (clean.isEmpty()) return
        if (Prefs.ttsProvider == "azure" && Prefs.azureKey.isNotBlank()) {
            try { speakAzure(ctx, clean); return } catch (e: Exception) {
                AuditLog.add(ctx, "azure tts failed: ${e.message}")
            }
        }
        speakSystem(ctx, clean)
    }

    // ---- System TTS ----
    private suspend fun engine(ctx: Context): TextToSpeech? = withContext(Dispatchers.Main) {
        val existing = tts
        if (existing != null && ready) return@withContext existing
        suspendCancellableCoroutine<TextToSpeech?> { cont ->
            val holder = arrayOfNulls<TextToSpeech>(1)
            holder[0] = TextToSpeech(ctx.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (cont.isActive) cont.resume(if (ready) holder[0] else null)
            }
            tts = holder[0]
        }
    }

    suspend fun systemVoices(ctx: Context): List<String> {
        val t = engine(ctx) ?: return emptyList()
        return t.voices?.map { it.name }?.filter { it.startsWith("fa") || it.startsWith("en") }?.sorted()?.take(40) ?: emptyList()
    }

    private suspend fun speakSystem(ctx: Context, text: String) {
        val t = engine(ctx) ?: return
        withContext(Dispatchers.Main) {
            t.setLanguage(if (isPersian(text)) Locale("fa", "IR") else Locale.US)
            if (Prefs.systemVoice.isNotBlank()) {
                t.voices?.firstOrNull { it.name == Prefs.systemVoice }?.let { t.voice = it }
            }
            for (chunk in text.chunked(3000)) {
                suspendCancellableCoroutine<Unit> { cont ->
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    })
                    t.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                    cont.invokeOnCancellation { t.stop() }
                }
            }
        }
    }

    // ---- Azure neural TTS (male/female voices) ----
    private suspend fun speakAzure(ctx: Context, text: String) {
        val voice = if (isPersian(text)) Prefs.azureVoiceFa else Prefs.azureVoiceEn
        val region = Prefs.azureRegion.lowercase().filter { it.isLetterOrDigit() }
        if (region.isEmpty()) throw IOException("region")
        val esc = text.take(2500).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val ssml = "<speak version='1.0' xml:lang='${voice.take(5)}'><voice name='$voice'>$esc</voice></speak>"
        val file = withContext(Dispatchers.IO) {
            val req = Request.Builder().url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
                .header("Ocp-Apim-Subscription-Key", Prefs.azureKey)
                .header("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .header("User-Agent", "dastyar")
                .post(ssml.toRequestBody("application/ssml+xml".toMediaType())).build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Azure HTTP ${r.code}")
                val f = File.createTempFile("tts", ".mp3", ctx.cacheDir)
                f.outputStream().use { o -> r.body?.byteStream()?.copyTo(o) }
                f
            }
        }
        try {
            withContext(Dispatchers.Main) {
                val mp = MediaPlayer()
                try {
                    mp.setDataSource(file.path)
                    mp.prepare()
                    suspendCancellableCoroutine<Unit> { cont ->
                        mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                        mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                        mp.start()
                        cont.invokeOnCancellation { try { mp.stop() } catch (_: Exception) {} }
                    }
                } finally { mp.release() }
            }
        } finally { file.delete() }
    }
}

object Listener {
    /** Listens once with the platform recognizer. Returns null on silence/error/timeout. */
    suspend fun listenOnce(ctx: Context, lang: String = Prefs.listenLang, timeoutMs: Long = 15000): String? =
        withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return@withContext null
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<String?> { cont ->
                    val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
                    val finish = { r: String? -> if (cont.isActive) { sr.destroy(); cont.resume(r) } }
                    sr.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(p: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(v: Float) {}
                        override fun onBufferReceived(b: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) { finish(null) }
                        override fun onResults(b: Bundle?) {
                            finish(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                        }
                        override fun onPartialResults(b: Bundle?) {}
                        override fun onEvent(t: Int, b: Bundle?) {}
                    })
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    sr.startListening(i)
                    cont.invokeOnCancellation { sr.destroy() }
                }
            }
        }
}

object Voice {
    private val yes = setOf("بله", "آره", "اره", "باشه", "تایید", "اوکی", "انجام", "yes", "yeah", "ok", "okay", "sure", "confirm")
        .map { Fuzzy.norm(it) }.toSet()
    private val no = setOf("نه", "نخیر", "خیر", "نکن", "لغو", "no", "nope", "cancel", "stop", "dont")
        .map { Fuzzy.norm(it) }.toSet()

    /** Asks the user a yes/no question by voice. Anything unclear counts as "no". */
    suspend fun confirm(ctx: Context, question: String): Boolean {
        Speaker.speak(ctx, question)
        val answer = Listener.listenOnce(ctx, timeoutMs = 10000) ?: return false
        val toks = Fuzzy.norm(answer).split(' ').toSet()
        if (toks.any { it in no }) return false
        return toks.any { it in yes }
    }
}
