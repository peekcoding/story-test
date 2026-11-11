package com.example.data.model

data class NodeBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun contains(x: Int, y: Int): Boolean {
        return x in left..right && y in top..bottom
    }
}

data class ScreenNode(
    val id: String = "",
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdResourceName: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val scrollable: Boolean = false,
    /** Shown on screen (Android's isVisibleToUser). Bars Facebook hid by scrolling are in the tree but not visible. */
    val visible: Boolean = true,
    val bounds: NodeBounds = NodeBounds(),
    val children: List<ScreenNode> = emptyList()
) {
    fun flatten(): List<ScreenNode> {
        val list = mutableListOf<ScreenNode>()
        list.add(this)
        for (child in children) {
            list.addAll(child.flatten())
        }
        return list
    }
}
