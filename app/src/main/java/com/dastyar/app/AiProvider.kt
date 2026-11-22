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
    private val openai by lazy { OpenAiCompatibleProvider() }

    fun resolve(id: String): AiProvider? = when (id.lowercase().trim()) {
        "anthropic" -> anthropic
        "openai", "openai-compatible", "compatible" -> openai
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

/**
 * OpenAI Chat Completions compatible adapter.
 * Works with OpenAI, OpenRouter, Groq, and many other /v1/chat/completions endpoints.
 * Converts Anthropic-style message history used by Agent into OpenAI format and back.
 */
private class OpenAiCompatibleProvider : AiProvider {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun validateConfiguration(baseUrl: String, model: String): String? {
        if (model.isBlank() || model.length > 120 || model.any { it.isWhitespace() }) {
            return "شناسه مدل معتبر نیست."
        }
        val url = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: return "آدرس API معتبر نیست."
        if (url.scheme != "https") {
            return "فقط HTTPS مجاز است."
        }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return "نام کاربری/رمز در آدرس مجاز نیست."
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

        val endpoint = resolveEndpoint(baseUrl)
        val openaiMessages = toOpenAiMessages(messages, system)
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1500)
            .put("messages", openaiMessages)
        if (withTools) body.put("tools", toOpenAiTools(Tools.definitions))

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val hint = responseText.take(200).replace(Regex("\\s+"), " ")
                throw IOException("AI provider returned HTTP ${response.code}" + if (hint.isNotBlank()) ": $hint" else "")
            }
            return try {
                fromOpenAiResponse(JSONObject(responseText))
            } catch (e: Exception) {
                throw IOException("AI provider returned an invalid response: ${e.message}")
            }
        }
    }

    private fun resolveEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private fun toOpenAiTools(defs: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until defs.length()) {
            val d = defs.getJSONObject(i)
            val schema = d.optJSONObject("input_schema") ?: JSONObject().put("type", "object").put("properties", JSONObject())
            out.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", d.getString("name"))
                            .put("description", d.optString("description", ""))
                            .put("parameters", schema)
                    )
            )
        }
        return out
    }

    private fun toOpenAiMessages(messages: JSONArray, system: String): JSONArray {
        val out = JSONArray()
        if (system.isNotBlank()) {
            out.put(JSONObject().put("role", "system").put("content", system))
        }
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.getString("role")
            val content = m.get("content")
            when (role) {
                "user" -> {
                    when (content) {
                        is String -> out.put(JSONObject().put("role", "user").put("content", content))
                        is JSONArray -> {
                            for (j in 0 until content.length()) {
                                val block = content.optJSONObject(j) ?: continue
                                if (block.optString("type") == "tool_result") {
                                    out.put(
                                        JSONObject()
                                            .put("role", "tool")
                                            .put("tool_call_id", block.optString("tool_use_id", "call_$j"))
                                            .put("content", block.optString("content", ""))
                                    )
                                }
                            }
                        }
                        else -> out.put(JSONObject().put("role", "user").put("content", content.toString()))
                    }
                }
                "assistant" -> {
                    when (content) {
                        is String -> out.put(JSONObject().put("role", "assistant").put("content", content))
                        is JSONArray -> {
                            val textParts = StringBuilder()
                            val toolCalls = JSONArray()
                            for (j in 0 until content.length()) {
                                val block = content.optJSONObject(j) ?: continue
                                when (block.optString("type")) {
                                    "text" -> textParts.append(block.optString("text", ""))
                                    "tool_use" -> {
                                        val id = block.optString("id", "call_$j")
                                        val name = block.optString("name", "")
                                        val input = block.optJSONObject("input") ?: JSONObject()
                                        toolCalls.put(
                                            JSONObject()
                                                .put("id", id)
                                                .put("type", "function")
                                                .put(
                                                    "function",
                                                    JSONObject()
                                                        .put("name", name)
                                                        .put("arguments", input.toString())
                                                )
                                        )
                                    }
                                }
                            }
                            val msg = JSONObject().put("role", "assistant")
                            if (textParts.isNotEmpty()) msg.put("content", textParts.toString())
                            else msg.put("content", JSONObject.NULL)
                            if (toolCalls.length() > 0) msg.put("tool_calls", toolCalls)
                            out.put(msg)
                        }
                        else -> out.put(JSONObject().put("role", "assistant").put("content", content.toString()))
                    }
                }
            }
        }
        return out
    }

    private fun fromOpenAiResponse(resp: JSONObject): JSONObject {
        val choices = resp.optJSONArray("choices") ?: throw IOException("missing choices")
        if (choices.length() == 0) throw IOException("empty choices")
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw IOException("missing message")

        val content = JSONArray()
        val text = message.optString("content", "")
        if (text.isNotBlank() && text != "null") {
            content.put(JSONObject().put("type", "text").put("text", text))
        }

        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val fn = tc.optJSONObject("function") ?: continue
                val name = fn.optString("name", "")
                val argsRaw = fn.optString("arguments", "{}")
                val input = try {
                    JSONObject(argsRaw)
                } catch (_: Exception) {
                    JSONObject().put("_raw", argsRaw)
                }
                content.put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", tc.optString("id", "call_$i"))
                        .put("name", name)
                        .put("input", input)
                )
            }
        }

        if (content.length() == 0) {
            content.put(JSONObject().put("type", "text").put("text", ""))
        }
        return JSONObject().put("content", content)
    }
}
