package org.rsmod.api.game.process.npc

import org.junit.jupiter.api.Test
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcInteractionProcessorApRerouteTest {
    /**
     * A reroute approach script may override AP range without discarding the active
     * interaction.
     */
    @Test
    fun GameTestState.`continuously re-route towards pathing entity that leaves valid ap range`() =
        runGameTest(ApRerouteTestScript::class) {
            val npcCoord = CoordGrid(0, 1, 1, 0, 0)
            allocZoneCollision(npcCoord)
            allocZoneCollision(npcCoord.translateX(8))
            allocZoneCollision(npcCoord.translateX(16))

            val playerCoord = npcCoord.translateX(5)
            val npc = spawnNpc(npcCoord, man)
            player.enableWalk()
            player.teleport(playerCoord)
            npc.apPlayer2(player)

            advance(ticks = 1)
            assertMessageSent("Receive attack.")

            player.walk(player.coords.translateX(3))

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(1), player.coords)
            assertEquals(npcCoord, npc.coords)
            assertMessageSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(2), player.coords)
            assertEquals(npcCoord, npc.coords)
            assertMessageSent("Receive attack.")

            // As npcs process before players, the npc is not yet aware of the player being too far.
            // As such, the npc is still within valid reroute script ap range.
            advance(ticks = 1)
            assertEquals(playerCoord.translateX(3), player.coords)
            assertEquals(npcCoord, npc.coords)
            assertMessageSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(3), player.coords)
            assertEquals(npcCoord.translateX(1), npc.coords)
            assertMessageSent("Receive attack.")

            player.telejump(player.coords.translateX(5))

            check(player.coords.chebyshevDistance(npc.coords) == 12)

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(2), npc.coords)
            assertMessageNotSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(3), npc.coords)
            assertMessageNotSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(4), npc.coords)
            assertMessageNotSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(5), npc.coords)
            assertMessageNotSent("Receive attack.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(6), npc.coords)
            assertMessageSent("Receive attack.")
        }

    private class ApRerouteTestScript : PluginScript() {
        override fun ScriptContext.startup() {
            onAiApPlayer2(man) { combatAp(it.target) }
        }

        private fun StandardNpcAccess.combatAp(target: Player) {
            check(isWithinDistance(target, 7)) { "Expected ap distance to be 7." }
            target.mes("Receive attack.")
        }
    }

    private companion object {
        private val man =
            npcTypeFactory.create {
                name = "Man"
                op[1] = "Attack"
                size = 1
                attackRange = 7
                maxRange = 100
            }
    }
}
