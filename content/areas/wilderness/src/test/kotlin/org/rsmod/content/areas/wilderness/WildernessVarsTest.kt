package org.rsmod.content.areas.wilderness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.wilderness.WildernessAreaScript.Companion.canPvp
import org.rsmod.content.areas.wilderness.WildernessAreaScript.Companion.hasWildernessOptOut
import org.rsmod.content.areas.wilderness.WildernessAreaScript.Companion.isDueling
import org.rsmod.content.areas.wilderness.WildernessAreaScript.Companion.setCanPvp
import org.rsmod.content.areas.wilderness.WildernessAreaScript.Companion.setWildernessOptOut
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class WildernessVarsTest {
    @Test fun `legacy login imports skull and optout with the existing combat timer fallback`() {
        val player = player()
        player.attr.putAllFromPersistence(mapOf(
            "is_skulled" to true,
            "skull_icon_type" to constants.skullicon_default,
            "skull_equip_locked" to false,
            "wilderness_opt_out" to true,
            "other_content" to 8,
        ))
        events().publish(SessionStateEvent.Login(player))
        assertTrue(player.isSkulled())
        assertFalse(player.isSkullEquipLocked())
        assertTrue(player.hasWildernessOptOut())
        assertEquals(constants.skullicon_default, player.skullIcon)
        assertEquals(SkullSource.COMBAT.timerTicks, player.skullTimerRemaining())
        assertNull(player.wildernessSkullSource)
        assertEquals(mapOf("other_content" to 8), player.attr.toPersistentMap())
    }

    @Test fun `equipment lock survives login prevents ordinary clears and death still clears it`() {
        val player = player()
        player.attr.putAllFromPersistence(mapOf(
            "is_skulled" to true,
            "skull_equip_locked" to true,
            "skull_icon_type" to constants.skullicon_default,
        ))
        events().publish(SessionStateEvent.Login(player))
        assertTrue(player.isSkullEquipLocked())
        assertEquals(0, player.skullTimerRemaining())
        player.clearSkull()
        assertTrue(player.isSkulled())
        WildernessDeathCleanupHook().cleanup(player)
        assertFalse(player.isSkulled())
        assertFalse(player.isSkullEquipLocked())
        assertNull(player.skullIcon)
        assertNull(player.wildernessSkullSource)
    }

    @Test fun `schema marker prevents stale legacy flags from reviving a cleared skull`() {
        val player = player()
        player.attr.putAllFromPersistence(mapOf("is_skulled" to true, "wilderness_opt_out" to true))
        player.migrateWildernessVars()
        player.clearSkull(force = true)
        player.setWildernessOptOut(false)
        player.attr.putAllFromPersistence(mapOf("is_skulled" to true, "wilderness_opt_out" to true))
        player.migrateWildernessVars()
        assertFalse(player.isSkulled())
        assertFalse(player.hasWildernessOptOut())
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test fun `permanent state reloads while source pvp duel loot cache and special timestamp reset`() {
        val old = player()
        old.migrateWildernessVars()
        old.applyEquipmentSkull()
        old.setCanPvp(true, duel = true)
        old.setWildernessOptOut(true)
        old.wildernessLootKeyCount = 3
        old.wildernessLastSpecTick = 100
        val restored = reload(old)
        assertTrue(restored.isSkulled())
        assertTrue(restored.isSkullEquipLocked())
        assertTrue(restored.hasWildernessOptOut())
        assertFalse(restored.canPvp())
        assertFalse(restored.isDueling())
        assertNull(restored.wildernessSkullSource)
        assertNull(restored.wildernessLastSpecTick)
        assertEquals(0, restored.wildernessLootKeyCount)
        events().publish(SessionStateEvent.Login(restored))
        assertEquals(constants.skullicon_default, restored.skullIcon)
        assertEquals(0, restored.skullTimerRemaining())
    }

    @Test fun `skull sources retain their independent timers and timer expiry clears state`() {
        val events = events()
        for (source in SkullSource.entries.filter { it != SkullSource.EQUIPMENT }) {
            val player = player()
            player.applySkull(source)
            assertEquals(source, player.wildernessSkullSource)
            assertEquals(source.timerTicks, player.skullTimerRemaining())
            events.publish(PlayerTimerEvent.Soft(player, "timer.skull_timer".asRSCM()))
            assertFalse(player.isSkulled())
            assertNull(player.wildernessSkullSource)
            assertNull(player.skullIcon)
        }
    }

    @Test fun `loot key icon tracks inventory independently of skull source and equipment lock`() {
        val player = player()
        player.applyEquipmentSkull()
        player.inv[0] = InvObj("obj.wildy_loot_key0", 1)
        player.inv[1] = InvObj("obj.wildy_loot_key1", 1)
        player.refreshSkullIconIfLootKeysChanged()
        assertEquals(2, player.wildernessLootKeyCount)
        assertEquals(constants.skullicon_loot_key_1 + 1, player.skullIcon)
        assertTrue(player.isSkullEquipLocked())
        player.inv[0] = null
        player.inv[1] = null
        player.refreshSkullIconIfLootKeysChanged()
        assertEquals(constants.skullicon_default, player.skullIcon)
        player.unlockEquipmentSkull()
        player.applyPostEquipSkull()
        assertFalse(player.isSkullEquipLocked())
        assertEquals(SkullSource.POST_EQUIP.timerTicks, player.skullTimerRemaining())
    }

    @Test fun `PvP optout and duel flags retain their existing combinations`() {
        val player = player()
        assertFalse(player.canPvp())
        player.setCanPvp(true, duel = true)
        assertTrue(player.canPvp())
        assertTrue(player.isDueling())
        player.setWildernessOptOut(true)
        assertFalse(player.canPvp())
        assertTrue(player.isDueling())
        player.setWildernessOptOut(false)
        assertTrue(player.canPvp())
        player.setCanPvp(false)
        assertFalse(player.canPvp())
        assertFalse(player.isDueling())
    }

    @Test fun `owned cache fields have explicit lifetime and do not transmit to client`() {
        for (name in listOf("player_flags", "skull_icon", "session_flags", "skull_source", "loot_key_count", "last_pvp_spec_tick")) {
            val type = checkNotNull(ServerCacheManager.getVarp("varp.wilderness_$name".asRSCM()))
            val expected = if (name in listOf("player_flags", "skull_icon")) VarpLifetime.Perm else VarpLifetime.Temp
            assertEquals(expected, type.scope, name)
            assertEquals(VarpTransmitLevel.Never, type.transmit, name)
        }
    }

    private fun player() = Player().apply {
        currentMapClock = 100
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
    }

    private fun events() = EventBus().also {
        with(WildernessAreaScript(it)) { ScriptContext(it, CheatCommandMap(), EngineQueueCache()).startup() }
    }

    private fun reload(original: Player) = player().apply {
        for ((id, value) in original.vars) {
            val type = checkNotNull(ServerCacheManager.getVarp(id))
            if (type.scope == VarpLifetime.Perm) VarPlayerIntMapSetter.set(this, type, value)
        }
        attr.putAllFromPersistence(original.attr.toPersistentMap())
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
