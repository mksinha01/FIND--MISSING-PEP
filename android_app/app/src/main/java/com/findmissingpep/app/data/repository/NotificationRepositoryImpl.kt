package com.findmissingpep.app.data.repository

import com.findmissingpep.app.data.api.FmpApiService
import com.findmissingpep.app.data.local.dao.NotificationDao
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.data.model.mapper.toEntity
import com.findmissingpep.app.domain.model.AppNotification
import com.findmissingpep.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: FmpApiService,
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun observeNotifications(): Flow<List<AppNotification>> =
        notificationDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override val unreadCount: Flow<Int> = notificationDao.observeUnreadCount()

    override suspend fun fetchNotifications(
        unreadOnly: Boolean,
        limit: Int
    ): Result<List<AppNotification>> = runCatching {
        val response = api.getNotifications(unreadOnly, limit)
        val notifications = response.items.map { it.toDomain() }
        notificationDao.insertAll(notifications.map { it.toEntity() })
        notifications
    }

    override suspend fun fetchUnreadCount(): Result<Int> = runCatching {
        api.getUnreadCount().unreadCount
    }

    override suspend fun markAsRead(id: String): Result<AppNotification> = runCatching {
        val response = api.markNotificationRead(id)
        val notification = response.toDomain()
        notificationDao.markAsRead(id)
        notification
    }

    override suspend fun markAllAsRead(): Result<Int> = runCatching {
        val response = api.markAllNotificationsRead()
        notificationDao.markAllAsRead()
        response.updatedCount
    }
}
