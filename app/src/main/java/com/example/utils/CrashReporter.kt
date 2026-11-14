package com.example.utils

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal in-app crash reporter: records the last uncaught crash (Kotlin/Java
 * level) to internal storage so the next launch can show it to the user.
 *
 * Native crashes cannot be caught here — a session marker written when the
 * camera screen opens (and cleared when it closes cleanly) tells us the app
 * died while the camera was open even when no report was written.
 */
object CrashReporter {

    const val CRASH_REPORT_FILE = "crash-report.txt"
    const val LAST_SCREEN_FILE = "last-screen.txt"
    const val SCREEN_CAMERA = "camera"
    const val SCREEN_HOME = "home"

    data class CrashInfo(
        val reportText: String?,
        val closedWhileCameraOpen: Boolean
    )

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never break the crash chain because reporting itself failed.
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val text = buildString {
            appendLine("AI Hair Color Studio crash report")
            appendLine("Timestamp: $timestamp")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(writer.toString())
        }
        File(context.filesDir, CRASH_REPORT_FILE).writeText(text)
    }

    fun readCrashInfo(context: Context): CrashInfo? {
        val reportFile = File(context.filesDir, CRASH_REPORT_FILE)
        val reportText = if (reportFile.exists()) {
            runCatching { reportFile.readText() }.getOrNull()
        } else {
            null
        }
        val closedWhileCameraOpen = readLastScreen(context) == SCREEN_CAMERA
        if (reportText == null && !closedWhileCameraOpen) return null
        return CrashInfo(reportText = reportText, closedWhileCameraOpen = closedWhileCameraOpen)
    }

    fun deleteCrashReport(context: Context) {
        runCatching { File(context.filesDir, CRASH_REPORT_FILE).delete() }
    }

    fun markScreen(context: Context, screen: String) {
        runCatching { File(context.filesDir, LAST_SCREEN_FILE).writeText(screen) }
    }

    fun clearLastScreen(context: Context) {
        runCatching { File(context.filesDir, LAST_SCREEN_FILE).delete() }
    }

    private fun readLastScreen(context: Context): String? {
        val file = File(context.filesDir, LAST_SCREEN_FILE)
        if (!file.exists()) return null
        return runCatching { file.readText().trim() }.getOrNull()
    }
}
