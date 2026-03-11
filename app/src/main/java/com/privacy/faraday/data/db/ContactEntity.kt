package com.privacy.faraday.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val lxmfAddress: String,
    val displayName: String = "",
    val sessionState: String = "UNKNOWN",
    val createdAt: Long = System.currentTimeMillis()
)
