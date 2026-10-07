package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.cheat.adminOneHitKill
import org.rsmod.api.player.debug.componentClickDebug
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.interfaces.makeover.scripts.MakeoverCommand
import org.rsmod.content.interfaces.makeover.scripts.MakeoverScript
import org.rsmod.content.other.cheatmenu.CheatMenuScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class UtilityCommandDispatchTest {
    @Test fun `home is public and teleports an idle player to Lumbridge`() {
        val f = Fixture()
        f.execute("home")
        assertEquals(HOME, f.player.coords)
        assertTrue(f.player.pendingTelejump)
        assertEquals(0, f.player.vars["varp.aide_tele_timer"])
    }

    @Test fun `home rejects both combat timers and the post-combat logout lock`() {
        for (timer in listOf("varp.lastcombat", "varp.lastcombat_pvp")) {
            val f = Fixture()
            VarPlayerIntMapSetter.set(f.player, timer, 99)
            f.execute("home")
            assertEquals(START, f.player.coords)
            assertTrue(f.messages().any { it.contains("combat") })
        }
        val f = Fixture()
        f.player.preventLogoutUntil = 101
        f.execute("home")
        assertEquals(START, f.player.coords)
    }

    @Test fun `home refusal preserves pending routes skill queues and forced movement`() {
        val actions: List<(Player) -> Unit> = listOf(
            { it.walk(START.translateX(5)) },
            { it.routeDestination.add(START.translateX(5)) },
            { it.forcedRoute = true },
            { it.weakQueue("queue.generic_queue1", 3) },
            { it.delay = 105 },
        )
        for (action in actions) {
            val f = Fixture()
            action(f.player)
            f.player.pendingSequence = EntitySeq(2293, 0, 0)
            val route = f.player.routeRequest
            val queued = f.player.weakQueueList.size
            f.execute("home")
            assertEquals(START, f.player.coords)
            assertSame(route, f.player.routeRequest)
            assertEquals(queued, f.player.weakQueueList.size)
            assertEquals(2293, f.player.pendingSequence.id)
        }
    }

    @Test fun `home honours normal teleport restrictions`() {
        val f = Fixture(denial = "Teleports are blocked here.")
        f.execute("home")
        assertEquals(START, f.player.coords)
        assertTrue(f.messages().contains("Teleports are blocked here."))
    }

    @Test fun `testwalk requires administrator and toggles one shared session state`() {
        val f = Fixture()
        f.execute("testwalk")
        assertFalse(f.player.adminNoClip)
        f.player.modLevel = Rights.ADMINISTRATOR
        f.execute("testwalk")
        assertTrue(f.player.adminNoClip)
        f.player.walk(START.translateX(5))
        f.player.routeDestination.add(START.translateX(5))
        f.execute("testwalk")
        assertFalse(f.player.adminNoClip)
        assertNull(f.player.routeRequest)
        assertTrue(f.player.routeDestination.isEmpty())
        assertFalse(Player().adminNoClip)
        assertNull(f.commands["noclip"])
    }

    @Test fun `combat cheat commands retain administrator permissions and toggle back off`() {
        for ((command, flag) in listOf(
            "ohk" to Player::adminOneHitKill,
            "norunes" to Player::adminInfiniteRunes,
        )) {
            val f = Fixture()
            f.execute(command)
            assertFalse(flag.get(f.player), command)
            assertEquals(Rights.NONE, f.player.modLevel)
            f.player.modLevel = Rights.ADMINISTRATOR
            f.execute(command)
            assertTrue(flag.get(f.player), command)
            f.execute(command)
            assertFalse(flag.get(f.player), command)
            assertEquals(Rights.ADMINISTRATOR, f.player.modLevel)
        }
    }

    @Test fun `component debug reports clicks only while enabled without bypassing disabled operations`() {
        val f = Fixture()
        f.execute("makeover")
        val before = f.player.vars["varbit.makeover_interface_type"]
        val component = "component.makeover_mage:burger_menu_frame"
        f.player.ui.events.add(component, 10..10, 0L)
        f.click(component, 10)
        assertTrue(f.messages().none { it.startsWith("[componentdebug]") })

        f.player.componentClickDebug = true
        f.click(component, 10)
        assertEquals(1, f.messages().count { it.startsWith("[componentdebug]") })
        assertEquals(before, f.player.vars["varbit.makeover_interface_type"])
        assertTrue(f.player.ui.containsModal("interface.makeover_mage"))
        assertEquals(Rights.NONE, f.player.modLevel)

        f.player.componentClickDebug = false
        f.click(component, 10)
        assertEquals(1, f.messages().count { it.startsWith("[componentdebug]") })
        assertEquals(before, f.player.vars["varbit.makeover_interface_type"])
    }

    @Test fun `makeover opens body editor and native navigator reaches hair and clothes`() {
        val f = Fixture()
        f.execute("makeover")
        assertTrue(f.player.ui.containsModal("interface.makeover_mage"))
        f.click("component.makeover_mage:burger_menu_frame", 10)
        assertTrue(f.player.ui.containsModal("interface.makeover"))
        assertEquals(0, f.player.vars["varbit.makeover_interface_type"])
        f.click("component.makeover:burger_menu_frame", 16)
        assertEquals(3, f.player.vars["varbit.makeover_interface_type"])
    }

    @Test fun `makeover body preview cancels without changing appearance and apply commits`() {
        val f = Fixture()
        f.player.appearance.bodyType = Appearance.BODY_TYPE_A
        f.execute("makeover")
        f.click("component.makeover_mage:bodytype_b")
        assertEquals(Appearance.BODY_TYPE_A, f.player.appearance.bodyType)
        f.player.ifClose(f.events)
        assertEquals(Appearance.BODY_TYPE_A, f.player.appearance.bodyType)
        f.execute("makeover")
        f.click("component.makeover_mage:bodytype_b")
        f.click("component.makeover_mage:apply_button")
        assertEquals(Appearance.BODY_TYPE_B, f.player.appearance.bodyType)
    }

    private class Fixture(denial: String? = null) {
        val events = EventBus()
        val commands = CheatCommandMap()
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = START
            currentMapClock = 100
            processedMapClock = 100
        }
        private val collision = CollisionFlagMap().apply {
            allocateIfAbsent(START.x, START.z, 0)
            allocateIfAbsent(HOME.x, HOME.z, 0)
        }
        private val world = WorldRepository(ZoneUpdateMap())
        private val factory = unused<ProtectedAccessContextFactory>().also {
            field(it, "eventBus", events)
            field(it, "collision", collision)
            field(it, "worldRepo", world)
            field(it, "areaChecker", unused<AreaChecker>())
            field(it, "teleportValidator", PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> denial })))
        }
        private val access = ProtectedAccessLauncher(factory)
        private val buttons = If3ButtonHandler(events, access)

        init {
            val scripts: List<PluginScript> = listOf(HomeCommand(access),
                CheatMenuScript(access, unused<MagicSpellbookManager>()),
                MakeoverScript(world), MakeoverCommand(access))
            for (script in scripts) with(script) { ScriptContext(events, commands, EngineQueueCache()).startup() }
        }

        fun execute(command: String) { assertTrue(commands.execute(player, command, emptyList())) }
        fun messages(): List<String> = client.messages.filterIsInstance<MessageGame>().map { it.message }
        fun click(name: String, sub: Int = -1) {
            val packed = name.asRSCM()
            buttons.handle(player, If3Button(CombinedId(packed ushr 16, packed and 0xffff), sub, -1, 1))
        }
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
        private val START = CoordGrid(3200, 3200)
        private val HOME = CoordGrid(3222, 3218)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }
        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run { isAccessible = true; get(null) as Unsafe }
            return unsafe.allocateInstance(T::class.java) as T
        }
    }
}
