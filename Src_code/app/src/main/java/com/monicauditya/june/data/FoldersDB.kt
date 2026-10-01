

package com.monicauditya.june.data

import androidx.compose.runtime.Stable
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "Folder")
@Stable
data class Folder(@PrimaryKey(autoGenerate = true) var id: Long = 0, var name: String = "")

@Dao
interface FolderDao {
    @Insert
    suspend fun insertFolder(folder: Folder)

    @Update
    suspend fun updateFolder(folder: Folder)

    @Query("SELECT * FROM Folder")
    fun getFolders(): Flow<List<Folder>>

    @Query("DELETE FROM Folder WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)
}
