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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Register the new FCM token with our backend
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
        Log.d(TAG, "FCM message received: ${message.data}")

        val data = message.data
        val type = data[KEY_TYPE] ?: "general"

        when (type) {
            "sighting" -> handleSightingAlert(data)
            "case_update" -> handleCaseUpdate(data)
            else -> handleGenericNotification(message.notification, data)
        }
    }

    private fun handleSightingAlert(data: Map<String, String>) {
        val sightingId = data[KEY_SIGHTING_ID] ?: return
        val personName = data[KEY_PERSON_NAME] ?: "Unknown"
        val similarity = data[KEY_SIMILARITY] ?: "N/A"
        val camera = data[KEY_CAMERA] ?: "Unknown Camera"

        // Deep link into the Forensic Inspector screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "forensic_inspector")
            putExtra("sighting_id", sightingId)
            action = "SIGHTING_ALERT"
        }

        val pendingIntent = PendingIntent.getActivity(
            this, sightingId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val notification = NotificationCompat.Builder(this, FindMissingPepApp.CHANNEL_SIGHTING_ALERTS)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("🚨 Match Detected: $personName")
            .setContentText("${similarity}% match at $camera")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A possible match for $personName was detected at $camera with ${similarity}% similarity. Tap to inspect.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(alertSound)
            .setVibrate(longArrayOf(0, 300, 200, 300, 200, 300))
            .build()

        try {
            NotificationManagerCompat.from(this).notify(sightingId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted", e)
        }
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
