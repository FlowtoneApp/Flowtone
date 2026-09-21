package ink.tenqui.flowtone.app

/** A screen requests this app-level window; the Scaffold owns its full-viewport presentation. */
internal data class ExtensionDiscardChangesConfirmation(
    val onKeepEditing: () -> Unit,
    val onDiscard: () -> Unit
)
