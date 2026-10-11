package com.findmissingpep.app.data.local.dao

import androidx.room.*
import com.findmissingpep.app.data.local.entity.CaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE user_id = :userId ORDER BY updated_at DESC")
    fun observeByUser(userId: String): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun getById(id: String): CaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cases: List<CaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(case_: CaseEntity)

    @Query("DELETE FROM cases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cases")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cases WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}
