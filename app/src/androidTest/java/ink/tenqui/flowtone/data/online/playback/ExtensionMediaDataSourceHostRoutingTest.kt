package ink.tenqui.flowtone.data.online.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionHttpTransport
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkGateway
import ink.tenqui.flowtone.data.online.network.GlobalExtensionNetworkLimiter
import ink.tenqui.flowtone.data.online.network.ExtensionStreamRequest
import ink.tenqui.flowtone.data.online.network.ExtensionStreamResponse
import ink.tenqui.flowtone.data.online.network.ExtensionStreamTransport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class ExtensionMediaDataSourceHostRoutingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun extensionResource_isReadThroughFlowtoneHost() {
        val audio = oneSecondWave()
        val transport = RecordingStreamTransport(audio)
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val uri = store.register(
            ExtensionPlaybackResource("test.extension", InvalidMediaUrl, mimeType = "audio/wav")
        )

        val loaded = extensionDataSource(store, host).useOpened(DataSpec(uri)) { readAll(it) }

        assertArrayEquals(audio, loaded)
        assertEquals(listOf("test.extension"), host.extensionIds)
        assertEquals(1, transport.requests.size)
        assertEquals(InvalidMediaUrl, transport.requests.single().url)
        assertTrue(loaded.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()))
    }

    @Test
    fun rejectedResource_doesNotFallbackToDefaultHttp() {
        val transport = RecordingStreamTransport(oneSecondWave())
        val host = RecordingHost(transport, allowedHosts = listOf("allowed.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val uri = store.register(ExtensionPlaybackResource("test.extension", InvalidMediaUrl))

        val failure = runCatching {
            extensionDataSource(store, host).useOpened(DataSpec(uri)) { readAll(it) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(listOf("test.extension"), host.extensionIds)
        assertTrue("被拒绝后真实 transport 不应收到请求", transport.requests.isEmpty())
    }

    @Test
    fun nonZeroPosition_becomesManagedRangeRequest() {
        val audio = oneSecondWave()
        val transport = RecordingStreamTransport(audio)
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val uri = store.register(ExtensionPlaybackResource("test.extension", InvalidMediaUrl))
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(128L)
            .setLength(64L)
            .build()

        val loaded = extensionDataSource(store, host).useOpened(dataSpec) { readAll(it) }

        assertArrayEquals(audio.copyOfRange(128, 192), loaded)
        val request = transport.requests.single()
        assertEquals(128L, request.position)
        assertEquals(64L, request.length)
        assertEquals("bytes=128-191", request.headers["Range"])
    }

    @Test
    fun localFile_stillUsesMedia3LocalDataSource() {
        val audio = oneSecondWave()
        val localFile = File(context.cacheDir, "extension-playback-local-test.wav")
        localFile.writeBytes(audio)
        val transport = RecordingStreamTransport(audio)
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val dataSource = DefaultDataSource.Factory(
            context,
            ExtensionMediaDataSource.Factory(store, host)
        ).createDataSource()

        val loaded = dataSource.useOpened(DataSpec(android.net.Uri.fromFile(localFile))) { readAll(it) }

        assertArrayEquals(audio, loaded)
        assertTrue(host.extensionIds.isEmpty())
        assertTrue(transport.requests.isEmpty())
        localFile.delete()
    }

    @Test
    fun hlsResource_usesHlsSourceFromRegisteredType_notOpaqueUriSuffix() {
        val transport = RecordingStreamTransport("#EXTM3U".encodeToByteArray())
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val opaqueUri = store.register(
            ExtensionPlaybackResource(
                extensionId = "test.extension",
                // 故意没有 .m3u8 后缀，类型只能来自已登记的 session 元数据。
                url = "https://media.invalid/live",
                type = ExtensionPlaybackResourceType.Hls
            )
        )
        val extensionFactory = ExtensionMediaDataSource.Factory(store, host)
        val factory = ExtensionMediaSourceFactory(
            resources = store,
            extensionDataSourceFactory = extensionFactory,
            fallbackFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, extensionFactory))
        )

        val source = factory.createMediaSource(
            MediaItem.Builder()
                .setUri(opaqueUri)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
        )

        assertTrue(source is HlsMediaSource)
    }

    @Test
    fun hlsRootPlaylistAndChildResources_keepBoundExtensionAndHostHeaders() {
        val transport = RecordingStreamTransport("#EXTM3U\nsegment.ts".encodeToByteArray())
        val host = RecordingHost(
            transport,
            allowedHosts = listOf("media.invalid", "cdn.invalid", "keys.invalid")
        )
        val store = ExtensionPlaybackResourceStore()
        val resource = ExtensionPlaybackResource(
            extensionId = "test.extension",
            url = "https://media.invalid/live",
            headers = mapOf(
                "Authorization" to "Bearer session-token",
                "Host" to "must-be-filtered",
                "Range" to "bytes=0-999"
            ),
            type = ExtensionPlaybackResourceType.Hls
        )
        val rootUri = store.register(resource)
        val factory = ExtensionMediaDataSource.Factory(store, host).forResource(resource)

        factory.createDataSource().useOpened(DataSpec(rootUri)) { readAll(it) }
        factory.createDataSource().useOpened(DataSpec(Uri.parse("https://cdn.invalid/segment.ts"))) { readAll(it) }
        factory.createDataSource().useOpened(DataSpec(Uri.parse("https://keys.invalid/key.bin"))) { readAll(it) }

        assertEquals(listOf("test.extension", "test.extension", "test.extension"), host.extensionIds)
        assertEquals(
            listOf(
                "https://media.invalid/live",
                "https://cdn.invalid/segment.ts",
                "https://keys.invalid/key.bin"
            ),
            transport.requests.map { it.url }
        )
        transport.requests.forEach { request ->
            assertEquals("Bearer session-token", request.headers["Authorization"])
            assertTrue(request.headers.keys.none { it.equals("Host", ignoreCase = true) })
            assertTrue(request.headers.keys.none { it.equals("Range", ignoreCase = true) })
        }
        assertEquals(0, host.limiter.snapshot().active)
        assertEquals(0, host.limiter.snapshot().inFlight)
    }

    @Test
    fun hlsChildOnUnapprovedHost_isRejectedBySameGateway() {
        val transport = RecordingStreamTransport("#EXTM3U".encodeToByteArray())
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val resource = ExtensionPlaybackResource(
            extensionId = "test.extension",
            url = "https://media.invalid/live",
            type = ExtensionPlaybackResourceType.Hls
        )
        val dataSource = ExtensionMediaDataSource.Factory(store, host)
            .forResource(resource)
            .createDataSource()

        val failure = runCatching {
            dataSource.useOpened(DataSpec(Uri.parse("https://evil.invalid/segment.ts"))) { readAll(it) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(listOf("test.extension"), host.extensionIds)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun hlsChildRedirectToUnapprovedHost_isRejectedBySameGateway() {
        val transport = RecordingStreamTransport(
            audio = "segment".encodeToByteArray(),
            redirectTo = "https://evil.invalid/segment.ts"
        )
        val host = RecordingHost(transport, allowedHosts = listOf("media.invalid", "cdn.invalid"))
        val store = ExtensionPlaybackResourceStore()
        val resource = ExtensionPlaybackResource(
            extensionId = "test.extension",
            url = "https://media.invalid/live",
            type = ExtensionPlaybackResourceType.Hls
        )
        val dataSource = ExtensionMediaDataSource.Factory(store, host)
            .forResource(resource)
            .createDataSource()

        val failure = runCatching {
            dataSource.useOpened(DataSpec(Uri.parse("https://cdn.invalid/segment.ts"))) { readAll(it) }
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertEquals(listOf("test.extension"), host.extensionIds)
        assertTrue(transport.requests.isEmpty())
        assertEquals(0, host.limiter.snapshot().active)
        assertEquals(0, host.limiter.snapshot().inFlight)
    }

    private fun extensionDataSource(
        store: ExtensionPlaybackResourceStore,
        host: ExtensionStreamNetworkHost
    ): DataSource = DefaultDataSource.Factory(
        context,
        ExtensionMediaDataSource.Factory(store, host)
    ).createDataSource()

    private fun <T> DataSource.useOpened(dataSpec: DataSpec, block: (DataSource) -> T): T {
        open(dataSpec)
        return try {
            block(this)
        } finally {
            close()
        }
    }

    private fun readAll(dataSource: DataSource): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = dataSource.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private class RecordingHost(
        transport: RecordingStreamTransport,
        allowedHosts: List<String>
    ) : ExtensionStreamNetworkHost {
        val extensionIds = mutableListOf<String>()
        val limiter = GlobalExtensionNetworkLimiter(maxActiveRequests = 1, maxInFlightRequests = 2)
        private val client = ExtensionNetworkGateway(
            transport = ExtensionHttpTransport { _, _ ->
                ExtensionHttpResponse(500, emptyMap(), ByteArray(0))
            },
            streamTransport = transport,
            logger = ExtensionCoreLogger { _, _ -> },
            limiter = limiter
        ).createStreamClientFor("test.extension", "media_stream", allowedHosts)

        override fun clientFor(extensionId: String) = client.also { extensionIds += extensionId }
    }

    private class RecordingStreamTransport(
        private val audio: ByteArray,
        private val redirectTo: String? = null
    ) : ExtensionStreamTransport {
        val requests = mutableListOf<ExtensionStreamRequest>()

        override fun open(
            request: ExtensionStreamRequest,
            authorizeUrl: (String) -> Unit
        ): ExtensionStreamResponse {
            authorizeUrl(request.url)
            redirectTo?.let(authorizeUrl)
            requests += request
            val start = request.position.toInt()
            val endExclusive = if (request.length == C.LENGTH_UNSET.toLong()) {
                audio.size
            } else {
                minOf(audio.size, start + request.length.toInt())
            }
            val body = audio.copyOfRange(start, endExclusive)
            val headers = mutableMapOf(
                "Content-Type" to listOf("audio/wav"),
                "Content-Length" to listOf(body.size.toString())
            )
            if (start > 0) {
                headers["Content-Range"] = listOf("bytes $start-${endExclusive - 1}/${audio.size}")
            }
            return ExtensionStreamResponse(
                statusCode = if (start == 0) 200 else 206,
                headers = headers,
                body = ByteArrayInputStream(body),
                resolvedUrl = request.url
            )
        }
    }

    private fun oneSecondWave(): ByteArray {
        val sampleRate = 8_000
        val pcm = ByteArray(sampleRate * 2)
        return ByteArrayOutputStream(44 + pcm.size).use { output ->
            fun littleEndian(value: Int, bytes: Int) {
                repeat(bytes) { index -> output.write(value ushr (index * 8) and 0xff) }
            }
            output.write("RIFF".encodeToByteArray())
            littleEndian(36 + pcm.size, 4)
            output.write("WAVEfmt ".encodeToByteArray())
            littleEndian(16, 4)
            littleEndian(1, 2)
            littleEndian(1, 2)
            littleEndian(sampleRate, 4)
            littleEndian(sampleRate * 2, 4)
            littleEndian(2, 2)
            littleEndian(16, 2)
            output.write("data".encodeToByteArray())
            littleEndian(pcm.size, 4)
            output.write(pcm)
            output.toByteArray()
        }
    }

    private companion object {
        const val InvalidMediaUrl = "https://media.invalid/test.wav"
    }
}
