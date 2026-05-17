package br.com.rmfacilities.funcionarioapp

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque

object TelemetryLogger {
    private const val PREF = "rm_funcionario_telemetry"
    private const val KEY_LAST = "last_error"
    private const val KEY_COUNT = "error_count"
    @Volatile private var initialized = false
    private val queue = LinkedBlockingDeque<Map<String, Any>>(500)
    private val executor = Executors.newSingleThreadExecutor()
    private val gson = Gson()
    private val http = OkHttpClient()
    @Volatile private var session: SessionManager? = null

    fun init(context: Context, sessionManager: SessionManager? = null) {
        session = sessionManager
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logHandled(context, "uncaught:${thread.name}", throwable)
                    flushSync(context)
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
        enqueue("FATAL", origem, throwable.message ?: throwable.javaClass.simpleName,
            throwable.stackTraceToString())
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        enqueue("ERROR", tag, msg, throwable?.stackTraceToString())
    }

    fun w(tag: String, msg: String) {
        enqueue("WARN", tag, msg, null)
    }

    fun i(tag: String, msg: String) {
        enqueue("INFO", tag, msg, null)
    }

    private fun enqueue(nivel: String, tag: String, mensagem: String, stack: String?) {
        val entry = mutableMapOf<String, Any>(
            "nivel" to nivel,
            "tag" to tag,
            "mensagem" to mensagem,
            "timestamp" to System.currentTimeMillis(),
            "versao" to BuildConfig.VERSION_NAME,
            "dispositivo" to "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        )
        if (!stack.isNullOrBlank()) entry["stack"] = stack
        queue.offerLast(entry)
        if (queue.size >= 20) flush()
    }

    fun flush() {
        executor.submit { flushSync(null) }
    }

    private fun flushSync(context: Context?) {
        val sess = session ?: return
        if (sess.accessToken.isBlank()) return
        val batch = ArrayList<Map<String, Any>>(20)
        var drained = 0
        while (drained < 50) {
            val item = queue.pollFirst() ?: break
            batch.add(item)
            drained++
        }
        if (batch.isEmpty()) return
        try {
            val base = sess.apiBaseUrl.trim().trimEnd('/')
            val body = gson.toJson(mapOf("logs" to batch))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$base/api/app/log")
                .post(body)
                .addHeader("Authorization", "Bearer ${sess.accessToken}")
                .addHeader("Content-Type", "application/json")
                .build()
            http.newCall(req).execute().use { /* fire and forget */ }
        } catch (_: Exception) {
            // Re-enfileirar em caso de falha de rede
            batch.forEach { queue.offerFirst(it) }
        }
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
