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

internal const val EXTRA_PLAYBACK_ORDER_MODE =
    "ink.tenqui.flowtone.extra.PLAYBACK_ORDER_MODE"

internal const val EXTRA_SHUFFLE_ORDER_INDICES =
    "ink.tenqui.flowtone.extra.SHUFFLE_ORDER_INDICES"

