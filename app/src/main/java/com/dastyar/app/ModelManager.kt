package com.dastyar.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Downloads/loads the small offline Persian Vosk model used only for wake-word spotting. */
object ModelManager {
    private const val URL = "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip"

    @Volatile var cached: Model? = null

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "vosk-fa")
    fun ready(ctx: Context) = File(dir(ctx), ".ready").exists()

    fun load(ctx: Context): Model? {
        cached?.let { return it }
        if (!ready(ctx)) return null
        return try {
            Model(dir(ctx).absolutePath).also { cached = it }
        } catch (e: Exception) {
            AuditLog.add(ctx, "model load failed: ${e.message}")
            null
        }
    }

    suspend fun download(ctx: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
        val tmp = File(ctx.cacheDir, "model.zip")
        client.newCall(Request.Builder().url(URL).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty response")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    var done = 0L
                    var last = -1
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; withContext(Dispatchers.Main) { onProgress(p) } }
                        }
                    }
                }
            }
        }
        try { tmp.inputStream().use { unzip(ctx, it) } } finally { tmp.delete() }
    }

    /** Also used to import a manually downloaded model zip. */
    suspend fun importZip(ctx: Context, input: InputStream) = withContext(Dispatchers.IO) { unzip(ctx, input) }

    private fun unzip(ctx: Context, input: InputStream) {
        val out = dir(ctx)
        cached = null
        out.deleteRecursively()
        out.mkdirs()
        val base = out.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val rel = e.name.substringAfter('/', "")
                if (rel.isNotEmpty()) {
                    val f = File(out, rel)
                    if (!f.canonicalPath.startsWith(base)) throw SecurityException("bad zip entry")
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zin.copyTo(it) }
                    }
                }
                e = zin.nextEntry
            }
        }
        if (!File(out, "am/final.mdl").exists()) {
            out.deleteRecursively()
            throw IOException("فایل مدل معتبر نیست")
        }
        File(out, ".ready").writeText("ok")
    }
}
