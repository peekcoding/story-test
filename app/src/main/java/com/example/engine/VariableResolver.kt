package com.example.engine

import com.example.data.model.ScriptStep

object VariableResolver {

    const val VAR_PAGE_NAME = "{{PAGE_NAME}}"
    const val VAR_LINK = "{{LINK}}"

    fun resolve(text: String, pageName: String, link: String): String {
        return text
            .replace(VAR_PAGE_NAME, pageName)
            .replace(VAR_LINK, link)
    }

    fun resolveStep(step: ScriptStep, pageName: String, link: String): ScriptStep {
        return step.copy(
            text = resolve(step.text, pageName, link),
            viewId = resolve(step.viewId, pageName, link),
            screenshotName = resolve(step.screenshotName, pageName, link),
            packageName = resolve(step.packageName, pageName, link)
        )
    }
}
