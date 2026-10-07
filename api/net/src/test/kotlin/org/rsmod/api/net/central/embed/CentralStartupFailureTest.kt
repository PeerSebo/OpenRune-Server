package org.rsmod.api.net.central.embed

import java.net.BindException
import java.sql.SQLException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CentralStartupFailureTest {
    @Test
    fun `successful startup leaves central running`() {
        val calls = mutableListOf<String>()

        startCentralPreservingDatabase(start = { calls += "start" }, stop = { calls += "stop" })

        assertEquals(listOf("start"), calls)
    }

    @ParameterizedTest
    @ValueSource(strings = ["42P01", "42703", "3F000", "08001"])
    fun `schema and connection failures stop central without reset or retry`(sqlState: String) {
        val sqlFailure = SQLException("Existing database needs repair", sqlState)
        val failure = RuntimeException("Central initialization failed", sqlFailure)
        val calls = mutableListOf<String>()

        val result = assertThrows<IllegalStateException> {
            startCentralPreservingDatabase(
                start = {
                    calls += "start"
                    throw failure
                },
                stop = { calls += "stop" },
            )
        }

        assertEquals(listOf("start", "stop"), calls)
        assertSame(failure, result.cause)
        assertSame(sqlFailure, result.cause?.cause)
        assertTrue(result.message.orEmpty().contains("No automatic database reset was attempted"))
        assertTrue(result.message.orEmpty().contains("back up the existing database"))
    }

    @Test
    fun `port bind failure stops central and preserves the cause`() {
        val failure = BindException("Address already in use")
        var stops = 0

        val result = assertThrows<IllegalStateException> {
            startCentralPreservingDatabase(start = { throw failure }, stop = { stops++ })
        }

        assertEquals(1, stops)
        assertSame(failure, result.cause)
    }

    @Test
    fun `cleanup failure does not hide the startup failure`() {
        val failure = SQLException("Missing column in an existing table", "42703")
        val cleanupFailure = IllegalStateException("Unable to stop listener")

        val result = assertThrows<IllegalStateException> {
            startCentralPreservingDatabase(
                start = { throw failure },
                stop = { throw cleanupFailure },
            )
        }

        assertSame(failure, result.cause)
        assertEquals(listOf(cleanupFailure), result.suppressed.toList())
    }

    @Test
    fun `cyclic exception causes do not prevent failed startup cleanup`() {
        val first = RuntimeException("First cause")
        val second = RuntimeException("Second cause", first)
        first.initCause(second)
        var stops = 0

        val result = assertThrows<IllegalStateException> {
            startCentralPreservingDatabase(start = { throw first }, stop = { stops++ })
        }

        assertEquals(1, stops)
        assertSame(first, result.cause)
    }
}
