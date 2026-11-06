package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val stepsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isTemplate: Boolean = false
)

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val screenshotPath: String?,
    val hierarchyJson: String,
    val nodeCount: Int,
    val tag: String = ""
)

@Entity(tableName = "safety_rules")
data class SafetyRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phrase: String,
    val isEnabled: Boolean = true
)

enum class RunStatus {
    OK,
    FAILED,
    PAUSED_NEEDS_PERSON,
    CANCELLED
}

data class RunResult(
    val pageName: String,
    val status: RunStatus,
    val failedStepName: String? = null,
    val durationSeconds: Double = 0.0,
    val errorDetails: String? = null,
    val capturePath: String? = null
)
