package com.dastyar.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Every capability the model can use. State-changing tools pass through the confirmation gate. */
object Tools {
    private fun t(
        name: String, desc: String,
        props: List<Triple<String, String, String>> = emptyList(),
        required: List<String> = props.map { it.first }
    ): JSONObject {
        val p = JSONObject()
        for ((n, ty, d) in props) {
            val o = JSONObject().put("type", ty).put("description", d)
            if (ty == "array") o.put("items", JSONObject().put("type", "string"))
            p.put(n, o)
        }
        return JSONObject().put("name", name).put("description", desc).put(
            "input_schema",
            JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
        )
    }

    private val S = "string"
    private val ALT = Triple("alt_queries", "array", "same name in other spellings/scripts, e.g. Persian and Latin")

    val definitions: JSONArray by lazy {
        JSONArray(listOf(
            t("find_contact", "Search contacts by name.", listOf(Triple("query", S, "name"), ALT), listOf("query")),
            t("call", "Place a phone call to a contact name or a phone number.", listOf(Triple("target", S, "contact name or number"), ALT), listOf("target")),
            t("send_sms", "Send an SMS to a contact name or number (user is asked to confirm).", listOf(Triple("to", S, "contact name or number"), Triple("text", S, "message"), ALT), listOf("to", "text")),
            t("read_sms", "Read the latest received SMS messages.", listOf(Triple("count", "integer", "1-10")), listOf()),
            t("set_alarm", "Set an alarm in the clock app.", listOf(Triple("hour", "integer", "0-23"), Triple("minute", "integer", "0-59"), Triple("label", S, "label"))),
            t("set_timer", "Start a countdown timer.", listOf(Triple("seconds", "integer", "duration"), Triple("label", S, "label")), listOf("seconds")),
            t("set_reminder", "Create a reminder that notifies and speaks at a local date-time.", listOf(Triple("text", S, "what to remind"), Triple("datetime", S, "local ISO date-time like 2026-11-12T09:30"))),
            t("flashlight", "Turn the flashlight on or off.", listOf(Triple("on", "boolean", "true=on"))),
            t("set_volume", "Set a volume stream level.", listOf(Triple("stream", S, "music|ring|alarm|notification|call"), Triple("percent", "integer", "0-100"))),
            t("set_brightness", "Set screen brightness percent.", listOf(Triple("percent", "integer", "1-100"))),
            t("open_app", "Open an installed app by name or package.", listOf(Triple("name", S, "app name or package"), ALT), listOf("name")),
            t("list_apps", "List installed launchable apps matching a query (empty = first 40).", listOf(Triple("query", S, "filter")), listOf()),
            t("open_settings", "Open a system settings page. page: main|wifi|bluetooth|network|display|sound|battery|location|airplane|nfc|apps|accessibility|date|language|app_details|app_notifications. package is needed for app_details/app_notifications.", listOf(Triple("page", S, "page"), Triple("package", S, "package name")), listOf("page")),
            t("quick_toggle", "Toggle a quick-settings tile via the accessibility service.", listOf(Triple("name", S, "wifi|bluetooth|airplane|flashlight|do_not_disturb|location|hotspot|mobile_data|auto_rotate|battery_saver|nfc"), Triple("on", "boolean", "desired state"))),
            t("open_url", "Open an http(s) link in the browser.", listOf(Triple("url", S, "url"))),
            t("media", "Control media playback.", listOf(Triple("action", S, "play_pause|next|previous|stop"))),
            t("battery", "Get battery level and charging state."),
            t("speak", "Read text aloud to the user.", listOf(Triple("text", S, "text to read"))),
            t("ask_ai", "Ask the configured online AI provider a separate question and return the answer text.", listOf(Triple("question", S, "question"))),
            t("save_memory", "Save a short non-sensitive fact only after the user explicitly asked the assistant to remember it. The app asks for confirmation.", listOf(Triple("text", S, "short fact to remember"))),
            t("list_memory", "List user-approved local memories. Never infer or add new memories here."),
            t("forget_memory", "Forget one exact saved memory after user confirmation.", listOf(Triple("text", S, "exact saved memory text"))),
            t("ui_read_screen", "Read the visible UI elements of the current screen (numbered)."),
            t("ui_click", "Click element by index from the latest ui_read_screen.", listOf(Triple("index", "integer", "element index"))),
            t("ui_click_text", "Click the visible element whose text matches.", listOf(Triple("text", S, "text or description"))),
            t("ui_type", "Type text (Persian or English) into the focused/first text field.", listOf(Triple("text", S, "text"), Triple("replace", "boolean", "replace existing text")), listOf("text")),
            t("ui_scroll", "Scroll the current screen.", listOf(Triple("direction", S, "down|up"))),
            t("ui_global", "System navigation action.", listOf(Triple("action", S, "back|home|recents|notifications|quick_settings|lock|screenshot"))),
            t("ui_wait", "Wait for the UI to settle.", listOf(Triple("ms", "integer", "up to 3000")))
        ))
    }

    private val toggles = mapOf(
        "wifi" to listOf("Wi-Fi", "Wi\u2011Fi", "WLAN", "Internet", "وای‌فای", "وای فای", "اینترنت"),
        "bluetooth" to listOf("Bluetooth", "بلوتوث"),
        "airplane" to listOf("Airplane", "Flight mode", "حالت پرواز", "هواپیما"),
        "flashlight" to listOf("Flashlight", "Torch", "چراغ قوه"),
        "do_not_disturb" to listOf("Do Not Disturb", "DND", "مزاحم نشوید", "مزاحم"),
        "location" to listOf("Location", "موقعیت", "مکان"),
        "hotspot" to listOf("Hotspot", "هات‌اسپات", "هات اسپات"),
        "mobile_data" to listOf("Mobile data", "Cellular", "داده همراه", "اینترنت همراه"),
        "auto_rotate" to listOf("Auto-rotate", "Auto rotate", "چرخش خودکار"),
        "battery_saver" to listOf("Battery Saver", "Battery saver", "بهینه‌سازی باتری", "صرفه‌جویی"),
        "nfc" to listOf("NFC")
    )

    private val DANGER = Regex(
        "uninstall|delete|erase|factory|reset|format|install|pay|transfer|purchase|buy|حذف|پاک|پرداخت|انتقال|خرید|بازنشانی|ریست|فرمت|نصب",
        RegexOption.IGNORE_CASE
    )

    private val READ_ONLY = setOf("find_contact", "read_sms", "list_apps", "battery", "speak", "ask_ai", "list_memory", "ui_read_screen", "ui_wait")

    private fun JSONObject.strs(key: String): List<String> {
        val o = optJSONArray(key) ?: return emptyList()
        return (0 until o.length()).map { o.optString(it) }.filter { it.isNotBlank() }
    }

    private fun untrusted(s: String) = "<untrusted_data>\n$s\n</untrusted_data>"

    suspend fun execute(ctx: Context, name: String, a: JSONObject): String {
        // Do not write contact identifiers, message bodies, screen text, or typed content to logs.
        val shown = if (name in setOf("send_sms", "read_sms", "find_contact", "call", "ui_type", "ui_read_screen", "ui_click_text", "save_memory", "forget_memory", "list_memory"))
            "(sensitive args hidden)" else a.toString().take(120)
        AuditLog.add(ctx, "tool $name $shown")
        return try {
            dispatch(ctx, name, a)
        } catch (e: SecurityException) {
            "ERROR: missing permission or access (${e.message}). Tell the user to grant it on the app's Home screen."
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private suspend fun gate(ctx: Context, question: String): Boolean {
        val ok = Voice.confirm(ctx, question)
        AuditLog.add(ctx, "confirm '${question.take(80)}' -> $ok")
        return ok
    }

    /** mode 0: never ask; 1: sensitive only; 2: every state-changing action. */
    private suspend fun allowed(ctx: Context, name: String, question: String, sensitive: Boolean): Boolean {
        val mode = Prefs.confirmMode
        val need = when (mode) { 0 -> false; 2 -> name !in READ_ONLY; else -> sensitive }
        return !need || gate(ctx, question)
    }

    private fun need(ctx: Context, perm: String) {
        if (ctx.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) throw SecurityException(perm)
    }

    private fun newTask(ctx: Context, i: Intent) { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun svc(): DastyarAccessibilityService = DastyarAccessibilityService.instance
        ?: throw IllegalStateException("Accessibility service is OFF. Ask the user to enable it from the app's Home screen.")

    // ---- contacts ----
    private class Hit(val name: String, val number: String)

    private fun searchContacts(ctx: Context, qs: List<String>): List<Hit> {
        need(ctx, Manifest.permission.READ_CONTACTS)
        val nq = qs.map { Fuzzy.norm(it) }.filter { it.isNotEmpty() }
        val all = ArrayList<Pair<Int, Hit>>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val sc = nq.minOfOrNull { Fuzzy.score(Fuzzy.norm(n), it) } ?: 99
                if (sc < 99) all.add(sc to Hit(n, num))
            }
        }
        val best = all.minOfOrNull { it.first } ?: return emptyList()
        return all.filter { it.first == best }.map { it.second }
            .distinctBy { it.name + Fuzzy.asciiDigits(it.number).filter { ch -> ch.isDigit() } }.take(6)
    }

    private class Resolved(val number: String?, val label: String, val error: String? = null)

    private fun resolve(ctx: Context, target: String, alts: List<String>): Resolved {
        if (target.none { it.isLetter() }) {
            val num = Fuzzy.asciiDigits(target).filter { it.isDigit() || it == '+' }
            return if (num.length >= 3) Resolved(num, num) else Resolved(null, target, "invalid number")
        }
        val hits = searchContacts(ctx, listOf(target) + alts)
        if (hits.isEmpty()) return Resolved(null, target, "no contact found for '$target'")
        val distinctNumbers = hits.distinctBy { Fuzzy.asciiDigits(it.number).filter { ch -> ch.isDigit() } }
        if (distinctNumbers.size > 1) {
            val choices = distinctNumbers.map { hit ->
                val digits = Fuzzy.asciiDigits(hit.number).filter { ch -> ch.isDigit() }
                "${hit.name} (ending ${digits.takeLast(4)})"
            }
            return Resolved(null, target, "ambiguous contact; ask the user to choose one: " + choices.joinToString("; "))
        }
        return Resolved(distinctNumbers[0].number, distinctNumbers[0].name)
    }

    // ---- apps ----
    private fun launchables(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }.distinctBy { it.second }
    }

    private suspend fun dispatch(ctx: Context, name: String, a: JSONObject): String {
        when (name) {
            "find_contact" -> {
                val hits = searchContacts(ctx, listOf(a.getString("query")) + a.strs("alt_queries"))
                return if (hits.isEmpty()) "no match" else hits.joinToString("\n") { "${it.name}: ${it.number}" }
            }
            "call" -> {
                val r = resolve(ctx, a.getString("target"), a.strs("alt_queries"))
                if (r.number == null) return r.error ?: "failed"
                if (!allowed(ctx, name, "تماس با ${r.label} برقرار شود؟", true)) return "user declined"
                need(ctx, Manifest.permission.CALL_PHONE)
                ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", r.number, null), Bundle())
                return "calling ${r.label}"
            }
            "send_sms" -> {
                val r = resolve(ctx, a.getString("to"), a.strs("alt_queries"))
                if (r.number == null) return r.error ?: "failed"
                val text = a.getString("text").trim()
                if (text.isBlank()) return "message is empty"
                // Keep the confirmation complete: never send a message whose full body was not confirmed.
                if (text.length > 500) return "message is too long (maximum 500 characters); split it into separate confirmed messages"
                if (!allowed(ctx, name, "پیامک به ${r.label} ارسال شود؟ متن کامل: $text", true)) return "user declined"
                need(ctx, Manifest.permission.SEND_SMS)
                val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
                sm.sendMultipartTextMessage(r.number, null, sm.divideMessage(text), null, null)
                return "SMS handed to Android's SMS service for ${r.label}; delivery has not been confirmed."
            }
            "read_sms" -> {
                need(ctx, Manifest.permission.READ_SMS)
                val n = a.optInt("count", 5).coerceIn(1, 10)
                val sb = StringBuilder()
                ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), null, null, "date DESC LIMIT $n"
                )?.use { c ->
                    while (c.moveToNext()) sb.append("From ${c.getString(0)}: ${c.getString(1)?.take(300)}\n")
                }
                return untrusted(sb.toString().ifBlank { "no messages" })
            }
            "set_alarm" -> {
                if (!allowed(ctx, name, "آلارم تنظیم شود؟", false)) return "user declined"
                newTask(ctx, Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, a.getInt("hour")).putExtra(AlarmClock.EXTRA_MINUTES, a.getInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "")).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                return "alarm set"
            }
            "set_timer" -> {
                if (!allowed(ctx, name, "تایمر تنظیم شود؟", false)) return "user declined"
                newTask(ctx, Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, a.getInt("seconds")).putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", ""))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                return "timer started"
            }
            "set_reminder" -> {
                val at = LocalDateTime.parse(a.getString("datetime")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (at <= System.currentTimeMillis()) return "that time is in the past"
                Reminders.add(ctx, a.getString("text"), at)
                return "reminder saved"
            }
            "flashlight" -> {
                val cm = ctx.getSystemService(CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    ?: return "no flash on this device"
                cm.setTorchMode(id, a.getBoolean("on"))
                return "ok"
            }
            "set_volume" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                val st = when (a.getString("stream")) {
                    "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM
                    "notification" -> AudioManager.STREAM_NOTIFICATION; "call" -> AudioManager.STREAM_VOICE_CALL
                    else -> AudioManager.STREAM_MUSIC
                }
                am.setStreamVolume(st, (am.getStreamMaxVolume(st) * a.getInt("percent").coerceIn(0, 100) / 100.0).roundToInt(), 0)
                return "volume set"
            }
            "set_brightness" -> {
                if (!Settings.System.canWrite(ctx)) throw SecurityException("Modify system settings")
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (a.getInt("percent").coerceIn(1, 100) * 255 / 100))
                return "brightness set"
            }
            "open_app" -> {
                val q = a.getString("name")
                val pm = ctx.packageManager
                var pkg: String? = if (q.contains('.') && !q.contains(' ')) q else null
                if (pkg == null) {
                    val nq = (listOf(q) + a.strs("alt_queries")).map { Fuzzy.norm(it) }
                    pkg = launchables(ctx).map { (label, p) -> (nq.minOfOrNull { Fuzzy.score(Fuzzy.norm(label), it) } ?: 99) to p }
                        .filter { it.first < 99 }.minByOrNull { it.first }?.second
                }
                if (pkg != null && Prefs.blockedPackages.split(',').map { it.trim() }.contains(pkg))
                    return "this app is blocked by the user's safety settings"
                val li = pkg?.let { pm.getLaunchIntentForPackage(it) } ?: return "app not found"
                newTask(ctx, li)
                return "opened $pkg"
            }
            "list_apps" -> {
                val q = Fuzzy.norm(a.optString("query", ""))
                return launchables(ctx).filter { q.isEmpty() || Fuzzy.norm(it.first).contains(q) }.take(40)
                    .joinToString("\n") { "${it.first} = ${it.second}" }
            }
            "open_settings" -> {
                val pkg = a.optString("package", "")
                val action = when (a.getString("page")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS; "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "network" -> Settings.ACTION_WIRELESS_SETTINGS; "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS; "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS; "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                    "nfc" -> Settings.ACTION_NFC_SETTINGS; "apps" -> Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS; "date" -> Settings.ACTION_DATE_SETTINGS
                    "language" -> Settings.ACTION_LOCALE_SETTINGS
                    "app_details" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    "app_notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                val i = Intent(action)
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    if (pkg.isBlank()) return "package required"
                    i.data = Uri.fromParts("package", pkg, null)
                }
                if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    if (pkg.isBlank()) return "package required"
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                }
                newTask(ctx, i)
                return "opened settings"
            }
            "quick_toggle" -> {
                val toggleName = a.getString("name")
                val labels = toggles[toggleName] ?: return "unknown toggle"
                val on = a.getBoolean("on")
                if (!allowed(ctx, name, "وضعیت ${toggleName} به ${if (on) "روشن" else "خاموش"} تغییر کند؟", true)) return "user declined"
                return svc().quickToggle(labels, on)
            }
            "open_url" -> {
                val raw = a.getString("url").trim()
                val u = Uri.parse(raw)
                if ((u.scheme != "http" && u.scheme != "https") || u.host.isNullOrBlank() || !u.userInfo.isNullOrBlank())
                    return "only valid http/https links without embedded credentials are allowed"
                newTask(ctx, Intent(Intent.ACTION_VIEW, u))
                return "opened"
            }
            "media" -> {
                val code = when (a.getString("action")) {
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "stop" -> KeyEvent.KEYCODE_MEDIA_STOP; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                val am = ctx.getSystemService(AudioManager::class.java)
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                return "ok"
            }
            "battery" -> {
                val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                return "battery ${lvl * 100 / scale}%, charging=$charging"
            }
            "speak" -> { Agent.spokeInTool = true; Speaker.speak(ctx, a.getString("text")); return "spoken" }
            "ask_ai" -> return untrusted(Agent.ask(a.getString("question")))
            "save_memory" -> {
                val text = a.getString("text").trim()
                if (text.isBlank()) return "متن حافظه خالی است."
                if (!gate(ctx, "این مورد در حافظه خصوصی و رمزگذاری‌شده ذخیره شود؟ ${text.take(160)}")) return "کاربر ذخیره را تأیید نکرد."
                return MemoryStore.add(text)
            }
            "list_memory" -> {
                val items = MemoryStore.list()
                return if (items.isEmpty()) "حافظه‌ای ذخیره نشده است." else items.mapIndexed { i, v -> "${i + 1}. $v" }.joinToString("\n")
            }
            "forget_memory" -> {
                val text = a.getString("text").trim()
                if (!gate(ctx, "این مورد از حافظه حذف شود؟ ${text.take(160)}")) return "کاربر حذف را تأیید نکرد."
                return MemoryStore.remove(text)
            }
            "ui_read_screen" -> return untrusted(svc().readScreen())
            "ui_click" -> {
                val s = svc()
                val n = s.nodeAt(a.getInt("index")) ?: return "invalid index; call ui_read_screen again"
                val d = s.describeNode(n)
                if (!allowed(ctx, name, "روی «${d.take(60)}» کلیک شود؟", DANGER.containsMatchIn(d))) return "user declined"
                return if (s.clickNode(n)) "clicked: $d" else "click failed"
            }
            "ui_click_text" -> {
                val s = svc()
                val n = s.findByText(a.getString("text")) ?: return "no visible element with that text"
                val d = s.describeNode(n)
                if (!allowed(ctx, name, "روی «${d.take(60)}» کلیک شود؟", DANGER.containsMatchIn(d))) return "user declined"
                return if (s.clickNode(n)) "clicked: $d" else "click failed"
            }
            "ui_type" -> {
                val text = a.getString("text")
                if (text.isBlank()) return "text is empty"
                if (text.length > 2000) return "text is too long (maximum 2000 characters)"
                if (!allowed(ctx, name, "این متن در فیلد فعال تایپ شود؟ ${text.take(240)}", true)) return "user declined"
                return svc().typeText(text, a.optBoolean("replace", false))
            }
            "ui_scroll" -> return svc().scroll(a.getString("direction") != "up")
            "ui_global" -> return svc().global(a.getString("action"))
            "ui_wait" -> { delay(a.optLong("ms", 800).coerceIn(100, 3000)); return "waited" }
        }
        return "unknown tool: $name"
    }
}
