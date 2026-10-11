package com.findmissingpep.app.domain.repository

import com.findmissingpep.app.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    /** Observe notifications from Room cache */
    fun observeNotifications(): Flow<List<AppNotification>>

    /** Unread count badge */
    val unreadCount: Flow<Int>

    /** Fetch notifications from network */
    suspend fun fetchNotifications(unreadOnly: Boolean = false, limit: Int = 50): Result<List<AppNotification>>

    /** Get unread count from network */
    suspend fun fetchUnreadCount(): Result<Int>

    /** Mark single notification as read */
    suspend fun markAsRead(id: String): Result<AppNotification>

    /** Mark all notifications as read */
    suspend fun markAllAsRead(): Result<Int>
}
