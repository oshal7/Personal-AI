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
