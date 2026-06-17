package com.personalai.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRegistryTest {

    @Test
    fun `default model has a well-formed https download url`() {
        val model = ModelRegistry.default
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertFalse(model.fileName.isBlank())
        assertTrue(model.fileName.endsWith(".gguf"))
    }

    @Test
    fun `default model reports a positive approximate size`() {
        assertTrue(ModelRegistry.default.approxSizeBytes > 0)
    }

    @Test
    fun `registry always includes the default model`() {
        assertTrue(ModelRegistry.all.contains(ModelRegistry.default))
    }

    @Test
    fun `every registered model has a unique id`() {
        val ids = ModelRegistry.all.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }
}
