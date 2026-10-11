package com.findmissingpep.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.findmissingpep.app.data.local.dao.CaseDao
import com.findmissingpep.app.data.local.dao.NotificationDao
import com.findmissingpep.app.data.local.dao.SightingDao
import com.findmissingpep.app.data.local.entity.CaseEntity
import com.findmissingpep.app.data.local.entity.NotificationEntity
import com.findmissingpep.app.data.local.entity.SightingEntity

@Database(
    entities = [
        CaseEntity::class,
        SightingEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FmpDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun sightingDao(): SightingDao
    abstract fun notificationDao(): NotificationDao
}
