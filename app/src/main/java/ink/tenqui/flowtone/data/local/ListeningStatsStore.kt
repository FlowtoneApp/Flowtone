package ink.tenqui.flowtone.data.local

import android.content.Context
import android.content.SharedPreferences
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import java.time.LocalDate

class ListeningStatsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getSnapshot(today: LocalDate = LocalDate.now()): ListeningStatsSnapshot {
        resetTodayIfNeeded(today)
        return readSnapshot(today)
    }

    fun recordSongPlayed(
        song: Song,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot {
        resetTodayIfNeeded(today)

        val todayEpochDay = today.toEpochDay()
        val nextSongCount = preferences.getInt(KEY_TODAY_SONG_COUNT, 0) + 1
        val updatedAtMillis = System.currentTimeMillis()

        preferences.edit()
            .putLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
            .putInt(KEY_TODAY_SONG_COUNT, nextSongCount)
            .putLong(KEY_LAST_PLAYED_SONG_ID, song.id)
            .putString(KEY_LAST_PLAYED_SONG_TITLE, song.title)
            .putLong(KEY_UPDATED_AT_MILLIS, updatedAtMillis)
            .apply()

        return readSnapshot(today)
    }

    fun addListeningDuration(
        durationMs: Long,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot {
        if (durationMs <= 0L) {
            return getSnapshot(today)
        }

        resetTodayIfNeeded(today)

        val nextDuration = preferences.getLong(KEY_TOTAL_LISTENING_DURATION_MS, 0L)
            .coerceAtLeast(0L) + durationMs
        val updatedAtMillis = System.currentTimeMillis()

        preferences.edit()
            .putLong(KEY_TOTAL_LISTENING_DURATION_MS, nextDuration)
            .putLong(KEY_UPDATED_AT_MILLIS, updatedAtMillis)
            .apply()

        return readSnapshot(today)
    }

    fun replaceWith(snapshot: ListeningStatsSnapshot): ListeningStatsSnapshot {
        preferences.edit()
            .putLong(KEY_TODAY_EPOCH_DAY, snapshot.todayEpochDay)
            .putInt(KEY_TODAY_SONG_COUNT, snapshot.todaySongCount.coerceAtLeast(0))
            .putLong(
                KEY_TOTAL_LISTENING_DURATION_MS,
                snapshot.totalListeningDurationMs.coerceAtLeast(0L)
            )
            .putNullableLong(KEY_LAST_PLAYED_SONG_ID, snapshot.lastPlayedSongId)
            .putNullableString(KEY_LAST_PLAYED_SONG_TITLE, snapshot.lastPlayedSongTitle)
            .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
            .apply()

        return getSnapshot()
    }

    private fun resetTodayIfNeeded(today: LocalDate) {
        val todayEpochDay = today.toEpochDay()
        val savedEpochDay = preferences.getLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
        if (preferences.contains(KEY_TODAY_EPOCH_DAY) && savedEpochDay == todayEpochDay) {
            return
        }

        preferences.edit()
            .putLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
            .putInt(KEY_TODAY_SONG_COUNT, 0)
            .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
            .apply()
    }

    private fun readSnapshot(today: LocalDate): ListeningStatsSnapshot {
        val todayEpochDay = today.toEpochDay()
        return ListeningStatsSnapshot(
            todayEpochDay = preferences.getLong(KEY_TODAY_EPOCH_DAY, todayEpochDay),
            todaySongCount = preferences.getInt(KEY_TODAY_SONG_COUNT, 0).coerceAtLeast(0),
            totalListeningDurationMs = preferences.getLong(
                KEY_TOTAL_LISTENING_DURATION_MS,
                0L
            ).coerceAtLeast(0L),
            lastPlayedSongId = preferences.getNullableLong(KEY_LAST_PLAYED_SONG_ID),
            lastPlayedSongTitle = preferences.getString(KEY_LAST_PLAYED_SONG_TITLE, null),
            updatedAtMillis = preferences.getLong(KEY_UPDATED_AT_MILLIS, 0L)
        )
    }

    private fun SharedPreferences.Editor.putNullableLong(
        key: String,
        value: Long?
    ): SharedPreferences.Editor {
        return if (value == null) {
            remove(key)
        } else {
            putLong(key, value)
        }
    }

    private fun SharedPreferences.Editor.putNullableString(
        key: String,
        value: String?
    ): SharedPreferences.Editor {
        return if (value == null) {
            remove(key)
        } else {
            putString(key, value)
        }
    }

    private fun SharedPreferences.getNullableLong(key: String): Long? {
        return if (contains(key)) {
            getLong(key, 0L)
        } else {
            null
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "flowtone_listening_stats"
        const val KEY_TODAY_EPOCH_DAY = "today_epoch_day"
        const val KEY_TODAY_SONG_COUNT = "today_song_count"
        const val KEY_TOTAL_LISTENING_DURATION_MS = "total_listening_duration_ms"
        const val KEY_LAST_PLAYED_SONG_ID = "last_played_song_id"
        const val KEY_LAST_PLAYED_SONG_TITLE = "last_played_song_title"
        const val KEY_UPDATED_AT_MILLIS = "updated_at_millis"
    }
}
