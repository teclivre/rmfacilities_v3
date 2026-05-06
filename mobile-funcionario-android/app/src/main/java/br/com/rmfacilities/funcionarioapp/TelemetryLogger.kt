package br.com.rmfacilities.funcionarioapp

import android.content.Context

object TelemetryLogger {
    private const val PREF = "rm_funcionario_telemetry"
    private const val KEY_LAST = "last_error"
    private const val KEY_COUNT = "error_count"
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logHandled(context, "uncaught:${thread.name}", throwable)
                } catch (_: Exception) {}
                previous?.uncaughtException(thread, throwable)
            }
            initialized = true
        }
    }

    fun logHandled(context: Context, origem: String, throwable: Throwable?) {
        if (throwable == null) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val msg = "[${System.currentTimeMillis()}] $origem: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        prefs.edit().putString(KEY_LAST, msg).putInt(KEY_COUNT, count).apply()
    }

    fun lastError(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST, "Sem erros registrados") ?: "Sem erros registrados"
    }

    fun errorCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COUNT, 0)
    }
}
