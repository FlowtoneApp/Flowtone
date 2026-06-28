package ink.tenqui.flowtone.app

import android.content.Context
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

    private companion object {
        const val DEFAULT_START_PAGE_KEY = "default_start_page"
        const val THEME_MODE_KEY = "theme_mode"
        const val HIDE_SECONDARY_BACK_BUTTON_KEY = "hide_secondary_back_button"
        const val RESUME_PLAYBACK_AFTER_CALL_KEY = "resume_playback_after_call"
        const val HOME_VALUE = "home"
        const val LIBRARY_VALUE = "library"
        const val MINE_VALUE = "mine"
        const val FOLLOW_SYSTEM_VALUE = "follow_system"
        const val LIGHT_VALUE = "light"
        const val DARK_VALUE = "dark"
    }
}
