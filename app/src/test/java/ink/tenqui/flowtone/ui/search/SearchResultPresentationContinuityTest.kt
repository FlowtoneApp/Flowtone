package ink.tenqui.flowtone.ui.search

import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchResultPresentationContinuityTest {
    @Test
    fun loadedImagePresentationStaysOwnedByOldLayerWhenItBecomesOutgoing() {
        val imageStateByPresentation = mapOf(4L to ImagePresentation.Loaded)

        val update = searchResultPresentationIdentityUpdate(
            currentId = 4L,
            lastAssignedId = 4L,
            hasExitingElements = true
        )

        assertEquals(4L, update.outgoingId)
        assertEquals(ImagePresentation.Loaded, imageStateByPresentation[update.outgoingId])
    }

    @Test
    fun loadingImagePresentationStaysOwnedByOldLayerWhenItBecomesOutgoing() {
        val imageStateByPresentation = mapOf(8L to ImagePresentation.Loading)

        val update = searchResultPresentationIdentityUpdate(
            currentId = 8L,
            lastAssignedId = 8L,
            hasExitingElements = true
        )

        assertEquals(8L, update.outgoingId)
        assertEquals(ImagePresentation.Loading, imageStateByPresentation[update.outgoingId])
    }

    @Test
    fun snapshotUpdateWithoutExitKeepsExistingPresentationIdentity() {
        val update = searchResultPresentationIdentityUpdate(
            currentId = 3L,
            lastAssignedId = 5L,
            hasExitingElements = false
        )

        assertEquals(3L, update.currentId)
        assertEquals(null, update.outgoingId)
        assertEquals(5L, update.lastAssignedId)
    }

    @Test
    fun oldResultHasOnlyOneVisiblePresentationOwnerDuringCategorySwitch() {
        val update = searchResultPresentationIdentityUpdate(
            currentId = 11L,
            lastAssignedId = 11L,
            hasExitingElements = true
        )

        assertNotEquals(update.currentId, update.outgoingId)
        assertEquals(2, setOf(update.currentId, update.outgoingId).size)
    }

    @Test
    fun outgoingProviderKeyUsesResultCategoryRatherThanSelectedCategory() {
        val beforeSwitch = providerSearchResultItemKey(
            providerId = "provider-a",
            category = ProviderSearchCategory.User,
            identity = "42"
        )
        val afterSelectorChangesToAlbum = providerSearchResultItemKey(
            providerId = "provider-a",
            category = ProviderSearchCategory.User,
            identity = "42"
        )
        val albumWithSameOpaqueId = providerSearchResultItemKey(
            providerId = "provider-a",
            category = ProviderSearchCategory.Album,
            identity = "42"
        )

        assertEquals(beforeSwitch, afterSelectorChangesToAlbum)
        assertNotEquals(beforeSwitch, albumWithSameOpaqueId)
    }

    private enum class ImagePresentation {
        Loading,
        Loaded
    }
}
