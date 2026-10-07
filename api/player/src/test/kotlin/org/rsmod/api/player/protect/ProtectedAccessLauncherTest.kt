package org.rsmod.api.player.protect

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Player
import sun.misc.Unsafe

@OptIn(InternalApi::class)
class ProtectedAccessLauncherTest {
    private val launcher = ProtectedAccessLauncher(contextFactory())

    @Test
    fun `overlay handler sees the active protected action without cancelling it`() {
        val player = Player()
        val active = player.launch { pause(String::class) }
        var blocked = false

        launcher.launchBeside(player) {
            blocked = player.isAccessProtected
        }

        assertTrue(blocked)
        assertSame(active, player.activeCoroutine)
        player.resumeActiveCoroutine("continue")
        assertNull(player.activeCoroutine)
    }

    @Test
    fun `overlay handler that suspends alongside an action is cancelled`() {
        val player = Player()
        val active = player.launch { pause(String::class) }
        var cleanedUp = false

        launcher.launchBeside(player) {
            try {
                coroutine.pause(Int::class)
            } finally {
                cleanedUp = true
            }
        }

        assertTrue(cleanedUp)
        assertSame(active, player.activeCoroutine)
    }

    @Test
    fun `overlay handler can begin a suspended flow when the player has no active action`() {
        val player = Player()
        var completed = false

        launcher.launchBeside(player) {
            coroutine.pause(Int::class)
            completed = true
        }

        assertTrue(player.activeCoroutine?.isSuspended == true)
        assertFalse(completed)
        player.resumeActiveCoroutine(1)
        assertTrue(completed)
        assertNull(player.activeCoroutine)
    }

    private fun contextFactory(): ProtectedAccessContextFactory {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null) as Unsafe
        return unsafe.allocateInstance(ProtectedAccessContextFactory::class.java)
            as ProtectedAccessContextFactory
    }
}
