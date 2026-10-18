package com.findmissingpep.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.findmissingpep.app.BuildConfig
import com.findmissingpep.app.FindMissingPepApp
import com.findmissingpep.app.R
import com.findmissingpep.app.data.api.SseEventListener
import com.findmissingpep.app.data.api.TokenProvider
import com.findmissingpep.app.data.local.dao.SightingDao
import com.findmissingpep.app.data.model.dto.SseAlertData
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.data.model.mapper.toEntity
import com.findmissingpep.app.domain.repository.AuthRepository
import com.findmissingpep.app.domain.repository.NotificationRepository
import com.findmissingpep.app.domain.repository.SightingRepository
import com.findmissingpep.app.presentation.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

data class LiveSightingAlert(
    val sightingId: String,
    val personName: String,
    val similarityText: String,
    val cameraLocation: String,
    val detectedAt: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * LiveAlertManager is the central engine for real-time missing person match alerts.
 * 
 * It combines:
 * 1. Immediate FCM Push notification parsing & dispatch.
 * 2. Real-time SSE streaming from the backend (/api/events/stream).
 * 3. Fallback active polling ticker (every 10 seconds) to guarantee zero-missed alerts.
 * 4. High-priority Android System Heads-Up Notifications with custom alarm sound & vibration.
 * 5. In-app live banner events broadcast to Compose screens.
 */
@Singleton
class LiveAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sseEventListener: SseEventListener,
    private val sightingRepository: SightingRepository,
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val sightingDao: SightingDao,
    private val tokenProvider: TokenProvider,
    private val json: Json
) {
    companion object {
        private const val TAG = "LiveAlertManager"
        private const val PREFS_NAME = "fmp_alert_prefs"
        private const val KEY_NOTIFIED_IDS = "notified_sighting_ids"
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Thread-safe set of sighting IDs that have already triggered an alert
    private val notifiedSightingIds = Collections.synchronizedSet(
        HashSet<String>(prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet())
    )

    private val _liveAlerts = MutableSharedFlow<LiveSightingAlert>(extraBufferCapacity = 32)
    val liveAlerts: SharedFlow<LiveSightingAlert> = _liveAlerts.asSharedFlow()

    private var isStarted = false

    /**
     * Start all real-time listeners and synchronization workers.
     */
    fun start() {
        if (isStarted) return
        isStarted = true
        Log.i(TAG, "Initializing LiveAlertManager...")

        // 1. Sync FCM token with backend immediately
        syncFcmToken()

        // 2. Connect SSE Real-time stream
        startSseStream()

        // 3. Observe SSE sighting events
        observeSseStream()

        // 4. Start fallback live polling ticker (every 10s)
        startPollingTicker()
    }

    /**
     * Explicitly sync device FCM token to backend user profile.
     */
    fun syncFcmToken() {
        managerScope.launch {
            try {
                val token = firebaseMessaging.token.await()
                Log.d(TAG, "Fetched FCM device token: $token")
                val res = authRepository.updateProfile(fcmToken = token)
                res.fold(
                    onSuccess = { Log.i(TAG, "FCM token successfully registered with backend.") },
                    onFailure = { Log.w(TAG, "Failed to register FCM token with backend: ${it.message}") }
                )
            } catch (e: Exception) {
                Log.w(TAG, "FCM token retrieval error (Play Services or offline): ${e.message}")
            }
        }
    }

    /**
     * Establishes real-time SSE stream connection to backend.
     */
    fun startSseStream() {
        managerScope.launch {
            val token = tokenProvider.getToken()
                ?: try {
                    firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
                } catch (e: Exception) {
                    null
                }
                ?: "test-${firebaseAuth.currentUser?.email ?: "mksinha77756@gmail.com"}"

            Log.d(TAG, "Starting SSE stream connection to ${BuildConfig.API_BASE_URL}...")
            sseEventListener.startListening(BuildConfig.API_BASE_URL, token)
        }
    }

    private fun observeSseStream() {
        managerScope.launch {
            sseEventListener.sightingAlerts.collect { rawJson ->
                try {
                    Log.i(TAG, "Received raw SSE alert: $rawJson")
                    val alert = json.decodeFromString<SseAlertData>(rawJson)
                    handleIncomingSseAlert(alert)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SSE sighting payload: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun handleIncomingSseAlert(alert: SseAlertData) {
        val sighting = alert.toDomain()
        
        // Save to Room cache so UI instantly reflects
        try {
            sightingDao.insert(sighting.toEntity())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache SSE sighting: ${e.message}")
        }

        val id = alert.sightingId.ifBlank { sighting.id }
        if (id.isBlank()) return

        val simText = formatSimilarity(alert.similarityScore, alert.similarity)
        val name = alert.personName ?: sighting.personName ?: "Missing Person"
        val cam = alert.cameraLocation ?: sighting.cameraLocation ?: "Surveillance Camera"

        // Trigger alert if not already notified
        dispatchAlert(
            sightingId = id,
            personName = name,
            similarityText = simText,
            cameraLocation = cam,
            detectedAt = alert.detectedAt
        )
    }

    /**
     * Fallback polling ticker (runs every 10 seconds).
     * Guarantees alerts fire even if SSE connection disconnects or FCM is dropped.
     */
    private fun startPollingTicker() {
        managerScope.launch {
            // Initial delay to let startup complete
            delay(3000)
            while (isActive) {
                try {
                    checkLatestSightings()
                } catch (e: Exception) {
                    Log.w(TAG, "Polling check warning: ${e.message}")
                }
                delay(10_000)
            }
        }
    }

    private suspend fun checkLatestSightings() {
        val result = sightingRepository.fetchSightings(limit = 10)
        result.onSuccess { sightings ->
            // Check top 3 most recent sightings
            for (sighting in sightings.take(3)) {
                if (!notifiedSightingIds.contains(sighting.id)) {
                    val simText = formatSimilarity(sighting.similarityScore, null)
                    dispatchAlert(
                        sightingId = sighting.id,
                        personName = sighting.personName ?: "Missing Person",
                        similarityText = simText,
                        cameraLocation = sighting.cameraLocation ?: "Surveillance Zone",
                        detectedAt = sighting.detectedAt
                    )
                }
            }
        }
        
        // Also sync unread notification count
        try {
            notificationRepository.fetchUnreadCount()
        } catch (_: Exception) { }
    }

    /**
     * Process an alert from FCM, SSE, or Polling.
     */
    fun dispatchAlert(
        sightingId: String,
        personName: String,
        similarityText: String,
        cameraLocation: String,
        detectedAt: String = ""
    ) {
        if (sightingId.isBlank() || notifiedSightingIds.contains(sightingId)) {
            return
        }

        // Record as notified
        notifiedSightingIds.add(sightingId)
        persistNotifiedIds()

        val alert = LiveSightingAlert(
            sightingId = sightingId,
            personName = personName,
            similarityText = similarityText,
            cameraLocation = cameraLocation,
            detectedAt = detectedAt
        )

        // 1. Emit to in-app Compose banner flow
        _liveAlerts.tryEmit(alert)

        // 2. Fire high-priority Android System Heads-Up Notification
        fireSystemNotification(alert)
    }

    private fun fireSystemNotification(alert: LiveSightingAlert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "forensic_inspector")
            putExtra("sighting_id", alert.sightingId)
            action = "SIGHTING_ALERT_${alert.sightingId}"
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.sightingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, FindMissingPepApp.CHANNEL_SIGHTING_ALERTS)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("🚨 Biometric Match: ${alert.personName}")
            .setContentText("${alert.similarityText} match at ${alert.cameraLocation}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("🚨 Biometric Match: ${alert.personName}")
                    .bigText(
                        "CCTV Facial Recognition detected ${alert.personName} at ${alert.cameraLocation} with ${alert.similarityText} confidence. Tap to inspect biometric evidence."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
            .addAction(
                R.drawable.ic_alert,
                "INSPECT EVIDENCE",
                pendingIntent
            )
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(alert.sightingId.hashCode(), notification)
                Log.i(TAG, "Heads-up notification fired for ${alert.personName} (${alert.sightingId})")
            } else {
                Log.w(TAG, "Notification permission is not enabled by user.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: POST_NOTIFICATIONS permission not granted.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display notification: ${e.message}", e)
        }
    }

    private fun persistNotifiedIds() {
        managerScope.launch {
            // Keep last 100 notified IDs to avoid unbounded growth
            val trimmed = synchronized(notifiedSightingIds) {
                if (notifiedSightingIds.size > 100) {
                    notifiedSightingIds.toList().takeLast(100).toSet()
                } else {
                    notifiedSightingIds.toSet()
                }
            }
            prefs.edit().putStringSet(KEY_NOTIFIED_IDS, trimmed).apply()
        }
    }

    private fun formatSimilarity(score: Double, fallback: Double?): String {
        val s = if (score > 0.0) score else (fallback ?: 0.0)
        return if (s > 1.0) {
            "${s.toInt()}%"
        } else if (s > 0.0) {
            "${(s * 100).toInt()}%"
        } else {
            "High"
        }
    }
}
