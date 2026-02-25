package com.example.deepseekchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.deepseekchat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesOnce(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query(
        """
        UPDATE chat_messages
        SET promptTokens = :promptTokens,
            promptCacheHitTokens = :promptCacheHitTokens,
            promptCacheMissTokens = :promptCacheMissTokens
        WHERE id = :messageId
        """
    )
    suspend fun updateUserMessageUsage(
        messageId: Long,
        promptTokens: Int?,
        promptCacheHitTokens: Int?,
        promptCacheMissTokens: Int?
    )
}
