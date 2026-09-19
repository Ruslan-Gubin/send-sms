package ru.pumpsms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Текущая задача в работе: что шлём и на какой стадии. */
data class CurrentTask(
    val id: Int,
    val phone: String,
    val message: String,
    val stage: String,
)

/** Состояние пульса, общее для сервиса и экрана. */
object PumpState {
    /** null — сервис не запущен / статус неизвестен. */
    private val _serverConnected = MutableStateFlow<Boolean?>(null)
    val serverConnected: StateFlow<Boolean?> = _serverConnected

    private val _currentTask = MutableStateFlow<CurrentTask?>(null)
    val currentTask: StateFlow<CurrentTask?> = _currentTask

    fun setServerConnected(value: Boolean?) {
        _serverConnected.value = value
    }

    fun setCurrentTask(task: CurrentTask?) {
        _currentTask.value = task
    }

    fun reset() {
        _serverConnected.value = null
        _currentTask.value = null
    }
}