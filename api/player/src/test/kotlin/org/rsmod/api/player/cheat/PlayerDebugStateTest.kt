package org.rsmod.api.player.cheat

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.or2.central.account.Rights
import kotlin.reflect.KMutableProperty1
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.debug.componentClickDebug
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

@ResourceLock("ServerCacheManager")
class PlayerDebugStateTest {
    @Test
    fun `all debug flags default to off regardless of account rights`() {
        for (rights in listOf(Rights.NONE, Rights.ADMINISTRATOR)) {
            val player = Player().apply { modLevel = rights }
            for (flag in FLAGS) assertFalse(flag.property.get(player), flag.symbol)
            assertTrue(player.vars.backing.isEmpty())
            assertEquals(rights, player.modLevel)
        }
    }

    @Test
    fun `public toggles remain independent and explicit off clears the corresponding state`() {
        val player = Player()
        for (flag in FLAGS) {
            flag.property.set(player, true)
            assertEquals(1, player.vars[flag.symbol])
            for (other in FLAGS) {
                assertEquals(other == flag, other.property.get(player), other.symbol)
            }
            flag.property.set(player, false)
            assertEquals(0, player.vars[flag.symbol])
            assertFalse(flag.property.get(player))
        }
        assertTrue(player.attr.toPersistentMap().isEmpty())
        assertEquals(Rights.NONE, player.modLevel)
    }

    @Test
    fun `death attribute cleanup preserves the existing session toggles`() {
        val player = Player()
        val deathState = AttributeKey<Boolean>(resetOnDeath = true)
        player.attr[deathState] = true
        FLAGS.forEach { it.property.set(player, true) }

        player.attr.removeIf { it.resetOnDeath }

        assertFalse(player.attr.has(deathState))
        for (flag in FLAGS) assertTrue(flag.property.get(player), flag.symbol)
        assertEquals(Rights.NONE, player.modLevel)
    }

    @Test
    fun `character serialization cannot carry debug flags into another session`() {
        val player = Player().apply { modLevel = Rights.ADMINISTRATOR }
        VarPlayerIntMapSetter.set(player, "varp.option_master_volume", 62)
        FLAGS.forEach { it.property.set(player, true) }
        val saved = player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }
        val restored = Player().apply {
            modLevel = player.modLevel
            vars.backing.putAll(saved)
            attr.putAllFromPersistence(player.attr.toPersistentMap())
        }

        assertEquals(62, restored.vars["varp.option_master_volume"])
        for (flag in FLAGS) {
            assertFalse(flag.symbol.asRSCM() in saved, flag.symbol)
            assertFalse(flag.property.get(restored), flag.symbol)
        }
        assertEquals(Rights.ADMINISTRATOR, restored.modLevel)
    }

    @Test
    fun `debug flags never transmit their private state to a logged in client`() {
        val client = RecordingClient()
        val player = Player(client).apply {
            currentMapClock = 100
            processedMapClock = 100
        }
        for (flag in FLAGS) {
            val definition = checkNotNull(ServerCacheManager.getVarp(flag.symbol.asRSCM()))
            assertEquals(VarpLifetime.Temp, definition.scope, flag.symbol)
            assertEquals(VarpTransmitLevel.Never, definition.transmit, flag.symbol)
            flag.property.set(player, true)
            flag.property.set(player, false)
        }

        assertTrue(client.messages.isEmpty())
        assertEquals(Rights.NONE, player.modLevel)
    }

    @Test
    fun `god mode suppresses real hit modification only until switched off`() {
        val player = Player()
        val modifier = StandardPlayerHitModifier(EventBus())
        fun damage(): Int {
            val hit = HitBuilder(
                type = HitType.Typeless,
                damage = 12,
                sourceUid = null,
                sourceSlot = null,
                isFromNpc = false,
                isFromPlayer = false,
                clientDelay = 0,
                righthandType = null,
                secondaryType = null,
                targetHitmark = 0,
                sourceHitmark = 0,
                publicHitmark = null,
                zeroDamageHitmarkLit = null,
                zeroDamageHitmarkTint = null,
                maxDamageHitmarkLit = null,
                targetMaxDamageThreshold = Int.MAX_VALUE,
                sourceMaxDamageThreshold = Int.MAX_VALUE,
            )
            with(modifier) { hit.modify(player) }
            return hit.damage
        }

        assertEquals(12, damage())
        player.adminGodMode = true
        assertEquals(0, damage())
        player.adminGodMode = false
        assertEquals(12, damage())
        assertEquals(Rights.NONE, player.modLevel)
    }

    private data class Flag(val symbol: String, val property: KMutableProperty1<Player, Boolean>)

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
        private val FLAGS = listOf(
            Flag("varp.player_admin_god_mode", Player::adminGodMode),
            Flag("varp.player_admin_max_hit", Player::adminMaxHit),
            Flag("varp.player_admin_one_hit_kill", Player::adminOneHitKill),
            Flag("varp.player_admin_infinite_runes", Player::adminInfiniteRunes),
            Flag("varp.player_admin_no_clip", Player::adminNoClip),
            Flag("varp.player_component_click_debug", Player::componentClickDebug),
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
