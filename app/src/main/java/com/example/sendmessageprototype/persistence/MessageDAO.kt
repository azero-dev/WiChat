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
        SELECT
            u.userID AS peerID,
            CASE WHEN :localID < u.userID THEN :localID || '_' || u.userID ELSE u.userID || '_' || :localID END AS conversationID,
            COALESCE(m.timestamp, 0) as lastMessageAt,
            COALESCE(m.content, x'') as lastMessageText
        FROM users u
        LEFT JOIN (
            SELECT conversationID, content, timestamp
            FROM messages
            WHERE (conversationID, timestamp) IN (
                SELECT conversationID, MAX(timestamp)
                FROM messages
                GROUP BY conversationID
            )
        ) m ON (m.conversationID = (CASE WHEN :localID < u.userID THEN :localID || '_' || u.userID ELSE u.userID || '_' || :localID END))
        WHERE u.isLocal = 0
        ORDER BY lastMessageAt DESC, u.userName ASC
    """)
    fun getConversationMetas(localID: String): Flow<List<ConversationMeta>>
}