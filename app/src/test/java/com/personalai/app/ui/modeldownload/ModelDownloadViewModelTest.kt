package com.personalai.app.ui.modeldownload

import android.content.Context
import com.personalai.app.data.repository.ModelRepository
import com.personalai.app.domain.model.ModelRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelDownloadViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeModelRepository(): ModelRepository {
        val context: Context = mockk(relaxed = true)
        every { context.filesDir } returns java.io.File(System.getProperty("java.io.tmpdir"), "dlvm-test-${System.nanoTime()}").apply { mkdirs() }
        return ModelRepository(context)
    }

    @Test
    fun `startDownload delegates to the injected background starter`() = runTest {
        var starts = 0
        val viewModel = ModelDownloadViewModel(fakeModelRepository()) { starts++ }

        viewModel.startDownload()

        assertEquals(1, starts)
    }

    @Test
    fun `uiState reports Done immediately when the model file is already present on disk`() = runTest {
        val modelRepository = fakeModelRepository()
        val model = ModelRegistry.default
        modelRepository.localFile(model).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val viewModel = ModelDownloadViewModel(modelRepository) {}

        assertTrue(viewModel.uiState.value is DownloadUiState.Done)
    }
}
