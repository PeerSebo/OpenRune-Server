package org.rsmod.api.bosses.runtime

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.BossStats
import org.rsmod.api.bosses.spec.PhaseSpec
import org.rsmod.game.entity.Npc

@OptIn(InternalApi::class)
class EncounterRegistryLifecycleTest {
    @Test
    fun `changing protection model preserves the existing encounter`() {
        val registry = registry()
        val npc = npc()
        val originalUid = npc.uid
        val encounter = registry.of(npc)
        encounter.currentPhaseName = "ranged"
        encounter.lastAbilityTick = 250
        encounter.firedTriggers += 1

        npc.transmog(transformedType, Int.MAX_VALUE)
        npc.assignUid()

        assertNotEquals(originalUid, npc.uid)
        assertSame(encounter, registry.of(npc))
        assertEquals("ranged", registry.of(npc).currentPhaseName)
        assertEquals(250, registry.of(npc).lastAbilityTick)
        assertEquals(setOf(1), registry.of(npc).firedTriggers)
    }

    @Test
    fun `respawn cleanup removes the encounter after a protection model change`() {
        val registry = registry()
        val npc = npc()
        val previous = registry.of(npc)
        previous.currentPhaseName = "ranged"
        npc.transmog(transformedType, Int.MAX_VALUE)
        npc.assignUid()

        registry.remove(npc)
        npc.resetTransmog()
        npc.assignUid()
        val fresh = registry.of(npc)

        assertNotSame(previous, fresh)
        assertEquals("melee", fresh.currentPhaseName)
        assertEquals(0, fresh.lastAbilityTick)
    }

    @Test
    fun `replacement npc using the same slot gets its own encounter`() {
        val registry = registry()
        val oldNpc = npc()
        val replacement = npc()

        assertEquals(oldNpc.uid, replacement.uid)
        assertNotSame(registry.of(oldNpc), registry.of(replacement))
    }

    private fun registry(): EncounterRegistry =
        EncounterRegistry(org.rsmod.game.MapClock()).apply {
            register(originalType.id, listOf(spec), spec)
            register(transformedType.id, listOf(spec), spec)
        }

    private fun npc(): Npc = Npc(originalType).apply {
        slotId = 1
        assignUid()
    }

    private val originalType = NpcServerType(id = 1, name = "Boss", size = 1, hitpoints = 50)
    private val transformedType = NpcServerType(id = 2, name = "Boss", size = 1, hitpoints = 50)
    private val spec = BossSpec(
        npcTypes = emptyList(),
        stats = BossStats(),
        abilities = emptyMap(),
        phases = linkedMapOf("melee" to PhaseSpec("melee"), "ranged" to PhaseSpec("ranged")),
        triggers = emptyList(),
    )
}
