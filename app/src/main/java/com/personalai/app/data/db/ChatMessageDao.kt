package com.personalai.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observeForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    /** Used when editing a previously sent message: drops it and everything after it so the edit can be resent. */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND id >= :fromMessageId")
    suspend fun deleteFromMessage(sessionId: Long, fromMessageId: Long)
}
