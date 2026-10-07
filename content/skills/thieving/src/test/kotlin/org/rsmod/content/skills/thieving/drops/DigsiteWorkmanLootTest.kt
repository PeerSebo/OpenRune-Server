package org.rsmod.content.skills.thieving.drops

import dtx.core.ArgMap
import dtx.core.RollResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.game.entity.Player

class DigsiteWorkmanLootTest {
    @Test
    fun `workmen provide the quest skull and every digging supply before quest completion`() {
        val loot = drops(0)
        assertEquals(11.0, loot.values.sumOf { it.second })
        assertEquals(3.0, loot.getValue("obj.rock_sample1").second)
        assertEquals(3.0, loot.getValue("obj.specimen_brush").second)
        assertEquals(1.0, loot.getValue("obj.coins").second)
        assertEquals(10..10, loot.getValue("obj.coins").first.count)
        for (item in listOf("rope", "bucket_empty", "leather_gloves", "spade")) {
            assertEquals(1.0, loot.getValue("obj.$item").second)
        }
        assertSame(PickpocketDropTables.digsiteWorkman(0), PickpocketDropTables.digsiteWorkman(8))
    }

    @Test
    fun `completed quest replaces skull slots with direct coins at the exact cache threshold`() {
        val loot = drops(9)
        assertEquals(11.0, loot.values.sumOf { it.second })
        assertEquals(4.0, loot.getValue("obj.coins").second)
        assertEquals(10..10, loot.getValue("obj.coins").first.count)
        assertFalse("obj.rock_sample1" in loot)
        assertFalse(loot.keys.any { "coin_pouch" in it })
        assertSame(PickpocketDropTables.digsiteWorkman(9), PickpocketDropTables.digsiteWorkman(10))
    }

    private fun drops(progress: Int): Map<String, Pair<DropRollItem, Double>> =
        PickpocketDropTables.digsiteWorkman(progress).tableEntries.associate {
            val drop = (it.roll(Player(), ArgMap()) as RollResult.Single).result
            drop.obj to (drop to it.weight)
        }
}
