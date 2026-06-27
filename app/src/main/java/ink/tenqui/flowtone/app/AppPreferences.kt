package ink.tenqui.flowtone.app

import android.content.Context

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

    private companion object {
        const val DEFAULT_START_PAGE_KEY = "default_start_page"
        const val HOME_VALUE = "home"
        const val LIBRARY_VALUE = "library"
        const val MINE_VALUE = "mine"
    }
}
