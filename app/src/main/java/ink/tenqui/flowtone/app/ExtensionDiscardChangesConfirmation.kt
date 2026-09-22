package ink.tenqui.flowtone.app

/** A screen requests this app-level window; the Scaffold owns its full-viewport presentation. */
internal data class ExtensionDiscardChangesConfirmation(
    val title: String = "放弃未保存的更改？",
    val message: String = "未保存的配置更改将会丢失。",
    val keepLabel: String = "继续编辑",
    val discardLabel: String = "放弃更改",
    val onKeepEditing: () -> Unit,
    val onDiscard: () -> Unit
)
