package com.example.data

import android.content.Context
import com.example.data.model.CaptureEntity
import com.example.data.model.SafetyRuleEntity
import com.example.data.model.ScriptEntity
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class StoryTestRepository(private val db: AppDatabase) {

    val scripts: Flow<List<ScriptEntity>> = db.scriptDao().getAllScripts()
    val captures: Flow<List<CaptureEntity>> = db.captureDao().getAllCaptures()
    val safetyRules: Flow<List<SafetyRuleEntity>> = db.safetyRuleDao().getAllRules()

    suspend fun getScript(id: Long): ScriptEntity? = db.scriptDao().getScriptById(id)

    suspend fun saveScript(name: String, description: String, steps: List<ScriptStep>, id: Long = 0): Long {
        val json = JsonCodec.stepsToJson(steps)
        val entity = ScriptEntity(
            id = id,
            name = name,
            description = description,
            stepsJson = json
        )
        return if (id == 0L) {
            db.scriptDao().insertScript(entity)
        } else {
            db.scriptDao().updateScript(entity)
            id
        }
    }

    suspend fun deleteScript(id: Long) = db.scriptDao().deleteScriptById(id)

    suspend fun saveCapture(capture: CaptureEntity): Long = db.captureDao().insertCapture(capture)

    suspend fun getCapture(id: Long): CaptureEntity? = db.captureDao().getCaptureById(id)

    suspend fun deleteCapture(id: Long) = db.captureDao().deleteCaptureById(id)

    suspend fun clearCaptures() = db.captureDao().clearAll()

    suspend fun getActiveSafetyPhrases(): List<String> = db.safetyRuleDao().getActivePhrases()

    suspend fun addSafetyRule(phrase: String): Long {
        return db.safetyRuleDao().insertRule(SafetyRuleEntity(phrase = phrase.trim(), isEnabled = true))
    }

    suspend fun updateSafetyRule(rule: SafetyRuleEntity) = db.safetyRuleDao().updateRule(rule)

    suspend fun deleteSafetyRule(id: Long) = db.safetyRuleDao().deleteRuleById(id)

    suspend fun seedDefaultsIfEmpty() = withContext(Dispatchers.IO) {
        // Seed default safety rules
        val existingRules = db.safetyRuleDao().getAllRules().firstOrNull()
        if (existingRules.isNullOrEmpty()) {
            val defaults = listOf(
                "Confirm it's you",
                "security check",
                "captcha",
                "Log in",
                "Enter the code"
            ).map { SafetyRuleEntity(phrase = it, isEnabled = true) }
            db.safetyRuleDao().insertRules(defaults)
        }

        // Seed default Facebook Story script
        val existingScripts = db.scriptDao().getAllScripts().firstOrNull()
        if (existingScripts.isNullOrEmpty()) {
            val defaultFbStorySteps = listOf(
                ScriptStep(
                    type = StepType.LAUNCH_APP,
                    packageName = "com.facebook.katana"
                ),
                ScriptStep(
                    type = StepType.SLEEP,
                    sleepMs = 2500L
                ),
                ScriptStep(
                    type = StepType.WAIT_TEXT,
                    text = "Create story",
                    exact = false,
                    timeoutMs = 12000L
                ),
                ScriptStep(
                    type = StepType.TAP_TEXT,
                    text = "Create story",
                    exact = false
                ),
                ScriptStep(
                    type = StepType.SLEEP,
                    sleepMs = 2000L
                ),
                ScriptStep(
                    type = StepType.PICK_NEWEST_VIDEO
                ),
                ScriptStep(
                    type = StepType.SLEEP,
                    sleepMs = 2000L
                ),
                ScriptStep(
                    type = StepType.WAIT_TEXT,
                    text = "See more",
                    exact = false,
                    timeoutMs = 6000L
                ),
                ScriptStep(
                    type = StepType.TAP_TEXT,
                    text = "See more",
                    exact = false
                ),
                ScriptStep(
                    type = StepType.WAIT_TEXT,
                    text = "Link",
                    exact = false,
                    timeoutMs = 6000L
                ),
                ScriptStep(
                    type = StepType.TAP_TEXT,
                    text = "Link",
                    exact = false
                ),
                ScriptStep(
                    type = StepType.WAIT_TEXT,
                    text = "Add link",
                    exact = false,
                    timeoutMs = 5000L
                ),
                ScriptStep(
                    type = StepType.TYPE_TEXT,
                    text = "{{LINK}}"
                ),
                ScriptStep(
                    type = StepType.TAP_TEXT,
                    text = "Add",
                    exact = true
                ),
                ScriptStep(
                    type = StepType.SLEEP,
                    sleepMs = 1500L
                ),
                ScriptStep(
                    type = StepType.TAP_TEXT,
                    text = "Share",
                    exact = false
                ),
                ScriptStep(
                    type = StepType.SLEEP,
                    sleepMs = 4000L
                ),
                ScriptStep(
                    type = StepType.SCREENSHOT,
                    screenshotName = "story_posted_{{PAGE_NAME}}"
                )
            )

            db.scriptDao().insertScript(
                ScriptEntity(
                    name = "Facebook Story with Link",
                    description = "Automates opening Facebook, choosing newest video, adding custom story link, and tapping Share for {{PAGE_NAME}}.",
                    stepsJson = JsonCodec.stepsToJson(defaultFbStorySteps),
                    isTemplate = true
                )
            )
        }
    }
}
