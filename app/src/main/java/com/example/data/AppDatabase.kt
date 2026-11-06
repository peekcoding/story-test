package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CaptureEntity
import com.example.data.model.SafetyRuleEntity
import com.example.data.model.ScriptEntity

@Database(
    entities = [ScriptEntity::class, CaptureEntity::class, SafetyRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun captureDao(): CaptureDao
    abstract fun safetyRuleDao(): SafetyRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "story_test.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
