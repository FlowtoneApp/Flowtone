package ink.tenqui.flowtone.app

import android.content.Context
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.theme.AppThemeMode

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(
        "flowtone_preferences",
        Context.MODE_PRIVATE
    )

    fun getDefaultStartPage(): TopLevelPage {
        return when (prefs.getString(DEFAULT_START_PAGE_KEY, HOME_VALUE)) {
            LIBRARY_VALUE -> TopLevelPage.Library
            MINE_VALUE -> TopLevelPage.Mine
            else -> TopLevelPage.Home
        }
    }

    fun setDefaultStartPage(page: TopLevelPage) {
        val value = when (page) {
            TopLevelPage.Home -> HOME_VALUE
            TopLevelPage.Library -> LIBRARY_VALUE
            TopLevelPage.Mine -> MINE_VALUE
        }

        prefs.edit()
            .putString(DEFAULT_START_PAGE_KEY, value)
            .apply()
    }

    fun getThemeMode(): AppThemeMode {
        return when (prefs.getString(THEME_MODE_KEY, FOLLOW_SYSTEM_VALUE)) {
            LIGHT_VALUE -> AppThemeMode.Light
            DARK_VALUE -> AppThemeMode.Dark
            else -> AppThemeMode.FollowSystem
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        val value = when (mode) {
            AppThemeMode.FollowSystem -> FOLLOW_SYSTEM_VALUE
            AppThemeMode.Light -> LIGHT_VALUE
            AppThemeMode.Dark -> DARK_VALUE
        }

        prefs.edit()
            .putString(THEME_MODE_KEY, value)
            .apply()
    }

    fun shouldDisablePausedArtworkTilt(): Boolean {
        return prefs.getBoolean(DISABLE_PAUSED_ARTWORK_TILT_KEY, false)
    }

    fun setDisablePausedArtworkTilt(disable: Boolean) {
        prefs.edit()
            .putBoolean(DISABLE_PAUSED_ARTWORK_TILT_KEY, disable)
            .apply()
    }

    fun shouldHideSecondaryBackButton(): Boolean {
        return prefs.getBoolean(HIDE_SECONDARY_BACK_BUTTON_KEY, false)
    }

    fun setHideSecondaryBackButton(hide: Boolean) {
        prefs.edit()
            .putBoolean(HIDE_SECONDARY_BACK_BUTTON_KEY, hide)
            .apply()
    }

    fun shouldResumePlaybackAfterCall(): Boolean {
        return prefs.getBoolean(RESUME_PLAYBACK_AFTER_CALL_KEY, false)
    }

    fun setResumePlaybackAfterCall(resume: Boolean) {
        prefs.edit()
            .putBoolean(RESUME_PLAYBACK_AFTER_CALL_KEY, resume)
            .apply()
    }

    fun shouldAllowFullscreenFromCollapsed(): Boolean {
        return prefs.getBoolean(ALLOW_FULLSCREEN_FROM_COLLAPSED_KEY, false)
    }

    fun setAllowFullscreenFromCollapsed(allow: Boolean) {
        prefs.edit()
            .putBoolean(ALLOW_FULLSCREEN_FROM_COLLAPSED_KEY, allow)
            .apply()
    }

    fun getSongMetadataPreloadCount(): Int {
        val savedValue = prefs.getInt(SONG_METADATA_PRELOAD_COUNT_KEY, DEFAULT_PRELOAD_COUNT)
        return PRELOAD_COUNT_OPTIONS.minBy { option ->
            kotlin.math.abs(option - savedValue)
        }
    }

    fun setSongMetadataPreloadCount(count: Int) {
        val value = PRELOAD_COUNT_OPTIONS.minBy { option ->
            kotlin.math.abs(option - count)
        }
        prefs.edit()
            .putInt(SONG_METADATA_PRELOAD_COUNT_KEY, value)
            .apply()
    }

    fun getSongRecordThresholdSeconds(): Int {
        return prefs.getInt(
            SONG_RECORD_THRESHOLD_SECONDS_KEY,
            DEFAULT_SONG_RECORD_THRESHOLD_SECONDS
        ).coerceIn(
            MIN_SONG_RECORD_THRESHOLD_SECONDS,
            MAX_SONG_RECORD_THRESHOLD_SECONDS
        )
    }

    fun setSongRecordThresholdSeconds(seconds: Int) {
        prefs.edit()
            .putInt(
                SONG_RECORD_THRESHOLD_SECONDS_KEY,
                seconds.coerceIn(
                    MIN_SONG_RECORD_THRESHOLD_SECONDS,
                    MAX_SONG_RECORD_THRESHOLD_SECONDS
                )
            )
            .apply()
    }

    fun getPlaybackQueueDisplayOrder(): QueueDisplayOrder {
        val savedValue = prefs.getString(PLAYBACK_QUEUE_DISPLAY_ORDER_KEY, null)
            ?: return QueueDisplayOrder.PlaybackOrder

        return runCatching {
            QueueDisplayOrder.valueOf(savedValue)
        }.getOrDefault(QueueDisplayOrder.PlaybackOrder)
    }

    fun setPlaybackQueueDisplayOrder(order: QueueDisplayOrder) {
        prefs.edit()
            .putString(PLAYBACK_QUEUE_DISPLAY_ORDER_KEY, order.name)
            .apply()
    }

    private companion object {
        const val DEFAULT_START_PAGE_KEY = "default_start_page"
        const val THEME_MODE_KEY = "theme_mode"
        const val DISABLE_PAUSED_ARTWORK_TILT_KEY = "disable_paused_artwork_tilt"
        const val HIDE_SECONDARY_BACK_BUTTON_KEY = "hide_secondary_back_button"
        const val RESUME_PLAYBACK_AFTER_CALL_KEY = "resume_playback_after_call"
        const val ALLOW_FULLSCREEN_FROM_COLLAPSED_KEY = "allow_fullscreen_from_collapsed"
        const val SONG_METADATA_PRELOAD_COUNT_KEY = "song_metadata_preload_count"
        const val SONG_RECORD_THRESHOLD_SECONDS_KEY = "song_record_threshold_seconds"
        const val PLAYBACK_QUEUE_DISPLAY_ORDER_KEY = "playback_queue_display_order"
        const val DEFAULT_PRELOAD_COUNT = 5
        const val DEFAULT_SONG_RECORD_THRESHOLD_SECONDS = 30
        const val MIN_SONG_RECORD_THRESHOLD_SECONDS = 1
        const val MAX_SONG_RECORD_THRESHOLD_SECONDS = 60
        const val HOME_VALUE = "home"
        const val LIBRARY_VALUE = "library"
        const val MINE_VALUE = "mine"
        const val FOLLOW_SYSTEM_VALUE = "follow_system"
        const val LIGHT_VALUE = "light"
        const val DARK_VALUE = "dark"
        val PRELOAD_COUNT_OPTIONS = listOf(1, 3, 5, 7, 10)
    }
}
