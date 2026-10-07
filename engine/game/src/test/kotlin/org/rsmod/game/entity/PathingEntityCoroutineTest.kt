package org.rsmod.game.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PathingEntityCoroutineTest {
    @Test
    fun `overlay input preserves a delayed action until its scheduled tick`() {
        val player = Player().apply {
            currentMapClock = 10
            processedMapClock = 10
        }
        var completed = false
        val action = player.launch {
            player.delay(2)
            pause { player.isNotDelayed }
            completed = true
        }

        repeat(3) {
            assertTrue(player.launchBeside {
                assertTrue(player.isAccessProtected)
                assertTrue(player.isModalButtonProtected)
            })
            assertSame(action, player.activeCoroutine)
            assertEquals(12, player.delay)
        }
        player.processedMapClock = 11
        player.advanceActiveCoroutine()
        assertFalse(completed)
        player.processedMapClock = 12
        player.advanceActiveCoroutine()
        assertTrue(completed)
        assertNull(player.activeCoroutine)
    }

    @Test
    fun `suspended side action is cancelled and releases its resources`() {
        val player = Player()
        var actionCompleted = false
        var sideCompleted = false
        var sideCleanedUp = false
        val action = player.launch {
            pause(String::class)
            actionCompleted = true
        }

        val completed = player.launchBeside {
            try {
                pause(Int::class)
                sideCompleted = true
            } finally {
                sideCleanedUp = true
            }
        }

        assertFalse(completed)
        assertTrue(sideCleanedUp)
        assertFalse(sideCompleted)
        assertSame(action, player.activeCoroutine)
        player.resumeActiveCoroutine("continue")
        assertTrue(actionCompleted)
        assertNull(player.activeCoroutine)
    }

    @Test
    fun `noninterrupting suspension is cancelled before reporting the invalid handler`() {
        val player = Player()
        val action = player.launch { pause(String::class) }
        var cleanedUp = false

        assertThrows<IllegalStateException> {
            player.launchNonInterrupting {
                try {
                    pause(Int::class)
                } finally {
                    cleanedUp = true
                }
            }
        }

        assertTrue(cleanedUp)
        assertSame(action, player.activeCoroutine)
        assertTrue(action.isSuspended)
    }

    @Test
    fun `failed side handler leaves the active action resumable`() {
        val player = Player()
        val action = player.launch { pause(String::class) }

        assertThrows<IllegalArgumentException> {
            player.launchBeside { throw IllegalArgumentException("invalid input") }
        }

        assertSame(action, player.activeCoroutine)
        player.resumeActiveCoroutine("continue")
        assertNull(player.activeCoroutine)
    }

    @Test
    fun `ordinary replacement still cancels the previous action`() {
        val player = Player()
        var cancelled = false
        val previous = player.launch {
            try {
                pause(String::class)
            } finally {
                cancelled = true
            }
        }

        val next = player.launch { pause(Int::class) }

        assertTrue(cancelled)
        assertTrue(previous.isIdle)
        assertSame(next, player.activeCoroutine)
    }
}
