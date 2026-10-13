package com.findmissingpep.app.presentation.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global snackbar manager that mirrors the web app's ToastContext.
 * Provides app-wide success/error/info messages via a SharedFlow.
 * Inject this into ViewModels or repositories to show user-facing feedback.
 */
@Singleton
class SnackbarManager @Inject constructor() {

    private val _messages = Channel<SnackbarMessage>(Channel.BUFFERED)
    val messages: Flow<SnackbarMessage> = _messages.receiveAsFlow()

    fun showSuccess(text: String) {
        _messages.trySend(SnackbarMessage(text, SnackbarType.SUCCESS))
    }

    fun showError(text: String) {
        _messages.trySend(SnackbarMessage(text, SnackbarType.ERROR))
    }

    fun showInfo(text: String) {
        _messages.trySend(SnackbarMessage(text, SnackbarType.INFO))
    }
}

data class SnackbarMessage(
    val text: String,
    val type: SnackbarType
)

enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO
}
