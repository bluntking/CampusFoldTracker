package com.campusfold.tracker.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,
    val message: String,
    val details: String? = null
) {
    fun formattedTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }
}

object AiDiagnostics {

    private const val TAG = "AiDiagnostics"
    private const val PREFS_NAME = "campusfold_diagnostics_prefs"
    private const val KEY_EVENTS_JSON = "diagnostic_events_json"
    private const val KEY_LAST_CRASH_TRACE = "last_uncaught_exception"
    private const val KEY_LAST_CRASH_TIME = "last_uncaught_exception_time"
    private const val MAX_EVENTS = 50

    private val lock = Any()
    private val inMemoryEvents = mutableListOf<DiagnosticEvent>()

    private val _eventsFlow = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val eventsFlow: StateFlow<List<DiagnosticEvent>> = _eventsFlow.asStateFlow()

    private val _lastCrashTrace = MutableStateFlow<String?>(null)
    val lastCrashTrace: StateFlow<String?> = _lastCrashTrace.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Load existing events from SharedPreferences
        synchronized(lock) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_EVENTS_JSON, null)
            if (!json.isNullOrBlank()) {
                try {
                    val array = JSONArray(json)
                    inMemoryEvents.clear()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        inMemoryEvents.add(
                            DiagnosticEvent(
                                timestamp = obj.getLong("timestamp"),
                                type = obj.getString("type"),
                                message = obj.getString("message"),
                                details = if (obj.isNull("details")) null else obj.getString("details")
                            )
                        )
                    }
                    _eventsFlow.value = inMemoryEvents.toList()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load diagnostic events from preferences", e)
                }
            }

            // Check for previous uncaught crash
            val savedTrace = prefs.getString(KEY_LAST_CRASH_TRACE, null)
            val crashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0L)
            if (!savedTrace.isNullOrBlank()) {
                val formattedTime = if (crashTime > 0) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(crashTime))
                } else "Previous launch"
                _lastCrashTrace.value = "[$formattedTime]\n$savedTrace"
            }
        }

        // Install global uncaught exception handler
        installUncaughtExceptionHandler()
    }

    private fun installUncaughtExceptionHandler() {
        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                appContext?.let { ctx ->
                    val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(KEY_LAST_CRASH_TRACE, stackTrace)
                        .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                        .commit() // Commit synchronously before process terminates

                    recordEvent(
                        type = "UNCAUGHT_EXCEPTION",
                        message = "Fatal uncaught exception on thread ${thread.name}: ${throwable.localizedMessage}",
                        details = stackTrace
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in uncaught exception handler", e)
            } finally {
                existingHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun recordEvent(type: String, message: String, details: String? = null) {
        val event = DiagnosticEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            details = details
        )
        synchronized(lock) {
            inMemoryEvents.add(event)
            while (inMemoryEvents.size > MAX_EVENTS) {
                inMemoryEvents.removeAt(0)
            }
            _eventsFlow.value = inMemoryEvents.toList()
            persistEventsLocked()
        }
    }

    private fun persistEventsLocked() {
        val ctx = appContext ?: return
        try {
            val array = JSONArray()
            for (ev in inMemoryEvents) {
                val obj = JSONObject().apply {
                    put("timestamp", ev.timestamp)
                    put("type", ev.type)
                    put("message", ev.message)
                    put("details", ev.details ?: JSONObject.NULL)
                }
                array.put(obj)
            }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_EVENTS_JSON, array.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist diagnostic events", e)
        }
    }

    fun clearCrashTrace() {
        _lastCrashTrace.value = null
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_LAST_CRASH_TRACE)
            ?.remove(KEY_LAST_CRASH_TIME)
            ?.apply()
    }

    fun clearEvents() {
        synchronized(lock) {
            inMemoryEvents.clear()
            _eventsFlow.value = emptyList()
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_EVENTS_JSON)
                ?.apply()
        }
    }

    fun exportLogSummary(context: Context): String {
        val mem = ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.getMemoryInfo(mem)

        val totalRamMb = mem.totalMem / (1024 * 1024)
        val availRamMb = mem.availMem / (1024 * 1024)

        return buildString {
            appendLine("=== CampusFold AI Diagnostics Report ===")
            appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Memory: $availRamMb MB available of $totalRamMb MB (lowMemory=${mem.lowMemory})")
            appendLine()

            val crash = _lastCrashTrace.value
            if (!crash.isNullOrBlank()) {
                appendLine("--- PREVIOUS CRASH TRACE ---")
                appendLine(crash)
                appendLine()
            }

            appendLine("--- LAST 50 DIAGNOSTIC EVENTS ---")
            val events = synchronized(lock) { inMemoryEvents.toList() }
            if (events.isEmpty()) {
                appendLine("(No events recorded)")
            } else {
                for (ev in events) {
                    appendLine("[${ev.formattedTime()}] [${ev.type}] ${ev.message}")
                    if (!ev.details.isNullOrBlank()) {
                        appendLine("    Details: ${ev.details.replace("\n", "\n    ")}")
                    }
                }
            }
            appendLine("=== End of Report ===")
        }
    }

    fun shareLog(context: Context) {
        val log = exportLogSummary(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CampusFold AI Diagnostics Report")
            putExtra(Intent.EXTRA_TEXT, log)
        }
        val chooser = Intent.createChooser(intent, "Share CampusFold Diagnostics Log")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
