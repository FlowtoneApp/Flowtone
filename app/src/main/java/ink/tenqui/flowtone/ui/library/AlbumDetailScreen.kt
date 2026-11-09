package ink.tenqui.flowtone.ui.library

import android.annotation.SuppressLint
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.playback.toPersistentTrackOrNull
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.SongListItemSkeleton
import ink.tenqui.flowtone.ui.components.StandardSongListItemSpacing
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.PageElementEnterScope
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType

internal val DetailSongRowHorizontalPadding = 8.dp

private val ProviderDetailSkeletonKeys = List(ProviderDetailSkeletonCount, ::providerDetailSkeletonKey)
private val ProviderDetailSkeletonFadeOut = tween<Float>(
    durationMillis = FlowtoneMotion.ShortDurationMillis,
    easing = FlowtoneMotion.Easing
)

private fun LazyListScope.providerDetailStatusRows(
    presentation: ProviderLibraryPresentation,
    emptyMessage: String,
    errorMessage: String,
    pageTransition: PageTransitionScope,
    skeletonPageMotion: SongListViewportMotion?,
    skeletonEnterScope: PageElementEnterScope?,
    selectionSlotPadding: Dp = 0.dp
) {
    when (presentation) {
        ProviderLibraryPresentation.Loading -> items(
            count = ProviderDetailSkeletonCount,
            key = ::providerDetailSkeletonKey
        ) { index ->
            val key = ProviderDetailSkeletonKeys[index]
            val motion = checkNotNull(skeletonPageMotion)
            val order = songListAnimationOrder(key, motion.keys)
            val pageRowModifier = {
                when {
                    pageTransition.phase == PageTransitionPhase.Current -> Modifier
                    !motion.enterGroupReady -> Modifier.graphicsLayer { alpha = 0f }
                    else -> pageTransition.elementModifierAt(motion.progress, order.order, order.orderCount)
                }
            }
            val rowMotion = skeletonEnterScope?.providerRowFadeModifier(
                key, pageTransition, order.order, order.orderCount, outgoingModifier = pageRowModifier
            ) ?: pageRowModifier()
            SongListItemSkeleton(
                // LazyColumn 仅保留已绘制的占位层用于渐出。正式行可以立即按正常尺寸排版。
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = null,
                    fadeOutSpec = ProviderDetailSkeletonFadeOut
                ).then(rowMotion).padding(horizontal = DetailSongRowHorizontalPadding),
                selectionSlotPadding = selectionSlotPadding
            )
        }
        ProviderLibraryPresentation.Empty, ProviderLibraryPresentation.Error -> {
            item(key = "provider-detail-status") {
                Text(
                    text = if (presentation == ProviderLibraryPresentation.Error) errorMessage else emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                )
            }
        }
        ProviderLibraryPresentation.Ready, ProviderLibraryPresentation.Refreshing -> Unit
    }
}

internal data class SongListAnimationOrder(
    val order: Int,
    val orderCount: Int
)

private data class SongListViewportMotion(
    val keys: List<String>,
    val progress: Float,
    val enterGroupReady: Boolean
)

@Composable
private fun rememberSongListViewportMotion(
    sessionKey: String,
    pageTransition: PageTransitionScope,
    visibleKeys: List<String>
): SongListViewportMotion {
    var frozenTransitionId by remember(sessionKey) { mutableStateOf<Int?>(null) }
    var frozenViewportKeys by remember(sessionKey) { mutableStateOf<List<String>>(emptyList()) }
    var capturedPageProgress by remember(sessionKey) { mutableStateOf(0f) }
    LaunchedEffect(pageTransition.transitionId, pageTransition.phase, visibleKeys) {
        if (pageTransition.phase == PageTransitionPhase.Current) {
            frozenTransitionId = null
            frozenViewportKeys = emptyList()
            capturedPageProgress = 0f
        } else if (frozenTransitionId != pageTransition.transitionId && visibleKeys.isNotEmpty()) {
            frozenTransitionId = pageTransition.transitionId
            frozenViewportKeys = visibleKeys
            capturedPageProgress = pageTransition.progress.coerceIn(0f, 1f)
        }
    }
    val keys = when (pageTransition.phase) {
        PageTransitionPhase.Current -> visibleKeys
        PageTransitionPhase.Incoming -> frozenViewportKeys
        PageTransitionPhase.Outgoing -> frozenViewportKeys.ifEmpty { visibleKeys }
    }
    val progress = when {
        pageTransition.phase != PageTransitionPhase.Incoming -> pageTransition.progress
        frozenViewportKeys.isEmpty() -> 0f
        else -> {
            val remaining = (1f - capturedPageProgress).coerceAtLeast(0.0001f)
            ((pageTransition.progress - capturedPageProgress) / remaining).coerceIn(0f, 1f)
        }
    }
    return SongListViewportMotion(
        keys = keys,
        progress = progress,
        enterGroupReady = pageTransition.phase != PageTransitionPhase.Incoming || frozenViewportKeys.isNotEmpty()
    )
}

internal fun providerPlaylistSelectionKeys(identities: List<ProviderEntityIdentity>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return identities.map { item ->
        val identity = item.stableKey
        val occurrence = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = occurrence + 1
        "provider:${identity.length}:$identity:$occurrence"
    }
}

internal fun providerPlaylistSelectableSongs(songs: List<ProviderSong>): List<SelectablePlaylistSong> {
    val keys = providerPlaylistSelectionKeys(songs.map { it.identity })
    return songs.mapIndexed { index, song ->
        SelectablePlaylistSong(
            selectionKey = keys[index],
            song = song.toPresentationSong(),
            track = song.toPersistentTrackOrNull(),
            artwork = song.artwork,
            providerSong = song
        )
    }
}

internal fun songListAnimationOrder(
    songKey: String,
    animationGroupKeys: List<String>
): SongListAnimationOrder {
    val orderCount = animationGroupKeys.size.coerceAtLeast(1)
    return SongListAnimationOrder(
        order = animationGroupKeys.indexOf(songKey).takeIf { it >= 0 } ?: orderCount - 1,
        orderCount = orderCount
    )
}

// Header、content 与页面根层分别参与现有转场和折叠布局，不能合并为单一 Modifier。
@SuppressLint("ModifierParameter")
@Composable
internal fun AlbumDetailScreen(
    albumId: Long,
    album: LocalAlbum?,
    currentSong: Song?,
    isPlaying: Boolean,
    songSort: PlaylistSongSort = PlaylistSongSort(),
    onSongClick: (List<Song>, Int) -> Unit,
    playbackErrorMessage: String? = null,
    playbackErrorEventId: Long = 0L,
    batchActions: PlaylistBatchActions = PlaylistBatchActions(),
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val vinylMotionActive = currentSong?.albumId == albumId && isPlaying
    val title = album?.title ?: "\u4e13\u8f91"
    val artist = album?.artist ?: "\u672a\u77e5\u827a\u672f\u5bb6"
    val listState = remember(albumId) { LazyListState() }
    val albumSongs = remember(albumId, album?.songs, songSort) {
        album?.songs.orEmpty()
            .map { song ->
                SelectablePlaylistSong(
                    selectionKey = "album:$albumId:${song.id}:${song.uri}",
                    song = song
                )
            }
            .sortedForPlaylist(songSort)
    }
    val songsById = remember(album?.songs) {
        album?.songs.orEmpty().associateBy { song -> song.id.toString() }
    }

    if (albumSongs.isEmpty()) {
        PlaylistDetailCollapsingHeaderScaffold(
            title = title,
            listState = null,
            showContentHeader = false,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            contentModifier = contentModifier,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AlbumMetadataHeader(
                    vinylSeed = albumId,
                    title = title,
                    artist = artist,
                    songCount = 0,
                    artworkUri = album?.artworkUri,
                    vinylMotionActive = vinylMotionActive,
                    modifier = pageTransition.elementModifier(0)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u672a\u627e\u5230\u8be5\u4e13\u8f91\u7684\u672c\u5730\u6b4c\u66f2",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    PlaylistDetailCollapsingHeaderScaffold(
        title = title,
        listState = listState,
        showContentHeader = false,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        SelectablePlaylistSongList(
            sourceKey = "album:$albumId",
            source = PlaylistSelectionSource.ReadOnly,
            playlistTitle = title,
            entries = albumSongs,
            listState = listState,
            currentSong = currentSong,
            likedSongKeys = batchActions.likedSongKeys,
            editablePlaylists = batchActions.editablePlaylists,
            clearSelectionRequest = batchActions.clearSelectionRequest,
            onSelectionModeChange = batchActions.onSelectionModeChange,
            onSelectionTopBarStateChange = batchActions.onSelectionTopBarStateChange,
            onSongClick = { tracks, index ->
                val songs = tracks.mapNotNull { track ->
                    (track as? PersistentTrack.Local)?.songId?.let(songsById::get)
                }
                if (songs.size == tracks.size && index in songs.indices) {
                    onSongClick(songs, index)
                }
            },
            externalErrorMessage = playbackErrorMessage,
            externalErrorEventId = playbackErrorEventId,
            onAddSongsNext = batchActions.onAddSongsNext,
            onAppendSongsToQueue = batchActions.onAppendSongsToQueue,
            onAddSongsToPlaylists = batchActions.onAddSongsToPlaylists,
            onSetSongsLiked = batchActions.onSetSongsLiked,
            onDeleteSongs = batchActions.onDeleteSongs,
            onRemoveEntries = { _, done -> done(false) },
            reorderAnimationKey = songSort,
            pageTransition = pageTransition,
            itemModifier = itemModifier,
            headerContent = {
                AlbumMetadataHeader(
                    vinylSeed = albumId,
                    title = title,
                    artist = artist,
                    songCount = albumSongs.size,
                    artworkUri = album?.artworkUri,
                    vinylMotionActive = vinylMotionActive,
                    modifier = pageTransition.elementModifier(0)
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal data class ProviderSongCollectionHeader(
    val key: String,
    val title: String,
    val subtitle: String,
    val artwork: ExtensionImage? = null,
    val songCount: Int? = null,
    val releaseMetadata: String? = null,
    val likedArtwork: Boolean = false
)

internal fun ProviderAlbum.collectionHeader() = ProviderSongCollectionHeader(
    key = identity.stableKey,
    title = title,
    subtitle = artist.ifBlank { "未知艺术家" },
    artwork = artwork,
    songCount = songCount,
    releaseMetadata = releaseMetadata
)

internal fun ProviderPlaylistSearchItem.collectionHeader() = ProviderSongCollectionHeader(
    key = identity.stableKey,
    title = title,
    subtitle = artist.ifBlank { "在线歌单" },
    artwork = artwork,
    songCount = metadata?.firstOrNull { it.type == "track_count" }?.value
        ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
)

@SuppressLint("ModifierParameter")
@Composable
internal fun ProviderSongCollectionDetailScreen(
    header: ProviderSongCollectionHeader,
    songs: List<ProviderSong>,
    presentation: ProviderLibraryPresentation,
    presentationSessionKey: String,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<ProviderSong>, Int) -> Unit,
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    emptyMessage: String = "未找到该专辑的在线歌曲",
    errorMessage: String = "当前在线来源无法读取该专辑的歌曲",
    playlistBatchActions: PlaylistBatchActions? = null,
    headerAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = remember(header.key) { LazyListState() }
    val presentedSongs = remember(songs) { songs.map(ProviderSong::toPresentationSong) }
    val songKeys = remember(songs) { songs.map { it.identity.stableKey } }
    val currentUri = currentSong?.uri
    val vinylMotionActive = isPlaying && presentedSongs.any { it.uri == currentUri }
    val headerContent: @Composable () -> Unit = {
        AlbumMetadataHeader(
            vinylSeed = header.key.hashCode().toLong(),
            title = header.title,
            artist = header.subtitle,
            songCount = presentedSongs.size.takeIf { it > 0 } ?: header.songCount ?: 0,
            releaseMetadata = header.releaseMetadata,
            artworkUri = null,
            extensionArtwork = header.artwork,
            useVinylArtwork = playlistBatchActions == null,
            useLikedArtwork = header.likedArtwork,
            vinylMotionActive = vinylMotionActive,
            modifier = pageTransition.elementModifier(0),
            additionalContent = { headerAction?.invoke() }
        )
    }
    val skeletonPageMotion = if (presentation == ProviderLibraryPresentation.Loading) {
        val visibleSkeletonKeys by remember(listState) {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    ProviderDetailSkeletonKeys.getOrNull(item.index - 1)?.takeIf { it == item.key }
                }
            }
        }
        rememberSongListViewportMotion("$presentationSessionKey:loading", pageTransition, visibleSkeletonKeys)
    } else null
    val skeletonEnterScope = if (presentation == ProviderLibraryPresentation.Loading) {
        rememberProviderSkeletonEnterScope(
            "$presentationSessionKey:loading", ProviderDetailSkeletonKeys, pageTransition.phase
        )
    } else null
    if (playlistBatchActions != null) {
        val entries = remember(songs) { providerPlaylistSelectableSongs(songs) }
        val indexByKey = remember(entries) { entries.mapIndexed { index, entry -> entry.selectionKey to index }.toMap() }
        PlaylistDetailCollapsingHeaderScaffold(
            title = header.title,
            listState = listState,
            showContentHeader = false,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            modifier = modifier
        ) {
            SelectablePlaylistSongList(
                sourceKey = "provider-playlist:$presentationSessionKey",
                source = PlaylistSelectionSource.ReadOnly,
                playlistTitle = header.title,
                entries = entries,
                listState = listState,
                currentSong = currentSong,
                likedSongKeys = playlistBatchActions.likedSongKeys,
                editablePlaylists = playlistBatchActions.editablePlaylists,
                clearSelectionRequest = playlistBatchActions.clearSelectionRequest,
                onSelectionModeChange = playlistBatchActions.onSelectionModeChange,
                onSelectionTopBarStateChange = playlistBatchActions.onSelectionTopBarStateChange,
                onSongClick = { _, _ -> },
                onEntryClick = { key -> indexByKey[key]?.let { onSongClick(songs, it) } },
                onlineReadOnly = true,
                onAddProviderSongsNext = playlistBatchActions.onAddProviderSongsNext,
                dynamicEnterSessionKey = presentationSessionKey,
                onAddSongsNext = playlistBatchActions.onAddSongsNext,
                onAppendSongsToQueue = playlistBatchActions.onAppendSongsToQueue,
                onAddSongsToPlaylists = playlistBatchActions.onAddSongsToPlaylists,
                onSetSongsLiked = playlistBatchActions.onSetSongsLiked,
                onDeleteSongs = playlistBatchActions.onDeleteSongs,
                onRemoveEntries = { _, done -> done(false) },
                pageTransition = pageTransition,
                itemModifier = itemModifier,
                headerContent = headerContent,
                emptyRows = {
                    providerDetailStatusRows(
                        presentation, emptyMessage, errorMessage, pageTransition,
                        skeletonPageMotion, skeletonEnterScope, SelectionSlotPadding
                    )
                }
            )
        }
        return
    }
    val visibleSongKeys by remember(listState, songKeys) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                songKeys.getOrNull(item.index - 1)?.takeIf { it == item.key }
            }.distinct()
        }
    }
    val songPageMotion = rememberSongListViewportMotion(presentationSessionKey, pageTransition, visibleSongKeys)
    val initialSongKeys = remember(presentationSessionKey) { songKeys.toSet() }
    val dynamicEnterScope = rememberProviderRowEnterScope(
        sessionKey = presentationSessionKey,
        rowKeys = songKeys,
        viewportKeys = visibleSongKeys,
        initiallyEnteredKeys = initialSongKeys,
        phase = pageTransition.phase
    )

    PlaylistDetailCollapsingHeaderScaffold(
        title = header.title,
        listState = listState,
        showContentHeader = false,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(StandardSongListItemSpacing),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "provider-album-header:${header.key}") {
                headerContent()
            }
            if (presentedSongs.isEmpty()) {
                providerDetailStatusRows(
                    presentation, emptyMessage, errorMessage, pageTransition,
                    skeletonPageMotion, skeletonEnterScope
                )
            } else {
                itemsIndexed(
                    items = presentedSongs,
                    key = { index, _ -> songs[index].identity.stableKey }
                ) { index, song ->
                    val songKey = songKeys[index]
                    val animationOrder = songListAnimationOrder(songKey, songPageMotion.keys)
                    val transitionModifier = {
                        itemModifier(
                            songPageMotion.progress,
                            animationOrder.order,
                            animationOrder.orderCount
                        )
                    }
                    val pageItemModifier = when {
                        songKey !in initialSongKeys -> dynamicEnterScope.providerRowFadeModifier(
                            songKey, pageTransition, animationOrder.order, animationOrder.orderCount,
                            outgoingModifier = transitionModifier
                        )
                        pageTransition.phase == PageTransitionPhase.Current -> Modifier
                        !songPageMotion.enterGroupReady -> Modifier.graphicsLayer { alpha = 0f }
                        else -> transitionModifier()
                    }
                    SongListItem(
                        song = song,
                        isCurrentSong = currentUri == song.uri,
                        extensionArtwork = songs[index].artwork,
                        onClick = { onSongClick(songs, index) },
                        modifier = pageItemModifier.padding(horizontal = DetailSongRowHorizontalPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumMetadataHeader(
    vinylSeed: Long,
    title: String,
    artist: String,
    songCount: Int,
    releaseMetadata: String? = null,
    artworkUri: android.net.Uri?,
    extensionArtwork: ExtensionImage? = null,
    useVinylArtwork: Boolean = true,
    useLikedArtwork: Boolean = false,
    vinylMotionActive: Boolean,
    modifier: Modifier = Modifier,
    additionalContent: @Composable ColumnScope.() -> Unit = {}
) {
    val supportingText = listOfNotNull(
        artist,
        releaseMetadata?.trim()?.takeIf(String::isNotEmpty),
        "$songCount \u9996\u6b4c\u66f2"
    ).joinToString(" \u00b7 ")
    CollectionMetadataHeader(
        title = title,
        supportingText = supportingText,
        artwork = {
            if (useLikedArtwork) {
                PlaylistCardSurface(
                    visualType = PlaylistCardVisualType.LikedMusic,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(0.dp),
                    isFlowCloudPlaying = false,
                    modifier = Modifier.size(128.dp)
                ) { colors ->
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = colors.iconColor,
                        modifier = Modifier.align(Alignment.Center).size(42.dp)
                    )
                }
            } else if (useVinylArtwork) {
                AlbumArtwork(
                    artworkUri = artworkUri,
                    extensionArtwork = extensionArtwork,
                    vinylMotionActive = vinylMotionActive,
                    vinylSeed = vinylSeed
                )
            } else {
                FlowtoneArtwork(
                    artworkUri = artworkUri,
                    extensionArtwork = extensionArtwork,
                    modifier = Modifier.size(128.dp)
                )
            }
        },
        modifier = modifier,
        additionalContent = additionalContent
    )
}
