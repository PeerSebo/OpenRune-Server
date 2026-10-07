package org.rsmod.content.interfaces.settings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import java.util.Properties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifOpenTop
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.settings.scripts.NewAccountPreferencesScript
import org.rsmod.content.interfaces.xpdrops.XpDropsToggleScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class NewAccountPreferencesTest {
    @Test
    fun `every profile field resolves and applies exactly to a new account`() {
        val preferences = NewAccountPreferences.load()
        val player = newPlayer()

        preferences.apply(player)

        assertTrue(profile.isNotEmpty())
        for ((symbol, value) in profile) {
            assertEquals(value, player.vars[symbol], symbol)
            val parent = if (symbol.startsWith("varp.")) {
                checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            } else {
                checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM())).baseVar
            }
            assertEquals(VarpLifetime.Perm, parent.scope, symbol)
        }
        assertEquals(1, player.vars["varbit.option_hide_rooftops"])
        assertEquals(1, player.vars["varbit.desktop_shiftclickdrop_enabled"])
        assertEquals(1, player.vars["varbit.xpdrops_enabled"])
        assertEquals(100, player.vars["varp.option_master_volume"])
        assertEquals(100, player.vars["varbit.option_master_volume_desktop"])
        assertEquals(100, player.vars["varp.option_music"])
        assertEquals(100, player.vars["varbit.option_music_desktop"])
        assertEquals(100, player.vars["varp.option_sounds"])
        assertEquals(100, player.vars["varbit.option_sounds_desktop"])
        assertEquals(100, player.vars["varp.option_areasounds"])
        assertEquals(100, player.vars["varbit.option_areasounds_desktop"])
        assertEquals(3, player.vars["varbit.option_collection_new_item"])
        assertEquals(0, player.vars["varp.option_chat_colour_public_opaque"])
        assertEquals(0, player.vars["varp.option_brightness"])
        assertEquals(0, player.vars["varbit.option_brightness_remember"])
    }

    @Test
    fun `existing accounts including empty saved preferences receive no defaults`() {
        val preferences = NewAccountPreferences.load()
        val empty = Player()
        val customized = Player().apply {
            VarPlayerIntMapSetter.set(this, "varbit.option_hide_rooftops", 0)
            VarPlayerIntMapSetter.set(this, "varbit.xpdrops_enabled", 0)
            VarPlayerIntMapSetter.set(this, "varp.option_master_volume", 21)
            VarPlayerIntMapSetter.set(this, "varp.option_chat_colour_public_opaque", 0x123457)
        }

        for (player in listOf(empty, customized)) {
            val before = player.vars.backing.toMap()
            preferences.apply(player)
            assertEquals(before, player.vars.backing.toMap())
        }
        assertTrue(empty.vars.backing.isEmpty())
    }

    @Test
    fun `customized preferences survive permanent-only reload without reapplying defaults`() {
        val preferences = NewAccountPreferences.load()
        val player = newPlayer()
        preferences.apply(player)
        VarPlayerIntMapSetter.set(player, "varbit.xpdrops_enabled", 0)
        VarPlayerIntMapSetter.set(player, "varbit.option_collection_new_item", 2)
        VarPlayerIntMapSetter.set(player, "varp.option_master_volume", 0)
        VarPlayerIntMapSetter.set(player, "varbit.option_master_volume_desktop", 0)
        VarPlayerIntMapSetter.set(player, "varp.option_chat_colour_public_opaque", 0x123457)
        val saved = player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }
        val restored = Player().apply { vars.backing.putAll(saved) }

        repeat(2) { preferences.apply(restored) }

        assertEquals(0, restored.vars["varbit.new_player_account"])
        assertEquals(saved, restored.vars.backing.toMap())
        assertEquals(0, restored.vars["varbit.xpdrops_enabled"])
        assertEquals(2, restored.vars["varbit.option_collection_new_item"])
        assertEquals(0, restored.vars["varp.option_master_volume"])
        assertEquals(0x123457, restored.vars["varp.option_chat_colour_public_opaque"])
    }

    @Test
    fun `copying named preference bits preserves all other packed bits`() {
        val masks = mutableMapOf<Int, Int>()
        for (symbol in profile.keys) {
            val (parent, mask) = if (symbol.startsWith("varp.")) {
                symbol.asRSCM() to -1
            } else {
                val varbit = checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM()))
                val mask = (1L shl (varbit.bits.last - varbit.bits.first + 1)) - 1
                varbit.baseVar.id to (mask shl varbit.bits.first).toInt()
            }
            masks[parent] = (masks[parent] ?: 0) or mask
        }
        assertTrue(masks.values.any { it != -1 })
        val player = newPlayer()
        val pattern = 0x5A5A5A5A
        for (parent in masks.keys) {
            VarPlayerIntMapSetter.set(player, checkNotNull(ServerCacheManager.getVarp(parent)), pattern)
        }

        NewAccountPreferences.load().apply(player)

        for ((parent, mask) in masks) {
            assertEquals(pattern and mask.inv(), player.vars.backing[parent] and mask.inv(), "$parent")
        }
        for ((symbol, value) in profile) assertEquals(value, player.vars[symbol], symbol)
    }

    @Test
    fun `profile values retain raw colour sentinels masks and audio levels`() {
        val raw = mapOf(
            "varp.option_chat_colour_public_opaque" to 0,
            "varp.option_chat_colour_public_transparent" to 0x1000000,
            "varbit.option_collection_new_item" to 2,
            "varp.option_master_volume" to 62,
            "varbit.option_master_volume_desktop" to 62,
            "varp.option_music" to 0,
            "varbit.option_music_desktop" to 0,
            "varp.option_sounds" to 100,
            "varbit.option_sounds_desktop" to 100,
            "varp.option_areasounds" to 100,
            "varbit.option_areasounds_desktop" to 100,
        )
        val preferences = NewAccountPreferences.load(
            raw.entries.joinToString("\n") { "${it.key}=${it.value}" }.reader()
        )
        val player = newPlayer()

        preferences.apply(player)

        for ((symbol, value) in raw) assertEquals(value, player.vars[symbol], symbol)
    }

    @Test
    fun `invalid profiles fail before registering account initialization`() {
        for (invalid in listOf(
            "",
            "obj.coins=1",
            "varp.option_master_volume=abc",
            "varp.option_master_volume=2147483648",
            "varp.option_master_volume=1\nvarp.option_master_volume=2",
            "varbit.new_player_account=1",
            "varbit.option_hide_rooftops=2147483647",
            "varbit.no_such_new_account_preference=0",
        )) {
            assertThrows(Exception::class.java, { NewAccountPreferences.load(invalid.reader()) }, invalid)
        }
    }

    @Test
    fun `initialization runs before XP login and login preserves the resulting settings`() {
        val fixture = LoginFixture()
        val player = newPlayer().apply { client = RecordingClient() }
        player.ifOpenTop("interface.toplevel_osrs_stretch")

        fixture.events.publish(SessionStateEvent.Initialize(player))

        assertEquals(1, player.vars["varbit.xpdrops_enabled"])
        assertFalse(player.ui.containsOverlay("interface.xp_drops"))
        VarPlayerIntMapSetter.set(player, "varbit.xpdrops_size", 0)
        val initialized = player.vars.backing.toMap()

        fixture.events.publish(SessionStateEvent.Login(player))

        assertTrue(player.ui.containsOverlay("interface.xp_drops"))
        assertEquals(initialized, player.vars.backing.toMap())
    }

    @Test
    fun `existing account login honors a disabled XP overlay`() {
        val fixture = LoginFixture()
        val player = Player().apply { client = RecordingClient() }
        VarPlayerIntMapSetter.set(player, "varbit.xpdrops_enabled", 0)
        VarPlayerIntMapSetter.set(player, "varbit.xpdrops_size", 0)
        val saved = player.vars.backing.toMap()

        fixture.events.publish(SessionStateEvent.Initialize(player))
        fixture.events.publish(SessionStateEvent.Login(player))

        assertFalse(player.ui.containsOverlay("interface.xp_drops"))
        assertEquals(saved, player.vars.backing.toMap())
    }

    private class LoginFixture {
        val events = EventBus()

        init {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            val contextFactory = unsafe.allocateInstance(ProtectedAccessContextFactory::class.java)
                as ProtectedAccessContextFactory
            ProtectedAccessContextFactory::class.java.getDeclaredField("eventBus").apply {
                isAccessible = true
                set(contextFactory, events)
            }
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(XpDropsToggleScript(ProtectedAccessLauncher(contextFactory))) { context.startup() }
            with(NewAccountPreferencesScript()) { context.startup() }
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val profile: Map<String, Int> by lazy {
            val properties = Properties()
            checkNotNull(NewAccountPreferencesTest::class.java.getResourceAsStream("/settings/new-account-preferences.properties"))
                .bufferedReader(Charsets.UTF_8).use { properties.load(it) }
            properties.stringPropertyNames().associateWith { properties.getProperty(it).toInt() }
        }

        private fun newPlayer() = Player().apply {
            VarPlayerIntMapSetter.set(this, "varbit.new_player_account", 1)
        }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
