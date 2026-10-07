package org.rsmod.content.skills.woodcutting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.stats.xpmod.WornXpModifiers
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasNestCapeBoost
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasStrungRabbitFoot
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WoodcuttingEquipmentTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `each classic and Forestry piece contributes its fractional bonus once`() {
        for (variant in listOf("ramble", "forestry")) {
            for (piece in pieces) {
                val player = player()
                player.worn[piece.slot.slot] = item("obj.${variant}_lumberjack_${piece.name}")
                assertEquals(1.0 + piece.bonus, modifiers.get(player, "stat.woodcutting"), 0.000001)
                assertEquals(1.0, modifiers.get(player, "stat.firemaking"), 0.000001)
            }
        }
    }

    @Test
    fun `every mixed full outfit grants 2 point 5 percent rather than 22 point 5`() {
        for (mask in 0..15) {
            val player = player()
            for ((index, piece) in pieces.withIndex()) {
                val variant = if (mask and (1 shl index) == 0) "ramble" else "forestry"
                player.worn[piece.slot.slot] = item("obj.${variant}_lumberjack_${piece.name}")
            }
            assertEquals(1.025, modifiers.get(player, "stat.woodcutting"), 0.000001)
        }
    }

    @Test
    fun `carrying an outfit grants no worn bonus`() {
        val player = player()
        for ((index, piece) in pieces.withIndex()) {
            player.inv[index] = item("obj.ramble_lumberjack_${piece.name}")
        }
        assertEquals(1.0, modifiers.get(player, "stat.woodcutting"))
    }

    @Test
    fun `only equipped skill capes and ordinary max capes increase nests`() {
        val player = player()
        for (cape in listOf("skillcape_woodcutting", "skillcape_woodcutting_trimmed", "skillcape_max", "skillcape_max_worn")) {
            player.worn[Wearpos.Back.slot] = item("obj.$cape")
            assertTrue(player.hasNestCapeBoost(), cape)
        }
        for (cape in listOf("skillcape_woodcutting_hood", "skillcape_max_firecape", "skillcape_max_infernalcape")) {
            player.worn[Wearpos.Back.slot] = item("obj.$cape")
            assertFalse(player.hasNestCapeBoost(), cape)
        }
        player.worn[Wearpos.Back.slot] = null
        player.inv[0] = item("obj.skillcape_woodcutting")
        assertFalse(player.hasNestCapeBoost())
    }

    @Test
    fun `rabbit foot effect requires the strung necklace to be equipped`() {
        val player = player()
        player.inv[0] = item("obj.hunting_strung_rabbit_foot")
        assertFalse(player.hasStrungRabbitFoot())
        player.worn[Wearpos.Front.slot] = item("obj.hunting_rabbit_foot")
        assertFalse(player.hasStrungRabbitFoot())
        player.worn[Wearpos.Front.slot] = item("obj.hunting_strung_rabbit_foot")
        assertTrue(player.hasStrungRabbitFoot())
    }

    private fun player() = Player().apply {
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
    }

    private fun item(symbol: String) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())))

    private val modifiers = XpModifiers(setOf(WornXpModifiers(), WoodcuttingLumberjackXpModifiers()))
    private val pieces = listOf(
        Piece("hat", Wearpos.Hat, 0.004),
        Piece("top", Wearpos.Torso, 0.008),
        Piece("legs", Wearpos.Legs, 0.006),
        Piece("boots", Wearpos.Feet, 0.002),
    )

    private data class Piece(val name: String, val slot: Wearpos, val bonus: Double)
}
