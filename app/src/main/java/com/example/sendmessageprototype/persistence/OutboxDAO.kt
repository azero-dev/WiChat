package com.example.sendmessageprototype.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface OutboxDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(outbox: OutboxEntity)

    @Update
    suspend fun update(outbox: OutboxEntity)

    @Query("DELETE FROM outbox WHERE messageID = :messageId")
    suspend fun remove(messageId: String)

    @Query("SELECT * FROM outbox")
    suspend fun getAllPending(): List<OutboxEntity>
}