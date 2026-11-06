package com.example.data

import com.example.data.model.NodeBounds
import com.example.data.model.ScreenNode
import com.example.data.model.ScriptStep
import com.example.data.model.StepType
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {

    fun stepsToJson(steps: List<ScriptStep>): String {
        val array = JSONArray()
        for (step in steps) {
            val obj = JSONObject().apply {
                put("id", step.id)
                put("type", step.type.name)
                put("text", step.text)
                put("exact", step.exact)
                put("index", step.index)
                put("viewId", step.viewId)
                put("xPercent", step.xPercent.toDouble())
                put("yPercent", step.yPercent.toDouble())
                put("timeoutMs", step.timeoutMs)
                put("sleepMs", step.sleepMs)
                put("screenshotName", step.screenshotName)
                put("packageName", step.packageName)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun jsonToSteps(json: String?): List<ScriptStep> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ScriptStep>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeName = obj.optString("type", StepType.WAIT_TEXT.name)
                val type = try {
                    StepType.valueOf(typeName)
                } catch (e: Exception) {
                    StepType.WAIT_TEXT
                }
                list.add(
                    ScriptStep(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        type = type,
                        text = obj.optString("text", ""),
                        exact = obj.optBoolean("exact", false),
                        index = obj.optInt("index", 0),
                        viewId = obj.optString("viewId", ""),
                        xPercent = obj.optDouble("xPercent", 50.0).toFloat(),
                        yPercent = obj.optDouble("yPercent", 50.0).toFloat(),
                        timeoutMs = obj.optLong("timeoutMs", 10000L),
                        sleepMs = obj.optLong("sleepMs", 1000L),
                        screenshotName = obj.optString("screenshotName", ""),
                        packageName = obj.optString("packageName", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun screenNodeToJson(node: ScreenNode): String {
        return nodeToJsonObject(node).toString(2)
    }

    fun nodeToJsonObject(node: ScreenNode): JSONObject {
        val obj = JSONObject()
        obj.put("id", node.id)
        obj.put("packageName", node.packageName ?: JSONObject.NULL)
        obj.put("className", node.className ?: JSONObject.NULL)
        obj.put("text", node.text ?: JSONObject.NULL)
        obj.put("contentDescription", node.contentDescription ?: JSONObject.NULL)
        obj.put("viewIdResourceName", node.viewIdResourceName ?: JSONObject.NULL)
        obj.put("clickable", node.clickable)
        obj.put("enabled", node.enabled)
        obj.put("focused", node.focused)
        obj.put("scrollable", node.scrollable)

        val boundsObj = JSONObject().apply {
            put("left", node.bounds.left)
            put("top", node.bounds.top)
            put("right", node.bounds.right)
            put("bottom", node.bounds.bottom)
            put("centerX", node.bounds.centerX)
            put("centerY", node.bounds.centerY)
            put("width", node.bounds.width)
            put("height", node.bounds.height)
        }
        obj.put("bounds", boundsObj)

        val childrenArray = JSONArray()
        for (child in node.children) {
            childrenArray.put(nodeToJsonObject(child))
        }
        obj.put("children", childrenArray)
        return obj
    }

    fun jsonToScreenNode(json: String): ScreenNode? {
        return try {
            jsonObjectToNode(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonObjectToNode(obj: JSONObject): ScreenNode {
        val boundsObj = obj.optJSONObject("bounds")
        val bounds = if (boundsObj != null) {
            NodeBounds(
                left = boundsObj.optInt("left", 0),
                top = boundsObj.optInt("top", 0),
                right = boundsObj.optInt("right", 0),
                bottom = boundsObj.optInt("bottom", 0)
            )
        } else {
            NodeBounds()
        }

        val children = mutableListOf<ScreenNode>()
        val childrenArray = obj.optJSONArray("children")
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                val childObj = childrenArray.optJSONObject(i)
                if (childObj != null) {
                    children.add(jsonObjectToNode(childObj))
                }
            }
        }

        return ScreenNode(
            id = obj.optString("id", ""),
            text = if (obj.isNull("text")) null else obj.optString("text"),
            contentDescription = if (obj.isNull("contentDescription")) null else obj.optString("contentDescription"),
            viewIdResourceName = if (obj.isNull("viewIdResourceName")) null else obj.optString("viewIdResourceName"),
            className = if (obj.isNull("className")) null else obj.optString("className"),
            packageName = if (obj.isNull("packageName")) null else obj.optString("packageName"),
            clickable = obj.optBoolean("clickable", false),
            enabled = obj.optBoolean("enabled", true),
            focused = obj.optBoolean("focused", false),
            scrollable = obj.optBoolean("scrollable", false),
            bounds = bounds,
            children = children
        )
    }
}
