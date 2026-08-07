package ink.tenqui.flowtone.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFileMetadataCacheTest {
    @Test
    fun `cache is reused only while file version is unchanged`() {
        assertTrue(
            isSameFileVersion(
                cachedDateModifiedSeconds = 100L,
                cachedSizeBytes = 2_048L,
                currentDateModifiedSeconds = 100L,
                currentSizeBytes = 2_048L
            )
        )
        assertFalse(
            isSameFileVersion(
                cachedDateModifiedSeconds = 100L,
                cachedSizeBytes = 2_048L,
                currentDateModifiedSeconds = 101L,
                currentSizeBytes = 2_048L
            )
        )
        assertFalse(
            isSameFileVersion(
                cachedDateModifiedSeconds = 100L,
                cachedSizeBytes = 2_048L,
                currentDateModifiedSeconds = 100L,
                currentSizeBytes = 4_096L
            )
        )
    }
}
