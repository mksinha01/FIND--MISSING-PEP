package com.findmissingpep.app.data.local.dao

import androidx.room.*
import com.findmissingpep.app.data.local.entity.SightingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {
    @Query("SELECT * FROM sightings ORDER BY detected_at DESC")
    fun observeAll(): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE person_id = :personId ORDER BY detected_at DESC")
    fun observeByPerson(personId: String): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE id = :id")
    suspend fun getById(id: String): SightingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sightings: List<SightingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sighting: SightingEntity)

    @Query("DELETE FROM sightings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sightings WHERE status = 'PENDING'")
    suspend fun countPending(): Int
}
