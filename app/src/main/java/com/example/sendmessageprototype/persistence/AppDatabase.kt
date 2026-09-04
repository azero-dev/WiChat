package com.example.sendmessageprototype.persistence

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userID: String,
    val userName: String,
    val createdAt: Long,
    val lastKnownDeviceAddress: String?,
    val isLocal: Boolean = false,
    val isPersistent: Boolean = false,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageID: String,
    val type: String,
    val conversationID: String,
    val senderID: String,
    val receiverID: String,
    val content: ByteArray,
    val timestamp: Long,
    val state: String,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val messageID: String,
    val ttl: Int,
    val retryCounter: Int,
    val lastAttemptAt: Long,
    val alreadySentTo: String,
)

class Converters {
    @TypeConverter
    fun fromStringList(value: String): Set<String> = Json.decodeFromString(value)

    @TypeConverter
    fun toStringList(set: Set<String>): String = Json.encodeToString(set)
}

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
        ConfigEntity::class
               ],
    version = 1,
    exportSchema = false,
)

@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun userDAO(): UserDAO
    abstract fun messageDAO(): MessageDAO
    abstract fun outboxDAO(): OutboxDAO
    abstract fun configDAO(): ConfigDAO
}