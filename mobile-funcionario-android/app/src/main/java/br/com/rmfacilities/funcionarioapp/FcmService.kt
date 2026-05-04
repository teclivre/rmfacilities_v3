package br.com.rmfacilities.funcionarioapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "rmf_documentos"
        const val CHANNEL_NAME = "Documentos"
        const val EXTRA_ARQUIVO_ID = "arquivo_id"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val session = SessionManager(applicationContext)
        if (session.accessToken.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient(session).registrarPushToken(token)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val tipo = data["tipo"] ?: ""
        val titulo = message.notification?.title ?: data["titulo"] ?: "RM Funcionário"
        val corpo = message.notification?.body ?: data["corpo"] ?: ""

        val arquivoId = data["arquivo_id"]?.toIntOrNull() ?: -1

        // Build intent for the target activity
        val targetIntent = if (tipo == "documento_assinar" && arquivoId > 0) {
            Intent(this, DocumentosActivity::class.java).apply {
                putExtra(EXTRA_ARQUIVO_ID, arquivoId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            arquivoId,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannel()

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fenix_round)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
}
