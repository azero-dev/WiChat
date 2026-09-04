package com.example.sendmessageprototype.persistence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_config")
data class ConfigEntity(
    @PrimaryKey val id: Int = 1,
    val notificationsEnabled: Boolean,
    val isInactiveMode: Boolean,
)

@Dao
interface ConfigDAO {
    @Query("SELECT * FROM app_config WHERE id = 1")
    fun getConfig(): Flow<ConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ConfigEntity)
}