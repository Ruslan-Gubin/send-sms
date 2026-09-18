package ru.pumpsms

/** Адрес бекенда. Телефон стучится на локальный ПК — телефон и ПК в одной Wi-Fi.
 *  Потом заменишь на прод-адрес и пересоберёшь.
 *  Важно: бекенд должен слушать 0.0.0.0, а не только 127.0.0.1.
 */
object Config {
    const val BASE_URL = "http://192.168.1.100:4010/"
    const val POLL_INTERVAL_MS = 10_000L
    const val SMS_SENT_TIMEOUT_MS = 60_000L
    const val LOG_MAX = 30
}
