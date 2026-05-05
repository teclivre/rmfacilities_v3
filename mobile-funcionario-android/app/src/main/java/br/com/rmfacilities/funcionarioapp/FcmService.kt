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
        const val CHANNEL_DOCS = "rmf_documentos"
        const val CHANNEL_CHAT = "rmf_chat"
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
        val arquivoId = data["arquivo_id"]?.toIntOrNull() ?: -1

        // Títulos e corpos descritivos por tipo
        val titulo = message.notification?.title ?: data["titulo"] ?: when (tipo) {
            "documento_assinar" -> "📄 Novo documento para assinar"
            "chat", "chat_broadcast" -> "💬 Nova mensagem"
            else -> "RM Funcionário"
        }
        val corpo = message.notification?.body ?: data["corpo"] ?: when (tipo) {
            "documento_assinar" -> "Você tem um documento aguardando sua assinatura."
            "chat" -> "Você recebeu uma nova mensagem."
            "chat_broadcast" -> "Há um aviso novo para você."
            else -> "Toque para abrir o aplicativo."
        }

        val isChat = tipo == "chat" || tipo == "chat_broadcast"

        val targetIntent = when {
            tipo == "documento_assinar" && arquivoId > 0 ->
                Intent(this, DocumentosActivity::class.java).apply {
                    putExtra(EXTRA_ARQUIVO_ID, arquivoId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            isChat ->
                Intent(this, MensagensActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            else ->
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        }

        val channelId = if (isChat) CHANNEL_CHAT else CHANNEL_DOCS

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannels()

        val notif = NotificationCompat.Builder(this, channelId)
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

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DOCS, "Documentos", NotificationManager.IMPORTANCE_HIGH)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_CHAT, "Mensagens", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
