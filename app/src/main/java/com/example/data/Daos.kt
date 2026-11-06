package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CaptureEntity
import com.example.data.model.SafetyRuleEntity
import com.example.data.model.ScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY createdAt DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id LIMIT 1")
    suspend fun getScriptById(id: Long): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity): Long

    @Update
    suspend fun updateScript(script: ScriptEntity)

    @Delete
    suspend fun deleteScript(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Long)
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun getCaptureById(id: Long): CaptureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Delete
    suspend fun deleteCapture(capture: CaptureEntity)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteCaptureById(id: Long)

    @Query("DELETE FROM captures")
    suspend fun clearAll()
}

@Dao
interface SafetyRuleDao {
    @Query("SELECT * FROM safety_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<SafetyRuleEntity>>

    @Query("SELECT phrase FROM safety_rules WHERE isEnabled = 1")
    suspend fun getActivePhrases(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SafetyRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRules(rules: List<SafetyRuleEntity>)

    @Update
    suspend fun updateRule(rule: SafetyRuleEntity)

    @Delete
    suspend fun deleteRule(rule: SafetyRuleEntity)

    @Query("DELETE FROM safety_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
