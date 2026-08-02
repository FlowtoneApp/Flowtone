package ink.tenqui.flowtone.lyrics

import android.content.Context
import android.net.Uri

/** Persists the user-selected folder whose sidecar .lrc files Flowtone may read. */
class LyricsDirectoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getTreeUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun saveTreeUri(uri: Uri) {
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "lyrics_directory"
        const val KEY_TREE_URI = "tree_uri"
    }
}
