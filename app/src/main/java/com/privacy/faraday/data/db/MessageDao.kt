package com.privacy.faraday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ConversationPreview(
    val lxmfAddress: String,
    val displayName: String,
    val sessionState: String,
    val lastMessage: String?,
    val lastTimestamp: Long?
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query(
        """
        SELECT c.lxmfAddress, c.displayName, c.sessionState,
               m.content AS lastMessage, m.timestamp AS lastTimestamp
        FROM contacts c
        LEFT JOIN messages m ON m.conversationId = c.lxmfAddress
            AND m.timestamp = (SELECT MAX(m2.timestamp) FROM messages m2 WHERE m2.conversationId = c.lxmfAddress)
        ORDER BY COALESCE(m.timestamp, c.createdAt) DESC
        """
    )
    fun getConversationPreviews(): Flow<List<ConversationPreview>>
}
