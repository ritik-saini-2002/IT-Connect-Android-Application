package com.saini.ritik.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * App-wide uncaught exception handler.
 *
 * Catches every crash that isn't handled anywhere else, writes a human-readable
 * report to SharedPreferences (readable by a debug/support screen), then
 * delegates to whatever handler was installed before (system, Firebase
 * Crashlytics, etc.) so those pipelines keep working.
 *
 * Install once, as the very first call in [MyApplication.onCreate]:
 * ```
 * GlobalCrashHandler.install(this)
 * ```
 */
class GlobalCrashHandler(
    private val context        : Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            val report = buildString {
                appendLine("═══════════════════════════════════════")
                appendLine("       IT Connect — Crash Report       ")
                appendLine("═══════════════════════════════════════")
                appendLine("Time    : $timestamp")
                appendLine("Thread  : ${thread.name} [id=${thread.id}]")
                appendLine("Device  : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("Android : ${Build.VERSION.RELEASE}  SDK ${Build.VERSION.SDK_INT}")
                appendLine("Error   : ${throwable.javaClass.name}: ${throwable.message}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(sw.toString())
            }

            // Persist for later inspection (debug / support screens)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, report)
                .putLong(KEY_CRASH_TIME, System.currentTimeMillis())
                .apply()

            Log.e(TAG, "UNCAUGHT EXCEPTION on thread '${thread.name}':\n$report")
        } catch (safetyNet: Exception) {
            // The crash handler itself must never throw
            Log.e(TAG, "CrashHandler internal error: ${safetyNet.message}")
        } finally {
            // Always forward to the previous handler (system restart, Crashlytics, etc.)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG            = "GlobalCrashHandler"
        private const val PREFS          = "itconnect_crashes"
        private const val KEY_LAST_CRASH = "last_crash"
        private const val KEY_CRASH_TIME = "last_crash_time"

        /**
         * Install the global crash handler. Safe to call multiple times — only
         * installs once. Must be called before any coroutines are started.
         */
        fun install(context: Context) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is GlobalCrashHandler) return  // Already installed
            Thread.setDefaultUncaughtExceptionHandler(
                GlobalCrashHandler(context.applicationContext, current)
            )
            Log.d(TAG, "Global crash handler installed ✅")
        }

        /** Returns the last crash report string, or null if no crash has occurred. */
        fun getLastCrash(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_CRASH, null)

        /** Returns the epoch-ms timestamp of the last crash, or 0 if none. */
        fun getLastCrashTime(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_CRASH_TIME, 0L)

        /** Clears the stored crash report. */
        fun clearLastCrash(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
    }
}
