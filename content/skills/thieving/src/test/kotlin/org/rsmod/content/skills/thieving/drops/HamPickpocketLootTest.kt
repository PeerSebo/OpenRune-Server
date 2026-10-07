package org.rsmod.content.skills.thieving.drops

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.rs.RSWeightedTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.content.skills.thieving.configs.CoinPouches
import org.rsmod.game.entity.Player

class HamPickpocketLootTest {
    @Test
    fun `normal loot contains all seven robe pieces and the quest junk at their published weights`() {
        val entries = directDrops()
        for (piece in listOf("boots", "cloak", "gloves", "hood", "badge", "robe", "shirt")) {
            assertEquals(1.0, entries.getValue("obj.ham_$piece").second)
        }
        for (junk in listOf("digsitebuttons", "digsitearmour1", "digsitesword")) {
            assertEquals(4.0, entries.getValue("obj.$junk").second)
        }
        assertEquals(15.0, entries.getValue(CoinPouches.HAM).second)
        assertEquals(98.0, PickpocketDropTables.hamNormalLoot.tableEntries.sumOf { it.weight })
    }

    @Test
    fun `stackable supplies retain their variable quantities and herbs occupy a real subtable`() {
        val entries = directDrops()
        assertEquals(1..13, entries.getValue("obj.bronze_arrow").first.count)
        assertEquals(1..13, entries.getValue("obj.steel_arrow").first.count)
        assertEquals(1..7, entries.getValue("obj.feather").first.count)
        assertEquals(1..10, entries.getValue("obj.thread").first.count)
        val herbs = PickpocketDropTables.hamNormalLoot.tableEntries.single { it.rollable is RSWeightedTable<*, *> }
        assertEquals(2.0, herbs.weight)
        @Suppress("UNCHECKED_CAST")
        val table = herbs.rollable as RSWeightedTable<Player, DropRollItem>
        val drops = table.tableEntries.associate {
            (it.roll(Player(), ArgMap()) as RollResult.Single).result.obj to it.weight
        }
        assertEquals(mapOf("obj.unidentified_guam" to 6.0, "obj.unidentified_marentill" to 3.0, "obj.unidentified_tarromin" to 2.0), drops)
        assertTrue("obj.bread" !in entries && "obj.silver_ore" !in entries)
    }

    private fun directDrops(): Map<String, Pair<DropRollItem, Double>> =
        PickpocketDropTables.hamNormalLoot.tableEntries.filter { it.rollable !is RSWeightedTable<*, *> }.associate {
            val drop = (it.roll(Player(), ArgMap()) as RollResult.Single).result
            drop.obj to (drop to it.weight)
        }
}
