package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.mechanics.toxins.Toxin
import org.rsmod.api.mechanics.toxins.ToxinImmunity
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@ResourceLock("ServerCacheManager")
class PlayerDeathToxinsTest {
    @Test
    fun `death cures infections and clears current and saved immunity before syncing native orbs`() {
        val player = player()
        ToxinImmunity.grantImmunity(player, poisonDuration = 200, venomDuration = 60)
        ToxinImmunity.onTimer(player)
        assertEquals(200, player.vars["varp.toxin_poison_immunity_remaining"])
        assertEquals(60, player.vars["varp.toxin_venom_immunity_remaining"])
        VarPlayerIntMapSetter.set(player, "varp.poison_severity", 10)
        VarPlayerIntMapSetter.set(player, "varp.venom_strikes", 1)
        VarPlayerIntMapSetter.set(player, "varp.disease_drain", 5)
        for (timer in InfectionTimers) player.timerAt(timer, mapClock = 100, cycles = 30)
        Toxin.syncStatusOrbs(player)
        assertEquals(1_000_000, player.vars["varp.poison"])
        assertEquals(1, player.vars["varp.disease"])

        player.resetDeathToxins()

        for (symbol in ClearedVars) assertEquals(0, player.vars[symbol], symbol)
        assertEquals(1, player.vars["varp.toxin_immunity_migrated"])
        assertFalse(ToxinImmunity.hasPoisonImmunity(player))
        assertFalse(ToxinImmunity.hasVenomImmunity(player))
        for (timer in InfectionTimers + ToxinImmunity.TIMER) {
            assertFalse(timer in player.timerMap, timer)
        }
    }

    @Test
    fun `death removes legacy immunity and leaves unrelated saved state intact`() {
        val player = player()
        player.attr.putAllFromPersistence(mapOf(
            "mechanics.toxins.poison_immunity_remaining" to 200,
            "mechanics.toxins.venom_immunity_remaining" to 60,
            "mechanics.toxins.poison_immunity_warned" to true,
            "unrelated.state" to 9,
        ))

        player.resetDeathToxins()
        player.resetDeathToxins()

        assertEquals(mapOf("unrelated.state" to 9), player.attr.toPersistentMap())
        assertEquals(1, player.vars["varp.toxin_immunity_migrated"])
        for (symbol in ClearedVars) assertEquals(0, player.vars[symbol], symbol)
        assertTrue("varp.toxin_poison_immunity_remaining" in player.vars)
        assertTrue("varp.toxin_venom_immunity_remaining" in player.vars)
        assertFalse(ToxinImmunity.TIMER in player.timerMap)
    }

    private fun player() = Player().apply {
        currentMapClock = 100
        client = object : Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
    }

    companion object {
        private val InfectionTimers = listOf(
            "timer.player_poison", "timer.player_venom", "timer.player_disease",
        )
        private val ClearedVars = listOf(
            "varp.poison_severity", "varp.venom_strikes", "varp.disease_drain",
            "varp.poison", "varp.disease", "varp.toxin_poison_immunity_until",
            "varp.toxin_venom_immunity_until", "varp.toxin_poison_immunity_warned_until",
            "varp.toxin_poison_immunity_display_until", "varp.toxin_poison_immunity_remaining",
            "varp.toxin_venom_immunity_remaining", "varp.toxin_poison_immunity_warned",
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
