package ru.pumpsms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
        val logs by AppLogger.logs.collectAsState()

        // синхронизируем с префами при изменениях из BootReceiver/сервиса
        LaunchedEffect(Unit) {
            // обновляем флаг при возврате на экран
        }

        MaterialTheme(colorScheme = lightColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PumpSms", style = MaterialTheme.typography.headlineSmall)
                    Text("Бекенд: ${Config.BASE_URL}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Устройство шлёт SMS каждые 10с. Работает с потухшим экраном.", style = MaterialTheme.typography.bodySmall)

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
                                prefs.edit().putString("senderPhone", normalized).apply()
                                phone = normalized
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
