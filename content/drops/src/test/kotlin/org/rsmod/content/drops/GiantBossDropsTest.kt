package org.rsmod.content.drops

import dtx.core.AllOf
import dtx.core.AnyOf
import dtx.core.Rollable
import dtx.core.Single
import dtx.impl.chance.ChanceRollable
import dtx.impl.weighted.WeightedRollable
import dtx.rs.RSWeightedTable
import dtx.table.Table
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.content.drops.tables.locs.bryophytaChestDropTable
import org.rsmod.content.drops.tables.locs.oborChestDropTable
import org.rsmod.content.drops.tables.monsters.bryophytaDropTable
import org.rsmod.content.drops.tables.monsters.oborDropTable
import org.rsmod.game.entity.Player

class GiantBossDropsTest {
    @Test fun `native chest NPC and loc bindings coexist without placing uniques on free kills`() {
        assertEquals(listOf("npc.gb_hillgiant_chest"), oborChestDropTable.npcs)
        assertEquals(listOf("loc.giantkey_chest_closed"), oborChestDropTable.locs)
        assertEquals(listOf("npc.gb_mossgiant_chest"), bryophytaChestDropTable.npcs)
        assertEquals(listOf("loc.mossykey_chest_closed"), bryophytaChestDropTable.locs)
        assertNotSame(oborDropTable, oborChestDropTable)
        assertNotSame(bryophytaDropTable, bryophytaChestDropTable)
        assertNotNull(oborDropTable.tableEntries)
        assertNotNull(bryophytaDropTable.tableEntries)
        for (table in listOf(oborDropTable, bryophytaDropTable)) {
            assertFalse("obj.hillgiant_boss_club" in objects(table))
            assertFalse("obj.gb_moss_essence" in objects(table))
        }
    }

    @Test fun `chest uniques remain one slot of118 and gem pairs share one roll`() {
        val obor = oborChestDropTable.tableEntries.elementAt(3) as RSWeightedTable<Player, DropRollItem>
        val bryo = (bryophytaChestDropTable.tableEntries.elementAt(3) as Table<Player, DropRollItem>)
            .tableEntries.map { it as RSWeightedTable<Player, DropRollItem> }
        for (table in listOf(obor) + bryo) {
            assertEquals(118.0, table.tableEntries.sumOf { it.weight })
            val unique = table.tableEntries.single { "obj.hillgiant_boss_club" in objects(it) || "obj.gb_moss_essence" in objects(it) }
            assertEquals(1.0, unique.weight)
            val gems = table.tableEntries.filter { "obj.cert_uncut_ruby" in objects(it) || "obj.cert_uncut_diamond" in objects(it) }
            assertEquals(1, gems.size)
            assertTrue(objects(gems.single()).containsAll(listOf("obj.cert_uncut_ruby", "obj.cert_uncut_diamond")))
            assertEquals(if (table === obor) 5.0 else 4.0, gems.single().weight)
        }
        assertTrue("obj.cert_strength4" in objects(bryo[1]))
        assertFalse("obj.bloodrune" in objects(bryo[1]))
    }

    private fun objects(rollable: Rollable<Player, DropRollItem>): Set<String> = when (rollable) {
        is Single<Player, DropRollItem> -> setOf(rollable.result.obj)
        is WeightedRollable<Player, DropRollItem> -> objects(rollable.rollable)
        is ChanceRollable<Player, DropRollItem> -> objects(rollable.rollable)
        is Table<Player, DropRollItem> -> rollable.tableEntries.flatMap { objects(it) }.toSet()
        is AllOf<Player, DropRollItem> -> rollable.rollables.flatMap { objects(it) }.toSet()
        is AnyOf<Player, DropRollItem> -> rollable.rollables.flatMap { objects(it) }.toSet()
        else -> emptySet()
    }
}
