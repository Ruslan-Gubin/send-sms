package ru.pumpsms.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.pumpsms.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(val time: String, val text: String)

object AppLogger {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    fun log(text: String) {
        val entry = LogEntry(fmt.format(Date()), text)
        val next = (listOf(entry) + _logs.value).take(Config.LOG_MAX)
        _logs.value = next
    }

    fun clear() { _logs.value = emptyList() }
}
