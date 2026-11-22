package com.dastyar.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { DastyarApp() }
    }
}

private val ACCENTS = listOf(
    Color(0xFF6750A4), Color(0xFF1E88E5), Color(0xFF00897B),
    Color(0xFFE53935), Color(0xFFF9A825), Color(0xFF8E24AA)
)

private fun loadBitmap(ctx: Context, uri: String) = try {
    if (uri.isBlank()) null else ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
    }
} catch (e: Exception) { null }

@Composable
fun DastyarApp() {
    val ctx = LocalContext.current
    var themeMode by remember { mutableStateOf(Prefs.themeMode) }
    var accent by remember { mutableStateOf(Prefs.accent) }
    var bgUri by remember { mutableStateOf(Prefs.bgUri) }
    var dim by remember { mutableStateOf(Prefs.bgDim) }
    val dark = when (themeMode) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true }
    val ac = ACCENTS[accent % ACCENTS.size]
    val black = themeMode == 3
    val scheme = if (dark) darkColorScheme(
        primary = ac,
        background = if (black) Color.Black else Color(0xFF121212),
        surface = if (black) Color.Black else Color(0xFF1E1E1E)
    ) else lightColorScheme(primary = ac)
    val bmp = remember(bgUri) { loadBitmap(ctx, bgUri) }

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (bmp != null) {
                    Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim / 100f)))
                }
                Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
                    var tab by remember { mutableStateOf(0) }
                    TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                        listOf("خانه", "تنظیمات", "گزارش").forEachIndexed { i, t ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                        }
                    }
                    when (tab) {
                        0 -> HomeScreen()
                        1 -> SettingsScreen(
                            onTheme = { themeMode = it; Prefs.themeMode = it },
                            onAccent = { accent = it; Prefs.accent = it },
                            onBg = { bgUri = it; Prefs.bgUri = it },
                            onDim = { dim = it; Prefs.bgDim = it },
                            dimValue = dim, themeValue = themeMode, accentValue = accent
                        )
                        else -> LogScreen()
                    }
                }
            }
        }
    }
}

private fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

private fun a11yOn(ctx: Context): Boolean =
    (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
        .contains(ctx.packageName + "/")

private fun openSettings(ctx: Context, action: String, withPkg: Boolean = true) {
    val i = Intent(action)
    if (withPkg) i.data = Uri.parse("package:${ctx.packageName}")
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (ok) Text("✓ فعال") else Button(onClick = onClick) { Text("فعال‌سازی") }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(WakeService.running) }
    var dl by remember { mutableStateOf(-1) }
    var modelReady by remember { mutableStateOf(ModelManager.ready(ctx)) }
    var msg by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; running = WakeService.running }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { ModelManager.importZip(ctx, it) }
                modelReady = true; msg = "مدل وارد شد"
            } catch (e: Exception) { msg = "خطا: ${e.message}" }
        }
    }

    // Request dangerous permissions independently: the user can enable only the feature they need.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val refresh = tick // re-evaluate permission states after returning from Settings

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("وضعیت: " + if (running) "در حال گوش دادن به «${Prefs.wakeWord}»" else "خاموش")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (running) { WakeService.stop(ctx); running = false }
                else if (!modelReady) msg = "اول مدل صوتی را دانلود کنید"
                else if (!has(ctx, Manifest.permission.RECORD_AUDIO)) msg = "اول مجوز میکروفون را بدهید"
                else { WakeService.start(ctx); running = true }
            }) { Text(if (running) "توقف" else "شروع") }
        }

        Text("مدل صوتی فارسی (برای تشخیص کلمه بیدارباش)")
        Text(
            "شنود آفلاین فقط هنگام روشن بودن سرویس فعال است. اعلان دائمی نشان می‌دهد میکروفون در حال استفاده است؛ برای کاهش مصرف باتری، هنگام نیاز نداشتن سرویس را متوقف کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (modelReady) Text("✓ آماده") else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        try { dl = 0; ModelManager.download(ctx) { dl = it }; modelReady = true; dl = -1; msg = "دانلود شد" }
                        catch (e: Exception) { dl = -1; msg = "دانلود ناموفق: ${e.message}" }
                    }
                }) { Text(if (dl >= 0) "$dl٪" else "دانلود (~۵۰MB)") }
                TextButton(onClick = { zipPicker.launch(arrayOf("*/*")) }) { Text("انتخاب فایل zip") }
            }
        }
        if (msg.isNotBlank()) Text(msg)

        Text("مجوزها (هر قابلیت مستقل)")
        PermRow("میکروفون ـ تشخیص کلمه بیدارباش", has(ctx, Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        PermRow("مخاطبین ـ جست‌وجوی نام", has(ctx, Manifest.permission.READ_CONTACTS)) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
        PermRow("تماس تلفنی", has(ctx, Manifest.permission.CALL_PHONE)) {
            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
        PermRow("ارسال پیامک", has(ctx, Manifest.permission.SEND_SMS)) {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
        PermRow("خواندن پیامک", has(ctx, Manifest.permission.READ_SMS)) {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            PermRow("اعلان‌ها", has(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermRow("سرویس دسترسی‌پذیری (کنترل صفحه)", a11yOn(ctx)) { openSettings(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS, false) }
        PermRow("نمایش روی سایر برنامه‌ها", Settings.canDrawOverlays(ctx)) { openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
        PermRow("تغییر تنظیمات سیستم (روشنایی)", Settings.System.canWrite(ctx)) { openSettings(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS) }
        PermRow("بدون محدودیت باتری",
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) {
            openSettings(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }
        PermRow("زنگ دقیق (یادآوری)", Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            openSettings(ctx, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        }
        PermRow("دسترسی مزاحم نشوید", ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            openSettings(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, false)
        }
        TextButton(onClick = { openSettings(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }) {
            Text("اطلاعات برنامه (برای «Allow restricted settings»)")
        }

        Text("تست دستور متنی")
        OutlinedTextField(cmd, { cmd = it }, Modifier.fillMaxWidth(), label = { Text("مثلا: با مهدی تماس بگیر") })
        Button(enabled = !busy && cmd.isNotBlank(), onClick = {
            busy = true
            scope.launch { result = Agent.handleText(ctx, cmd); busy = false }
        }) { Text(if (busy) "در حال اجرا…" else "اجرا") }
        if (result.isNotBlank()) Text(result)
    }
}

@Composable
private fun Field(label: String, init: String, secret: Boolean = false, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(init) }
    OutlinedTextField(
        value = v, onValueChange = { v = it; onSave(it) }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, l) -> FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(l) }) }
    }
}

@Composable
fun SettingsScreen(
    onTheme: (Int) -> Unit, onAccent: (Int) -> Unit, onBg: (String) -> Unit, onDim: (Int) -> Unit,
    dimValue: Int, themeValue: Int, accentValue: Int
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(Prefs.listenLang) }
    var provider by remember { mutableStateOf(Prefs.ttsProvider) }
    var speak by remember { mutableStateOf(Prefs.speakReplies) }
    var confirm by remember { mutableStateOf(Prefs.confirmMode) }
    var faV by remember { mutableStateOf(Prefs.azureVoiceFa) }
    var enV by remember { mutableStateOf(Prefs.azureVoiceEn) }
    var voices by remember { mutableStateOf<List<String>>(emptyList()) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            onBg(uri.toString())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("عمومی")
        Field("نام دستیار", Prefs.assistantName) { Prefs.assistantName = it }
        Field("کلمه بیدارباش", Prefs.wakeWord) { Prefs.wakeWord = it }
        Field("تلفظ‌های جایگزین (با کاما جدا کنید)", Prefs.wakeVariants) { Prefs.wakeVariants = it }
        Text("برای کاهش فعال‌سازی اشتباه، عبارت بیدارباش و تلفظ‌های جایگزین را کوتاه و متمایز انتخاب کنید.", style = MaterialTheme.typography.bodySmall)
        Text("زبان تشخیص دستور")
        Choice(listOf("fa-IR" to "فارسی", "en-US" to "English"), lang) { lang = it; Prefs.listenLang = it }

        Text("هوش مصنوعی (ارائه‌دهنده فعلی: Anthropic؛ زیرساخت قابل توسعه)")
        Field("کلید API", Prefs.apiKey, true) { Prefs.apiKey = it.trim() }
        Text("نشانی امن سرویس: api.anthropic.com (قابل تغییر نیست)", style = MaterialTheme.typography.bodySmall)
        Field("شناسه مدل", Prefs.model) { Prefs.model = it.trim() }
        Text("حافظه خصوصی")
        Text("${MemoryStore.count()} مورد ذخیره‌شده؛ فقط مواردی که صریحاً درخواست و تأیید کنی نگهداری می‌شوند.", style = MaterialTheme.typography.bodySmall)
        Text("اطلاعات در فضای رمزگذاری‌شده محلی دستگاه ذخیره می‌شود و به‌صورت خودکار از گفتگوها استخراج نمی‌شود.", style = MaterialTheme.typography.bodySmall)

        Text("صدا")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("خواندن پاسخ‌ها با صدا", Modifier.weight(1f))
            Switch(speak, { speak = it; Prefs.speakReplies = it })
        }
        Choice(listOf("system" to "صدای سیستم", "azure" to "Azure (زن/مرد)"), provider) { provider = it; Prefs.ttsProvider = it }
        if (provider == "azure") {
            Field("کلید Azure Speech", Prefs.azureKey, true) { Prefs.azureKey = it.trim() }
            Field("منطقه Azure (مثلا westeurope)", Prefs.azureRegion) { Prefs.azureRegion = it.trim() }
            Text("صدای فارسی")
            Choice(Speaker.azureFaVoices, faV) { faV = it; Prefs.azureVoiceFa = it }
            Text("English voice")
            Choice(Speaker.azureEnVoices, enV) { enV = it; Prefs.azureVoiceEn = it }
        } else {
            TextButton(onClick = { scope.launch { voices = Speaker.systemVoices(ctx) } }) { Text("نمایش صداهای نصب‌شده") }
            voices.forEach { v -> TextButton(onClick = { Prefs.systemVoice = v }) { Text(v) } }
        }

        Text("امنیت")
        Choice(
            listOf("0" to "بدون تأیید", "1" to "تأیید کارهای حساس (پیشنهادی)", "2" to "تأیید همه کارها"),
            confirm.toString()
        ) { confirm = it.toInt(); Prefs.confirmMode = confirm }
        if (confirm == 0) Text("هشدار: متن پیامک یا صفحه وب ممکن است به دستیار دستور مخفی بدهد. این حالت خطرناک است.")
        Field("برنامه‌های ممنوع برای دستیار (package، با کاما)", Prefs.blockedPackages) { Prefs.blockedPackages = it }

        Text("ظاهر")
        Choice(listOf("0" to "سیستم", "1" to "روشن", "2" to "تیره", "3" to "آمولد"), themeValue.toString()) { onTheme(it.toInt()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENTS.forEachIndexed { i, c ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { onAccent(i) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { bgPicker.launch(arrayOf("image/*")) }) { Text("انتخاب تصویر پس‌زمینه") }
            TextButton(onClick = { onBg("") }) { Text("حذف") }
        }
        Text("تیرگی پس‌زمینه")
        Slider(value = dimValue / 100f, onValueChange = { onDim((it * 100).toInt()) })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    var lines by remember { mutableStateOf(AuditLog.read(ctx)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { lines = AuditLog.read(ctx) }) { Text("بازخوانی") }
            TextButton(onClick = { AuditLog.clear(ctx); lines = emptyList() }) { Text("پاک کردن") }
        }
        lines.forEach { Text(it) }
    }
}
