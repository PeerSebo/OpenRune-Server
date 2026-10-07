package org.rsmod.content.minigames.blastfurnace

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class BlastFurnaceCacheContractTest {
    @Test
    fun `workers return to a stationary default after facing a player`() {
        for (name in listOf("pedaller", "pumper", "stoker_empty", "stoker_coke", "cog_repairer", "pipe_repairer")) {
            val type = checkNotNull(ServerCacheManager.getNpc("npc.blast_furnace_automaton_$name".asRSCM()))
            val worker = Npc(type, CoordGrid(1950, 4963))
            val player = Player().apply { slotId = 1; coords = worker.coords.translateX(1) }
            worker.playerFaceClose(player)
            assertEquals(NpcMode.PlayerFaceClose, worker.mode)
            worker.resetMode()
            worker.defaultMode()
            assertEquals(NpcMode.None, worker.mode, name)
            assertEquals(MoveRestrict.NoMove, type.moveRestrict, name)
            assertEquals(0, type.wanderRange, name)
        }
    }

    @Test
    fun `belt visual npcs have no interaction operations and native effects are available`() {
        for (ore in listOf("tin_ore", "copper_ore", "iron_ore", "mithril_ore", "adamantite_ore", "runite_ore", "silver_ore", "gold_ore", "coal", "lead_ore", "nickel_ore", "smithing_catalyst")) {
            val npc = checkNotNull(ServerCacheManager.getNpc("npc.blast_furnace_$ore".asRSCM()))
            for (op in 1..5) assertFalse(npc.hasOp(op), "$ore op $op")
        }
        for (seq in listOf("conveyer_ore_fall", "conveyer_belt", "conveyer_cogs", "ore_dispense", "temp_gauge51")) {
            val animation = checkNotNull(ServerCacheManager.getAnim("seq.blast_furnace_$seq".asRSCM()))
            assertTrue(animation.tickDuration > 0, seq)
        }
        val dispenser = checkNotNull(ServerCacheManager.getObject("loc.blast_furnace_dispenser".asRSCM()))
        assertEquals("loc.blast_furnace_ore_dispenser_foranim".asRSCM(), dispenser.multiLoc[1])
    }

    @Test
    fun `foreman permission resolves to an actual packed varbit`() {
        val permission = checkNotNull(
            ServerCacheManager.getVarbit(BlastFurnaceParams.FOREMAN_PERMISSION_VARBIT.asRSCM()),
        ) { "Foreman permission must have a packed definition, not just a gameval name" }
        assertEquals("varp.blast_furnace_access".asRSCM(), permission.baseVar.id)
        assertEquals(VarpLifetime.Perm, permission.baseVar.scope)
        assertFalse(permission.baseVar.transmit.always || permission.baseVar.transmit.onDiff)
    }

    @Test
    fun `native ore and bar stocks survive player saves`() {
        for (name in listOf("1", "2", "3", "4", "5", "6", "7")) {
            val varp = checkNotNull(ServerCacheManager.getVarp("varp.blast_furnace_$name".asRSCM()))
            assertEquals(VarpLifetime.Perm, varp.scope, "Stock varp $name must be persistent")
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .first { Files.isRegularFile(it.resolve(".data/gamevals-binary/gamevals.dat")) }
            val originalDirectory = System.getProperty("user.dir")
            try {
                System.setProperty("user.dir", root.toString())
                GameValProvider.load(root.toString() + "/")
                ServerCacheManager.init(root.resolve(".data/cache/SERVER"), 240).close()
            } finally {
                System.setProperty("user.dir", originalDirectory)
            }
        }
    }
}
