

package com.monicauditya.june.data

import android.text.Spanned
import androidx.compose.runtime.Stable
import androidx.core.text.toSpanned
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ChatMessage")
@Stable
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var chatId: Long = 0,
    var message: String = "",
    var isUserMessage: Boolean = false,
    @Ignore
    var renderedMessage: Spanned = "".toSpanned()
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM ChatMessage WHERE chatId = :chatId")
    fun getMessages(chatId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM ChatMessage WHERE chatId = :chatId")
    suspend fun getMessagesForModel(chatId: Long): List<ChatMessage>

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM ChatMessage WHERE chatId = :chatId")
    suspend fun deleteMessages(chatId: Long)

    @Query("DELETE FROM ChatMessage WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)
}
