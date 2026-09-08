package com.example.WiChat.persistence

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context, password: ByteArray): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(password)
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wichat_secure.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                instance = db
                db
            }
        }
    }

    fun getInstance(): AppDatabase? = instance
}