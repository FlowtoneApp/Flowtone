package ink.tenqui.flowtone.ui.components

internal enum class SelectionGroupPosition {
    None,
    Single,
    Top,
    Middle,
    Bottom
}

internal fun selectionGroupPosition(
    isSelected: Boolean,
    isPreviousSelected: Boolean,
    isNextSelected: Boolean
): SelectionGroupPosition {
    if (!isSelected) return SelectionGroupPosition.None

    return when {
        isPreviousSelected && isNextSelected -> SelectionGroupPosition.Middle
        isPreviousSelected -> SelectionGroupPosition.Bottom
        isNextSelected -> SelectionGroupPosition.Top
        else -> SelectionGroupPosition.Single
    }
}

internal val SelectionGroupPosition.connectsTop: Boolean
    get() = this == SelectionGroupPosition.Middle || this == SelectionGroupPosition.Bottom

internal val SelectionGroupPosition.connectsBottom: Boolean
    get() = this == SelectionGroupPosition.Top || this == SelectionGroupPosition.Middle
