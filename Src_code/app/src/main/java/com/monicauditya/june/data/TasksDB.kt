

package com.monicauditya.june.data

import androidx.compose.runtime.Stable
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "Task")
@Stable
data class Task(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var name: String = "",
    var systemPrompt: String = "",
    var modelId: Long = -1,
    var shortcutId: String? = null,
    @Transient var modelName: String = "",
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM Task WHERE id = :taskId")
    suspend fun getTask(taskId: Long): Task

    @Query("SELECT * FROM Task")
    fun getTasks(): Flow<List<Task>>

    @Insert
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM Task WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)
}
