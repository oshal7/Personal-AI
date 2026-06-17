package com.personalai.app.domain.tools

/**
 * Small quantized on-device models are unreliable at structured extraction, so task detection
 * is done with deterministic regex matching instead of asking the LLM. Only surfaces a
 * suggestion — the caller decides whether/how to confirm with the user before saving anything.
 */
object TaskSuggestionDetector {

    private val patterns = listOf(
        Regex("""(?i)\bremind me to\s+(.+)"""),
        Regex("""(?i)\bremind me\s+(.+)"""),
        Regex("""(?i)\bdon'?t forget to\s+(.+)"""),
        Regex("""(?i)\bi need to\s+(.+)"""),
        Regex("""(?i)\bi(?:'ve| have) got to\s+(.+)"""),
        Regex("""(?i)\bi have to\s+(.+)"""),
        Regex("""(?i)\bi should\s+(.+)"""),
        Regex("""(?i)\bmake sure (?:i|to)\s+(.+)"""),
        Regex("""(?i)\btodo:?\s+(.+)"""),
        Regex("""(?i)\bnote to self:?\s+(.+)"""),
    )

    fun detect(message: String): String? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return null

        for (pattern in patterns) {
            val captured = pattern.find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trimEnd('.', '!', '?')
                ?: continue
            if (captured.isBlank()) continue
            return captured.replaceFirstChar { it.uppercase() }
        }
        return null
    }
}
