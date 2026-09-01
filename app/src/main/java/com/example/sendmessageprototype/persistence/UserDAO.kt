package com.example.sendmessageprototype.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("DELETE FROM users WHERE userID = :userId")
    suspend fun remove(userId: String)

    @Query("SELECT * FROM users WHERE userID = :userId")
    suspend fun getUserByID(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE isLocal = 1 LIMIT 1")
    suspend fun getLocalUser(): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}