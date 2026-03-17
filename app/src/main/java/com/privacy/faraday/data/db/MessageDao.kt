package com.privacy.faraday.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ConversationPreview(
    val lxmfAddress: String,
    val displayName: String,
    val nickname: String,
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

    @Query("UPDATE messages SET status = :newStatus WHERE conversationId = :conversationId AND isOutgoing = 1 AND status = :currentStatus")
    suspend fun updateOutgoingStatus(conversationId: String, currentStatus: String, newStatus: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getQueuedMessages(conversationId: String): List<MessageEntity>

    @Query(
        """
        SELECT c.lxmfAddress, c.displayName, c.nickname, c.sessionState,
               m.content AS lastMessage, m.timestamp AS lastTimestamp
        FROM contacts c
        LEFT JOIN messages m ON m.conversationId = c.lxmfAddress
            AND m.timestamp = (SELECT MAX(m2.timestamp) FROM messages m2 WHERE m2.conversationId = c.lxmfAddress)
        ORDER BY COALESCE(m.timestamp, c.createdAt) DESC
        """
    )
    fun getConversationPreviews(): Flow<List<ConversationPreview>>

    // Delete outgoing messages where timer started at send time
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND isOutgoing = 1 AND isSystem = 0 AND timestamp < :cutoffTimestamp")
    suspend fun deleteExpiredOutgoing(conversationId: String, cutoffTimestamp: Long)

    // Delete incoming messages where timer started at read time (readAt > 0 means read)
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND isOutgoing = 0 AND isSystem = 0 AND readAt > 0 AND readAt < :cutoffTimestamp")
    suspend fun deleteExpiredIncoming(conversationId: String, cutoffTimestamp: Long)

    // Mark unread incoming messages as read
    @Query("UPDATE messages SET readAt = :readAt WHERE conversationId = :conversationId AND isOutgoing = 0 AND readAt = 0")
    suspend fun markIncomingAsRead(conversationId: String, readAt: Long)
}
