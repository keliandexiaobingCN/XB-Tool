package com.xb.tool.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBus {
    private const val MAX_LINES = 400
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var seq = 0L
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(
            id = ++seq,
            time = timeFormat.format(Date()),
            level = level,
            message = message
        )
        val next = (_entries.value + entry).takeLast(MAX_LINES)
        _entries.value = next
    }

    fun info(message: String) = log(LogLevel.INFO, message)
    fun ok(message: String) = log(LogLevel.OK, message)
    fun warn(message: String) = log(LogLevel.WARN, message)
    fun error(message: String) = log(LogLevel.ERROR, message)

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}
