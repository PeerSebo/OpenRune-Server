package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants
import org.rsmod.content.minigames.tempoross.configs.TemporossOutfitUnlocks
import org.rsmod.content.minigames.tempoross.configs.migrateTemporossVars
import org.rsmod.content.minigames.tempoross.configs.temporossBestPoints
import org.rsmod.content.minigames.tempoross.configs.temporossBestTicks
import org.rsmod.content.minigames.tempoross.configs.temporossRewardSearches
import org.rsmod.content.minigames.tempoross.scripts.TemporossPlayerScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class TemporossVarsTest {
    @Test fun `login migrates old saves while preserving permits kills and pending rewards`() {
        val player = Player()
        val pending = mapOf(
            "tempoross_pending_pool_reward" to "obj.spirit_flakes,48,false",
            "tempoross_pending_casket_reward" to "obj.coins,5000,false;obj.keyhalf1,1,true",
            "other_content" to 7,
        )
        player.attr.putAllFromPersistence(pending + mapOf(
            "tempoross_best_points" to 8700,
            "tempoross_best_ticks" to 421,
            "tempoross_reward_searches" to 123,
            "tempoross_spirit_angler_hat" to true,
            "tempoross_spirit_angler_top" to true,
            "tempoross_spirit_angler_legs" to false,
            "tempoross_spirit_angler_boots" to true,
        ))
        VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_PERMITS, 99)
        VarPlayerIntMapSetter.set(player, TemporossStats.KILLS, 17)

        val events = EventBus()
        with(TemporossPlayerScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        events.publish(SessionStateEvent.Login(player))

        assertEquals(8700, player.temporossBestPoints)
        assertEquals(421, player.temporossBestTicks)
        assertEquals(123, player.temporossRewardSearches)
        for (part in listOf("hat", "top", "boots")) assertTrue(TemporossOutfitUnlocks.has(player, part))
        assertFalse(TemporossOutfitUnlocks.has(player, "legs"))
        assertEquals(99, player.vars[TemporossConstants.VARBIT_PERMITS])
        assertEquals(17, player.vars[TemporossStats.KILLS])
        assertEquals(pending, player.attr.toPersistentMap())
    }

    @Test fun `migration is idempotent and keeps better current records and unlocks`() {
        val player = Player()
        player.temporossBestPoints = 9000
        player.temporossBestTicks = 400
        player.temporossRewardSearches = 150
        TemporossOutfitUnlocks.unlock(player, "legs")
        player.attr.putAllFromPersistence(mapOf(
            "tempoross_best_points" to 8700,
            "tempoross_best_ticks" to 421,
            "tempoross_reward_searches" to 123,
            "tempoross_spirit_angler_legs" to false,
            "tempoross_spirit_angler_hat" to true,
        ))
        repeat(2) { player.migrateTemporossVars() }
        assertEquals(9000, player.temporossBestPoints)
        assertEquals(400, player.temporossBestTicks)
        assertEquals(150, player.temporossRewardSearches)
        assertTrue(TemporossOutfitUnlocks.has(player, "legs"))
        assertTrue(TemporossOutfitUnlocks.has(player, "hat"))
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test fun `new characters keep empty defaults and invalid legacy records are discarded`() {
        val player = Player()
        player.migrateTemporossVars()
        assertEquals(0, player.temporossBestPoints)
        assertEquals(0, player.temporossBestTicks)
        assertEquals(0, player.temporossRewardSearches)
        player.attr.putAllFromPersistence(mapOf(
            "tempoross_best_points" to -1,
            "tempoross_best_ticks" to -1,
            "tempoross_reward_searches" to -1,
        ))
        player.migrateTemporossVars()
        assertEquals(0, player.temporossBestPoints)
        assertEquals("-", TemporossStats.time(player.temporossBestTicks))
        assertEquals(0, player.temporossRewardSearches)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test fun `new saved vars survive reload and explicit resets do not restore old attributes`() {
        val original = Player()
        original.attr.putAllFromPersistence(mapOf("tempoross_best_points" to 8700))
        original.migrateTemporossVars()
        original.temporossBestTicks = 421
        original.temporossRewardSearches = 123
        TemporossOutfitUnlocks.unlock(original, "boots")

        val restored = Player()
        restored.vars.backing.putAll(original.vars.backing)
        restored.attr.putAllFromPersistence(original.attr.toPersistentMap())
        restored.migrateTemporossVars()
        assertEquals(8700, restored.temporossBestPoints)
        assertEquals(421, restored.temporossBestTicks)
        assertEquals(123, restored.temporossRewardSearches)
        assertTrue(TemporossOutfitUnlocks.has(restored, "boots"))
        restored.temporossBestPoints = 0
        restored.temporossBestTicks = 0
        restored.temporossRewardSearches = 0
        VarPlayerIntMapSetter.set(restored, "varp.tempoross_outfit_unlocks", 0)
        restored.migrateTemporossVars()
        assertEquals(0, restored.temporossBestPoints)
        assertEquals(0, restored.temporossBestTicks)
        assertEquals(0, restored.temporossRewardSearches)
        assertFalse(TemporossOutfitUnlocks.has(restored, "boots"))
    }

    @Test fun `owned records are permanent server only and outfit bits are independent`() {
        for (name in listOf("best_points", "best_ticks", "reward_searches", "outfit_unlocks")) {
            val varp = checkNotNull(ServerCacheManager.getVarp("varp.tempoross_$name".asRSCM()))
            assertEquals(VarpLifetime.Perm, varp.scope)
            assertEquals(VarpTransmitLevel.Never, varp.transmit)
        }
        for (part in TemporossOutfitUnlocks.parts) {
            val player = Player()
            TemporossOutfitUnlocks.unlock(player, part)
            for (other in TemporossOutfitUnlocks.parts) {
                assertEquals(part == other, TemporossOutfitUnlocks.has(player, other))
            }
        }
    }

    @Test fun `victory notification colors only the number and count saturates`() {
        val client = RecordingClient()
        val player = Player().apply { this.client = client }
        TemporossStats.recordWin(player, 3000, 450)
        assertTrue(client.messages.any { "Tempoross <col=ff0000>1</col> time." in it.toString() })
        VarPlayerIntMapSetter.set(player, TemporossStats.KILLS, Int.MAX_VALUE)
        TemporossStats.recordWin(player, 3500, 400)
        assertEquals(Int.MAX_VALUE, player.vars[TemporossStats.KILLS])
        assertTrue(client.messages.any { "Tempoross <col=ff0000>${Int.MAX_VALUE}</col> times." in it.toString() })
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
