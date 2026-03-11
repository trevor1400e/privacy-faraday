package com.privacy.faraday.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE lxmfAddress = :address")
    suspend fun getByAddress(address: String): ContactEntity?

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET sessionState = :state WHERE lxmfAddress = :address")
    suspend fun updateSessionState(address: String, state: String)

    @Query("SELECT * FROM contacts")
    suspend fun getAllOnce(): List<ContactEntity>
}
