# Personal AI

An offline, on-device personal assistant for Android. Runs a local LLM
(via [llama.cpp](https://github.com/ggml-org/llama.cpp)) entirely on-device after a
one-time model download — no internet required for chat, notes, calculations, or
voice interaction.

## Status

This is **Phase 1** of a phased build-out: the core chat MVP.

Implemented so far:
- Gradle multi-module scaffold (`app` + `llama`)
- `llama` module: JNI bridge to llama.cpp (vendored as a git submodule), built via CMake/NDK
- Model download flow with progress reporting (`ModelRepository`, `ModelDownloadScreen`)
- Streaming chat UI backed by the local model (`ChatScreen`, `ChatViewModel`)
- Room-persisted chat history (`AppDatabase`, `ChatMessageEntity`/`ChatMessageDao`)

Not yet implemented (planned for later phases): notes/reminders, a deterministic
calculator/unit-conversion/date-math tool layer, voice input/output, and a
settings/model-management screen.

## Requirements

- Android Studio (latest stable) with:
  - Android SDK, compileSdk/targetSdk per `gradle/libs.versions.toml`
  - NDK **29.0.13113456**
  - CMake **3.31.6**
- A physical **arm64-v8a** Android device with 6-8GB+ RAM for running the model.
  Emulators are not a reliable substitute for performance/memory testing here.

## Setup

1. Clone the repo and initialize the llama.cpp submodule:
   ```sh
   git submodule update --init --recursive
   ```
2. Open the project root in Android Studio and let it sync (this requires
   network access to `dl.google.com`/`maven.google.com`/`repo1.maven.org`).
3. Build and run on a connected arm64-v8a device.
4. On first launch, the app prompts to download the default model
   (Llama 3.2 3B Instruct, Q4_K_M GGUF, ~2GB) from Hugging Face into
   app-private storage. This requires internet access *once*; after that,
   the app works fully offline.

## Known gaps to address before relying on this build

- **`ModelInfo.sha256` is empty** (`app/src/main/java/com/personalai/app/domain/model/ModelInfo.kt`).
  This sandbox could not reach `huggingface.co` to compute/verify the real
  checksum of the default model file. Checksum verification is skipped
  gracefully when this field is blank, but it should be filled in with the
  correct SHA-256 before shipping, so a corrupted/truncated download is caught.
- **This scaffold has not been compiled.** The sandbox used to write this code
  has no Android SDK/NDK installed and blocks `dl.google.com`/`maven.google.com`/
  `huggingface.co`, so a real Gradle sync was not possible here. The JNI
  bridge's native/Kotlin symbol names were manually cross-checked for
  consistency, but a full build + run on a real device is required to confirm
  everything compiles and works end-to-end.

## Architecture

See module layout:

```
app/        Kotlin/Compose UI, Room DB, repositories, ViewModels. No native code.
llama/      Android library module wrapping llama.cpp via JNI. The only module
            with externalNativeBuild/CMake. Vendors llama.cpp as a git submodule
            pinned to a tagged release.
```

Key files:
- `llama/src/main/cpp/llama_jni.cpp` — JNI bridge to llama.cpp
- `llama/src/main/java/com/personalai/llama/LlamaSession.kt` — Kotlin-facing session API
- `app/src/main/java/com/personalai/app/data/repository/ModelRepository.kt` — model download/checksum
- `app/src/main/java/com/personalai/app/ui/chat/ChatViewModel.kt` — chat orchestration
