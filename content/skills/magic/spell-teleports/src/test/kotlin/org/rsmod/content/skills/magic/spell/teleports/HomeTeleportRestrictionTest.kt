package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.util.CombinedId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.net.rsprot.handlers.If3ButtonHandler
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.NonInterruptingComponents
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class HomeTeleportRestrictionTest {
    @Test
    fun `home channel distinguishes an unset tile from packed coordinate zero`() {
        val player = Player()
        assertNull(HomeTeleportState.castCoords(player))
        for (tile in listOf(0, START.packed, CoordGrid(16383, 16383, 3).packed)) {
            HomeTeleportState.start(player, tile)
            assertEquals(tile, HomeTeleportState.castCoords(player))
            HomeTeleportState.clear(player)
            assertNull(HomeTeleportState.castCoords(player))
        }
    }

    @Test
    fun `death and session cleanup abandon the channel without clearing the saved home cooldown`() {
        for (reset in listOf("death", "login", "logout")) {
            val f = Fixture()
            f.clickHome()
            assertEquals(START.packed, HomeTeleportState.castCoords(f.player))
            f.set("varp.aide_tele_timer", 1234)
            when (reset) {
                "death" -> f.script.cleanup(f.player)
                "login" -> assertTrue(f.events.publish(SessionStateEvent.Login(f.player)))
                else -> assertTrue(f.events.publish(SessionStateEvent.Logout(f.player)))
            }
            assertNull(HomeTeleportState.castCoords(f.player), reset)
            repeat(30) { f.nextTick() }
            assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE), reset)
            assertEquals(START, f.player.coords, reset)
            assertEquals(1234, f.player.vars["varp.aide_tele_timer"], reset)
        }
    }

    @Test
    fun `home channel coordinate is temporary and server only`() {
        val type = checkNotNull(ServerCacheManager.getVarp("varp.home_teleport_cast_coords".asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    @Test
    fun `Home click rejects recent PvM and PvP combat until the ten second quiet period ends`() {
        for (combatVar in listOf("varp.lastcombat", "varp.lastcombat_pvp")) {
            val f = Fixture()
            f.set(combatVar, f.clock.cycle - 16)
            f.clickHome()
            f.assertNotCasting()
            assertTrue(f.client.messages.any { it.toString().contains("10 seconds") })
            f.nextTick()
            f.clickHome()
            assertEquals(5, f.player.queueList.count(TELEPORT_QUEUE))
        }
    }

    @Test
    fun `the hit logout lock still rejects Home after a killed NPC clears its combat stamp`() {
        val f = Fixture()
        f.player.preventLogoutUntil = f.clock.cycle + 5
        f.clickHome()
        f.assertNotCasting()
    }

    @Test
    fun `Home refuses an active route pending walk request or production queue without cancelling them`() {
        val walking = Fixture()
        val target = walking.player.coords.translateX(4)
        walking.player.walk(target)
        walking.clickHome()
        walking.assertNotCasting()
        assertTrue(walking.player.routeDestination.isNotEmpty())

        val request = Fixture()
        val route = RouteRequestCoord(target, clientRequest = true)
        request.player.routeRequest = route
        request.clickHome()
        request.assertNotCasting()
        assertSame(route, request.player.routeRequest)

        val skilling = Fixture()
        skilling.player.weakQueue("queue.hit", cycles = 5)
        skilling.clickHome()
        skilling.assertNotCasting()
        assertEquals(1, skilling.player.weakQueueList.count("queue.hit"))
    }

    @Test
    fun `native Home button preserves a suspended agility traversal and its forced route`() {
        val f = Fixture()
        var finishObstacle = false
        var obstacleFinished = false
        val obstacle = f.player.launch {
            pause { finishObstacle }
            obstacleFinished = true
        }
        val obstacleSequence = EntitySeq("seq.human_walk_logbalance".asRSCM(), 0, 6)
        f.player.pendingSequence = obstacleSequence
        f.player.forcedRoute = true
        f.player.routeDestination.add(f.player.coords.translateX(1))
        f.clickHome()
        f.assertNotCasting()
        assertEquals(obstacleSequence, f.player.pendingSequence)
        assertSame(obstacle, f.player.activeCoroutine, "The packet dispatcher must not cancel the obstacle first")
        assertTrue(obstacle.isSuspended)
        assertTrue(f.player.forcedRoute)
        assertTrue(f.player.routeDestination.isNotEmpty())
        finishObstacle = true
        f.player.advanceActiveCoroutine()
        assertTrue(obstacleFinished)
    }

    @Test
    fun `starting movement or another animation aborts Home without consuming its cooldown`() {
        for (moving in listOf(true, false)) {
            val f = Fixture()
            f.clickHome()
            f.finishTick()
            assertEquals(5, f.player.queueList.count(TELEPORT_QUEUE))
            val otherAnimation = EntitySeq(866, 0, 6)
            if (moving) f.player.walk(f.player.coords.translateX(1))
            else f.player.pendingSequence = otherAnimation
            f.script.onPostTick(f.player)
            assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE))
            if (!moving) assertEquals(otherAnimation, f.player.pendingSequence)
            repeat(30) { f.nextTick() }
            assertEquals(START, f.player.coords)
            assertEquals(0, f.player.vars["varp.aide_tele_timer"])
        }
    }

    @Test
    fun `a replacement protected action cancels every future Home beat without a later landing`() {
        val f = Fixture()
        f.clickHome()
        f.finishTick()
        repeat(7) { f.nextTick() }
        assertEquals(listOf(0, 6), f.stageTicks)
        var finishAction = false
        var actionFinished = false
        val replacement = f.player.launch {
            pause { finishAction }
            actionFinished = true
        }
        val replacementSequence = EntitySeq(866, 0, 6)
        f.player.pendingSequence = replacementSequence
        f.script.onPostTick(f.player)
        assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE))
        assertEquals(replacementSequence, f.player.pendingSequence)
        assertSame(replacement, f.player.activeCoroutine)
        repeat(30) { f.nextTick() }
        assertFalse(actionFinished)
        finishAction = true
        f.player.advanceActiveCoroutine()
        assertTrue(actionFinished)
        repeat(5) { f.nextTick() }
        assertEquals(START, f.player.coords)
        assertEquals(0, f.player.vars["varp.aide_tele_timer"])
        assertEquals(listOf(0, 6), f.stageTicks)
    }

    @Test
    fun `even a zero damage combat impact aborts the current Home channel immediately`() {
        val f = Fixture()
        f.clickHome()
        f.finishTick()
        assertTrue(f.events.publish(PlayerHitEvents.Impact(f.player, Hit(HitType.Melee, Hitmark(0L), 0, null, null))))
        assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE))
        repeat(30) { f.nextTick() }
        assertEquals(START, f.player.coords)
        assertEquals(0, f.player.vars["varp.aide_tele_timer"])
    }

    @Test
    fun `an idle Home cast completes all captured stages and consumes cooldown only at landing`() {
        val f = Fixture()
        f.clickHome()
        f.finishTick()
        for (tick in 1..22) {
            f.nextTick()
            assertEquals(START, f.player.coords, "Early landing at +$tick")
            assertEquals(0, f.player.vars["varp.aide_tele_timer"])
        }
        f.nextTick()
        assertEquals(HOME, f.player.coords)
        assertTrue(f.player.vars["varp.aide_tele_timer"] > 0)
        assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE))
        assertEquals(listOf(0, 6, 12, 16, 20), f.stageTicks)
    }

    @Test
    fun `Ancient Home shares busy checks channel cancellation and cooldown with Lumbridge Home`() {
        val denied = Fixture("obj.01_zaros_home_tele")
        denied.set("varp.lastcombat", denied.clock.cycle)
        denied.clickHome()
        denied.assertNotCasting()

        val interrupted = Fixture("obj.01_zaros_home_tele")
        interrupted.clickHome()
        interrupted.finishTick()
        interrupted.player.walk(START.translateX(1))
        interrupted.script.onPostTick(interrupted.player)
        repeat(30) { interrupted.nextTick() }
        assertEquals(START, interrupted.player.coords)
        assertEquals(0, interrupted.player.vars["varp.aide_tele_timer"])

        val completed = Fixture("obj.01_zaros_home_tele")
        completed.clickHome()
        completed.finishTick()
        repeat(23) { completed.nextTick() }
        assertEquals(CoordGrid(3087, 3496, 0), completed.player.coords)
        assertTrue(completed.player.vars["varp.aide_tele_timer"] > 0)
        assertEquals(listOf(0, 6, 12, 16, 20), completed.stageTicks)
    }

    @Test
    fun `ordinary rune teleports remain usable during combat and movement`() {
        val f = Fixture()
        f.player.adminInfiniteRunes = true
        f.set("varp.lastcombat", f.clock.cycle)
        f.player.walk(f.player.coords.translateX(4))
        f.click("obj.25_varrock_teleport")
        assertEquals(1, f.player.queueList.count(TELEPORT_QUEUE))
        f.finishTick()
        repeat(3) { f.nextTick() }
        assertNotEquals(START, f.player.coords)
        assertEquals(0, f.player.vars["varp.aide_tele_timer"])
    }

    @Test
    fun `Varrock alternate button lands at Grand Exchange after exactly three ticks`() {
        val f = Fixture()
        f.player.adminInfiniteRunes = true
        f.click("obj.25_varrock_teleport", op = 2)
        assertEquals(1, f.player.queueList.count(TELEPORT_QUEUE))
        f.finishTick()
        repeat(2) {
            f.nextTick()
            assertEquals(START, f.player.coords)
        }
        f.nextTick()
        assertEquals(CoordGrid(3164, 3487, 0), f.player.coords)
        assertEquals(0, f.player.queueList.count(TELEPORT_QUEUE))
        assertEquals(0, f.player.vars["varp.aide_tele_timer"])
    }

    private class Fixture(private val homeObj: String = "obj.48_home_teleport") {
        val clock = MapClock(100)
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = START
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            statMap.setBaseLevel("stat.magic", 99)
            statMap.setCurrentLevel("stat.magic", 99)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val events = EventBus()
        val stageTicks = mutableListOf<Int>()
        private var sequenceBeforeHomeClick = player.pendingSequence
        private val collision = CollisionFlagMap().apply {
            allocateIfAbsent(START.x, START.z, START.level)
            allocateIfAbsent(HOME.x, HOME.z, HOME.level)
        }
        private val spells = initialized(MagicSpellRegistry())
        private val areaChecker = unused<AreaChecker>()
        private val world = WorldRepository(ZoneUpdateMap())
        private val validator = PlayerTeleportValidator(emptySet<PlayerTeleportValidateHook>())
        private val runes = MagicRuneManager(
            initialized(FakeRuneRepository()), initialized(ComboRuneRepository()),
            initialized(CompactRuneRepository()), initialized(UnlimitedRuneRepository()),
            initialized(StaffSubstituteRepository()), initialized(RuneSubstituteRepository()),
            emptySet(),
        )
        val script = SpellTeleportScript(spells, runes, validator, areaChecker, world)

        private val contextFactory = unused<ProtectedAccessContextFactory>().also {
            field(it, "eventBus", events)
            field(it, "collision", collision)
            field(it, "areaChecker", areaChecker)
            field(it, "teleportValidator", validator)
            field(it, "worldRepo", world)
        }
        private val launcher = ProtectedAccessLauncher(contextFactory)
        private val buttons = If3ButtonHandler(events, launcher)
        private val queues = PlayerQueueProcessor(events, launcher)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            if (homeObj == "obj.01_zaros_home_tele") {
                set("varbit.spellbook", 1)
                collision.allocateIfAbsent(3087, 3496, 0)
            }
            val varrock = checkNotNull(spells.getObjSpell(checkNotNull(ServerCacheManager.getItem("obj.25_varrock_teleport".asRSCM()))))
            val destination = checkNotNull(varrock.obj.paramOrNull(org.rsmod.api.config.refs.params.spell_telecoord))
            collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            collision.allocateIfAbsent(3164, 3487, 0)
        }

        fun set(key: String, value: Int) = VarPlayerIntMapSetter.set(player, key, value)

        fun clickHome() {
            sequenceBeforeHomeClick = player.pendingSequence
            click(homeObj)
        }

        fun click(obj: String, op: Int = 1) {
            val spell = checkNotNull(spells.getObjSpell(checkNotNull(ServerCacheManager.getItem(obj.asRSCM()))))
            val component = spell.component
            assertTrue(component.hasEvent(if (op == 2) IfEvent.Op2 else IfEvent.Op1), "The native spell button must be enabled")
            if (obj == homeObj) assertTrue(NonInterruptingComponents.contains(component.packed))
            player.ui.topLevel = UserInterface(component.packed ushr 16)
            buttons.handle(player, If3Button(CombinedId(component.packed ushr 16, component.packed and 0xFFFF), -1, -1, op))
        }

        fun assertNotCasting() {
            assertEquals(0, player.queueList.count(TELEPORT_QUEUE))
            assertEquals(0, player.vars["varp.aide_tele_timer"])
            assertEquals(START, player.coords)
            assertEquals(sequenceBeforeHomeClick, player.pendingSequence, "A refused cast must preserve the current action's sequence")
        }

        fun finishTick() {
            queues.process(player)
            script.onPostTick(player)
            if (player.pendingSequence.id in HOME_SEQUENCES) stageTicks += clock.cycle - 100
            player.pendingSequence = EntitySeq.NULL
            player.pendingSpotanims.clear()
            player.pendingTelejump = false
            player.pendingTeleport = false
        }

        fun nextTick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            finishTick()
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
        private const val TELEPORT_QUEUE = "queue.spell_teleport"
        private val START = CoordGrid(3200, 3200)
        private val HOME = CoordGrid(3222, 3218)
        private val HOME_SEQUENCES = setOf(4847, 4850, 4853, 4855, 4857)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private fun <T : Any> initialized(value: T): T = value.also {
            it.javaClass.declaredMethods.single { method -> method.name.startsWith("init") && method.parameterCount == 0 }
                .apply { isAccessible = true }.invoke(it)
        }

        private fun field(target: Any, name: String, value: Any) {
            target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
