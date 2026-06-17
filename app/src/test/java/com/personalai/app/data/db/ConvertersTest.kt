package com.personalai.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `role to string and back round-trips for every enum value`() {
        for (role in MessageRole.entries) {
            val stored = converters.roleToString(role)
            assertEquals(role, converters.stringToRole(stored))
        }
    }

    @Test
    fun `roleToString matches enum name exactly`() {
        assertEquals("USER", converters.roleToString(MessageRole.USER))
        assertEquals("ASSISTANT", converters.roleToString(MessageRole.ASSISTANT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `stringToRole rejects unknown values`() {
        converters.stringToRole("NOT_A_ROLE")
    }
}
