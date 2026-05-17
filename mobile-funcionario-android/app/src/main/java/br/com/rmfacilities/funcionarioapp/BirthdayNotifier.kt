package br.com.rmfacilities.funcionarioapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Checa se hoje é o aniversário do funcionário e dispara notificação local.
 * Chamado em HomeActivity.onCreate() após carregar dados do /api/me.
 * Usa SharedPreferences para não repetir a notificação no mesmo dia.
 */
object BirthdayNotifier {

    private const val CHANNEL_ID = "aniversario_canal"
    private const val NOTIF_ID = 7777
    private const val PREF_FILE = "birthday_prefs"
    private const val KEY_LAST_NOTIF = "ultima_notificacao_aniversario"

    fun verificar(context: Context, dataNascimento: String?, nomeFunc: String) {
        if (dataNascimento.isNullOrBlank()) return

        // Espera formato YYYY-MM-DD
        val partes = dataNascimento.split("-")
        if (partes.size < 3) return

        val mesNasc = partes[1].toIntOrNull() ?: return
        val diaNasc = partes[2].toIntOrNull() ?: return

        val hoje = Calendar.getInstance()
        val mesHoje = hoje.get(Calendar.MONTH) + 1  // Calendar.MONTH é 0-based
        val diaHoje = hoje.get(Calendar.DAY_OF_MONTH)

        if (mesNasc != mesHoje || diaNasc != diaHoje) return

        // Verifica se já notificou hoje
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val dataHoje = "${mesHoje.toString().padStart(2,'0')}-${diaHoje.toString().padStart(2,'0')}-${hoje.get(Calendar.YEAR)}"
        if (prefs.getString(KEY_LAST_NOTIF, null) == dataHoje) return

        // Cria canal (Android 8+)
        criarCanal(context)

        val primeiroNome = nomeFunc.split(" ").firstOrNull() ?: nomeFunc
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎂 Feliz Aniversário, $primeiroNome!")
            .setContentText("Hoje é seu dia especial! Toda a equipe RM Facilities deseja um ótimo dia! 🎉")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Hoje é seu dia especial!\nToda a equipe RM Facilities deseja um ótimo aniversário! 🎉🥳"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notifManager.notify(NOTIF_ID, notif)

        // Registra que já notificou hoje
        prefs.edit().putString(KEY_LAST_NOTIF, dataHoje).apply()
    }

    private fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Aniversário",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificação de aniversário do colaborador"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }
}
