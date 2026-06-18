package com.personalai.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getById(id: Long): SpaceEntity?

    @Insert
    suspend fun insert(space: SpaceEntity): Long

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteById(id: Long)
}
