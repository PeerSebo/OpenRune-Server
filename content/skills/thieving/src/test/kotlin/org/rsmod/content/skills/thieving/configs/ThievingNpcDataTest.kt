package org.rsmod.content.skills.thieving.configs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.thieving.drops.PickpocketDropTables
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class ThievingNpcDataTest {
    @Test
    fun `all effective profiles match the independent pre-migration catalogue in order`() {
        val expected = LegacyPickpocketCatalogue.all
        val actual = ThievingNpcs.all

        assertEquals(28, actual.size)
        assertEquals(expected.map { it.copy(loot = ignoredLoot) }, actual.map { it.snapshot() })
        assertEquals(
            LegacyPickpocketCatalogue.blackjackTargets.map { it.copy(loot = ignoredLoot) },
            ThievingNpcs.blackjackTargets.map { it.snapshot() },
        )
    }

    @Test
    fun `every public profile accessor preserves its original base NPC list and scalar fields`() {
        val oldGetters = LegacyPickpocketCatalogue::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == LegacyPickpocketNpc::class.java }
            .associateBy { it.name }
        val newGetters = ThievingNpcs::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == PickpocketNpc::class.java }
            .associateBy { it.name }

        assertEquals(27, oldGetters.size)
        assertEquals(oldGetters.keys, newGetters.keys)
        for ((name, getter) in oldGetters) {
            val expected = getter.invoke(LegacyPickpocketCatalogue) as LegacyPickpocketNpc
            val actual = newGetters.getValue(name).invoke(ThievingNpcs) as PickpocketNpc
            assertEquals(expected.copy(loot = ignoredLoot), actual.snapshot(), name)
        }
    }

    @Test
    fun `all 445 bindings retain their operation and all 93 added NPCs keep native Pickpocket`() {
        val expected = LegacyPickpocketCatalogue.all.flatMap { target ->
            target.npcs.map { it to target.pickpocketOp }
        }
        val actual = ThievingNpcs.all.flatMap { target ->
            target.npcs.map { it to target.pickpocketOp }
        }
        assertEquals(445, actual.size)
        assertEquals(expected, actual)
        assertEquals(actual.size, actual.map { it.first }.distinct().size)
        assertEquals(LegacyPr216PickpocketTargets.manWoman, Pr216PickpocketTargets.manWoman)
        assertEquals(LegacyPr216PickpocketTargets.guards, Pr216PickpocketTargets.guards)
        assertEquals(LegacyPr216PickpocketTargets.hamGuards, Pr216PickpocketTargets.hamGuards)
        val additional = Pr216PickpocketTargets.manWoman +
            Pr216PickpocketTargets.guards + Pr216PickpocketTargets.hamGuards
        assertEquals(93, additional.size)
        val operations = actual.toMap()
        for (npc in additional) {
            val type = checkNotNull(ServerCacheManager.getNpc(npc.asRSCM()))
            assertEquals("Pickpocket", type.actions.getOpOrNull(operations.getValue(npc) - 1), npc)
        }
        for (npc in Pr216PickpocketTargets.hamGuards) assertEquals(1, operations.getValue(npc))
    }

    @Test
    fun `every loot callback preserves the old table across farming and quest thresholds`() {
        for (farming in listOf(1, 84, 85, 99)) {
            for (progress in listOf(0, 8, 9, 10)) {
                val player = player(farming, progress)
                for ((old, current) in LegacyPickpocketCatalogue.all.zip(ThievingNpcs.all)) {
                    assertSame(
                        old.loot(player), current.loot(player),
                        "${old.displayName}, Farming $farming, exam $progress",
                    )
                }
            }
        }
    }

    @Test
    fun `master farmer callback uses the current farming level at the 85 boundary`() {
        val player = player(farming = 84)
        player.statMap.setBaseLevel("stat.farming", 99)
        assertSame(PickpocketDropTables.masterFarmer, ThievingNpcs.masterFarmer.loot(player))

        player.statMap.setCurrentLevel("stat.farming", 85)
        player.statMap.setBaseLevel("stat.farming", 1)
        assertSame(PickpocketDropTables.masterFarmerHighFarming, ThievingNpcs.masterFarmer.loot(player))

        player.statMap.setCurrentLevel("stat.farming", 84)
        assertSame(PickpocketDropTables.masterFarmer, ThievingNpcs.masterFarmer.loot(player))
    }

    @Test
    fun `Digsite callback rereads exam progress and preserves its separate action delay`() {
        val player = player(examProgress = 8)
        assertSame(PickpocketDropTables.digsiteWorkman(8), ThievingNpcs.digsiteWorkman.loot(player))
        VarPlayerIntMapSetter.set(player, "varp.itexamlevel", 9)
        assertSame(PickpocketDropTables.digsiteWorkman(9), ThievingNpcs.digsiteWorkman.loot(player))
        assertEquals(4, ThievingNpcs.digsiteWorkman.actionDelay)
        assertFalse(ThievingNpcs.digsiteWorkman.usesCoinPouches)
    }

    @Test
    fun `negative chance endpoints fractional XP and direct coin distinctions survive packing`() {
        assertEquals(-200, ThievingNpcs.tzhaarHur.low)
        assertEquals(200, ThievingNpcs.tzhaarHur.high)
        assertTrue(ThievingNpcs.tzhaarHur.alwaysDamagesWithoutIceGloves)
        assertEquals(306.9, ThievingNpcs.vyre.xp)
        assertEquals(84.3, ThievingNpcs.knightOfArdougne.xp)
        assertEquals(0.0, ThievingNpcs.villager.xp)
        assertEquals(8.0, ThievingNpcs.villager.koXp)
        assertEquals(5, ThievingNpcs.villager.directCoins)
        assertFalse(ThievingNpcs.villager.usesCoinPouches)
        assertEquals(null, ThievingNpcs.villager.pouch)
        assertTrue(ThievingNpcs.prifddinasElf.prifddinas)
        assertFalse(ThievingNpcs.elf.prifddinas)
    }

    @Test
    fun `cache rows preserve typed columns explicit order and optional NPC and pouch fields`() {
        val table = "dbtable.thieving_pickpockets".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(table))
        val rows = DbHelper.table("dbtable.thieving_pickpockets")
            .sortedBy { it.column("dbcol.thieving_pickpockets:order", IntCodec) }
        assertEquals(28, rows.size)
        assertEquals(28, rows.distinctBy { it.id }.size)
        val columns = mapOf(
            "order" to VarType.INT, "npcs" to VarType.NPC,
            "display_name" to VarType.STRING, "level" to VarType.INT,
            "xp_fine" to VarType.INT, "low" to VarType.INT, "high" to VarType.INT,
            "damage" to VarType.INT, "fail_bark" to VarType.STRING,
            "in_ardougne" to VarType.BOOLEAN, "prifddinas" to VarType.BOOLEAN,
            "pickpocket_op" to VarType.INT, "blackjack" to VarType.BOOLEAN,
            "stun_movement" to VarType.INT, "stun_action" to VarType.INT,
            "action_delay" to VarType.INT, "direct_coins" to VarType.INT,
            "uses_coin_pouches" to VarType.BOOLEAN, "ko_xp_fine" to VarType.INT,
            "always_damages_without_ice_gloves" to VarType.BOOLEAN,
        )
        for ((index, row) in rows.withIndex()) {
            assertEquals(table, row.tableId)
            assertEquals(index, row.column("dbcol.thieving_pickpockets:order", IntCodec))
            for ((name, type) in columns) {
                assertEquals(listOf(type), row.getColumn("dbcol.thieving_pickpockets:$name").types.toList())
            }
            if (LegacyPickpocketCatalogue.all[index].pouch != null) {
                assertEquals(listOf(VarType.OBJ), row.getColumn("dbcol.thieving_pickpockets:pouch").types.toList())
            }
        }
        for (name in listOf("man_woman", "guard")) {
            val row = DbHelper.row("dbrow.thieving_pickpocket_$name")
            assertEquals(listOf(VarType.NPC), row.getColumn("dbcol.thieving_pickpockets:additional_npcs").types.toList())
        }
    }

    private fun player(farming: Int = 1, examProgress: Int = 0) = Player().apply {
        statMap.setCurrentLevel("stat.farming", farming.toByte())
        VarPlayerIntMapSetter.set(this, "varp.itexamlevel", examProgress)
    }

    private fun PickpocketNpc.snapshot() = LegacyPickpocketNpc(
        npcs = npcs, displayName = displayName, levelReq = levelReq, xp = xp,
        low = low, high = high, damage = damage, failBark = failBark, pouch = pouch,
        loot = ignoredLoot, inArdougne = inArdougne, prifddinas = prifddinas,
        pickpocketOp = pickpocketOp, blackjack = blackjack, stunMovement = stunMovement,
        stunAction = stunAction, actionDelay = actionDelay, directCoins = directCoins,
        usesCoinPouches = usesCoinPouches, koXp = koXp,
        alwaysDamagesWithoutIceGloves = alwaysDamagesWithoutIceGloves,
    )

    companion object {
        private val ignoredLoot: (Player) -> Nothing? = { null }

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
        }
    }
}
