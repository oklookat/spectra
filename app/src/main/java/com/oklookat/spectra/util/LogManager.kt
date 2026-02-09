package com.oklookat.spectra.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LogManager {
    private const val MAX_LOGS = 1000
    val logs = mutableStateListOf<String>()
    var isPaused by mutableStateOf(false)

    fun addLog(log: String?) {
        if (log == null || isPaused) return
        if (logs.size >= MAX_LOGS) {
            logs.removeAt(0)
        }
        logs.add(log)
    }

    fun clear() {
        logs.clear()
    }
}
