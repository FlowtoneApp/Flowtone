package ink.tenqui.flowtone.data

import android.content.Context
import ink.tenqui.flowtone.playback.PlaybackOrderMode

class PlaybackSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getPlaybackOrderMode(): PlaybackOrderMode {
        val savedValue = preferences.getString(KEY_PLAYBACK_ORDER_MODE, null)
            ?: return PlaybackOrderMode.Sequence

        return runCatching {
            PlaybackOrderMode.valueOf(savedValue)
        }.getOrDefault(PlaybackOrderMode.Sequence)
    }

    fun setPlaybackOrderMode(mode: PlaybackOrderMode) {
        preferences.edit()
            .putString(KEY_PLAYBACK_ORDER_MODE, mode.name)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "flowtone_playback_settings"
        const val KEY_PLAYBACK_ORDER_MODE = "playback_order_mode"
    }
}
