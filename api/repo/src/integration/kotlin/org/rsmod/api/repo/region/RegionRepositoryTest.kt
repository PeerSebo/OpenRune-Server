package org.rsmod.api.repo.region

import org.junit.jupiter.api.Test
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class RegionRepositoryTest {
    @Test
    fun GameTestState.`delete respawning npcs on region clear`() = runGameTest {
        val region = createRegion(template)
        val npc = spawnNpc(region.southWest, man).apply { respawns = true }
        val deathCoord = region.southWest.translate(8, 0)

        check(region.slot != -1)
        check(npc.isValidTarget())

        player.placeAt(region.southWest)
        npc.telejump(deathCoord)

        advance(ticks = 1)
        npcRepo.despawn(npc, man.respawnRate)

        advance(ticks = 1)
        assertFalse(npc.isValidTarget())
        assertEquals(npc, npcRepo.findAll(deathCoord).single())

        player.telejump(CoordGrid(0, 50, 50, 0, 0))

        advance(ticks = 1)
        check(!npc.isValidTarget()) { "Expected npc to be mid-respawn. (npc=$npc)" }
        check(npc.isSlotAssigned) { "Expected npc to still have a slot assigned. (npc=$npc)" }
        removeInactiveRegions()

        advance(ticks = 1)
        check(region.slot == -1) { "Expected region to be deleted. (region=$region)" }
        assertFalse(npc.isValidTarget())
        assertEquals(emptyList<Npc>(), npcRepo.findAll(deathCoord).toList())
        assertEquals(emptyList<Npc>(), npcRepo.findAll(npc.spawnCoords).toList())

        advance(ticks = man.respawnRate)
        assertFalse(npc.isValidTarget())
        assertEquals(emptyList<Npc>(), npcRepo.findAll(deathCoord).toList())
        assertEquals(emptyList<Npc>(), npcRepo.findAll(npc.spawnCoords).toList())
    }

    private companion object {
        private val template =
            RegionTemplate.create {
                copy(225, 562, 0) {
                    zoneWidth = 4
                    zoneLength = 4
                }
            }

        private val man =
            npcTypeFactory.create {
                name = "Man"
                op[1] = "Attack"
                size = 1
                hitpoints = 10
                wanderRange = 0
                respawnRate = 10
            }
    }
}
