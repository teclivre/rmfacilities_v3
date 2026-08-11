package com.rmfacilities.app.data.model

import java.time.LocalDateTime

enum class NotificationType {
    OCORRENCIA_NOVA,
    TAREFA_ATRASADA,
    VISITA_PROGRAMADA,
    FUNCIONARIO_AUSENTE,
    ALERTA_OPERACIONAL
}

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: LocalDateTime,
    val read: Boolean
)
