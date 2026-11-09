package com.example.engine

import com.example.data.model.ScriptStep

object VariableResolver {

    const val VAR_PAGE_NAME = "{{PAGE_NAME}}"
    const val VAR_LINK = "{{LINK}}"
    const val VAR_MODEL_CODE = "{{MODEL_CODE}}"

    fun resolve(text: String, pageName: String, link: String, modelCode: String = ""): String {
        return text
            .replace(VAR_PAGE_NAME, pageName)
            .replace(VAR_LINK, link)
            .replace(VAR_MODEL_CODE, modelCode)
    }

    fun resolveStep(step: ScriptStep, pageName: String, link: String, modelCode: String = ""): ScriptStep {
        return step.copy(
            text = resolve(step.text, pageName, link, modelCode),
            viewId = resolve(step.viewId, pageName, link, modelCode),
            screenshotName = resolve(step.screenshotName, pageName, link, modelCode),
            packageName = resolve(step.packageName, pageName, link, modelCode)
        )
    }
}
