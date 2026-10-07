package org.rsmod.content.skills.magic.spell.attacks

import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class SpellEffectsRegressionTest {
    @Test
    fun `target classification uses combat flags instead of misleading NPC names`() {
        val demon = npc("Friendly-looking creature", demon = true)
        assertTrue(SpellEffects.isDemon(demon))
        assertFalse(SpellEffects.isUndead(demon))
        val impostor = npc("Ghostly demon costume")
        assertFalse(SpellEffects.isDemon(impostor))
        assertFalse(SpellEffects.isUndead(impostor))
        assertTrue(SpellEffects.isUndead(npc("Restless villager", undead = true)))
    }

    @Test
    fun `NPC curses drain actual combat levels without stacking or changing base`() {
        val npc = npc("Target").apply { baseDefenceLvl = 100; defenceLvl = 100 }
        SpellEffects.drainPercent(npc, SpellEffects.DEFENCE, 10)
        assertEquals(90, npc.defenceLvl)
        assertEquals(100, npc.baseDefenceLvl)
        SpellEffects.drainPercent(npc, SpellEffects.DEFENCE, 10)
        assertEquals(90, npc.defenceLvl)
        SpellEffects.drainFlat(npc, SpellEffects.DEFENCE, 200)
        assertEquals(0, npc.defenceLvl)
    }

    @Test
    fun `freeze expires and never claims an unrelated existing movement lock`() {
        val npc = npc("Target")
        assertTrue(CombatEffects.freeze(npc, 8))
        assertTrue(CombatEffects.isFrozen(npc))
        assertFalse(npc.movementLocked)
        CombatEffects.unfreeze(npc)
        assertFalse(CombatEffects.isFrozen(npc))
        npc.movementLocked = true
        npc.currentMapClock += 5
        assertTrue(CombatEffects.freeze(npc, 8))
        CombatEffects.unfreeze(npc)
        assertTrue(npc.movementLocked)
        assertFalse(npc.timerMap.isNotEmpty)
    }

    private fun npc(name: String, demon: Boolean = false, undead: Boolean = false): Npc {
        val type = NpcServerType(id = 1, name = name)
        type.paramMap = ParamMap(primitiveMap = mapOf(params.demon.id to if (demon) 1 else 0, params.undead.id to if (undead) 1 else 0))
        return Npc(type, CoordGrid(3200, 3200))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
