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

    val llama1b = ModelInfo(
        id = "llama-3.2-1b-instruct-q4_k_m",
        displayName = "Llama 3.2 1B Instruct (Q4_K_M) — smallest, fastest",
        downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        fileName = "llama-3.2-1b-instruct-q4_k_m.gguf",
        approxSizeBytes = 847_000_000L,
        sha256 = "",
    )

    val qwen3b = ModelInfo(
        id = "qwen2.5-3b-instruct-q4_k_m",
        displayName = "Qwen2.5 3B Instruct (Q4_K_M)",
        downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-3B-Instruct-GGUF/resolve/main/Qwen2.5-3B-Instruct-Q4_K_M.gguf",
        fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
        approxSizeBytes = 2_020_000_000L,
        sha256 = "",
    )

    val phi35mini = ModelInfo(
        id = "phi-3.5-mini-instruct-q4_k_m",
        displayName = "Phi-3.5 Mini Instruct (Q4_K_M)",
        downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
        fileName = "phi-3.5-mini-instruct-q4_k_m.gguf",
        approxSizeBytes = 2_570_000_000L,
        sha256 = "",
    )

    val gemma2b = ModelInfo(
        id = "gemma-2-2b-it-q4_k_m",
        displayName = "Gemma 2 2B IT (Q4_K_M)",
        downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
        fileName = "gemma-2-2b-it-q4_k_m.gguf",
        approxSizeBytes = 1_840_000_000L,
        sha256 = "",
    )

    val all = listOf(default, llama1b, qwen3b, phi35mini, gemma2b)

    fun byId(id: String): ModelInfo = all.firstOrNull { it.id == id } ?: default
}
