package org.rsmod.api.game.process.npc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.entityFactory
import org.rsmod.api.testing.factory.npcFactory
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.map.collision.add
import org.rsmod.game.map.collision.get
import org.rsmod.game.map.collision.set
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.flag.CollisionFlag

class NpcMovementProcessorTest {
    /**
     * Reapply stationary NPC collision every cycle: another entity may have cleared the shared
     * tile flag.
     */
    @Test
    fun GameTestState.`reapply collision flag without steps but valid route destination`() =
        runBasicGameTest {
            withCollisionState {
                val targetCoords = CoordGrid(0, 0, 0, 0, 7)
                val startCoords = CoordGrid(0, 0, 0, 0, 0)
                val type = npcTypeFactory.create()
                val npc = npcFactory.create(type, startCoords)
                val target = entityFactory.createAvatar(targetCoords)
                it.allocateCollision(ZoneKey(0, 0, 0))
                it.collision.add(CoordGrid(0, 0, 0, 0, 3), CollisionFlag.LOC)
                withNpc(npc) {
                    val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                    check(target.coords == targetCoords)
                    check(routeDestination.isEmpty())

                    fun process() {
                        previousCoords = coords
                        currentMapClock++
                        processedMapClock++
                        movement.process(this)
                    }

                    move(target)

                    repeat(2) { index ->
                        val steps = 1 + index
                        process()
                        assertEquals(startCoords.translateZ(steps), coords)
                        assertEquals(targetCoords.translateZ(-1), routeDestination.peekLast())
                    }

                    val stopCoords = coords

                    process()
                    assertEquals(stopCoords, coords)
                    assertEquals(CollisionFlag.BLOCK_NPCS, it.collision[coords])

                    it.collision[coords] = 0
                    check(it.collision[coords] == 0)

                    process()
                    assertEquals(CollisionFlag.BLOCK_NPCS, it.collision[coords])
                    assertEquals(stopCoords, coords)
                }
            }
        }
}
