package com.findmissingpep.app.service

import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.findmissingpep.app.FindMissingPepApp
import com.findmissingpep.app.R
import com.findmissingpep.app.domain.repository.AuthRepository
import com.findmissingpep.app.presentation.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FmpFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FmpFCM"
        private const val KEY_TYPE = "type"
        private const val KEY_SIGHTING_ID = "sighting_id"
        private const val KEY_PERSON_NAME = "person_name"
        private const val KEY_SIMILARITY = "similarity_score"
        private const val KEY_CAMERA = "camera_location"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var liveAlertManager: LiveAlertManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Register the new FCM token with our backend and LiveAlertManager
        serviceScope.launch {
            try {
                authRepository.updateProfile(fcmToken = token)
                Log.d(TAG, "FCM token synced with backend")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM message received. Data: ${message.data}, Notification: ${message.notification?.title}")

        val data = message.data
        val type = data[KEY_TYPE] ?: ""
        val notifTitle = message.notification?.title ?: ""

        val isSighting = type.equals("sighting", ignoreCase = true) ||
                data.containsKey(KEY_SIGHTING_ID) ||
                data.containsKey("similarity") ||
                notifTitle.contains("Match", ignoreCase = true) ||
                notifTitle.contains("Sighting", ignoreCase = true)

        val isCaseUpdate = type.equals("case_update", ignoreCase = true) ||
                data.containsKey("case_id")

        when {
            isSighting -> handleSightingAlert(message)
            isCaseUpdate -> handleCaseUpdate(data)
            else -> handleGenericNotification(message.notification, data)
        }
    }

    private fun handleSightingAlert(message: RemoteMessage) {
        val data = message.data
        val sightingId = data[KEY_SIGHTING_ID] ?: data["id"] ?: System.currentTimeMillis().toString()
        val personName = data[KEY_PERSON_NAME] ?: data["person"] ?: message.notification?.title ?: "Missing Person"
        val rawSim = data[KEY_SIMILARITY] ?: data["similarity"] ?: "85"
        val similarityText = try {
            val d = rawSim.toDouble()
            if (d <= 1.0) "${(d * 100).toInt()}%" else "${d.toInt()}%"
        } catch (_: Exception) {
            rawSim
        }
        val camera = data[KEY_CAMERA] ?: data["camera"] ?: "Surveillance Camera"
        val detectedAt = data["detected_at"] ?: ""

        Log.i(TAG, "Dispatching sighting alert via LiveAlertManager for $personName ($sightingId)")
        liveAlertManager.dispatchAlert(
            sightingId = sightingId,
            personName = personName,
            similarityText = similarityText,
            cameraLocation = camera,
            detectedAt = detectedAt
        )
    }

    private fun handleCaseUpdate(data: Map<String, String>) {
        val title = data["title"] ?: "Case Update"
        val body = data["body"] ?: "Your case status has been updated."
        val caseId = data["case_id"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "case_detail")
            putExtra("case_id", caseId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, caseId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FindMissingPepApp.CHANNEL_CASE_UPDATES)
            .setSmallIcon(R.drawable.ic_case_update)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(caseId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
    }

    private fun handleGenericNotification(
        notification: RemoteMessage.Notification?,
        data: Map<String, String>
    ) {
        val title = notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = notification?.body ?: data["body"] ?: ""

        val builtNotification = NotificationCompat.Builder(this, FindMissingPepApp.CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builtNotification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
    }
}
