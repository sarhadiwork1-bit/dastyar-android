package com.dastyar.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Provider boundary: the agent does not know vendor-specific HTTP details. */
interface AiProvider {
    fun validateConfiguration(baseUrl: String, model: String): String?
    fun send(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        system: String,
        withTools: Boolean
    ): JSONObject
}

object AiProviders {
    private val anthropic by lazy { AnthropicMessagesProvider() }

    fun resolve(id: String): AiProvider? = when (id.lowercase().trim()) {
        "anthropic" -> anthropic
        else -> null
    }
}

/** Anthropic adapter. Custom proxy hosts are deliberately rejected until separately reviewed. */
private class AnthropicMessagesProvider : AiProvider {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override fun validateConfiguration(baseUrl: String, model: String): String? {
        if (model.isBlank() || model.length > 120 || model.any { it.isWhitespace() }) {
            return "شناسه مدل معتبر نیست."
        }
        val url = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: return "آدرس API معتبر نیست."
        if (url.scheme != "https" || url.host != "api.anthropic.com" ||
            url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.encodedPath !in listOf("", "/")) {
            return "برای امنیت، فعلاً فقط نشانی رسمی https://api.anthropic.com مجاز است."
        }
        return null
    }

    override fun send(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: JSONArray,
        system: String,
        withTools: Boolean
    ): JSONObject {
        if (apiKey.isBlank()) throw IOException("API key is not configured")
        validateConfiguration(baseUrl, model)?.let { throw IOException(it) }

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1500)
            .put("system", system)
            .put("messages", messages)
        if (withTools) body.put("tools", Tools.definitions)

        val url = "https://api.anthropic.com/v1/messages".toHttpUrlOrNull()
            ?: throw IOException("Invalid provider endpoint")
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Do not expose response bodies: they may contain request details or provider diagnostics.
                throw IOException("AI provider returned HTTP ${response.code}")
            }
            return try {
                JSONObject(responseText)
            } catch (_: Exception) {
                throw IOException("AI provider returned an invalid response")
            }
        }
    }
}
