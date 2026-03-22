package com.example.ritik_2.windowscontrol

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
//  PcControlCrashHandler
//  Catches all uncaught exceptions in the pccontrol package,
//  logs them, and restarts the activity gracefully
// ─────────────────────────────────────────────────────────────

class PcControlCrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Build crash report
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val report = buildString {
                appendLine("═══ PC Control Crash Report ═══")
                appendLine("Time:    ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("Thread:  ${thread.name}")
                appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE}")
                appendLine("Error:   ${throwable.message}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(stackTrace)
            }

            // Save crash log to prefs
            context.getSharedPreferences("pccontrol_crashes", Context.MODE_PRIVATE)
                .edit()
                .putString("last_crash", report)
                .putLong("last_crash_time", System.currentTimeMillis())
                .apply()

            android.util.Log.e("PcControl", "CRASH: $report")

            // Restart activity after brief delay
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_restart", true)
                    putExtra("crash_message", throwable.message ?: "Unknown error")
                }

            if (intent != null) {
                context.startActivity(intent)
            }

        } catch (e: Exception) {
            // Never crash the crash handler
            android.util.Log.e("PcControl", "CrashHandler failed: ${e.message}")
        }

        // Let default handler also run (Firebase Crashlytics etc)
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(context: Context) {
            val default = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                PcControlCrashHandler(context.applicationContext, default)
            )
        }

        fun getLastCrash(context: Context): String? {
            return context.getSharedPreferences("pccontrol_crashes", Context.MODE_PRIVATE)
                .getString("last_crash", null)
        }

        fun clearCrash(context: Context) {
            context.getSharedPreferences("pccontrol_crashes", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }
}
