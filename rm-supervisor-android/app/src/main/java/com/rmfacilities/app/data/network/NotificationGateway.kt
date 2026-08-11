package com.rmfacilities.app.data.network

import com.rmfacilities.app.data.model.NotificationItem

interface NotificationGateway {
    suspend fun getNotifications(): List<NotificationItem>
    suspend fun markAsRead(id: String)
}

class MockNotificationGateway : NotificationGateway {
    override suspend fun getNotifications(): List<NotificationItem> = emptyList()
    override suspend fun markAsRead(id: String) = Unit
}
