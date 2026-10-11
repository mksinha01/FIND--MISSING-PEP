package com.findmissingpep.app.data.api

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSE event listener for real-time sighting alerts.
 * Establishes a long-lived HTTP connection to the backend's SSE endpoint
 * and emits raw JSON payloads via a SharedFlow.
 */
@Singleton
class SseEventListener @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SseEventListener"
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
    }

    private val _sightingAlerts = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val sightingAlerts = _sightingAlerts.asSharedFlow()

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState = _connectionState.asSharedFlow()

    private var eventSource: EventSource? = null
    private var currentRetryDelay = INITIAL_RETRY_DELAY_MS

    enum class ConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }

    fun startListening(baseUrl: String, token: String) {
        stopListening()
        _connectionState.tryEmit(ConnectionState.CONNECTING)

        val request = Request.Builder()
            .url("$baseUrl/api/events/stream?token=$token")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val sseClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // Keep connection open
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        val factory = EventSources.createFactory(sseClient)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.d(TAG, "SSE connection opened")
                currentRetryDelay = INITIAL_RETRY_DELAY_MS
                _connectionState.tryEmit(ConnectionState.CONNECTED)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                when (type) {
                    "sighting" -> {
                        Log.d(TAG, "SSE sighting received: $data")
                        _sightingAlerts.tryEmit(data)
                    }
                    "ping", null -> {
                        // Keep-alive, ignore
                    }
                    else -> {
                        Log.d(TAG, "SSE event type=$type, data=$data")
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "SSE connection closed")
                _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                Log.e(TAG, "SSE connection failed: ${t?.message}", t)
                _connectionState.tryEmit(ConnectionState.ERROR)

                // Exponential backoff reconnection
                currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                Log.d(TAG, "Will retry SSE in ${currentRetryDelay}ms")
            }
        })
    }

    fun stopListening() {
        eventSource?.cancel()
        eventSource = null
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
    }

    val isConnected: Boolean
        get() = eventSource != null
}
