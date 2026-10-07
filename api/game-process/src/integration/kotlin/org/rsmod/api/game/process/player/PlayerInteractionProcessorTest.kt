package org.rsmod.api.game.process.player

import org.junit.jupiter.api.Test
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.locTypeFactory
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlayerInteractionProcessorTest {
    @Test
    fun GameTestState.`cancel interaction due to verification condition`() = runGameTest {
        val loc = placeMapLoc(CoordGrid(0, 50, 50, 22, 43), locTypes.getValue(1276))

        val route = player.routeDestination
        player.moveSpeed = MoveSpeed.Walk
        player.teleport(loc.coords.translateX(-5))
        player.opLoc1(loc)

        advance(ticks = 1)
        val dest = route.lastOrNull()
        checkNotNull(dest)
        checkNotNull(player.interaction)

        placeMapLoc(CoordGrid(0, 50, 50, 22, 43), locTypes.getValue(1274))

        advance(ticks = 1)
        assertTrue(route.isEmpty())
        assertNull(player.interaction)
        assertNotEquals(player.coords, dest)
    }

    /** A moving NPC target can change position before its queued interaction route is consumed. */
    @Test
    fun GameTestState.`recalc request should not be cleared on empty route`() = runGameTest {
        val npc = spawnNpc(CoordGrid(0, 49, 50, 57, 62), sheep)
        player.teleport(CoordGrid(0, 49, 50, 58, 62))
        player.enableRun()
        player.opNpc1(npc)

        npc.walk(npc.coords.translateZ(-1))

        advance(ticks = 1)
        assertMessageNotSent(constants.dm_reach)
    }

    /**
     * Recalculate moving-target routes only during the last waypoint. Fixture layout:
     * https://i.imgur.com/iniC8VQ.png
     */
    @Test
    fun GameTestState.`recalculate route during last waypoint stretch`() = runGameTest {
        val base = CoordGrid(0, 1, 1, 0, 0)
        val sourceStart = base.translate(7, 6)
        val targetStart = base.translate(0, 7)
        val targetEnd = base.translate(7, 7)
        val target = spawnNpc(targetStart, dummy)
        for (dx in 1..6) {
            placeMapLoc(base.translate(dx, 1), crate)
            placeMapLoc(base.translate(dx, 1), crate)
            placeMapLoc(base.translate(dx, 7), crate)
        }
        for (dz in 1..7) {
            placeMapLoc(base.translate(1, dz), crate)
            placeMapLoc(base.translate(6, dz), crate)
        }

        player.coords = sourceStart
        player.varMoveSpeed = MoveSpeed.Walk
        check(player.routeDestination.isEmpty())
        check(target.coords == targetStart)
        player.opNpc1(target)

        advance(ticks = 1)
        checkNotNull(player.interaction)
        assertEquals(3, player.routeDestination.size)

        target.coords = targetEnd

        repeat(4) { i ->
            val travelled = 1 + (i + 1)
            advance(ticks = 1)
            assertEquals(3, player.routeDestination.size)
            assertEquals(sourceStart.translateZ(-travelled), player.coords)
        }

        advance(ticks = 1)
        assertEquals(2, player.routeDestination.size)
        assertEquals(base.translate(0, 0), player.routeDestination.peekFirst())
        assertEquals(base.translate(7, 0), player.coords)

        repeat(6) { i ->
            val travelled = i + 1
            advance(ticks = 1)
            assertEquals(2, player.routeDestination.size)
            assertEquals(sourceStart.translate(-travelled, -6), player.coords)
        }

        assertEquals(base.translate(0, 0), player.routeDestination.peekFirst())

        advance(ticks = 1)
        assertEquals(1, player.routeDestination.size)
        assertEquals(base.translate(0, 0), player.coords)

        advance(ticks = 1)
        assertEquals(base.translate(7, 0), player.routeDestination.peekFirst())
        assertEquals(base.translate(7, 6), player.routeDestination.peekLast())
        assertEquals(2, player.routeDestination.size)

    }

    /** Protected access pauses interaction validation; it must resume when the modal closes. */
    @Test
    fun GameTestState.`skip interaction verification while player isBusy`() = runGameTest {
        val loc = placeMapLoc(CoordGrid(0, 50, 50, 22, 43), locTypes.getValue(1276))

        val route = player.routeDestination
        player.moveSpeed = MoveSpeed.Walk
        player.teleport(loc.coords.translateX(-5))
        player.opLoc1(loc)

        advance(ticks = 1)
        val dest = route.lastOrNull()
        checkNotNull(dest)
        checkNotNull(player.interaction)

        player.ifOpenMain(interfaces.hpbar_hud)

        placeMapLoc(CoordGrid(0, 50, 50, 22, 43), locTypes.getValue(1274))

        advance(ticks = 1)
        assertTrue(route.isNotEmpty())
        assertNotNull(player.interaction)

        advanceUntil(route::isEmpty) {
            "Could not reach destination: coords=${player.coords}, dest=$dest"
        }

        // Player should be still; however, the interaction is not cleared until the player is no
        // longer flagged as busy.
        assertTrue(route.isEmpty())
        assertNotNull(player.interaction)

        player.ifClose(eventBus)

        advance(ticks = 1)
        assertNull(player.interaction)
    }

    /**
     * When an ap interaction fails due to distance requirements, `apRangeCalled` should be set to
     * `true`. This signals to the engine that the player must continue movement along their initial
     * route to reach the required ap distance.
     */
    @Test
    fun GameTestState.`restore cleared waypoints on ap interaction when apRangeCalled is true`() =
        runGameTest(ApTestScript::class) {
            val loc = placeMapLoc(CoordGrid(0, 50, 50, 35, 28), signpost)
            val start = CoordGrid(0, 50, 50, 34, 20)
            player.enableWalk()
            player.teleport(start)
            player.opLoc1(loc)

            val expectedPath =
                listOf(
                    CoordGrid(0, 50, 50, 34, 21),
                    CoordGrid(0, 50, 50, 34, 22),
                    CoordGrid(0, 50, 50, 34, 23),
                )

            for (coord in expectedPath) {
                advance(ticks = 1)
                assertEquals(coord, player.coords)
            }

            assertMessageSent(ApTestScript.READ_MESSAGE)

            assertTrue(player.routeDestination.isEmpty())

            advance(ticks = 1)
            assertTrue(player.routeDestination.isEmpty())
        }

    /**
     * When an ap interaction succeeds (the `isWithinDistance` condition passes), previously queued
     * waypoints should not be restored. This happens because `apRangeCalled` is **not** set to
     * `true`, as the ap distance condition was met without needing it.
     */
    @Test
    fun GameTestState.`clear waypoints on ap interaction when initial ap range condition passes`() =
        runGameTest(ApTestScript::class) {
            val loc = placeMapLoc(CoordGrid(0, 50, 50, 35, 28), signpost)
            val start = CoordGrid(0, 50, 50, 34, 25)
            player.enableWalk()
            player.teleport(start)
            player.opLoc1(loc)

            advance(ticks = 1)
            assertTrue(player.routeDestination.isEmpty())
            assertMessageSent(ApTestScript.READ_MESSAGE)
            assertEquals(start, player.coords)
        }

    private class ApTestScript : PluginScript() {
        override fun ScriptContext.startup() {
            onApLoc1(signpost) { apReadSignpost(it.loc) }
            onOpLoc1(signpost) { readSignpost() }
        }

        private fun ProtectedAccess.apReadSignpost(loc: BoundLocInfo) {
            if (isWithinApRange(loc, distance = 5)) {
                readSignpost()
            }
        }

        private fun ProtectedAccess.readSignpost() {
            mes(READ_MESSAGE)
        }

        companion object {
            const val READ_MESSAGE = "You've read the signpost!"
        }
    }

    /**
     * A reroute approach script may override AP range without discarding the active
     * interaction.
     */
    @Test
    fun GameTestState.`continuously re-route towards pathing entity that leaves valid ap range`() =
        runGameTest(ApRerouteTestScript::class) {
            val playerCoord = CoordGrid(0, 1, 1, 0, 0)
            allocZoneCollision(playerCoord)
            allocZoneCollision(playerCoord.translateX(8))
            allocZoneCollision(playerCoord.translateX(16))

            val npcCoord = playerCoord.translateX(5)
            val npc = spawnNpc(npcCoord, man)
            player.varMoveSpeed = MoveSpeed.Walk
            player.teleport(playerCoord)
            player.opNpc2(npc)

            advance(ticks = 1)
            assertMessageSent("Attack target.")

            npc.walk(npc.coords.translateX(3))

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(1), npc.coords)
            assertEquals(playerCoord, player.coords)
            assertMessageSent("Attack target.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(2), npc.coords)
            assertEquals(playerCoord, player.coords)
            assertMessageSent("Attack target.")

            advance(ticks = 1)
            assertEquals(npcCoord.translateX(3), npc.coords)
            assertEquals(playerCoord.translateX(1), player.coords)
            assertMessageSent("Attack target.")

            npc.coords = npc.coords.translateX(5)

            check(npc.coords.chebyshevDistance(player.coords) == 12)

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(2), player.coords)
            assertMessageNotSent("Attack target.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(3), player.coords)
            assertMessageNotSent("Attack target.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(4), player.coords)
            assertMessageNotSent("Attack target.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(5), player.coords)
            assertMessageNotSent("Attack target.")

            advance(ticks = 1)
            assertEquals(playerCoord.translateX(6), player.coords)
            assertMessageSent("Attack target.")
        }

    private class ApRerouteTestScript : PluginScript() {
        override fun ScriptContext.startup() {
            onApNpc2(man) { combatAp(it.npc) }
        }

        private fun ProtectedAccess.combatAp(target: Npc) {
            if (isWithinApRange(target, distance = 7)) {
                mes("Attack target.")
                opNpc2(target)
            }
        }
    }

    private companion object {
        private val signpost =
            locTypeFactory.create {
                name = "Signpost"
                op[0] = "Read"
                width = 2
                length = 2
            }

        private val sheep =
            npcTypeFactory.create {
                name = "Sheep"
                op[0] = "Shear"
                size = 1
            }

        private val man =
            npcTypeFactory.create {
                name = "Man"
                op[1] = "Attack"
                size = 1
                maxRange = 100
            }

        private val dummy =
            npcTypeFactory.create {
                name = "Dummy"
                op[0] = "Talk-to"
                size = 1
                wanderRange = 0
            }

        private val crate =
            locTypeFactory.create {
                name = "Crate"
                op[0] = "Search"
                width = 1
                length = 1
            }
    }
}
