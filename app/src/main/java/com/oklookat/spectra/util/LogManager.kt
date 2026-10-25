package com.oklookat.spectra.util

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LogManager {
    private const val MAX_LOGS = 1000
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    val logs = mutableStateListOf<String>()
    var isPaused by mutableStateOf(false)

    fun initWelcomeLogs(appVersion: String) {
        if (logs.isNotEmpty()) return

        val xrayFullVersion = try {
            libv2ray.Libv2ray.checkVersionX()
        } catch (_: Throwable) {
            ""
        }

        val libVersion = if (xrayFullVersion.contains("Lib v")) {
            "v" + xrayFullVersion.substringAfter("Lib v").substringBefore(",")
        } else "N/A"

        val coreVersion = if (xrayFullVersion.contains("core v")) {
            "v" + xrayFullVersion.substringAfter("core v").substringBefore(")")
        } else "N/A"

        addLog("Welcome to Spectra! 🚀")
        addLog("Versions: Spectra v$appVersion | LibXray $libVersion | Xray-core $coreVersion")
        addLog("Contributors: oklookat & Gemini AI Agent 🤖")
    }

    fun addLog(log: String?) {
        if (log.isNullOrBlank() || isPaused) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            addLogInternal(log)
        } else {
            mainHandler.post { addLogInternal(log) }
        }
    }

    private fun addLogInternal(log: String) {
        if (isPaused) return
        if (logs.size >= MAX_LOGS) {
            logs.removeAt(0)
        }
        logs.add(log)
    }

    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            logs.clear()
        } else {
            mainHandler.post { logs.clear() }
        }
    }
}
