package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import net.rsprot.protocol.game.outgoing.specific.LocAnimSpecific
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.closeSubs
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FoundryCrucibleTest {
    @Test
    fun `invalid mould selections reject repeated pours before effects and can be corrected`() {
        for (invalid in 0..2) {
            val f = Fixture()
            f.prepareCast()
            val part = FoundryMouldPart.FORTE
            when (invalid) {
                0 -> VarPlayerIntMapSetter.set(f.player, part.selectedVarbit, 0)
                1 -> {
                    val locked = FoundryData.moulds.first { it.part == part && it.unlockVarbit != null }
                    VarPlayerIntMapSetter.set(f.player, part.selectedVarbit, locked.index)
                }
                2 -> {
                    val highLevel = FoundryData.moulds.first { it.part == part && it.unlockVarbit == null && it.level > 1 }
                    VarPlayerIntMapSetter.set(f.player, part.selectedVarbit, highLevel.index)
                    f.player.statMap.setCurrentLevel("stat.smithing", 1)
                }
            }
            val sequence = f.player.pendingSequence
            repeat(2) {
                f.fill(expectMenu = false)
                assertTrue(f.coroutine.isIdle)
                f.tick(8)
            }
            assertTrue(f.client.packets.filterIsInstance<LocAnimSpecific>().isEmpty())
            assertTrue(f.client.packets.filterIsInstance<SynthSound>().isEmpty())
            assertEquals(sequence, f.player.pendingSequence)
            assertEquals(28, FoundryGame.metalCounts(f.player).sum())
            assertEquals(1, f.player.foundryMouldState)
            assertTrue(f.client.packets.filterIsInstance<MessageGame>().any { "mould jig" in it.message })
            f.player.statMap.setCurrentLevel("stat.smithing", 99)
            f.prepareCast()
            f.fill(expectMenu = false)
            f.tick(6)
            assertEquals(2, f.player.foundryMouldState)
            assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        }
    }

    @Test
    fun `mould invalidated during a pour clears the effect without consuming the metal`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(2)
        VarPlayerIntMapSetter.set(f.player, FoundryMouldPart.FORTE.selectedVarbit, 0)
        f.tick(4)
        assertTrue(f.coroutine.isIdle)
        assertEquals("seq.giants_foundry_idle".asRSCM(), f.client.packets.filterIsInstance<LocAnimSpecific>().last().id)
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        assertEquals(1, f.player.foundryMouldState)
    }

    @Test
    fun `pour sends the recorded personal crucible animation and consumes metal after six ticks`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        val animation = f.client.packets.filterIsInstance<LocAnimSpecific>().single()
        assertEquals("seq.giants_foundry_crucible_pour01".asRSCM(), animation.id)
        assertEquals(3373 - f.player.buildArea.x, animation.zoneX + animation.xInZone)
        assertEquals(11488 - f.player.buildArea.z, animation.zoneZ + animation.zInZone)
        assertEquals(10, animation.shape)
        assertEquals(1, animation.rotation)
        f.tick(5)
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        assertEquals(1, f.player.foundryMouldState)
        f.tick()
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(1, f.client.packets.filterIsInstance<LocAnimSpecific>().size)
    }

    @Test
    fun `walking away from an adamant pour completes it once without resetting the crucible`() {
        for (elapsed in listOf(0, 3, 5)) {
            val f = Fixture()
            f.prepareCast("obj.adamantite_bar")
            f.fill(expectMenu = false)
            assertTrue(f.coroutine.isIdle)
            assertFalse(f.player.isDelayed)
            f.tick(elapsed)
            f.move(f.player.coords.translateX(-2))
            f.tick(5 - elapsed)
            f.assertMetalCount("obj.adamantite_bar", 28)
            f.tick()
            val animations = f.client.packets.filterIsInstance<LocAnimSpecific>()
            assertEquals(listOf("seq.giants_foundry_crucible_pour01".asRSCM()), animations.map { it.id })
            assertTrue(animations.all { it.id in 0 until 0xffff })
            assertEquals(2, f.player.foundryMouldState)
            assertEquals(50, f.player.foundryDifficulty)
            assertEquals(0, FoundryGame.metalCounts(f.player).sum())
            val quality = f.player.foundryStartQuality
            f.tick(8)
            assertEquals(quality, f.player.foundryStartQuality)
            assertEquals(1, f.pourEndCount())
        }
    }

    @Test
    fun `repeated pour clicks do not restart the six tick deadline`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(3)
        repeat(2) { f.fill(expectMenu = false) }
        f.tick(2)
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        f.tick()
        assertEquals(2, f.player.foundryMouldState)
        f.tick(6)
        assertEquals(1, f.pourEndCount())
        assertEquals(1, f.client.packets.filterIsInstance<LocAnimSpecific>().size)
    }

    @Test
    fun `pour completion preserves the player's new dialogue and animation`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(2)
        f.action {
            anim("seq.human_pickuptable")
            mesbox("The new conversation should remain open.")
        }
        val dialogue = f.coroutine
        val animation = f.player.pendingSequence
        assertTrue(dialogue.isAwaiting(ResumePauseButtonInput::class))
        f.tick(4)
        assertEquals(2, f.player.foundryMouldState)
        assertSame(dialogue, f.player.activeCoroutine)
        assertTrue(dialogue.isAwaiting(ResumePauseButtonInput::class))
        assertTrue(f.player.ui.containsModal("interface.messagebox"))
        assertEquals(animation, f.player.pendingSequence)
        f.player.cancelActiveCoroutine()
    }

    @Test
    fun `old completion cannot consume metal or reset a replacement pour`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(2)
        f.game.cancel(f.player)
        f.prepareCast("obj.adamantite_bar")
        f.fill(expectMenu = false)
        f.tick(4)
        f.assertMetalCount("obj.adamantite_bar", 28)
        assertEquals(1, f.player.foundryMouldState)
        assertEquals(0, f.pourEndCount())
        assertTrue(f.client.packets.filterIsInstance<LocAnimSpecific>().all {
            it.id == "seq.giants_foundry_crucible_pour01".asRSCM()
        })
        f.tick(2)
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(50, f.player.foundryDifficulty)
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        assertEquals(1, f.pourEndCount())
    }

    @Test
    fun `cancelled commission restores a valid idle without consuming metal`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(2)
        f.game.cancel(f.player)
        f.tick(4)
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        assertEquals(0, f.player.foundryStartQuality)
        assertEquals("seq.giants_foundry_idle".asRSCM(), f.client.packets.filterIsInstance<LocAnimSpecific>().last().id)
        assertEquals(0, f.pourEndCount())
    }

    @Test
    fun `logout or leaving and returning to the workshop preserves unpoured metal`() {
        for (logout in listOf(false, true)) {
            val f = Fixture()
            f.prepareCast("obj.adamantite_bar")
            f.fill(expectMenu = false)
            f.tick(3)
            if (logout) {
                f.events.publish(SessionStateEvent.Logout(f.player))
                f.player.loggingOut = true
            } else {
                val inside = f.player.coords
                f.move(SleepingGiantsWorkshop.OUTSIDE)
                f.move(inside)
            }
            f.tick(5)
            f.assertMetalCount("obj.adamantite_bar", 28)
            assertEquals(1, f.player.foundryMouldState)
            assertEquals(0, f.player.foundryStartQuality)
            assertEquals(0, f.pourEndCount())
            assertTrue(f.client.packets.filterIsInstance<LocAnimSpecific>().all { it.id in 0 until 0xffff })
        }
    }

    @Test
    fun `storing workshop equipment during a pour does not cancel it`() {
        val f = Fixture()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(3)
        f.game.store(f.player)
        f.tick(3)
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        assertEquals(1, f.pourEndCount())
    }

    @Test
    fun `pouring blocks emptying the crucible and changing or resetting the mould`() {
        val f = Fixture()
        f.prepareCast()
        val selected = FoundryGame.selectedMoulds(f.player)
        f.fill(expectMenu = false)
        f.tick(2)
        f.operate(InteractionOp.Op3)
        assertTrue(f.coroutine.isIdle)
        f.mouldButton(FoundryInterfaces.MOULD_RESET)
        f.mouldButton(FoundryInterfaces.MOULD_LIST, 17)
        assertEquals(selected, FoundryGame.selectedMoulds(f.player))
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        f.tick(4)
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
    }

    @Test
    fun `walking inside the private quest workshop preserves the pour`() {
        val f = Fixture()
        f.enterPrivateWorkshop()
        f.prepareCast()
        f.fill(expectMenu = false)
        f.tick(2)
        f.move(f.player.coords.translateX(-2))
        f.tick(4)
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        assertEquals(1, f.pourEndCount())
        assertTrue(f.client.packets.filterIsInstance<LocAnimSpecific>().all {
            it.id == "seq.giants_foundry_crucible_pour01".asRSCM()
        })
    }

    @Test
    fun `all six pure metals cast successfully with only valid location animations`() {
        for (metal in FoundryData.metals) {
            val f = Fixture()
            f.prepareCast(metal.bar)
            f.fill(expectMenu = false)
            f.tick(6)
            assertEquals(2, f.player.foundryMouldState, metal.bar)
            assertTrue(f.player.foundryStartQuality > 0, metal.bar)
            assertEquals(0, FoundryGame.metalCounts(f.player).sum(), metal.bar)
            assertTrue(f.client.packets.filterIsInstance<LocAnimSpecific>().all {
                it.id == "seq.giants_foundry_crucible_pour01".asRSCM()
            })
        }
    }

    @Test
    fun `native bar menu includes all six metals and uses the captured smelt action`() {
        val fixture = Fixture()
        fixture.fill()
        val setup = fixture.client.packets.filterIsInstance<RunClientScript>().single { it.id == 2046 }
        assertEquals(13, setup.values[0])
        assertEquals("What metal would you like to add?|Bronze bar|Iron bar|Steel bar|Mithril bar|Adamantite bar|Runite bar", setup.values[1])
        assertEquals(listOf(2349, 2351, 2353, 2359, 2361, 2363), setup.values.subList(3, 9))
        fixture.player.cancelActiveCoroutine()
    }

    @Test
    fun `bar selection deposits only the chosen material after its animation and reports a singular remainder`() {
        val fixture = Fixture()
        val bronze = FoundryData.metals.first()
        VarPlayerIntMapSetter.set(fixture.player, bronze.countVarbit, 26)
        fixture.fill()
        fixture.select(1, 1)
        assertFalse(fixture.player.isDelayed)
        fixture.tick(2)
        assertEquals(1, fixture.player.inv.count("obj.iron_bar"))
        fixture.tick()

        assertEquals(1, fixture.player.inv.count("obj.bronze_bar"))
        assertEquals(0, fixture.player.inv.count("obj.iron_bar"))
        fixture.assertMetalCount("obj.bronze_bar", 26)
        fixture.assertMetalCount("obj.iron_bar", 1)
        assertTrue(fixture.text().contains("You add 1 Iron bar to the crucible."))
        fixture.resume("component.objectbox:universe", -1)
        assertTrue(fixture.client.packets.any { it.toString().contains("another bar's worth") })
        assertFalse(fixture.client.packets.any { it.toString().contains("1 bars' worth") })
    }

    @Test
    fun `unavailable bar stays selectable and reports missing stock without consuming another metal`() {
        val fixture = Fixture()
        fixture.fill()
        fixture.select(2, 1)
        assertTrue(fixture.text().contains("You have no Steel bars to add to the crucible."))
        assertFalse(fixture.coroutine.isAwaiting(ResumePCountDialogInput::class))
        assertEquals(1, fixture.player.inv.count("obj.bronze_bar"))
        assertEquals(1, fixture.player.inv.count("obj.iron_bar"))
        assertEquals(0, FoundryGame.metalCounts(fixture.player).sum())
        fixture.player.cancelActiveCoroutine()
    }

    @Test
    fun `movement during deposit cancels before consuming items`() {
        val fixture = Fixture()
        fixture.fill()
        fixture.select(0, 1)
        fixture.player.cancelActiveCoroutine()
        fixture.player.coords = fixture.player.coords.translateX(-1)
        fixture.tick(5)
        assertEquals(1, fixture.player.inv.count("obj.bronze_bar"))
        assertEquals(0, FoundryGame.metalCounts(fixture.player).sum())
    }

    @Test
    fun `equipment uses the native one X all menu and reports its bar value`() {
        val fixture = Fixture()
        fixture.player.inv[0] = InvObj("obj.bronze_battleaxe")
        fixture.player.inv[1] = InvObj("obj.bronze_battleaxe")
        fixture.useItem(0)
        val menu = fixture.client.packets.filterIsInstance<RunClientScript>().single { it.id == 58 }
        assertEquals(listOf("How many would you like to add?", "1|X|All"), menu.values)
        fixture.resume("component.chatmenu:options", 3)
        fixture.tick(3)
        assertEquals(0, fixture.player.inv.count("obj.bronze_battleaxe"))
        fixture.assertMetalCount("obj.bronze_bar", 4)
        assertTrue(fixture.text().contains("You add 2 Bronze battleaxes to the crucible, worth 4 Bronze bars."))
        fixture.player.cancelActiveCoroutine()
    }

    @Test
    fun `equipment overflow needs confirmation and cancel preserves inventory`() {
        val fixture = Fixture()
        fixture.player.inv[0] = InvObj("obj.bronze_platebody")
        VarPlayerIntMapSetter.set(fixture.player, FoundryData.metals.first().countVarbit, 27)
        fixture.useItem(0)
        assertEquals(27, FoundryGame.metalCounts(fixture.player).sum())
        val buttons = fixture.client.packets.filterIsInstance<RunClientScript>().last { it.id == 2868 }
        assertEquals(listOf("No.|Yes."), buttons.values)
        fixture.resume("component.objectbox:universe", 0)
        assertEquals(1, fixture.player.inv.count("obj.bronze_platebody"))
        assertEquals(27, FoundryGame.metalCounts(fixture.player).sum())
    }

    @Test
    fun `equipment quantity menu remembers X and includes five only when enough items remain`() {
        val fixture = Fixture()
        repeat(14) { fixture.player.inv[it] = InvObj("obj.iron_platebody") }
        fixture.useItem(0)
        assertEquals("1|5|X|All", fixture.quantityOptions())
        fixture.resume("component.chatmenu:options", 3)
        fixture.coroutine.resumeWith(ResumePCountDialogInput(8))
        assertEquals(8, fixture.player.foundryLastDepositCount)
        fixture.player.cancelActiveCoroutine()
        fixture.useItem(0)
        assertEquals("1|5|8|X|All", fixture.quantityOptions())
        fixture.player.cancelActiveCoroutine()
        for (slot in 2 until 14) fixture.player.inv[slot] = null
        fixture.useItem(0)
        assertEquals("1|X|All", fixture.quantityOptions())
        fixture.player.cancelActiveCoroutine()
    }

    @Test
    fun `confirmed equipment overflow discards excess without overfilling crucible`() {
        val fixture = Fixture()
        fixture.player.inv[0] = InvObj("obj.bronze_platebody")
        VarPlayerIntMapSetter.set(fixture.player, FoundryData.metals.first().countVarbit, 27)
        fixture.useItem(0)
        fixture.resume("component.objectbox:universe", 1)
        fixture.tick(3)
        assertEquals(0, fixture.player.inv.count("obj.bronze_platebody"))
        assertEquals(28, FoundryGame.metalCounts(fixture.player).sum())
        fixture.player.cancelActiveCoroutine()
    }

    private class Fixture {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player(client = client).apply {
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            inv[0] = InvObj("obj.bronze_bar")
            inv[1] = InvObj("obj.iron_bar")
            coords = CoordGrid(3372, 11488)
            buildArea = CoordGrid(3312, 11440)
            previousCoords = coords
            currentMapClock = 100
            processedMapClock = 100
            statMap.setBaseLevel("stat.smithing", 99)
            statMap.setCurrentLevel("stat.smithing", 99)
        }
        private val interfaces = FoundryInterfaces(events)
        val game = FoundryGame(interfaces, events, XpModifiers(emptySet()))
        private val world = FoundryQuestTestWorld(game, events)
        private val queues = WorldQueueList()
        private val worldProcess = WorldQueueListProcess(queues)
        private var crucibleCoords = CoordGrid(3373, 11488)
        private val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events },
            getLocInteractions = { interactions },
            getAlignment = { TextAlignment() },
            getRandom = { DefaultGameRandom() },
            getCollision = { world.collision },
            getAreaChecker = { AreaChecker(world.regions, AreaIndex()) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        )
        lateinit var coroutine: GameCoroutine
            private set

        init {
            with(FoundryCrucibleScript(game, queues, world.workshop)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            with(FoundryMouldScript(interfaces)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun prepareCast(bar: String = "obj.bronze_bar") {
            player.foundryWord1 = 4
            player.foundryWord2 = 5
            player.foundryMouldState = 1
            for (metal in FoundryData.metals) {
                VarPlayerIntMapSetter.set(player, metal.countVarbit, if (metal.bar == bar) 28 else 0)
            }
            for (part in FoundryMouldPart.entries) {
                val mould = FoundryData.moulds.first { it.part == part && it.unlockVarbit == null }
                VarPlayerIntMapSetter.set(player, part.selectedVarbit, mould.index)
            }
        }

        fun fill(expectMenu: Boolean = true) {
            operate(InteractionOp.Op1)
            if (expectMenu) assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
        }

        fun operate(op: InteractionOp) {
            val id = "loc.giants_foundry_crucible_multi".asRSCM()
            val type = checkNotNull(ServerCacheManager.getObject(id))
            val info = LocInfo(2, crucibleCoords, LocEntity(id, 10, 1))
            val loc = BoundLocInfo(info, type)
            val event = checkNotNull(interactions.opTrigger(player, loc, op))
            action { events.publish(this, event) }
        }

        fun action(block: suspend ProtectedAccess.() -> Unit) {
            coroutine = player.launch { ProtectedAccess(player, this, context).block() }
        }

        fun move(destination: CoordGrid) {
            player.cancelActiveCoroutine()
            val previous = player.coords
            player.coords = destination
            events.publish(PlayerMovementEvent.CoordsMovedEvent(player, previous))
        }

        fun mouldButton(component: String, sub: Int = -1) {
            action {
                events.publish(this, IfModalButton(ServerCacheManager.fromComponent(component), sub, null, IfButtonOp.Op1))
            }
        }

        fun enterPrivateWorkshop() {
            world.loadMap()
            action { checkNotNull(world.workshop.enter(this)) }
            crucibleCoords = world.workshop.map(player, CoordGrid(3373, 11488))
            player.coords = crucibleCoords.translateX(-1)
            player.previousCoords = player.coords
            player.buildArea = player.coords.translate(-48, -48)
        }

        fun select(row: Int, amount: Int) {
            resume("component.skillmulti:${('a'.code + row).toChar()}", amount)
            assertFalse(player.ui.containsModal("interface.skillmulti"))
        }

        fun resume(component: String, value: Int) {
            coroutine.resumeWith(ResumePauseButtonInput(component, value))
            for (packed in player.ui.closeQueue.intIterator()) {
                player.closeSubs(Component(packed), events)
            }
            player.ui.closeQueue.clear()
        }

        fun useItem(slot: Int) {
            val type = checkNotNull(ServerCacheManager.getObject("loc.giants_foundry_crucible_empty".asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, CoordGrid(3373, 11488), LocEntity(type.id, 10, 0)), type)
            val use = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
                .apply { isAccessible = true }.newInstance(events)
            coroutine = player.launch {
                val access = ProtectedAccess(player, this, context)
                val event = with(use) { access.opTrigger(loc, loc, type,
                    checkNotNull(ServerCacheManager.getItem(player.inv[slot]!!.id)), slot) }
                assertTrue(events.publish(access, checkNotNull(event)))
            }
        }

        fun tick(count: Int = 1) = repeat(count) {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            worldProcess.process()
            coroutine.takeIf { it.isSuspended }?.advance()
        }

        fun pourEndCount() = client.packets.filterIsInstance<SynthSound>().count {
            it.id == "synth.SG_CRUCIBLE_POUR_END".asRSCM()
        }

        fun text() = client.packets.filterIsInstance<IfSetText>().joinToString(" ") { it.text.replace("<br>", " ") }

        fun quantityOptions() = client.packets.filterIsInstance<RunClientScript>().last { it.id == 58 }.values[1]

        fun assertMetalCount(item: String, expected: Int) {
            val metal = checkNotNull(FoundryData.materials[item.asRSCM()]).metal
            assertEquals(expected, player.vars[metal.countVarbit])
        }
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val packets = mutableListOf<Any>()
        override fun write(message: Any) {
            packets += message
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
