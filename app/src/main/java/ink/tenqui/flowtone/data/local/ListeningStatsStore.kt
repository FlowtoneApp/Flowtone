package ink.tenqui.flowtone.data.local

import android.content.Context
import android.content.SharedPreferences
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningPeriodStats
import ink.tenqui.flowtone.data.listening.ListeningSongStats
import ink.tenqui.flowtone.data.listening.ListeningSourceStats
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.listening.rankListeningStats
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackSourceType
import java.time.LocalDate
import java.util.Base64

class ListeningStatsStore(context: Context) {
    private val lock = Any()
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getSnapshot(today: LocalDate = LocalDate.now()): ListeningStatsSnapshot =
        synchronized(lock) {
            ensureReadyFor(today)
            readSnapshot(today)
        }

    fun recordSongPlayed(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot {
        return recordEffectivePlay(
            song = song,
            source = source,
            today = today,
            includeTotal = true,
            includeToday = true
        )
    }

    fun recordEffectivePlay(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        today: LocalDate = LocalDate.now(),
        includeTotal: Boolean,
        includeToday: Boolean
    ): ListeningStatsSnapshot = synchronized(lock) {
        ensureReadyFor(today)
        if (!includeTotal && !includeToday) {
            return@synchronized readSnapshot(today)
        }

        val todayEpochDay = today.toEpochDay()
        val songKey = song.listeningStatsSongKey()
        val safeSource = source.safeSource()
        val allSongKeys = preferences.getStringSetCopy(KEY_SONG_KEYS).toMutableSet()
        val todaySongKeys = preferences.getStringSetCopy(KEY_TODAY_SONG_KEYS).toMutableSet()
        val allSourceKeys = preferences.getStringSetCopy(KEY_SOURCE_KEYS).toMutableSet()
        val todaySourceKeys = preferences.getStringSetCopy(KEY_TODAY_SOURCE_KEYS).toMutableSet()
        val addedSong = allSongKeys.add(songKey)
        todaySongKeys.add(songKey)
        val addedSource = allSourceKeys.add(safeSource.key)
        todaySourceKeys.add(safeSource.key)

        val updatedAtMillis = System.currentTimeMillis()
        val editor = preferences.edit()
        editor.putLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
            .putStringSet(KEY_SONG_KEYS, allSongKeys)
            .putStringSet(KEY_TODAY_SONG_KEYS, todaySongKeys)
            .putStringSet(KEY_SOURCE_KEYS, allSourceKeys)
            .putStringSet(KEY_TODAY_SOURCE_KEYS, todaySourceKeys)
            .putInt(KEY_TODAY_DISTINCT_SONG_COUNT, todaySongKeys.size)
            .putInt(KEY_TOTAL_DISTINCT_SONG_COUNT, allSongKeys.size)
            .putLong(KEY_LAST_PLAYED_SONG_ID, song.id)
            .putString(KEY_LAST_PLAYED_SONG_TITLE, song.title)
            .putLong(KEY_UPDATED_AT_MILLIS, updatedAtMillis)
        if (includeToday) {
            editor.putInt(
                KEY_TODAY_EFFECTIVE_PLAY_COUNT,
                preferences.getInt(KEY_TODAY_EFFECTIVE_PLAY_COUNT, 0).coerceAtLeast(0) + 1
            )
        }
        if (includeTotal) {
            editor.putInt(
                KEY_TOTAL_EFFECTIVE_PLAY_COUNT,
                preferences.getInt(KEY_TOTAL_EFFECTIVE_PLAY_COUNT, 0).coerceAtLeast(0) + 1
            )
        }

        writeSongSnapshot(editor, songKey, song, addedSong)
        writeSourceSnapshot(editor, safeSource, addedSource)
        if (includeToday) {
            editor.incrementInt(songPrefix(songKey) + KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT)
                .incrementInt(sourcePrefix(safeSource.key) + KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT)
        }
        if (includeTotal) {
            editor.incrementInt(songPrefix(songKey) + KEY_PART_TOTAL_EFFECTIVE_PLAY_COUNT)
                .incrementInt(sourcePrefix(safeSource.key) + KEY_PART_TOTAL_EFFECTIVE_PLAY_COUNT)
        }
        editor.apply()

        readSnapshot(today)
    }

    fun addListeningDuration(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        durationMs: Long,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot = synchronized(lock) {
        if (durationMs <= 0L) {
            ensureReadyFor(today)
            return@synchronized readSnapshot(today)
        }

        ensureReadyFor(today)

        val songKey = song.listeningStatsSongKey()
        val safeSource = source.safeSource()
        val allSongKeys = preferences.getStringSetCopy(KEY_SONG_KEYS).toMutableSet()
        val todaySongKeys = preferences.getStringSetCopy(KEY_TODAY_SONG_KEYS).toMutableSet()
        val allSourceKeys = preferences.getStringSetCopy(KEY_SOURCE_KEYS).toMutableSet()
        val todaySourceKeys = preferences.getStringSetCopy(KEY_TODAY_SOURCE_KEYS).toMutableSet()
        val addedSong = allSongKeys.add(songKey)
        todaySongKeys.add(songKey)
        val addedSource = allSourceKeys.add(safeSource.key)
        todaySourceKeys.add(safeSource.key)

        val updatedAtMillis = System.currentTimeMillis()
        val editor = preferences.edit()
        editor.putStringSet(KEY_SONG_KEYS, allSongKeys)
            .putStringSet(KEY_TODAY_SONG_KEYS, todaySongKeys)
            .putStringSet(KEY_SOURCE_KEYS, allSourceKeys)
            .putStringSet(KEY_TODAY_SOURCE_KEYS, todaySourceKeys)
            .putInt(KEY_TODAY_DISTINCT_SONG_COUNT, todaySongKeys.size)
            .putInt(KEY_TOTAL_DISTINCT_SONG_COUNT, allSongKeys.size)
            .putLong(
                KEY_TODAY_LISTENING_DURATION_MS,
                preferences.getLong(KEY_TODAY_LISTENING_DURATION_MS, 0L)
                    .coerceAtLeast(0L) + durationMs
            )
            .putLong(
                KEY_TOTAL_LISTENING_DURATION_MS,
                preferences.getLong(KEY_TOTAL_LISTENING_DURATION_MS, 0L)
                    .coerceAtLeast(0L) + durationMs
            )
            .putLong(KEY_UPDATED_AT_MILLIS, updatedAtMillis)

        writeSongSnapshot(editor, songKey, song, addedSong)
        writeSourceSnapshot(editor, safeSource, addedSource)
        editor.incrementLong(songPrefix(songKey) + KEY_PART_TODAY_LISTENING_DURATION_MS, durationMs)
            .incrementLong(songPrefix(songKey) + KEY_PART_TOTAL_LISTENING_DURATION_MS, durationMs)
            .incrementLong(sourcePrefix(safeSource.key) + KEY_PART_TODAY_LISTENING_DURATION_MS, durationMs)
            .incrementLong(sourcePrefix(safeSource.key) + KEY_PART_TOTAL_LISTENING_DURATION_MS, durationMs)
            .apply()

        readSnapshot(today)
    }

    fun addListeningDuration(
        durationMs: Long,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot = synchronized(lock) {
        if (durationMs <= 0L) {
            ensureReadyFor(today)
            return@synchronized readSnapshot(today)
        }

        ensureReadyFor(today)
        preferences.edit()
            .putLong(
                KEY_TOTAL_LISTENING_DURATION_MS,
                preferences.getLong(KEY_TOTAL_LISTENING_DURATION_MS, 0L)
                    .coerceAtLeast(0L) + durationMs
            )
            .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
            .apply()
        readSnapshot(today)
    }

    fun replaceWith(snapshot: ListeningStatsSnapshot): ListeningStatsSnapshot =
        synchronized(lock) {
            ensureReadyFor(LocalDate.now())
            preferences.edit()
                .putLong(KEY_TODAY_EPOCH_DAY, snapshot.todayEpochDay)
                .putInt(
                    KEY_TODAY_EFFECTIVE_PLAY_COUNT,
                    snapshot.today.effectivePlayCount.coerceAtLeast(0)
                )
                .putLong(
                    KEY_TODAY_LISTENING_DURATION_MS,
                    snapshot.today.listeningDurationMs.coerceAtLeast(0L)
                )
                .putInt(
                    KEY_TOTAL_EFFECTIVE_PLAY_COUNT,
                    snapshot.total.effectivePlayCount.coerceAtLeast(0)
                )
                .putLong(
                    KEY_TOTAL_LISTENING_DURATION_MS,
                    snapshot.total.listeningDurationMs.coerceAtLeast(0L)
                )
                .putNullableLong(KEY_LAST_PLAYED_SONG_ID, snapshot.lastPlayedSongId)
                .putNullableString(KEY_LAST_PLAYED_SONG_TITLE, snapshot.lastPlayedSongTitle)
                .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
                .apply()
            getSnapshot()
        }

    private fun ensureReadyFor(today: LocalDate) {
        migrateIfNeeded(today)
        resetTodayIfNeeded(today)
    }

    private fun migrateIfNeeded(today: LocalDate) {
        val savedVersion = preferences.getInt(KEY_SCHEMA_VERSION, 0)
        if (savedVersion >= CURRENT_SCHEMA_VERSION) {
            return
        }

        val todayEpochDay = today.toEpochDay()
        val oldSavedEpochDay = preferences.getLong(OLD_KEY_TODAY_EPOCH_DAY, todayEpochDay)
        val oldTodayCount = if (oldSavedEpochDay == todayEpochDay) {
            preferences.getInt(OLD_KEY_TODAY_SONG_COUNT, 0).coerceAtLeast(0)
        } else {
            0
        }
        val oldTotalDurationMs = preferences.getLong(
            OLD_KEY_TOTAL_LISTENING_DURATION_MS,
            0L
        ).coerceAtLeast(0L)
        val hasLegacyData = preferences.contains(OLD_KEY_TODAY_SONG_COUNT) ||
            preferences.contains(OLD_KEY_TOTAL_LISTENING_DURATION_MS) ||
            preferences.contains(OLD_KEY_LAST_PLAYED_SONG_ID) ||
            preferences.contains(OLD_KEY_LAST_PLAYED_SONG_TITLE)
        val startedAtMillis = System.currentTimeMillis()

        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .putLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
            .putInt(KEY_TODAY_EFFECTIVE_PLAY_COUNT, oldTodayCount)
            .putInt(KEY_TODAY_DISTINCT_SONG_COUNT, 0)
            .putLong(KEY_TODAY_LISTENING_DURATION_MS, 0L)
            .putInt(KEY_TOTAL_EFFECTIVE_PLAY_COUNT, oldTodayCount)
            .putInt(KEY_TOTAL_DISTINCT_SONG_COUNT, 0)
            .putLong(KEY_TOTAL_LISTENING_DURATION_MS, oldTotalDurationMs)
            .putLong(KEY_DETAILED_STATS_STARTED_AT_MILLIS, startedAtMillis)
            .putBoolean(KEY_INCLUDES_LEGACY_AGGREGATE_DATA, hasLegacyData)
            .putStringSet(KEY_SONG_KEYS, emptySet())
            .putStringSet(KEY_TODAY_SONG_KEYS, emptySet())
            .putStringSet(KEY_SOURCE_KEYS, emptySet())
            .putStringSet(KEY_TODAY_SOURCE_KEYS, emptySet())
            .putLong(KEY_UPDATED_AT_MILLIS, startedAtMillis)
            .apply()
    }

    private fun resetTodayIfNeeded(today: LocalDate) {
        val todayEpochDay = today.toEpochDay()
        val savedEpochDay = preferences.getLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
        if (savedEpochDay == todayEpochDay) {
            return
        }

        val oldTodaySongKeys = preferences.getStringSetCopy(KEY_TODAY_SONG_KEYS)
        val oldTodaySourceKeys = preferences.getStringSetCopy(KEY_TODAY_SOURCE_KEYS)
        val editor = preferences.edit()
            .putLong(KEY_TODAY_EPOCH_DAY, todayEpochDay)
            .putInt(KEY_TODAY_EFFECTIVE_PLAY_COUNT, 0)
            .putInt(KEY_TODAY_DISTINCT_SONG_COUNT, 0)
            .putLong(KEY_TODAY_LISTENING_DURATION_MS, 0L)
            .putStringSet(KEY_TODAY_SONG_KEYS, emptySet())
            .putStringSet(KEY_TODAY_SOURCE_KEYS, emptySet())
            .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())

        oldTodaySongKeys.forEach { songKey ->
            editor.putInt(songPrefix(songKey) + KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT, 0)
                .putLong(songPrefix(songKey) + KEY_PART_TODAY_LISTENING_DURATION_MS, 0L)
        }
        oldTodaySourceKeys.forEach { sourceKey ->
            editor.putInt(sourcePrefix(sourceKey) + KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT, 0)
                .putLong(sourcePrefix(sourceKey) + KEY_PART_TODAY_LISTENING_DURATION_MS, 0L)
        }
        editor.apply()
    }

    private fun readSnapshot(today: LocalDate): ListeningStatsSnapshot {
        val todayEpochDay = today.toEpochDay()
        val todaySources = readSourceStats(
            keys = preferences.getStringSetCopy(KEY_TODAY_SOURCE_KEYS),
            period = StatsPeriod.Today
        )
        val totalSources = readSourceStats(
            keys = preferences.getStringSetCopy(KEY_SOURCE_KEYS),
            period = StatsPeriod.Total
        )
        val todaySongs = readSongStats(
            keys = preferences.getStringSetCopy(KEY_TODAY_SONG_KEYS),
            period = StatsPeriod.Today
        )
        val totalSongs = readSongStats(
            keys = preferences.getStringSetCopy(KEY_SONG_KEYS),
            period = StatsPeriod.Total
        )
        val todayStats = ListeningPeriodStats(
            effectivePlayCount = preferences.getInt(KEY_TODAY_EFFECTIVE_PLAY_COUNT, 0)
                .coerceAtLeast(0),
            distinctSongCount = preferences.getInt(
                KEY_TODAY_DISTINCT_SONG_COUNT,
                todaySongs.size
            ).coerceAtLeast(0),
            listeningDurationMs = preferences.getLong(KEY_TODAY_LISTENING_DURATION_MS, 0L)
                .coerceAtLeast(0L),
            topSource = todaySources.firstOrNull()
        )
        val totalStats = ListeningPeriodStats(
            effectivePlayCount = preferences.getInt(KEY_TOTAL_EFFECTIVE_PLAY_COUNT, 0)
                .coerceAtLeast(0),
            distinctSongCount = preferences.getInt(
                KEY_TOTAL_DISTINCT_SONG_COUNT,
                totalSongs.size
            ).coerceAtLeast(0),
            listeningDurationMs = preferences.getLong(KEY_TOTAL_LISTENING_DURATION_MS, 0L)
                .coerceAtLeast(0L),
            topSource = totalSources.firstOrNull()
        )

        return ListeningStatsSnapshot(
            todayEpochDay = preferences.getLong(KEY_TODAY_EPOCH_DAY, todayEpochDay),
            todaySongCount = todayStats.effectivePlayCount,
            totalListeningDurationMs = totalStats.listeningDurationMs,
            lastPlayedSongId = preferences.getNullableLong(KEY_LAST_PLAYED_SONG_ID),
            lastPlayedSongTitle = preferences.getString(KEY_LAST_PLAYED_SONG_TITLE, null),
            updatedAtMillis = preferences.getLong(KEY_UPDATED_AT_MILLIS, 0L),
            schemaVersion = preferences.getInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION),
            today = todayStats,
            total = totalStats,
            todaySources = todaySources,
            totalSources = totalSources,
            todaySongs = todaySongs,
            totalSongs = totalSongs,
            detailedStatsStartedAtMillis = preferences.getLong(
                KEY_DETAILED_STATS_STARTED_AT_MILLIS,
                0L
            ),
            includesLegacyAggregateData = preferences.getBoolean(
                KEY_INCLUDES_LEGACY_AGGREGATE_DATA,
                false
            )
        )
    }

    private fun readSongStats(
        keys: Set<String>,
        period: StatsPeriod
    ): List<ListeningSongStats> {
        return keys.mapNotNull { songKey ->
            val prefix = songPrefix(songKey)
            val countKey = when (period) {
                StatsPeriod.Today -> KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT
                StatsPeriod.Total -> KEY_PART_TOTAL_EFFECTIVE_PLAY_COUNT
            }
            val durationKey = when (period) {
                StatsPeriod.Today -> KEY_PART_TODAY_LISTENING_DURATION_MS
                StatsPeriod.Total -> KEY_PART_TOTAL_LISTENING_DURATION_MS
            }
            val count = preferences.getInt(prefix + countKey, 0).coerceAtLeast(0)
            val duration = preferences.getLong(prefix + durationKey, 0L).coerceAtLeast(0L)
            if (count <= 0 && duration <= 0L) {
                return@mapNotNull null
            }

            ListeningSongStats(
                songKey = songKey,
                songId = preferences.getLong(prefix + KEY_PART_SONG_ID, 0L),
                title = preferences.getString(prefix + KEY_PART_TITLE, null)
                    ?: "未知歌曲",
                artist = preferences.getString(prefix + KEY_PART_ARTIST, null)
                    ?: "未知艺术家",
                artworkUri = preferences.getString(prefix + KEY_PART_ARTWORK_URI, null),
                effectivePlayCount = count,
                listeningDurationMs = duration
            ) to preferences.getLong(prefix + KEY_PART_ORDER, Long.MAX_VALUE)
        }.let { items ->
            rankListeningStats(
                items = items,
                durationSelector = { it.first.listeningDurationMs },
                playCountSelector = { it.first.effectivePlayCount },
                stableOrderSelector = { it.second }
            )
        }
            .map { it.first }
    }

    private fun readSourceStats(
        keys: Set<String>,
        period: StatsPeriod
    ): List<ListeningSourceStats> {
        return keys.mapNotNull { sourceKey ->
            val prefix = sourcePrefix(sourceKey)
            val countKey = when (period) {
                StatsPeriod.Today -> KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT
                StatsPeriod.Total -> KEY_PART_TOTAL_EFFECTIVE_PLAY_COUNT
            }
            val durationKey = when (period) {
                StatsPeriod.Today -> KEY_PART_TODAY_LISTENING_DURATION_MS
                StatsPeriod.Total -> KEY_PART_TOTAL_LISTENING_DURATION_MS
            }
            val count = preferences.getInt(prefix + countKey, 0).coerceAtLeast(0)
            val duration = preferences.getLong(prefix + durationKey, 0L).coerceAtLeast(0L)
            if (count <= 0 && duration <= 0L) {
                return@mapNotNull null
            }
            val sourceType = preferences.getString(prefix + KEY_PART_SOURCE_TYPE, null)
                ?.let { runCatching { PlaybackSourceType.valueOf(it) }.getOrNull() }
                ?: PlaybackSourceType.Unknown

            ListeningSourceStats(
                sourceKey = sourceKey,
                sourceType = sourceType,
                sourceId = preferences.getString(prefix + KEY_PART_SOURCE_ID, null),
                displayName = preferences.getString(prefix + KEY_PART_DISPLAY_NAME, null)
                    ?: "未知来源",
                effectivePlayCount = count,
                listeningDurationMs = duration
            ) to preferences.getLong(prefix + KEY_PART_ORDER, Long.MAX_VALUE)
        }.let { items ->
            rankListeningStats(
                items = items,
                durationSelector = { it.first.listeningDurationMs },
                playCountSelector = { it.first.effectivePlayCount },
                stableOrderSelector = { it.second }
            )
        }
            .map { it.first }
    }

    private fun writeSongSnapshot(
        editor: SharedPreferences.Editor,
        songKey: String,
        song: Song,
        isNewSong: Boolean
    ) {
        val prefix = songPrefix(songKey)
        editor.putLong(prefix + KEY_PART_SONG_ID, song.id)
            .putString(prefix + KEY_PART_TITLE, song.title)
            .putString(prefix + KEY_PART_ARTIST, song.artist)
            .putNullableString(prefix + KEY_PART_ARTWORK_URI, song.artworkUri?.toString())
        if (isNewSong || !preferences.contains(prefix + KEY_PART_ORDER)) {
            editor.putLong(prefix + KEY_PART_ORDER, nextStableOrder())
        }
    }

    private fun writeSourceSnapshot(
        editor: SharedPreferences.Editor,
        source: PlaybackSource,
        isNewSource: Boolean
    ) {
        val prefix = sourcePrefix(source.key)
        editor.putString(prefix + KEY_PART_SOURCE_TYPE, source.type.name)
            .putNullableString(prefix + KEY_PART_SOURCE_ID, source.sourceId)
            .putString(prefix + KEY_PART_DISPLAY_NAME, source.displayName)
        if (isNewSource || !preferences.contains(prefix + KEY_PART_ORDER)) {
            editor.putLong(prefix + KEY_PART_ORDER, nextStableOrder())
        }
    }

    private fun nextStableOrder(): Long {
        val nextOrder = preferences.getLong(KEY_NEXT_STABLE_ORDER, 0L)
        preferences.edit()
            .putLong(KEY_NEXT_STABLE_ORDER, nextOrder + 1L)
            .apply()
        return nextOrder
    }

    private fun SharedPreferences.getStringSetCopy(key: String): Set<String> {
        return getStringSet(key, emptySet()).orEmpty().toSet()
    }

    private fun SharedPreferences.Editor.incrementInt(
        key: String,
        delta: Int = 1
    ): SharedPreferences.Editor {
        return putInt(key, preferences.getInt(key, 0).coerceAtLeast(0) + delta)
    }

    private fun SharedPreferences.Editor.incrementLong(
        key: String,
        delta: Long
    ): SharedPreferences.Editor {
        return putLong(key, preferences.getLong(key, 0L).coerceAtLeast(0L) + delta)
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

    private fun Song.listeningStatsSongKey(): String {
        return if (id > 0L) {
            "${sourceType.name}:$id"
        } else {
            "${sourceType.name}:$uri"
        }
    }

    private fun PlaybackSource.safeSource(): PlaybackSource {
        return if (key.isBlank()) {
            PlaybackSource.Unknown
        } else {
            copy(displayName = displayName.ifBlank { PlaybackSource.Unknown.displayName })
        }
    }

    private enum class StatsPeriod {
        Today,
        Total
    }

    private companion object {
        const val PREFERENCES_NAME = "flowtone_listening_stats"
        const val CURRENT_SCHEMA_VERSION = 2

        const val KEY_SCHEMA_VERSION = "stats_schema_version"
        const val KEY_LEGACY_MIGRATED = "legacy_migrated"
        const val KEY_TODAY_EPOCH_DAY = "today_epoch_day"
        const val KEY_TODAY_EFFECTIVE_PLAY_COUNT = "today_effective_play_count"
        const val KEY_TODAY_DISTINCT_SONG_COUNT = "today_distinct_song_count"
        const val KEY_TODAY_LISTENING_DURATION_MS = "today_listening_duration_ms"
        const val KEY_TOTAL_EFFECTIVE_PLAY_COUNT = "total_effective_play_count"
        const val KEY_TOTAL_DISTINCT_SONG_COUNT = "total_distinct_song_count"
        const val KEY_TOTAL_LISTENING_DURATION_MS = "total_listening_duration_ms"
        const val KEY_DETAILED_STATS_STARTED_AT_MILLIS = "detailed_stats_started_at_millis"
        const val KEY_INCLUDES_LEGACY_AGGREGATE_DATA = "includes_legacy_aggregate_data"
        const val KEY_UPDATED_AT_MILLIS = "updated_at_millis"
        const val KEY_LAST_PLAYED_SONG_ID = "last_played_song_id"
        const val KEY_LAST_PLAYED_SONG_TITLE = "last_played_song_title"
        const val KEY_SONG_KEYS = "song_keys"
        const val KEY_TODAY_SONG_KEYS = "today_song_keys"
        const val KEY_SOURCE_KEYS = "source_keys"
        const val KEY_TODAY_SOURCE_KEYS = "today_source_keys"
        const val KEY_NEXT_STABLE_ORDER = "next_stable_order"

        const val KEY_PART_ORDER = "order"
        const val KEY_PART_SONG_ID = "song_id"
        const val KEY_PART_TITLE = "title"
        const val KEY_PART_ARTIST = "artist"
        const val KEY_PART_ARTWORK_URI = "artwork_uri"
        const val KEY_PART_SOURCE_TYPE = "source_type"
        const val KEY_PART_SOURCE_ID = "source_id"
        const val KEY_PART_DISPLAY_NAME = "display_name"
        const val KEY_PART_TODAY_EFFECTIVE_PLAY_COUNT = "today_effective_play_count"
        const val KEY_PART_TOTAL_EFFECTIVE_PLAY_COUNT = "total_effective_play_count"
        const val KEY_PART_TODAY_LISTENING_DURATION_MS = "today_listening_duration_ms"
        const val KEY_PART_TOTAL_LISTENING_DURATION_MS = "total_listening_duration_ms"

        const val OLD_KEY_TODAY_EPOCH_DAY = "today_epoch_day"
        const val OLD_KEY_TODAY_SONG_COUNT = "today_song_count"
        const val OLD_KEY_TOTAL_LISTENING_DURATION_MS = "total_listening_duration_ms"
        const val OLD_KEY_LAST_PLAYED_SONG_ID = "last_played_song_id"
        const val OLD_KEY_LAST_PLAYED_SONG_TITLE = "last_played_song_title"

        fun songPrefix(songKey: String): String = "song.${encodeKey(songKey)}."

        fun sourcePrefix(sourceKey: String): String = "source.${encodeKey(sourceKey)}."

        fun encodeKey(rawKey: String): String {
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawKey.toByteArray(Charsets.UTF_8))
        }
    }
}
