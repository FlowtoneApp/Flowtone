package ink.tenqui.flowtone.playback

enum class PlaybackOrderMode {
    Sequence,
    RepeatOne,
    Shuffle
}

internal const val ACTION_TOGGLE_PLAYBACK_ORDER =
    "ink.tenqui.flowtone.action.TOGGLE_PLAYBACK_ORDER"

internal const val ACTION_SET_PLAYBACK_ORDER =
    "ink.tenqui.flowtone.action.SET_PLAYBACK_ORDER"

internal const val ACTION_TOGGLE_LIKED =
    "ink.tenqui.flowtone.action.TOGGLE_LIKED"

internal const val ACTION_ADD_PROVIDER_NEXT = "ink.tenqui.flowtone.action.ADD_PROVIDER_NEXT"
internal const val EXTRA_PROVIDER_QUEUE_ITEMS = "ink.tenqui.flowtone.extra.PROVIDER_QUEUE_ITEMS"

internal const val EXTRA_PLAYBACK_ORDER_MODE =
    "ink.tenqui.flowtone.extra.PLAYBACK_ORDER_MODE"

