package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.gotr.scripts.GotrPlayerScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class GotrVarsTest {
    @Test
    fun `login imports all four old reward fields and preserves unrelated state`() {
        val player = Player()
        val retained = mapOf("unrelated" to 7)
        player.attr.putAllFromPersistence(legacyRewards + retained)
        VarPlayerIntMapSetter.set(player, "varbit.gotr_colourful_fade_disabled", 1)
        VarPlayerIntMapSetter.set(player, "varbit.gotr_unlocked_needle", 1)

        login(player)

        assertEquals(2, player.gotrElementalPoints)
        assertEquals(3, player.gotrCatalyticPoints)
        assertEquals(41, player.gotrTotalSearches)
        assertEquals(17, player.gotrRiftsClosed)
        assertEquals(1, player.vars["varp.gotr_rewards_migrated"])
        assertEquals(1, player.vars["varbit.gotr_colourful_fade_disabled"])
        assertEquals(1, player.vars["varbit.gotr_unlocked_needle"])
        assertEquals(retained, player.attr.toPersistentMap())
    }

    @Test
    fun `new characters retain empty defaults and repeated login cannot import later stale attributes`() {
        val player = Player()
        login(player)
        player.attr.putAllFromPersistence(legacyRewards)

        repeat(2) { login(player) }

        assertRewardsEmpty(player)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `negative legacy counters become empty without truncating positive saved balances`() {
        val invalid = Player()
        invalid.attr.putAllFromPersistence(legacyRewards.mapValues { -1 })
        invalid.migrateGotrRewards()
        assertRewardsEmpty(invalid)
        assertTrue(invalid.attr.toPersistentMap().isEmpty())

        val credited = Player()
        credited.attr.putAllFromPersistence(legacyRewards.mapValues { 80_000 })
        credited.migrateGotrRewards()
        assertEquals(80_000, credited.gotrElementalPoints)
        assertEquals(80_000, credited.gotrCatalyticPoints)
        assertEquals(80_000, credited.gotrTotalSearches)
        assertEquals(80_000, credited.gotrRiftsClosed)
    }

    @Test
    fun `existing vars including explicit zero win before a migration marker exists`() {
        val player = Player()
        player.attr.putAllFromPersistence(legacyRewards)
        player.gotrElementalPoints = 0
        player.gotrTotalSearches = 49
        player.gotrRiftsClosed = 0

        player.migrateGotrRewards()

        assertEquals(0, player.gotrElementalPoints)
        assertEquals(3, player.gotrCatalyticPoints)
        assertEquals(49, player.gotrTotalSearches)
        assertEquals(0, player.gotrRiftsClosed)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `search payment imports balances and spends both energy types only when both are available`() {
        val player = Player()
        player.attr.putAllFromPersistence(legacyRewards)

        assertEquals(2, player.gotrEnergySearches())
        assertTrue(player.spendGotrEnergy())
        assertEquals(1, player.gotrElementalPoints)
        assertEquals(2, player.gotrCatalyticPoints)
        assertTrue(player.spendGotrEnergy())
        assertFalse(player.spendGotrEnergy())
        assertEquals(0, player.gotrElementalPoints)
        assertEquals(1, player.gotrCatalyticPoints)
        assertEquals(0, player.gotrEnergySearches())
        assertEquals(41, player.gotrTotalSearches)
        assertEquals(17, player.gotrRiftsClosed)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `reload preserves spent balances and cannot resurrect rewards from stale attributes`() {
        val original = Player()
        original.attr.putAllFromPersistence(legacyRewards)
        repeat(2) { assertTrue(original.spendGotrEnergy()) }
        original.gotrTotalSearches = 43
        original.gotrRiftsClosed = 18
        val restored = reload(original)
        restored.attr.putAllFromPersistence(legacyRewards)

        repeat(2) { login(restored) }

        assertEquals(0, restored.gotrElementalPoints)
        assertEquals(1, restored.gotrCatalyticPoints)
        assertEquals(43, restored.gotrTotalSearches)
        assertEquals(18, restored.gotrRiftsClosed)
        assertFalse(restored.spendGotrEnergy())
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `explicit resets remain zero after reload even if stale attributes reappear`() {
        val original = Player()
        original.attr.putAllFromPersistence(legacyRewards)
        original.migrateGotrRewards()
        original.gotrElementalPoints = 0
        original.gotrCatalyticPoints = 0
        original.gotrTotalSearches = 0
        original.gotrRiftsClosed = 0
        val restored = reload(original)
        restored.attr.putAllFromPersistence(legacyRewards)

        login(restored)

        assertRewardsEmpty(restored)
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `packed counters and migration marker are permanent and never transmitted`() {
        for (name in legacyRewards.keys + "gotr_rewards_migrated") {
            val varp = checkNotNull(ServerCacheManager.getVarp("varp.$name".asRSCM()))
            assertEquals(VarpLifetime.Perm, varp.scope, name)
            assertEquals(VarpTransmitLevel.Never, varp.transmit, name)
        }
    }

    private fun assertRewardsEmpty(player: Player) {
        assertEquals(0, player.gotrElementalPoints)
        assertEquals(0, player.gotrCatalyticPoints)
        assertEquals(0, player.gotrTotalSearches)
        assertEquals(0, player.gotrRiftsClosed)
        assertEquals(0, player.gotrEnergySearches())
    }

    private fun login(player: Player) {
        val events = EventBus()
        with(GotrPlayerScript()) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        events.publish(SessionStateEvent.Login(player))
    }

    private fun reload(player: Player) = Player().apply {
        vars.backing.putAll(player.vars.backing.filterKeys { id ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    companion object {
        private val legacyRewards = mapOf(
            "gotr_elemental_points" to 2,
            "gotr_catalytic_points" to 3,
            "gotr_total_searches" to 41,
            "gotr_rifts_closed" to 17,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
