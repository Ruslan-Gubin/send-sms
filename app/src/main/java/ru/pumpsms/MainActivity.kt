package ru.pumpsms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ru.pumpsms.util.AppLogger
import ru.pumpsms.util.DeviceNumber

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }
    private val readPhoneLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) tryFillFromSim()
    }

    private fun tryFillFromSim() {
        val digits = DeviceNumber.fromTelephony(this)
        if (digits != null) {
            getSharedPreferences("pump", MODE_PRIVATE).edit().putString("senderPhone", digits).apply()
            // UI подхватит через remember; лог
            AppLogger.log("Номер из SIM: $digits")
        } else {
            AppLogger.log("Не удалось прочитать номер SIM")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PumpScreen() }
    }

    @Composable
    fun PumpScreen() {
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("pump", MODE_PRIVATE) }
        var phone by remember { mutableStateOf(prefs.getString("senderPhone", "") ?: "") }
        var isRunning by remember { mutableStateOf(prefs.getBoolean("isRunning", false)) }
        var baseUrl by remember { mutableStateOf(prefs.getString("baseUrl", Config.DEFAULT_BASE_URL) ?: Config.DEFAULT_BASE_URL) }
        var pollIntervalMs by remember { mutableStateOf(prefs.getLong("pollIntervalMs", Config.DEFAULT_POLL_INTERVAL_MS)) }
        val logs by AppLogger.logs.collectAsState()
        val serverConnected by PumpState.serverConnected.collectAsState()
        val currentTask by PumpState.currentTask.collectAsState()

        // синхронизируем с префами при изменениях из BootReceiver/сервиса
        LaunchedEffect(Unit) {
            // обновляем флаг при возврате на экран
        }

        MaterialTheme(colorScheme = lightColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PumpSms", style = MaterialTheme.typography.headlineSmall)
                    Text("Бекенд: $baseUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Устройство шлёт SMS каждые ${pollIntervalMs / 1_000}с. Работает с потухшим экраном.", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            prefs.edit().putString("senderPhone", it).apply()
                        },
                        label = { Text(ctx.getString(R.string.hint_phone)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                                val d = DeviceNumber.fromTelephony(ctx)
                                if (d != null) {
                                    phone = d
                                    prefs.edit().putString("senderPhone", d).apply()
                                    AppLogger.log("Номер из SIM: $d")
                                } else AppLogger.log("Номер SIM пуст")
                            } else {
                                readPhoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            }
                        }) { Text(ctx.getString(R.string.btn_fill_my_number), fontSize = 12.sp) }

                        TextButton(onClick = { AppLogger.clear() }) { Text("Очистить лог") }
                    }

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            prefs.edit().putString("baseUrl", it).apply()
                        },
                        label = { Text(ctx.getString(R.string.hint_base_url)) },
                        placeholder = { Text(Config.DEFAULT_BASE_URL) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(ctx.getString(R.string.label_poll_interval),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Config.POLL_INTERVAL_OPTIONS_MS.forEach { opt ->
                            FilterChip(
                                selected = opt == pollIntervalMs,
                                onClick = {
                                    pollIntervalMs = opt
                                    prefs.edit().putLong("pollIntervalMs", opt).apply()
                                },
                                label = { Text("${opt / 1_000}с") }
                            )
                        }
                    }

                    val digits = DeviceNumber.normalize(phone)
                    val valid = DeviceNumber.isValidDigits(digits)
                    if (phone.isNotEmpty() && !valid) {
                        Text("Нужно 10–15 цифр (сейчас ${digits.length})", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val perms = mutableListOf<String>()
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) perms += Manifest.permission.SEND_SMS
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms += Manifest.permission.POST_NOTIFICATIONS
                                if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())

                                val normalized = DeviceNumber.normalize(phone)
                                val normalizedUrl = Config.normalizeBaseUrl(baseUrl)
                                prefs.edit()
                                    .putString("senderPhone", normalized)
                                    .putString("baseUrl", normalizedUrl)
                                    .apply()
                                phone = normalized
                                baseUrl = normalizedUrl
                                PumpService.start(ctx, normalized)
                                isRunning = true
                            },
                            enabled = valid && !isRunning,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(ctx.getString(R.string.btn_start)) }

                        Button(
                            onClick = {
                                PumpService.stop(ctx)
                                isRunning = false
                            },
                            enabled = isRunning,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(ctx.getString(R.string.btn_stop)) }
                    }

                    Text(if (isRunning) ctx.getString(R.string.service_running) else ctx.getString(R.string.service_stopped),
                        color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)

                    // Индикатор соединения с сервером
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (connColor, connText) = when (serverConnected) {
                            true -> Color(0xFF2E7D32) to "Сервер: подключён"
                            false -> Color(0xFFC62828) to "Сервер: недоступен"
                            null -> Color(0xFF9E9E9E) to "Сервер: —"
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(connColor))
                        Text(connText, style = MaterialTheme.typography.bodySmall)
                    }

                    if (Config.SIMULATE_SMS) {
                        Text("Режим симуляции: SMS не отправляются",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }

                    // Текущая задача
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Текущая задача", style = MaterialTheme.typography.titleSmall)
                            val t = currentTask
                            if (t == null) {
                                Text("Нет активной задачи",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("#${t.id} → ${t.phone}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(t.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Text("Лог (последние ${Config.LOG_MAX})", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                                items(logs) { e ->
                                    Text("${e.time}  ${e.text}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                if (logs.isEmpty()) {
                                    item { Text("Пока пусто — нажми Старт", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }

                    Text("Подсказка: если сервис отваливается — включи Автозапуск в настройках батареи (Xiaomi/Samsung).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
