package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PopupRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PopupRuleDao {

    @Query("SELECT * FROM popup_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<PopupRuleEntity>>

    @Query("SELECT * FROM popup_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<PopupRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PopupRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<PopupRuleEntity>)

    @Update
    suspend fun updateRule(rule: PopupRuleEntity)

    @Query("DELETE FROM popup_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
