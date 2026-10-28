package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.playback.PlaybackQueueItem

data class ExtensionContentFailure(
    val id: Long,
    val title: String,
    val details: String
)

data class MusicUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<LocalAlbum> = emptyList(),
    val sourceQueue: List<Song> = emptyList(),
    val playbackQueue: List<Song> = emptyList(),
    val sourceQueueItems: List<PlaybackQueueItem> = emptyList(),
    val playbackQueueItems: List<PlaybackQueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val errorMessage: String? = null,
    val hasScanned: Boolean = false,
    val listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    val likedTracks: List<PersistentTrack> = emptyList(),
    val trackPlaybackErrorMessage: String? = null,
    val trackPlaybackErrorEventId: Long = 0L,
    /** 在线请求尚未被播放器确认；绝不能覆盖 confirmed PlaybackState。 */
    val providerSongs: Map<String, List<ProviderSong>> = emptyMap(),
    val providerAlbums: Map<String, List<ProviderAlbum>> = emptyMap(),
    val providerUserPlaylists: Map<String, List<ProviderPlaylist>> = emptyMap(),
    val providerPlaylistDetails: Map<String, ProviderPlaylistDetailState> = emptyMap(),
    val libraryProviderOptions: List<LibraryProviderOption> = emptyList(),
    val libraryProviderSourcesResolved: Boolean = false,
    val providerSongCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerAlbumCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerUserPlaylistLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val extensionContentFailures: List<ExtensionContentFailure> = emptyList(),
    val libraryRefreshingSources: Set<String> = emptySet(),
    val libraryRefreshFailure: Pair<String, Long>? = null
)

internal fun MusicUiState.startLibraryRefresh(sourceKey: String): MusicUiState =
    copy(libraryRefreshingSources = libraryRefreshingSources + sourceKey)

internal fun MusicUiState.finishLibraryRefresh(sourceKey: String, failureId: Long?): MusicUiState =
    copy(
        libraryRefreshingSources = libraryRefreshingSources - sourceKey,
        libraryRefreshFailure = failureId?.let { sourceKey to it } ?: libraryRefreshFailure
    )

enum class ProviderCatalogLoadState {
    Loading,
    Loaded,
    Failed
}

data class ProviderPlaylistDetailState(
    val songs: List<ProviderSong> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
)

internal data class MusicFeedbackState(
    val extensionContentFailures: List<ExtensionContentFailure> = emptyList(),
    val libraryRefreshingSources: Set<String> = emptySet(),
    val libraryRefreshFailure: Pair<String, Long>? = null
)

/** UI 快照只在此组合；业务判断读取各模块自己的状态，避免依赖组合流的调度时机。 */
internal fun assembleMusicUiState(
    local: LocalLibraryState,
    provider: ProviderLibraryState,
    queue: PlaybackQueuePresentation,
    feedback: MusicFeedbackState,
    likedTracks: List<PersistentTrack>,
    listeningStats: ListeningStatsSnapshot
): MusicUiState = MusicUiState(
    hasPermission = local.hasPermission,
    isLoading = local.isLoading,
    songs = local.songs,
    albums = local.albums,
    errorMessage = local.errorMessage,
    hasScanned = local.hasScanned,
    sourceQueue = queue.sourceQueue,
    playbackQueue = queue.playbackQueue,
    sourceQueueItems = queue.sourceQueueItems,
    playbackQueueItems = queue.playbackQueueItems,
    currentQueueIndex = queue.currentQueueIndex,
    trackPlaybackErrorMessage = queue.trackPlaybackErrorMessage,
    trackPlaybackErrorEventId = queue.trackPlaybackErrorEventId,
    providerSongs = provider.providerSongs,
    providerAlbums = provider.providerAlbums,
    providerUserPlaylists = provider.providerUserPlaylists,
    providerPlaylistDetails = provider.providerPlaylistDetails,
    libraryProviderOptions = provider.libraryProviderOptions,
    libraryProviderSourcesResolved = provider.libraryProviderSourcesResolved,
    providerSongCatalogLoadStates = provider.providerSongCatalogLoadStates,
    providerAlbumCatalogLoadStates = provider.providerAlbumCatalogLoadStates,
    providerUserPlaylistLoadStates = provider.providerUserPlaylistLoadStates,
    extensionContentFailures = feedback.extensionContentFailures,
    libraryRefreshingSources = feedback.libraryRefreshingSources,
    libraryRefreshFailure = feedback.libraryRefreshFailure,
    likedTracks = likedTracks,
    listeningStats = listeningStats
)
