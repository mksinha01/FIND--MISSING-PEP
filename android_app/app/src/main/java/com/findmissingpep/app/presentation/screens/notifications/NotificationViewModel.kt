package com.findmissingpep.app.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findmissingpep.app.domain.model.AppNotification
import com.findmissingpep.app.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        // Observe Room cached notifications
        viewModelScope.launch {
            notificationRepository.observeNotifications().collect { list ->
                _uiState.update { it.copy(notifications = list) }
            }
        }
        // Observe unread count
        viewModelScope.launch {
            notificationRepository.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
        refreshNotifications()
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            notificationRepository.fetchNotifications().fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false) } },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.localizedMessage) }
                }
            )
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }
}
