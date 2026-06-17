package com.personalai.app.domain.model

/** Entry in the small, hardcoded registry of downloadable GGUF models. */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String,
    val approxSizeBytes: Long,
    val sha256: String,
)

object ModelRegistry {
    val default = ModelInfo(
        id = "llama-3.2-3b-instruct-q4_k_m",
        displayName = "Llama 3.2 3B Instruct (Q4_K_M)",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
        fileName = "llama-3.2-3b-instruct-q4_k_m.gguf",
        approxSizeBytes = 2_019_000_000L,
        // TODO: fill in once the exact file revision is pinned — verify against the
        // checksum published on the model's Hugging Face page before relying on it.
        sha256 = "",
    )

    val all = listOf(default)
}
