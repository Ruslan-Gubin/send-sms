package ru.pumpsms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import ru.pumpsms.net.NetworkMonitor
import ru.pumpsms.net.OutboxApi
import ru.pumpsms.net.MarkFailedBody
import ru.pumpsms.net.SmsOutbox
import ru.pumpsms.sms.SmsResult
import ru.pumpsms.sms.SmsSender
import ru.pumpsms.util.AppLogger
import ru.pumpsms.util.DeviceNumber

class PumpService : Service() {
    companion object {
        const val ACTION_START = "ru.pumpsms.START"
        const val ACTION_STOP = "ru.pumpsms.STOP"
        const val EXTRA_SENDER_PHONE = "sender_phone"
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "pump_channel"

        fun start(context: Context, senderPhone: String) {
            val i = Intent(context, PumpService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SENDER_PHONE, senderPhone)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PumpService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pumpJob: Job? = null
    private var senderPhone: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLogger.log("Остановлен")
                pumpJob?.cancel()
                PumpState.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                // флаг — был включён
                getSharedPreferences("pump", MODE_PRIVATE).edit().putBoolean("isRunning", false).apply()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                senderPhone = DeviceNumber.normalize(intent.getStringExtra(EXTRA_SENDER_PHONE) ?: "")
                if (!DeviceNumber.isValidDigits(senderPhone)) {
                    AppLogger.log("Неверный номер устройства: $senderPhone")
                    stopSelf()
                    return START_NOT_STICKY
                }
                getSharedPreferences("pump", MODE_PRIVATE).edit()
                    .putString("senderPhone", senderPhone)
                    .putBoolean("isRunning", true)
                    .apply()
                startForeground(NOTIF_ID, buildNotification("Пульс активен · $senderPhone"))
                pumpJob?.cancel()
                PumpState.reset()
                pumpJob = scope.launch { pumpLoop(senderPhone) }
                AppLogger.log("Запущен · $senderPhone")
            }
        }
        return START_STICKY
    }

    private suspend fun pumpLoop(phone: String) {
        val api = OutboxApi.create()
        while (currentCoroutineContext().isActive) {
            try {
                if (!NetworkMonitor.hasInternet(this)) {
                    AppLogger.log("Нет интернета — пропуск")
                    PumpState.setServerConnected(false)
                    PumpState.setCurrentTask(null)
                    delay(Config.POLL_INTERVAL_MS)
                    continue
                }
                AppLogger.log("Опрос /sms/outbox…")
                val resp = try {
                    api.getOutbox(phone)
                } catch (e: Exception) {
                    AppLogger.log("Ошибка опроса: ${e.message}")
                    PumpState.setServerConnected(false)
                    PumpState.setCurrentTask(null)
                    delay(Config.POLL_INTERVAL_MS)
                    continue
                }
                PumpState.setServerConnected(true)
                if (resp.status != "success") {
                    AppLogger.log("Бекенд: ${resp.message}")
                    delay(Config.POLL_INTERVAL_MS)
                    continue
                }
                val task = resp.data
                if (task == null) {
                    // нет задач — тихо, без спама лога каждую итерацию
                    PumpState.setCurrentTask(null)
                    delay(Config.POLL_INTERVAL_MS)
                    continue
                }
                AppLogger.log("Взята задача #${task.id} → ${task.phone}")
                updateNotification("Отправка #${task.id} → ${task.phone}")
                PumpState.setCurrentTask(CurrentTask(task.id, task.phone, task.messageText, "Отправка…"))

                val result: SmsResult = sendSms(this, task)
                when (result) {
                    is SmsResult.Ok -> {
                        AppLogger.log(if (Config.SIMULATE_SMS) "СИМУЛЯЦИЯ: #${task.id} отправлено — delivered"
                                      else "SMS отправлено #${task.id} — delivered")
                        PumpState.setCurrentTask(
                            PumpState.currentTask.value?.copy(
                                stage = if (Config.SIMULATE_SMS) "Доставлено (симуляция)" else "Доставлено"
                            )
                        )
                        try { api.markDelivered(task.id) } catch (e: Exception) {
                            AppLogger.log("markDelivered ошибка: ${e.message}")
                        }
                    }
                    is SmsResult.Failed -> {
                        AppLogger.log("SMS ошибка #${task.id}: ${result.errorCode} — ${result.errorReason}")
                        PumpState.setCurrentTask(
                            PumpState.currentTask.value?.copy(stage = "Ошибка: ${result.errorReason}")
                        )
                        try {
                            api.markFailed(task.id, MarkFailedBody(result.errorCode, result.errorReason))
                        } catch (e: Exception) {
                            AppLogger.log("markFailed ошибка: ${e.message}")
                        }
                    }
                }
                updateNotification("Пульс активен · $phone")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppLogger.log("Цикл ошибка: ${e.message}")
            }
            delay(Config.POLL_INTERVAL_MS)
        }
    }

    /** Отправка SMS: при SIMULATE_SMS — имитация с задержкой вместо реального SmsManager. */
    private suspend fun sendSms(context: Context, task: SmsOutbox): SmsResult {
        if (Config.SIMULATE_SMS) {
            AppLogger.log("СИМУЛЯЦИЯ #${task.id}: отправка SMS на ${task.phone}…")
            delay(1_500) // имитация времени работы SmsManager
            return SmsResult.Ok
        }
        return SmsSender.send(context, task.phone, task.messageText)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_desc)
            }
            (getSystemService(NotificationManager::class.java) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pumpJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
