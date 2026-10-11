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
        val sightingChannel = NotificationChannel(
            CHANNEL_SIGHTING_ALERTS,
            getString(R.string.channel_sighting_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time CCTV match alerts for missing persons"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setShowBadge(true)
        }

        val caseChannel = NotificationChannel(
            CHANNEL_CASE_UPDATES,
            getString(R.string.channel_case_updates),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Status updates for your missing person cases"
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
