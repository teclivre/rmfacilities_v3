package br.com.rmfacilities.funcionarioapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_DOCS = "rmf_documentos"
        const val CHANNEL_CHAT = "rmf_chat"
        const val EXTRA_ARQUIVO_ID = "arquivo_id"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val session = SessionManager(applicationContext)
        if (session.notificationsEnabled && session.accessToken.isNotBlank()) {
            serviceScope.launch {
                try {
                    ApiClient(session).registrarPushToken(token)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val session = SessionManager(applicationContext)
        if (!session.notificationsEnabled) return
        val data = message.data
        val tipo = data["tipo"] ?: ""
        val arquivoId = data["arquivo_id"]?.toIntOrNull() ?: -1

        // Títulos e corpos descritivos por tipo
        val titulo = message.notification?.title ?: data["titulo"] ?: when (tipo) {
            "documento_assinar" -> "📄 Novo documento para assinar"
            "novo_documento" -> "📁 Novo documento disponível"
            "chat", "chat_broadcast" -> "💬 Nova mensagem"
            "aviso_geral" -> "📢 Comunicado do RH"
            else -> "RM Funcionário"
        }
        val corpo = message.notification?.body ?: data["corpo"] ?: when (tipo) {
            "documento_assinar" -> "Você tem um documento aguardando sua assinatura."
            "novo_documento" -> "Um novo documento foi adicionado ao seu perfil."
            "chat" -> "Você recebeu uma nova mensagem."
            "chat_broadcast" -> "Há um aviso novo para você."
            "aviso_geral" -> "Toque para ver o comunicado."
            else -> "Toque para abrir o aplicativo."
        }

        val isChat = tipo == "chat" || tipo == "chat_broadcast"

        val targetIntent = when {
            tipo == "documento_assinar" && arquivoId > 0 ->
                Intent(this, DocumentosActivity::class.java).apply {
                    putExtra(EXTRA_ARQUIVO_ID, arquivoId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            tipo == "novo_documento" ->
                Intent(this, DocumentosActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            isChat ->
                Intent(this, MensagensActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            tipo == "aviso_geral" && !data["url"].isNullOrBlank() ->
                // Comunicado com link: abre o artigo direto no WebView
                Intent(this, WebViewActivity::class.java).apply {
                    putExtra(WebViewActivity.EXTRA_URL, data["url"])
                    putExtra(WebViewActivity.EXTRA_TITULO, titulo)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            tipo == "aviso_geral" ->
                Intent(this, MensagensActivity::class.java).apply {
                    putExtra("open_tab", "avisos")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            else ->
                Intent(this, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        }

        val channelId = if (isChat) CHANNEL_CHAT else CHANNEL_DOCS
        val openLabel = when (tipo) {
            "documento_assinar" -> "Assinar agora"
            "novo_documento" -> "Abrir documento"
            "chat", "chat_broadcast" -> "Abrir chat"
            "aviso_geral" -> "Ver comunicado"
            else -> "Abrir"
        }

        val laterIntent = Intent(this, HomeActivity::class.java).apply {
            putExtra("notif_later", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openNowPendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() + 1).toInt(),
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val laterPendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() + 2).toInt(),
            laterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        ensureChannels()

        val notifId = System.currentTimeMillis().toInt()
        val badgeNumber = (data["badge"]?.toIntOrNull() ?: 1).coerceAtLeast(1)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.accent))
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(badgeNumber)
            .addAction(0, openLabel, openNowPendingIntent)
            .addAction(0, "Marcar para depois", laterPendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notif)
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
