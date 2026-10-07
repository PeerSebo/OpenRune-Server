package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.content.other.cheatmenu.CheatMenuScript
import org.rsmod.content.other.commandbrowser.CommandBrowserScript
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DebugCommandDispatchTest {
    @Test
    fun `cheat menu and debug commands register together in either order with one heal owner`() {
        for (cheatMenuFirst in listOf(true, false)) {
            val f = Fixture(cheatMenuFirst)
            assertTrue(f.commands.commands.keys.containsAll(
                listOf("cheat", "cheatmenu", "testwalk", "heal", "restore", "energy")
            ))
            assertEquals(Rights.ADMINISTRATOR, f.commands["heal"]?.requiredRights)
            assertTrue(f.commands["heal"]?.desc.orEmpty().contains("without removing boosts"))
            f.execute("setlevel", "hitpoints", "50")
            f.execute("setlevel", "prayer", "40")
            f.execute("setlevel", "mining", "50")
            f.execute("setcurrent", "hitpoints", "5")
            f.execute("setcurrent", "prayer", "2")
            f.execute("setcurrent", "mining", "55")
            f.execute("heal")
            assertEquals(50, f.player.stat("stat.hitpoints"))
            assertEquals(40, f.player.stat("stat.prayer"))
            assertEquals(55, f.player.stat("stat.mining"))
            assertEquals(4321, f.player.runEnergy)
            f.bindMenu()
            f.execute("commands", "heal")
            val summaryId = "component.command_browser:summary".asRSCM()
            val summary = f.client.messages.filterIsInstance<IfSetText>()
                .last { it.combinedId == summaryId }.text
            assertTrue(summary.startsWith("1 match"), summary)
        }
    }

    @Test
    fun `commands menu renders new debug entries with the same administrator restriction`() {
        val f = Fixture()
        f.bindMenu()
        f.execute("commands", "debugstate")
        assertTrue(f.player.ui.containsModal("interface.command_browser"))
        fun text(component: String): String {
            val id = "component.command_browser:$component".asRSCM()
            return f.client.messages.filterIsInstance<IfSetText>().last { it.combinedId == id }.text
        }
        assertTrue(text("command0").contains("::debugstate"))
        assertEquals("Administrator", text("privilege0"))
        assertTrue(text("description0").startsWith("Debug:"))
        assertEquals("", text("command1"))
        f.execute("commands", "debug")
        assertTrue(text("summary").startsWith("9 matches"))
        assertEquals("Page 1 of 2", text("page"))
        f.execute("commands", "administrator")
        assertTrue(text("summary").startsWith("9 matches"))
    }

    @Test
    fun `all new commands enforce administrator rights before reading or mutating state`() {
        for (rights in listOf(Rights.NONE, Rights.MOD)) {
            val f = Fixture()
            f.player.modLevel = rights
            for ((command, args) in COMMANDS) {
                assertEquals(Rights.ADMINISTRATOR, f.commands[command]?.requiredRights)
                f.execute(command, *args.toTypedArray())
            }
            assertEquals(1, f.player.statBase("stat.mining"))
            assertEquals(10, f.player.stat("stat.hitpoints"))
            assertEquals(4321, f.player.runEnergy)
            assertTrue(f.client.messages.isEmpty())
        }
    }

    @Test
    fun `setlevel raises and lowers only the requested skill with exact XP and client updates`() {
        val f = Fixture()
        f.player.xpRate = 10.0
        f.player.globalXpRate = 5.0
        f.execute("setlevel", "attack", "75")
        assertEquals(75, f.player.statBase("stat.attack"))
        assertEquals(75, f.player.stat("stat.attack"))
        assertEquals(PlayerSkillXPTable.getXPFromLevel(75), f.player.statMap.getXP("stat.attack"))
        assertEquals(1, f.player.statBase("stat.mining"))
        assertTrue(f.player.combatLevel > 3)
        assertTrue(f.player.pendingStatUpdates["stat.attack".asRSCM()])
        f.execute("setlevel", "stat.attack", "1")
        assertEquals(1, f.player.statBase("stat.attack"))
        assertEquals(1, f.player.stat("stat.attack"))
        assertEquals(0, f.player.statMap.getXP("stat.attack"))
        assertEquals(3, f.player.combatLevel)
        assertTrue(f.player.engineQueueList.isNotEmpty)
    }

    @Test
    fun `current level changes neither base nor XP including values above signed byte range`() {
        val f = Fixture()
        f.execute("setlevel", "mining", "50")
        val xp = f.player.statMap.getXP("stat.mining")
        for (level in listOf(55, 0, 255)) {
            f.execute("setcurrent", "MINING", level.toString())
            assertEquals(level, f.player.stat("stat.mining"))
            assertEquals(50, f.player.statBase("stat.mining"))
            assertEquals(xp, f.player.statMap.getXP("stat.mining"))
        }
    }

    @Test
    fun `heal preserves boosts while restore resets current levels cures toxins and retains XP`() {
        val f = Fixture()
        f.execute("setlevel", "hitpoints", "50")
        f.execute("setlevel", "prayer", "40")
        f.execute("setlevel", "mining", "50")
        f.execute("setcurrent", "hitpoints", "5")
        f.execute("setcurrent", "prayer", "2")
        f.execute("setcurrent", "mining", "55")
        VarPlayerIntMapSetter.set(f.player, "varp.poison_severity", 21)
        VarPlayerIntMapSetter.set(f.player, "varp.venom_strikes", 2)
        VarPlayerIntMapSetter.set(f.player, "varp.disease_drain", 3)
        f.execute("heal")
        assertEquals(50, f.player.stat("stat.hitpoints"))
        assertEquals(40, f.player.stat("stat.prayer"))
        assertEquals(55, f.player.stat("stat.mining"))
        assertEquals(21, f.player.vars["varp.poison_severity"])
        f.execute("setcurrent", "hitpoints", "60")
        f.execute("heal")
        assertEquals(60, f.player.stat("stat.hitpoints"))
        f.execute("restore")
        assertEquals(50, f.player.stat("stat.hitpoints"))
        assertEquals(50, f.player.stat("stat.mining"))
        assertEquals(PlayerSkillXPTable.getXPFromLevel(50), f.player.statMap.getXP("stat.mining"))
        assertEquals(10000, f.player.runEnergy)
        for (key in listOf("varp.poison_severity", "varp.venom_strikes", "varp.disease_drain")) {
            assertEquals(0, f.player.vars[key])
        }
    }

    @Test
    fun `energy accepts endpoints and defaults to full while emitting a client energy update`() {
        val f = Fixture()
        for (percent in listOf(0, 37, 100)) {
            f.execute("energy", percent.toString())
            assertEquals(percent * 100, f.player.runEnergy)
            assertTrue(f.client.messages.last { it is UpdateRunEnergy } is UpdateRunEnergy)
        }
        f.execute("energy", "0")
        f.execute("energy")
        assertEquals(10000, f.player.runEnergy)
    }

    @Test
    fun `bad inputs are refused without partial stat energy or movement changes`() {
        val cases = listOf(
            "setlevel" to listOf("mining", "0"),
            "setlevel" to listOf("mining", "100"),
            "setlevel" to listOf("hitpoints", "1"),
            "setlevel" to listOf("nosuchskill", "50"),
            "setlevel" to listOf("mining", "2147483648"),
            "setlevel" to emptyList(),
            "setcurrent" to listOf("hitpoints", "0"),
            "setcurrent" to listOf("mining", "256"),
            "setcurrent" to listOf("mining", "-1"),
            "energy" to listOf("-1"),
            "energy" to listOf("101"),
            "energy" to listOf("word"),
            "energy" to listOf("50", "extra"),
            "heal" to listOf("extra"),
            "restore" to listOf("extra"),
            "face" to listOf("-1", "3200"),
            "face" to listOf("3201"),
            "npcsnear" to listOf("17"),
            "locsnear" to listOf("-1"),
        )
        for ((command, args) in cases) {
            val f = Fixture()
            f.execute(command, *args.toTypedArray())
            assertEquals(1, f.player.statBase("stat.mining"), command)
            assertEquals(10, f.player.stat("stat.hitpoints"), command)
            assertEquals(4321, f.player.runEnergy, command)
            assertTrue(f.messages().isNotEmpty(), command)
            assertTrue(f.player.pendingFaceSquare == CoordGrid.NULL, command)
        }
    }

    @Test
    fun `restoration cannot revive a dead player or alter an account already logging out`() {
        for (command in listOf("heal", "restore", "setlevel", "setcurrent")) {
            for (dead in listOf(true, false)) {
                val f = Fixture()
                if (dead) f.player.statMap.setCurrentLevel("stat.hitpoints", 0)
                else f.player.loggingOut = true
                val args =
                    if (command.startsWith("set")) arrayOf("hitpoints", "50") else emptyArray()
                f.execute(command, *args)
                assertEquals(if (dead) 0 else 10, f.player.stat("stat.hitpoints"))
                assertEquals(10, f.player.statBase("stat.hitpoints"))
                assertEquals(4321, f.player.runEnergy)
            }
        }
    }

    @Test
    fun `nearby NPC report respects boundaries visibility plane and output limit`() {
        val f = Fixture()
        repeat(12) { f.npc(START.translate(1, 0), it) }
        f.npc(START.copy(level = 1), 50)
        f.npc(START, 51).hidden = true
        f.npc(START.translate(17, 0), 52)
        f.execute("npcsnear", "4", "bob")
        assertTrue(f.messages().first().startsWith("12 matching NPCs"))
        assertEquals(8, f.messages().count { it.contains("slot=") })
        assertTrue(f.messages().none {
            it.contains("slot=50") || it.contains("slot=51") || it.contains("slot=52")
        })
        f.execute("npcsnear", "4", "no such npc")
        assertTrue(f.messages().last().startsWith("0 matching NPCs"))
    }

    @Test
    fun `nearby objects show effective map angle shape and allow symbol or id searches`() {
        val f = Fixture()
        f.loc(START.translate(1, 0))
        f.loc(START.copy(level = 1))
        f.loc(START.translate(17, 0))
        f.execute("locsnear", "4", "hauntedleverup")
        assertTrue(f.messages().first().startsWith("1 matching objects"))
        assertTrue(f.messages().any { it.contains("shape=10, angle=2") })
        f.execute("locsnear", "4", "loc.hauntedleverup".asRSCM().toString())
        assertTrue(f.messages().takeLast(3).first().startsWith("1 matching objects"))
    }

    @Test
    fun `debugstate is observational and face preserves active routes when refused`() {
        val f = Fixture()
        f.player.walk(START.translate(5, 0))
        f.player.routeDestination.add(START.translate(5, 0))
        val route = f.player.routeRequest
        val waypoints = f.player.routeDestination.size
        f.execute("debugstate")
        assertSame(route, f.player.routeRequest)
        assertEquals(waypoints, f.player.routeDestination.size)
        assertEquals(4321, f.player.runEnergy)
        f.execute("face", "3200", "3205")
        assertSame(route, f.player.routeRequest)
        assertEquals(CoordGrid.NULL, f.player.pendingFaceSquare)
        val idle = Fixture()
        idle.execute("face", "3200", "3205")
        assertEquals(CoordGrid(3200, 3205), idle.player.pendingFaceSquare)
        assertEquals(START, idle.player.coords)
        assertNull(idle.player.routeRequest)
    }

    private class Fixture(cheatMenuFirst: Boolean? = null) {
        val commands = CheatCommandMap()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = START
            modLevel = Rights.ADMINISTRATOR
            currentMapClock = 100
            processedMapClock = 100
            runEnergy = 4321
            for (type in ServerCacheManager.getStats().values) {
                statMap.setBaseLevel(type.internalName, type.minLevel.toByte())
                statMap.setCurrentLevel(type.internalName, type.minLevel.toByte())
            }
        }
        private val events = EventBus()
        private val collision = CollisionFlagMap()
        private val clock = MapClock(100)
        private val npcList = NpcList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        private val storage = LocZoneStorage()

        init {
            val normal = LocRegistryNormal(ZoneUpdateMap(), collision, storage)
            val locRepo = LocRepository(clock,
                LocRegistry(storage, normal, unused<LocRegistryRegion>()), unused<RegionRegistry>())
            val context = ScriptContext(events, commands, EngineQueueCache())
            val menu = CheatMenuScript(
                ProtectedAccessLauncher(unused<ProtectedAccessContextFactory>()),
                unused<MagicSpellbookManager>(),
            )
            if (cheatMenuFirst == true) with(menu) { context.startup() }
            with(AdminStatCommands()) { context.startup() }
            if (cheatMenuFirst == false) with(menu) { context.startup() }
            with(AdminInspectionCommands(NpcRepository(clock, npcRegistry, npcList), locRepo)) {
                context.startup()
            }
        }

        fun execute(command: String, vararg args: String) {
            assertTrue(commands.execute(player, command, args.toList()))
            assertTrue(messages().none { it.contains("Uncaught exception") }, messages().toString())
            assertTrue(messages().all { it.length <= 250 }, "Debug output must fit a game message")
        }

        fun messages() = client.messages.filterIsInstance<MessageGame>().map { it.message }
        fun bindMenu() {
            val factory = unused<ProtectedAccessContextFactory>().also {
                it.javaClass.getDeclaredField("eventBus")
                    .apply { isAccessible = true }.set(it, events)
            }
            with(CommandBrowserScript(ProtectedAccessLauncher(factory))) {
                ScriptContext(events, commands, EngineQueueCache()).startup()
            }
        }
        fun npc(coords: CoordGrid, slot: Int): Npc =
            Npc(checkNotNull(ServerCacheManager.getNpc("npc.bob".asRSCM())), coords).also {
                it.slotId = slot
                npcRegistry.zones.getOrPut(ZoneKey.from(coords)).add(it)
            }

        fun loc(coords: CoordGrid) {
            storage.mapLocs[ZoneKey.from(coords), LocZoneKey(coords.x and 7, coords.z and 7, 2)] =
                LocEntity("loc.hauntedleverup".asRSCM(), 10, 2)
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
        private val START = CoordGrid(3207, 3200)
        private val COMMANDS = mapOf(
            "heal" to emptyList(), "restore" to emptyList(), "energy" to listOf("50"),
            "setlevel" to listOf("mining", "50"), "setcurrent" to listOf("mining", "55"),
            "debugstate" to emptyList(), "npcsnear" to emptyList(), "locsnear" to emptyList(),
            "face" to listOf("3200", "3205"),
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            return unsafe.allocateInstance(T::class.java) as T
        }
    }
}
