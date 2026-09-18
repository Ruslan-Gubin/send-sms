package ru.pumpsms.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

object DeviceNumber {
    /** Нормализация бекенда: только цифры, 10–15. */
    fun normalize(raw: String): String = raw.replace(Regex("\\D"), "")

    fun isValidDigits(digits: String): Boolean = digits.length in 10..15 && digits.all { it.isDigit() }

    /** Best-effort. Возвращает цифры или null (нужно READ_PHONE_STATE, на многих прошивках пусто). */
    fun fromTelephony(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val raw = tm.line1Number // может быть +7..., 8..., 900..., пусто
            if (raw.isNullOrBlank()) null else {
                val digits = normalize(raw)
                if (isValidDigits(digits)) digits else null
            }
        } catch (_: Exception) { null }
    }
}
