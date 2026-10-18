package com.findmissingpep.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FindMissingPepApp : Application() {

    companion object {
        const val CHANNEL_SIGHTING_ALERTS = "sighting_alerts"
        const val CHANNEL_CASE_UPDATES = "case_updates"
        const val CHANNEL_GENERAL = "general"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .build()

        val sightingChannel = NotificationChannel(
            CHANNEL_SIGHTING_ALERTS,
            getString(R.string.channel_sighting_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time CCTV match alerts for missing persons"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            enableLights(true)
            lightColor = android.graphics.Color.RED
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val caseChannel = NotificationChannel(
            CHANNEL_CASE_UPDATES,
            getString(R.string.channel_case_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Status updates for your missing person cases"
            enableVibration(true)
            setShowBadge(true)
        }

        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL,
            getString(R.string.channel_general),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "General system notifications"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(sightingChannel, caseChannel, generalChannel)
        )
    }
}
