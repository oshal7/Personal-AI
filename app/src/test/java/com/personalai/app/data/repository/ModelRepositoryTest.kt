package com.personalai.app.data.repository

import android.content.Context
import com.personalai.app.domain.model.ModelInfo
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var repository: ModelRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root

        repository = ModelRepository(context)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun modelFor(bytes: ByteArray, sha256: String = ""): ModelInfo = ModelInfo(
        id = "test-model",
        displayName = "Test Model",
        downloadUrl = server.url("/model.gguf").toString(),
        fileName = "test-model.gguf",
        approxSizeBytes = bytes.size.toLong(),
        sha256 = sha256,
    )

    @Test
    fun `download writes the file and reports completion`() = runTest {
        val bytes = Random.nextBytes(256 * 1024)
        val model = modelFor(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Complete)
        assertTrue(repository.isDownloaded(model))
        assertArrayEquals(bytes, repository.localFile(model).readBytes())
    }

    @Test
    fun `progress events report monotonically increasing bytes read`() = runTest {
        val bytes = Random.nextBytes(1024 * 1024)
        val model = modelFor(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()
        val progressEvents = events.filterIsInstance<DownloadProgress.InProgress>()

        assertTrue(progressEvents.isNotEmpty())
        assertTrue(progressEvents.zipWithNext().all { (a, b) -> b.bytesRead >= a.bytesRead })
        assertEquals(bytes.size.toLong(), progressEvents.last().bytesRead)
    }

    @Test
    fun `checksum mismatch fails the download and removes the partial file`() = runTest {
        val bytes = Random.nextBytes(64 * 1024)
        val model = modelFor(bytes, sha256 = "0".repeat(64))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
        assertFalse(repository.isDownloaded(model))
        assertFalse(File(tempFolder.root, "models/${model.fileName}.part").exists())
    }

    @Test
    fun `correct checksum allows download to complete`() = runTest {
        val bytes = Random.nextBytes(64 * 1024)
        val model = modelFor(bytes, sha256 = sha256(bytes))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Complete)
        assertTrue(repository.isDownloaded(model))
    }

    @Test
    fun `http error response fails the download`() = runTest {
        val model = modelFor(ByteArray(0))
        server.enqueue(MockResponse().setResponseCode(404))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
        assertEquals("HTTP 404", (events.last() as DownloadProgress.Failed).message)
    }

    @Test
    fun `connection drop mid-download keeps the partial bytes for a later resume`() = runTest {
        val bytes = Random.nextBytes(2 * 1024 * 1024)
        val model = modelFor(bytes, sha256 = sha256(bytes))
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(bytes))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
        val partial = File(tempFolder.root, "models/${model.fileName}.part")
        assertTrue("partial file should be kept so a retry can resume", partial.exists())
        assertTrue(partial.length() > 0)
        assertTrue("should not have written the whole payload before the drop", partial.length() < bytes.size)
    }

    @Test
    fun `retry after a dropped connection resumes via an HTTP Range request`() = runTest {
        val bytes = Random.nextBytes(2 * 1024 * 1024)
        val model = modelFor(bytes, sha256 = sha256(bytes))

        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(bytes))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        repository.download(model).toList()
        server.takeRequest()

        val partial = File(tempFolder.root, "models/${model.fileName}.part")
        val resumeFrom = partial.length()
        assertTrue(resumeFrom > 0)

        val remaining = bytes.copyOfRange(resumeFrom.toInt(), bytes.size)
        server.enqueue(MockResponse().setResponseCode(206).setBody(okio.Buffer().write(remaining)))

        val events = repository.download(model).toList()
        val resumeRequest = server.takeRequest()

        assertEquals("bytes=$resumeFrom-", resumeRequest.getHeader("Range"))
        assertTrue(events.last() is DownloadProgress.Complete)
        assertArrayEquals(bytes, repository.localFile(model).readBytes())
    }

    @Test
    fun `server ignoring the range request restarts the download cleanly`() = runTest {
        val bytes = Random.nextBytes(512 * 1024)
        val model = modelFor(bytes, sha256 = sha256(bytes))
        val partial = File(tempFolder.root, "models/${model.fileName}.part")
        partial.parentFile?.mkdirs()
        partial.writeBytes(Random.nextBytes(1024))

        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Complete)
        assertArrayEquals(bytes, repository.localFile(model).readBytes())
    }

    @Test
    fun `416 from the server clears a stale partial instead of looping forever`() = runTest {
        val model = modelFor(ByteArray(0))
        val partial = File(tempFolder.root, "models/${model.fileName}.part")
        partial.parentFile?.mkdirs()
        partial.writeBytes(Random.nextBytes(1024))

        server.enqueue(MockResponse().setResponseCode(416))

        val events = repository.download(model).toList()

        assertTrue(events.last() is DownloadProgress.Failed)
        assertFalse(partial.exists())
    }

    @Test
    fun `delete removes a previously downloaded file`() = runTest {
        val bytes = Random.nextBytes(8 * 1024)
        val model = modelFor(bytes)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        repository.download(model).toList()
        assertTrue(repository.isDownloaded(model))

        repository.delete(model)

        assertFalse(repository.isDownloaded(model))
    }

    @Test
    fun `stress - large multi-megabyte payload downloads intact across many chunks`() = runTest {
        val bytes = Random.nextBytes(8 * 1024 * 1024)
        val model = modelFor(bytes, sha256 = sha256(bytes))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))

        val events = repository.download(model).toList()
        val progressEvents = events.filterIsInstance<DownloadProgress.InProgress>()

        assertTrue(events.last() is DownloadProgress.Complete)
        assertTrue("expected many incremental progress emissions for an 8MB payload", progressEvents.size > 10)
        assertArrayEquals(bytes, repository.localFile(model).readBytes())
    }
}
