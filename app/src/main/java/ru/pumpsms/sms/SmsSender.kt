package ru.pumpsms.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.pumpsms.Config
import kotlin.coroutines.resume

sealed class SmsResult {
    data object Ok : SmsResult()
    data class Failed(val errorCode: String, val errorReason: String) : SmsResult()
}

object SmsErrorMapper {
    fun mapFail(resultCode: Int, reason: String?): SmsResult.Failed {
        val (code, defaultReason) = when (resultCode) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> "network" to "Нет сети"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "network" to "Радиомодуль выключен"
            SmsManager.RESULT_ERROR_NULL_PDU -> "gateway" to "NULL PDU"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "failed" to "Ошибка отправки"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "failed" to "Превышен лимит отправки SMS"
            SmsManager.RESULT_ERROR_FQDN_NOT_RESOLVED -> "gateway" to "SMS-шлюз не найден"
            SmsManager.RESULT_ERROR_UNSUPPORTED_URI -> "failed" to "Неподдерживаемый формат"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "failed" to "Короткий номер запрещён"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "failed" to "Короткий номер запрещён навсегда"
            else -> "failed" to "Код $resultCode"
        }
        return SmsResult.Failed(code, reason ?: defaultReason)
    }
}

object SmsSender {
    private const val ACTION_SENT = "ru.pumpsms.SMS_SENT"

    suspend fun send(context: Context, phone: String, text: String): SmsResult {
        val sentIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_SENT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    if (cont.isActive) {
                        val rc = resultCode
                        if (rc == Activity.RESULT_OK) cont.resume(SmsResult.Ok)
                        else cont.resume(SmsErrorMapper.mapFail(rc, intent.getStringExtra("error")))
                    }
                }
            }
            // Android 14+ — флаг RECEIVER_EXPORTED
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(ACTION_SENT), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, IntentFilter(ACTION_SENT))
            }

            cont.invokeOnCancellation {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }

            // Таймаут — считаем gateway-ошибкой
            android.os.Handler(context.mainLooper).postDelayed({
                if (cont.isActive) {
                    try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                    cont.resume(SmsResult.Failed("timeout", "Таймаут отправки SMS"))
                }
            }, Config.SMS_SENT_TIMEOUT_MS)

            try {
                @Suppress("DEPRECATION")
                val sm = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
                else SmsManager.getDefault()
                sm.sendTextMessage(phone, null, text, sentIntent, null)
            } catch (e: Exception) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                if (cont.isActive) cont.resume(SmsResult.Failed("failed", e.message ?: "Исключение при отправке"))
            }
        }
    }
}
