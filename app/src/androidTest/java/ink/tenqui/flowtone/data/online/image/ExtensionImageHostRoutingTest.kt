package ink.tenqui.flowtone.data.online.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionStreamRequest
import ink.tenqui.flowtone.data.online.network.ExtensionStreamResponse
import ink.tenqui.flowtone.data.online.network.ExtensionStreamClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionImageHostRoutingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun extensionImage_isFetchedThroughFlowtoneHost() = runBlocking {
        val host = RecordingExtensionImageHost(pngBytes())
        val result = imageLoader(host).execute(request(ExtensionImage("test.extension", InvalidUrl)))

        assertTrue(result is SuccessResult)
        assertEquals(listOf("test.extension"), host.resolvedExtensionIds)
        assertEquals(1, host.requests.size)
        assertEquals(InvalidUrl, host.requests.single().request.url)
    }

    @Test
    fun rejectedExtensionImage_doesNotFallbackToDirectCoilNetwork() = runBlocking {
        val host = RecordingExtensionImageHost(pngBytes(), deniedExtensions = setOf("test.extension"))
        val result = imageLoader(host).execute(request(ExtensionImage("test.extension", InvalidUrl)))

        assertTrue(result is ErrorResult)
        assertEquals(listOf("test.extension"), host.resolvedExtensionIds)
        assertEquals(1, host.requests.size)
    }

    @Test
    fun extensionImage_keepsOwningExtensionIdentity() = runBlocking {
        val host = RecordingExtensionImageHost(pngBytes())
        val loader = imageLoader(host)

        assertTrue(loader.execute(request(ExtensionImage("extension.a", InvalidUrl))) is SuccessResult)
        assertTrue(loader.execute(request(ExtensionImage("extension.b", InvalidUrl))) is SuccessResult)

        assertEquals(listOf("extension.a", "extension.b"), host.resolvedExtensionIds)
        assertEquals(listOf("extension.a", "extension.b"), host.requests.map { it.extensionId })
    }

    @Test
    fun extensionImage_usesCoilDiskCacheAcrossImageLoaders() = runBlocking {
        val directory = File(context.cacheDir, "extension-image-disk-test-${UUID.randomUUID()}")
        val image = ExtensionImage("test.extension", InvalidUrl)
        val firstHost = RecordingExtensionImageHost(pngBytes())
        val firstLoader = diskCachingImageLoader(firstHost, directory)
        try {
            assertTrue(firstLoader.execute(diskRequest(image)) is SuccessResult)
            assertEquals(1, firstHost.requests.size)
        } finally {
            firstLoader.shutdown()
        }

        val secondHost = RecordingExtensionImageHost(pngBytes(), deniedExtensions = setOf("test.extension"))
        val secondLoader = diskCachingImageLoader(secondHost, directory)
        try {
            assertTrue(secondLoader.execute(diskRequest(image)) is SuccessResult)
            assertTrue(secondHost.requests.isEmpty())
            assertTrue(secondHost.resolvedExtensionIds.isEmpty())
        } finally {
            secondLoader.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedDownloadDoesNotCommitDiskCache() = runBlocking {
        val directory = File(context.cacheDir, "extension-image-failed-${UUID.randomUUID()}")
        val image = ExtensionImage("test.extension", InvalidUrl)
        val failedHost = RecordingExtensionImageHost(pngBytes(), failRead = true)
        val failedLoader = diskCachingImageLoader(failedHost, directory)
        try {
            assertTrue(failedLoader.execute(diskRequest(image)) is ErrorResult)
        } finally { failedLoader.shutdown() }
        val retryHost = RecordingExtensionImageHost(pngBytes())
        val retryLoader = diskCachingImageLoader(retryHost, directory)
        try {
            assertTrue(retryLoader.execute(diskRequest(image)) is SuccessResult)
            assertEquals(1, retryHost.requests.size)
        } finally {
            retryLoader.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun imageLoader(host: ExtensionImageNetworkHost): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache(null)
            .diskCache(null)
            .components {
                add(ExtensionImageKeyer)
                add(ExtensionImageFetcher.Factory(host))
            }
            .build()

    private fun request(data: ExtensionImage): ImageRequest = ImageRequest.Builder(context)
        .data(data)
        .size(16)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    private fun diskCachingImageLoader(host: ExtensionImageNetworkHost, directory: File): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache(null)
            .diskCache {
                DiskCache.Builder()
                    .directory(directory.absolutePath.toPath())
                    .maxSizeBytes(4L * 1024 * 1024)
                    .build()
            }
            .components {
                add(ExtensionImageKeyer)
                add(ExtensionImageFetcher.Factory(host))
            }
            .build()

    private fun diskRequest(data: ExtensionImage): ImageRequest = ImageRequest.Builder(context)
        .data(data)
        .size(16)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
    }

    private class RecordingExtensionImageHost(
        private val body: ByteArray,
        private val deniedExtensions: Set<String> = emptySet(),
        private val failRead: Boolean = false
    ) : ExtensionImageNetworkHost {
        data class RequestRecord(val extensionId: String, val request: ExtensionStreamRequest)
        val resolvedExtensionIds = mutableListOf<String>()
        val requests = mutableListOf<RequestRecord>()

        override fun clientFor(extensionId: String): ExtensionStreamClient {
            resolvedExtensionIds += extensionId
            return object : ExtensionStreamClient {
                override fun open(request: ExtensionStreamRequest): ExtensionStreamResponse {
                    requests += RequestRecord(extensionId, request)
                    if (extensionId in deniedExtensions) throw SecurityException("host permission denied")
                    return ExtensionStreamResponse(
                        statusCode = 200,
                        headers = mapOf("Content-Type" to listOf("image/png")),
                        body = if (failRead) object : java.io.InputStream() {
                            override fun read(): Int = throw java.io.IOException("test read failure")
                        } else ByteArrayInputStream(body),
                        resolvedUrl = request.url
                    )
                }
            }
        }
    }

    private companion object {
        const val InvalidUrl = "https://extension-image.invalid/avatar.png"
    }
}
