package com.privacy.faraday.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["lxmfAddress"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean,
    val status: String = "SENT"
)
