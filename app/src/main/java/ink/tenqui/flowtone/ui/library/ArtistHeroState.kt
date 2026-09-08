package ink.tenqui.flowtone.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage

internal enum class ArtistHeroBackgroundKind { Banner, Cloud }

internal data class ArtistSocialStats(
    val followers: String? = null,
    val following: String? = null
)

internal fun artistSocialStatsText(stats: ArtistSocialStats): String? = listOfNotNull(
    stats.followers?.trim()?.takeIf(String::isNotEmpty)?.let { "$it 粉丝" },
    stats.following?.trim()?.takeIf(String::isNotEmpty)?.let { "$it 关注" }
).joinToString(" · ").takeIf(String::isNotEmpty)

internal data class ArtistHeroGeometry(
    val backgroundHeight: Dp,
    val avatarSize: Dp,
    val infoCardOverlap: Dp,
    val avatarProtrusionAboveCard: Dp,
    val infoCardHorizontalMargin: Dp,
    val infoCardHorizontalPadding: Dp,
    val infoCardBottomPadding: Dp,
    val avatarToNameGap: Dp,
    val biographyHorizontalInset: Dp
) {
    val infoCardTop: Dp
        get() = backgroundHeight - infoCardOverlap

    val avatarTop: Dp
        get() = infoCardTop - avatarProtrusionAboveCard

    val avatarBottom: Dp
        get() = avatarTop + avatarSize

    val avatarDepthInsideCard: Dp
        get() = avatarSize - avatarProtrusionAboveCard

    val infoCardContentTopPadding: Dp
        get() = avatarDepthInsideCard + avatarToNameGap

    fun infoCardWidth(availableWidth: Dp): Dp =
        (availableWidth - infoCardHorizontalMargin * 2).coerceAtLeast(0.dp)

    fun infoCardContentWidth(availableWidth: Dp): Dp =
        (infoCardWidth(availableWidth) - infoCardHorizontalPadding * 2).coerceAtLeast(0.dp)

    fun biographyWidth(availableWidth: Dp): Dp =
        (infoCardContentWidth(availableWidth) - biographyHorizontalInset * 2)
            .coerceAtLeast(0.dp)

    fun measuredHeight(infoCardHeight: Dp): Dp = maxOf(
        backgroundHeight,
        infoCardTop + infoCardHeight,
        avatarBottom
    )
}

internal fun artistHeroGeometry(availableWidth: Dp): ArtistHeroGeometry {
    val backgroundHeight = (availableWidth * 0.72f).coerceIn(260.dp, 320.dp)
    val avatarSize = when {
        availableWidth < 340.dp -> 96.dp
        availableWidth < 400.dp -> 100.dp
        else -> 104.dp
    }
    return ArtistHeroGeometry(
        backgroundHeight = backgroundHeight,
        avatarSize = avatarSize,
        infoCardOverlap = avatarSize * 0.72f,
        avatarProtrusionAboveCard = avatarSize * 0.58f,
        infoCardHorizontalMargin = 12.dp,
        infoCardHorizontalPadding = 16.dp,
        infoCardBottomPadding = 16.dp,
        avatarToNameGap = 12.dp,
        biographyHorizontalInset = 8.dp
    )
}

internal const val ArtistInfoCardTintTopAlpha = 0.07f
internal const val ArtistInfoCardTintBottomAlpha = 0.01f

internal fun artistBiographyExpandVisible(
    biography: String?,
    previewHasVisualOverflow: Boolean
): Boolean = !biography.isNullOrBlank() && previewHasVisualOverflow

internal const val ArtistBiographyPreviewMaxLines = 2
internal val ArtistAvatarOutlineWidth = 1.5.dp

internal data class ArtistAvatarPresentation(
    val measuredSize: Dp,
    val outlineWidth: Dp
)

internal fun artistAvatarPresentation(size: Dp): ArtistAvatarPresentation =
    ArtistAvatarPresentation(
        measuredSize = size,
        outlineWidth = ArtistAvatarOutlineWidth
    )

@Stable
internal class ArtistHeroStateOwner internal constructor(
    val entryKey: String,
    initialAvatar: ExtensionImage?,
    initialBackgroundKind: ArtistHeroBackgroundKind
) {
    var resolvedAvatar by mutableStateOf(initialAvatar)
        private set

    var backgroundKind by mutableStateOf(initialBackgroundKind)
        private set

    var focusRequested by mutableStateOf(false)

    fun updatePresentation(
        avatar: ExtensionImage?,
        backgroundKind: ArtistHeroBackgroundKind
    ) {
        resolvedAvatar = avatar
        this.backgroundKind = backgroundKind
    }
}

internal class ArtistHeroStateStore {
    private val owners = mutableMapOf<String, ArtistHeroStateOwner>()

    fun ownerFor(
        entryKey: String,
        initialAvatar: ExtensionImage?,
        initialBackgroundKind: ArtistHeroBackgroundKind
    ): ArtistHeroStateOwner = owners.getOrPut(entryKey) {
        ArtistHeroStateOwner(entryKey, initialAvatar, initialBackgroundKind)
    }

    fun owner(entryKey: String?): ArtistHeroStateOwner? = entryKey?.let(owners::get)

    fun retainEntries(entryKeys: Set<String>) {
        owners.keys.retainAll(entryKeys)
    }
}
