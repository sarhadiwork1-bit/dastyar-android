package com.dastyar.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.reflect.KProperty

/** All settings. Secrets (API keys) live in EncryptedSharedPreferences. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var secure: SharedPreferences

    @Synchronized
    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        val app = ctx.applicationContext
        sp = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        secure = try {
            createSecure(app)
        } catch (e: Exception) {
            // Keystore data corrupted (e.g. restored device): reset secrets and retry once.
            app.deleteSharedPreferences("secure")
            createSecure(app)
        }
    }

    private fun createSecure(app: Context): SharedPreferences {
        val mk = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            app, "secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private class Str(val key: String, val def: String, val sec: Boolean = false) {
        operator fun getValue(t: Any?, p: KProperty<*>): String =
            (if (sec) Prefs.secure else Prefs.sp).getString(key, def) ?: def
        operator fun setValue(t: Any?, p: KProperty<*>, v: String) {
            (if (sec) Prefs.secure else Prefs.sp).edit().putString(key, v).apply()
        }
    }

    private class Bool(val key: String, val def: Boolean) {
        operator fun getValue(t: Any?, p: KProperty<*>): Boolean = Prefs.sp.getBoolean(key, def)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Boolean) { Prefs.sp.edit().putBoolean(key, v).apply() }
    }

    private class Num(val key: String, val def: Int) {
        operator fun getValue(t: Any?, p: KProperty<*>): Int = Prefs.sp.getInt(key, def)
        operator fun setValue(t: Any?, p: KProperty<*>, v: Int) { Prefs.sp.edit().putInt(key, v).apply() }
    }

    @Synchronized
    internal fun readSecret(key: String, fallback: String): String = secure.getString(key, fallback) ?: fallback

    @Synchronized
    internal fun writeSecret(key: String, value: String) { secure.edit().putString(key, value).apply() }

    // General
    var assistantName by Str("name", "دستیار")
    var wakeWord by Str("wake", "کیارش")
    var wakeVariants by Str("wake_variants", "کی آرش,کیا رش,کیارس,کیارشی")
    var listenLang by Str("lang", "fa-IR")

    // AI
    var aiProvider by Str("ai_provider", "anthropic")
    var apiKey by Str("api_key", "", true)
    var apiBase by Str("api_base", "https://api.anthropic.com")
    var model by Str("model", "claude-sonnet-5")

    // Voice
    var speakReplies by Bool("speak", true)
    var ttsProvider by Str("tts_provider", "system")
    var systemVoice by Str("sys_voice", "")
    var azureKey by Str("azure_key", "", true)
    var azureRegion by Str("azure_region", "westeurope")
    var azureVoiceFa by Str("azure_voice_fa", "fa-IR-DilaraNeural")
    var azureVoiceEn by Str("azure_voice_en", "en-US-JennyNeural")

    // Safety: 0 = never ask, 1 = ask for sensitive actions, 2 = ask for every state-changing action
    var confirmMode by Num("confirm", 1)
    var blockedPackages by Str(
        "blocked",
        "com.x8bit.bitwarden,com.agilebits.onepassword,com.lastpass.lpandroid,com.google.android.apps.authenticator2"
    )

    // Appearance: themeMode 0 system, 1 light, 2 dark, 3 amoled
    var themeMode by Num("theme", 0)
    var accent by Num("accent", 0)
    var bgUri by Str("bg", "")
    var bgDim by Num("bgdim", 40)

    // Reminders storage (JSON)
    var reminders by Str("reminders", "[]")
}
