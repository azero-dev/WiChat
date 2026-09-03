package com.example.sendmessageprototype.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sendmessageprototype.core.ConversationMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(message: MessageEntity)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE messageID = :messageId")
    suspend fun remove(messageId: String)

    @Query("DELETE FROM messages WHERE conversationID = :conversationId")
    suspend fun removeConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationID = :conversationId ORDER BY timestamp ASC")
    fun getMessagesOf(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageID = :messageId")
    suspend fun getMessageByID(messageId: String): MessageEntity?

    @Query("""
        SELECT conversationID, 
        CASE WHEN senderID = :localID THEN receiverID ELSE senderID END as peerID,
        MAX(timestamp) as lastMessageAt,
        (SELECT content FROM messages WHERE conversationID = m.conversationID ORDER BY timestamp DESC LIMIT 1) as lastMessageText
        FROM messages m
        GROUP BY conversationID
        ORDER BY lastMessageAt DESC
    """)
    fun getConversationMetas(localID: String): Flow<List<ConversationMeta>>
}