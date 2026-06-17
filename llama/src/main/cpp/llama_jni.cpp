#include <android/log.h>
#include <jni.h>
#include <string>
#include <sstream>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

// JNI bridge between com.personalai.llama.internal.LlamaEngineImpl (Kotlin) and llama.cpp.
// Adapted from llama.cpp's own examples/llama.android reference implementation
// (lib/src/main/cpp/ai_chat.cpp), trimmed to this app's v1 needs: load a single
// GGUF model, run one chat session at a time, stream generated tokens back to Kotlin.

constexpr int   N_THREADS_MIN        = 2;
constexpr int   N_THREADS_MAX        = 4;
constexpr int   N_THREADS_HEADROOM   = 2;

constexpr int   DEFAULT_CONTEXT_SIZE = 4096;
constexpr int   OVERFLOW_HEADROOM    = 4;
constexpr int   BATCH_SIZE           = 512;
constexpr float DEFAULT_SAMPLER_TEMP = 0.7f;

static llama_model               *g_model;
static llama_context             *g_context;
static llama_batch                g_batch;
static common_chat_templates_ptr  g_chat_templates;
static common_sampler            *g_sampler;

extern "C"
JNIEXPORT void JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_init(JNIEnv * /*env*/, jobject /*unused*/) {
    llama_log_set(personalai_android_log_callback, nullptr);
    llama_backend_init();
    LOGi("llama backend initialized");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();

    const auto *model_path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGd("%s: loading model from %s", __func__, model_path);
    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);

    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    if (!g_model) {
        LOGe("%s: model not loaded", __func__);
        return 1;
    }

    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                    (int) sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));
    LOGi("%s: using %d threads", __func__, n_threads);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = DEFAULT_CONTEXT_SIZE;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) {
        LOGe("%s: llama_init_from_model() returned null", __func__);
        return 2;
    }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    common_params_sampling sparams;
    sparams.temp = DEFAULT_SAMPLER_TEMP;
    g_sampler = common_sampler_init(g_model, sparams);

    return 0;
}

/**
 * Chat state: position tracking + accumulated chat history for prompt formatting.
 */
constexpr const char *ROLE_SYSTEM    = "system";
constexpr const char *ROLE_USER      = "user";
constexpr const char *ROLE_ASSISTANT = "assistant";

static std::vector<common_chat_msg> g_chat_msgs;
static llama_pos g_system_prompt_position;
static llama_pos g_current_position;
static llama_pos g_stop_generation_position;
static std::string g_cached_token_chars;
static std::ostringstream g_assistant_stream;

static void reset_chat_state(bool clear_kv_cache) {
    g_chat_msgs.clear();
    g_system_prompt_position = 0;
    g_current_position = 0;
    g_stop_generation_position = 0;
    g_cached_token_chars.clear();
    g_assistant_stream.str("");
    if (clear_kv_cache && g_context) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

static void shift_context() {
    const int n_discard = (g_current_position - g_system_prompt_position) / 2;
    LOGi("%s: discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_prompt_position, g_system_prompt_position + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_prompt_position + n_discard, g_current_position, -n_discard);
    g_current_position -= n_discard;
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), g_chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    g_chat_msgs.push_back(new_msg);
    return formatted;
}

static int decode_tokens_in_batches(const llama_tokens &tokens, llama_pos start_pos, bool compute_last_logit) {
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        common_batch_clear(g_batch);

        if (start_pos + i + cur_batch_size >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            LOGw("%s: context nearly full, shifting", __func__);
            shift_context();
        }

        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == (int) tokens.size() - 1);
            common_batch_add(g_batch, token_id, position, {0}, want_logit);
        }

        if (llama_decode(g_context, g_batch) != 0) {
            LOGe("%s: llama_decode() failed", __func__);
            return 1;
        }
    }
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_processSystemPrompt(
        JNIEnv *env, jobject /*unused*/, jstring jsystem_prompt) {
    reset_chat_state(true);

    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    std::string formatted(system_prompt);
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    const bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_template) {
        formatted = chat_add_and_format(ROLE_SYSTEM, formatted);
    }

    const auto tokens = common_tokenize(g_context, formatted, has_template, has_template);
    const int max_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if ((int) tokens.size() > max_size) {
        LOGe("%s: system prompt too long: %d tokens", __func__, (int) tokens.size());
        return 1;
    }

    if (decode_tokens_in_batches(tokens, g_current_position, false)) {
        return 2;
    }

    g_system_prompt_position = g_current_position = (int) tokens.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_processUserPrompt(
        JNIEnv *env, jobject /*unused*/, jstring juser_prompt, jint n_predict) {
    g_stop_generation_position = 0;
    g_cached_token_chars.clear();
    g_assistant_stream.str("");

    const auto *user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string formatted(user_prompt);
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    const bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_template) {
        formatted = chat_add_and_format(ROLE_USER, formatted);
    }

    auto tokens = common_tokenize(g_context, formatted, has_template, has_template);
    const int prompt_size = (int) tokens.size();
    const int max_size = DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM;
    if (prompt_size > max_size) {
        tokens.resize(max_size);
        LOGw("%s: user prompt truncated to fit context", __func__);
    }

    if (decode_tokens_in_batches(tokens, g_current_position, true)) {
        return 2;
    }

    g_current_position += prompt_size;
    g_stop_generation_position = g_current_position + n_predict;
    return 0;
}

static bool is_valid_utf8(const char *s) {
    const auto *bytes = (const unsigned char *) s;
    while (*bytes) {
        int num;
        if ((*bytes & 0x80) == 0x00) num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;
        bytes++;
        for (int i = 1; i < num; i++) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes++;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_generateNextToken(JNIEnv *env, jobject /*unused*/) {
    if (g_current_position >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        shift_context();
    }
    if (g_current_position >= g_stop_generation_position) {
        return nullptr;
    }

    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, g_current_position, {0}, true);
    if (llama_decode(g_context, g_batch) != 0) {
        LOGe("%s: llama_decode() failed", __func__);
        return nullptr;
    }
    g_current_position++;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        chat_add_and_format(ROLE_ASSISTANT, g_assistant_stream.str());
        return nullptr;
    }

    g_cached_token_chars += common_token_to_piece(g_context, new_token_id);

    if (is_valid_utf8(g_cached_token_chars.c_str())) {
        jstring result = env->NewStringUTF(g_cached_token_chars.c_str());
        g_assistant_stream << g_cached_token_chars;
        g_cached_token_chars.clear();
        return result;
    }
    // Multi-byte UTF-8 sequence still incomplete; wait for the next token.
    return env->NewStringUTF("");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    reset_chat_state(false);
    if (g_sampler) common_sampler_free(g_sampler);
    g_chat_templates.reset();
    if (g_batch.token) llama_batch_free(g_batch);
    if (g_context) llama_free(g_context);
    if (g_model) llama_model_free(g_model);
    g_sampler = nullptr;
    g_context = nullptr;
    g_model = nullptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_personalai_llama_internal_LlamaEngineImpl_shutdown(JNIEnv * /*unused*/, jobject /*unused*/) {
    llama_backend_free();
}
