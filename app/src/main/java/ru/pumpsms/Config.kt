package ru.pumpsms

/** Настройки по умолчанию.
 *  Адрес бекенда и интервал опроса пользователь меняет прямо в приложении —
 *  значения сохраняются в SharedPreferences и применяются при следующем Старте.
 *  Важно: бекенд должен слушать 0.0.0.0, а не только 127.0.0.1; телефон и ПК в одной сети.
 */
object Config {
    /** Дефолтный адрес бекенда — подставляется в поле «Адрес бекенда» при первом запуске. */
    const val DEFAULT_BASE_URL = "http://192.168.88.12:4010/"

    const val DEFAULT_POLL_INTERVAL_MS = 10_000L
    const val POLL_INTERVAL_MIN_MS = 1_000L
    const val POLL_INTERVAL_MAX_MS = 300_000L

    /** Доступные варианты интервала опроса в UI (мс). */
    val POLL_INTERVAL_OPTIONS_MS = listOf(3_000L, 5_000L, 10_000L, 20_000L)

    const val SMS_SENT_TIMEOUT_MS = 60_000L
    const val LOG_MAX = 30

    /** Приводит введённый адрес к виду, который требует Retrofit: непустой, с '/' в конце. */
    fun normalizeBaseUrl(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_BASE_URL
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}