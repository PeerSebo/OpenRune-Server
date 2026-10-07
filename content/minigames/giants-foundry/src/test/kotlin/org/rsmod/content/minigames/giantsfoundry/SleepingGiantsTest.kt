package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.sound.MidiJingle
import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.MusicModernRow
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMath
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.content.minigames.giantsfoundry.quest.*
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.content.quest.manager.JournalState
import org.rsmod.content.quest.manager.QuestJournalRegistry
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SleepingGiantsTest {
    @Test
    fun `strike and hammer use start the real quest dialogue and register its native journal`() {
        for (useHammer in listOf(false, true)) {
            val f = Fixture()
            val giant = Npc("npc.giants_foundry_kovac_fake_attack", SleepingGiantsWorkshop.OUTSIDE)
            f.player.inv[0] = InvObj("obj.hammer")
            f.action {
                val event = if (useHammer) NpcUEvents.Op(giant, 0,
                    checkNotNull(ServerCacheManager.getItem("obj.hammer".asRSCM())), giant.type)
                else NpcEvents.Op1(giant)
                assertTrue(f.events.publish(this, event))
            }
            assertTrue(f.player.ui.containsModal("interface.chat_left"))
            assertEquals(0, f.player.sleepingGiantsStage)
            f.finishDialogue(choice = 2)
            assertEquals(0, f.player.sleepingGiantsStage)
            assertNotNull(QuestJournalRegistry.getById(170))
            f.action { QuestJournalRegistry.openJournal(this, f.quest, JournalState.OVERVIEW) }
            assertTrue(f.player.ui.containsOverlay("interface.questjournal_overview"))
        }
    }

    @Test
    fun `quest start requires base Smithing but unfinished quests never block normal foundry entry`() {
        val f = Fixture()
        f.world.loadMap()
        f.player.statMap.setBaseLevel("stat.smithing", 14)
        f.player.statMap.setCurrentLevel("stat.smithing", 99)
        f.action { f.world.service.start(this, Npc("npc.giants_foundry_kovac_fake_attack", player.coords)) }
        assertTrue(f.output().contains("Smithing level of 15"))
        assertEquals(0, f.player.sleepingGiantsStage)
        f.player.statMap.setBaseLevel("stat.smithing", 99)
        for (stage in listOf(0, 5, 15, 25, 30)) {
            f.quest.jumpToStage(f.player, stage)
            f.player.coords = SleepingGiantsWorkshop.OUTSIDE
            f.operate("loc.giants_foundry_entrance", CoordGrid(3359, 3150))
            f.finishDialogue(choice = 2)
            assertEquals(SleepingGiantsWorkshop.ENTRANCE, f.player.coords)
            assertEquals(stage, f.player.sleepingGiantsStage)
            assertFalse(f.world.workshop.contains(f.player))
        }
    }

    @Test
    fun `native repair room can be restored and its first sword completes exactly once`() {
        val f = Fixture()
        f.world.loadMap()
        f.action { f.world.service.start(this, Npc("npc.giants_foundry_kovac_fake_attack", player.coords)) }
        f.finishDialogue()
        assertEquals(15, f.player.sleepingGiantsStage)
        assertTrue(f.world.workshop.contains(f.player))
        assertEquals(SleepingGiantsWorkshop.OUTSIDE, f.player.loginExitCoord)
        f.materials()
        for (repair in SleepingGiantsData.repairs) {
            val mapped = f.world.workshop.map(f.player, repair.coords)
            assertTrue(f.world.locs.findAll(mapped).any { it.id == repair.multi.asRSCM() })
            assertTrue(f.world.locs.findAll(repair.coords).any { it.id == repair.original.asRSCM() })
            f.operate(repair.broken, mapped)
            assertEquals(1, f.player.vars[repair.state])
            f.tick(repair.animations.sumOf { it.second } - 1)
            assertEquals(1, f.player.vars[repair.state])
            f.tick()
            assertEquals(2, f.player.vars[repair.state], repair.name)
        }
        assertEquals(0, f.player.inv.count("obj.oak_logs"))
        assertEquals(0, f.player.inv.count("obj.wool"))
        assertEquals(0, SleepingGiantsData.nails.sumOf { f.player.inv.count(it) })
        f.talk()
        f.finishDialogue()
        assertEquals(25, f.player.sleepingGiantsStage)
        assertEquals(SleepingGiantsCutscene.TALK_TILE, f.player.coords)
        assertFalse(f.world.workshop.contains(f.player))
        assertEquals(3, f.player.foundryWord1)
        assertEquals(4, f.player.foundryWord2)
        assertTrue(f.world.service.takeSupplies(f.player))
        for ((item, count) in SleepingGiantsData.supplies) {
            assertTrue(f.game.deposit(f.player, item.asRSCM(), count) > 0)
        }
        assertEquals(listOf(16, 12, 0, 0, 0, 0), FoundryGame.metalCounts(f.player))
        for (part in FoundryMouldPart.entries) VarPlayerIntMapSetter.set(f.player, part.selectedVarbit, 6)
        f.player.foundryMouldState = 1
        assertTrue(f.game.cast(f.player, f.random))
        f.player.foundryMouldState = 0
        f.player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        var operations = 0
        while (f.player.foundryCompletion < 1000 && operations++ < 150) {
            val section = FoundryMath.section(f.player.foundryCompletion, f.player.foundryDifficulty)
            val station = FoundryStation.entries[f.player.vars["varbit.giants_foundry_seed_$section"]]
            f.player.foundryTemperature = FoundryMath.temperatureRange(station, f.player.foundryDifficulty).first
            assertNotEquals(FoundryGame.WorkResult.Damaged, f.game.work(f.player, station))
        }
        assertEquals(1000, f.player.foundryCompletion)
        val before = f.player.statMap.getXP("stat.smithing")
        f.action { assertTrue(f.world.service.handIn(this)) }
        assertEquals(55, f.player.sleepingGiantsTutorial)
        f.finishDialogue()
        assertEquals(30, f.player.sleepingGiantsStage)
        assertEquals(65, f.player.sleepingGiantsTutorial)
        assertEquals(before + 6000, f.player.statMap.getXP("stat.smithing"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertTrue(f.player.ui.containsModal("interface.questscroll"))
        assertEquals(listOf(MidiJingle(154, 5869)), f.messages.filterIsInstance<MidiJingle>())
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.foundryReputation)
        f.action { assertFalse(f.world.service.handIn(this)) }
        assertEquals(before + 6000, f.player.statMap.getXP("stat.smithing"))
        assertEquals(1, f.player.vars["varp.qp"])
        assertEquals(1, f.messages.filterIsInstance<MidiJingle>().size)
    }

    @Test
    fun `Kovac instructions follow current heat and section instead of repeating the entire tutorial`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, SleepingGiantsState.TUTORIAL)
        f.player.sleepingGiantsTutorialCommission = true
        f.player.foundryWord1 = 3
        f.player.foundryWord2 = 4
        f.player.foundryStartQuality = 75
        f.player.foundryQuality = 75
        f.player.foundryDifficulty = 10
        f.player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        FoundryState.setStations(f.player, FoundryStation.entries)
        for ((index, tool) in listOf("trip hammer", "grindstone", "polishing wheel").withIndex()) {
            f.player.foundryCompletion = index * 334
            val range = FoundryMath.temperatureRange(FoundryStation.entries[index], 10)
            f.player.foundryTemperature = range.first - 1
            assertTrue(SleepingGiantsService.instruction(f.player).contains("too cold for the $tool"))
            f.player.foundryTemperature = range.last + 1
            assertTrue(SleepingGiantsService.instruction(f.player).contains("too hot for the $tool"))
            f.player.foundryTemperature = range.first
            assertTrue(SleepingGiantsService.instruction(f.player).contains("Use the $tool"))
        }
        f.talk()
        assertTrue(f.player.ui.containsModal("interface.chat_left"))
        assertTrue(f.output().replace("<br>", " ").contains("Use the polishing wheel"))
        f.cancel()
        f.player.foundryCompletion = 1000
        assertEquals("The sword is finished. Bring it to Kovac.", SleepingGiantsService.instruction(f.player))
        f.player.foundryStored = 1
        assertTrue(SleepingGiantsService.instruction(f.player).contains("storage"))
        f.action { assertTrue(f.world.service.handIn(this)) }
        assertTrue(f.output().contains("storage before bringing it to Kovac"))
        assertEquals(25, f.player.sleepingGiantsStage)
    }

    @Test
    fun `cooled preform guidance moves on to pickup and ruined swords do not request more work`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, SleepingGiantsState.TUTORIAL)
        f.player.sleepingGiantsTutorialCommission = true
        f.player.foundryWord1 = 3
        f.player.foundryWord2 = 4
        f.player.foundryStartQuality = 75
        f.player.foundryQuality = 75
        f.player.foundryMouldState = 2
        assertTrue(SleepingGiantsService.instruction(f.player).startsWith("Cool the preform"))
        f.player.foundryMouldState = 3
        assertEquals("The preform has cooled. Pick it up from the mould jig.", SleepingGiantsService.instruction(f.player))
        f.player.foundryMouldState = 0
        f.player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        f.player.foundryQuality = 0
        assertTrue(SleepingGiantsService.instruction(f.player).startsWith("The sword is ruined."))
    }

    @Test
    fun `both cave directions use requested landing tiles and private entry maps the same tile`() {
        val f = Fixture()
        f.world.loadMap()
        f.player.coords = CoordGrid(3361, 3149)
        f.operate("loc.giants_foundry_entrance", CoordGrid(3359, 3150))
        assertEquals(CoordGrid(3366, 11483), f.player.coords)
        f.operate("loc.giants_foundry_exit", CoordGrid(3364, 11482))
        assertEquals(CoordGrid(3361, 3149), f.player.coords)
        f.quest.jumpToStage(f.player, SleepingGiantsState.REPAIRS)
        f.action { f.world.service.enterQuest(this) }
        assertEquals(f.world.workshop.map(f.player, CoordGrid(3366, 11483)), f.player.coords)
        f.operate("loc.giants_foundry_exit", f.world.workshop.map(f.player, CoordGrid(3364, 11482)))
        assertEquals(CoordGrid(3361, 3149), f.player.coords)
        assertNull(f.player.loginExitCoord)
    }

    @Test
    fun `restored workshop changes to the public room without moving the player within it`() {
        for (stage in listOf(SleepingGiantsState.REPAIRS, SleepingGiantsState.REPAIRED)) {
            for (destination in listOf(CoordGrid(3367, 11485), CoordGrid(3362, 11494), CoordGrid(3370, 11489))) {
                val f = Fixture()
                f.world.loadMap()
                f.quest.jumpToStage(f.player, stage)
                f.action { f.world.service.enterQuest(this) }
                f.player.coords = f.world.workshop.map(f.player, destination)
                for (repair in SleepingGiantsData.repairs) {
                    VarPlayerIntMapSetter.set(f.player, repair.state, 2)
                }
                f.talk()
                f.finishDialogue()
                assertEquals(destination, f.player.coords)
                assertFalse(f.world.workshop.contains(f.player))
                assertNull(f.player.loginExitCoord)
                assertTrue(f.world.npcs.none { it.isSlotAssigned })
                assertEquals(SleepingGiantsState.TUTORIAL, f.player.sleepingGiantsStage)
                assertEquals(0, f.player.vars["varp.qp"])
            }
        }
    }

    @Test
    fun `private workshop unlocks and plays the foundry before its opening camera pan`() {
        val track = MusicModernRow.getRow("dbrow.music_modern_the_foundry").tracks.single()
        for (alreadyUnlocked in listOf(false, true)) {
            val f = Fixture()
            f.world.loadMap()
            f.quest.jumpToStage(f.player, SleepingGiantsState.ACCEPTED)
            VarPlayerIntMapSetter.set(f.player, "varbit.option_master_volume_desktop", 62)
            VarPlayerIntMapSetter.set(f.player, "varbit.option_music_desktop", 5)
            if (alreadyUnlocked) f.world.music.unlock(f.player, track)
            f.messages.clear()
            assertEquals(alreadyUnlocked, f.world.music.hasUnlocked(f.player, track))
            f.action { f.world.service.enterQuest(this) }
            assertTrue(f.world.music.hasUnlocked(f.player, track))
            assertEquals(listOf(track.midi.id), f.messages.filterIsInstance<MidiSongV2>().map { it.id })
            assertEquals(!alreadyUnlocked, f.output().contains("You have unlocked a new music track:"))
            assertFalse(f.messages.any { it.javaClass.simpleName == "CamMoveToV3" })
            f.tick(10)
            f.cancel()
            f.assertSceneRestored()
            assertTrue(f.world.music.hasUnlocked(f.player, track))
            assertEquals(SleepingGiantsState.INSIDE, f.player.sleepingGiantsStage)
            f.action { f.world.service.enterQuest(this) }
            f.finishDialogue()
            assertEquals(1, f.messages.filterIsInstance<MidiSongV2>().size)
            assertEquals(SleepingGiantsState.REPAIRS, f.player.sleepingGiantsStage)
            f.assertSceneRestored()
            f.world.workshop.leave(f.player)
        }
    }

    @Test
    fun `repair cancellation and missing materials cannot consume nails or mark a machine repaired`() {
        val f = Fixture()
        f.world.loadMap()
        f.quest.jumpToStage(f.player, 15)
        f.action { f.world.service.enterQuest(this) }
        f.materials()
        val repair = SleepingGiantsData.repairs.first()
        val mapped = f.player.coords.translate(repair.coords.x - SleepingGiantsWorkshop.ENTRANCE.x,
            repair.coords.z - SleepingGiantsWorkshop.ENTRANCE.z)
        val before = f.player.inv.toList()
        f.operate(repair.broken, mapped)
        f.tick()
        f.cancel()
        assertEquals(before, f.player.inv.toList())
        assertEquals(1, f.player.vars[repair.state])
        for (slot in f.player.inv.indices) {
            if (f.player.inv[slot]?.id == "obj.oak_logs".asRSCM()) f.player.inv[slot] = null
        }
        assertFalse(f.world.service.consumeRepairMaterials(f.player, repair))
        assertEquals(10, SleepingGiantsData.nails.sumOf { f.player.inv.count(it) })
        assertEquals(1, f.player.vars[repair.state])
    }

    @Test
    fun `logout releases the private room while retaining native repair progress for a later visit`() {
        val f = Fixture()
        f.world.loadMap()
        f.quest.jumpToStage(f.player, 15)
        f.action { f.world.service.enterQuest(this) }
        val firstRoom = f.player.coords
        val repaired = SleepingGiantsData.repairs.first()
        VarPlayerIntMapSetter.set(f.player, repaired.state, 2)
        f.events.publish(SessionStateEvent.Logout(f.player))
        assertEquals(SleepingGiantsWorkshop.OUTSIDE, f.player.coords)
        assertNull(f.player.loginExitCoord)
        assertFalse(f.world.workshop.contains(f.player))
        assertEquals(15, f.player.sleepingGiantsStage)
        f.action { f.world.service.enterQuest(this) }
        assertTrue(f.world.workshop.contains(f.player))
        assertNotEquals(SleepingGiantsWorkshop.ENTRANCE, f.player.coords)
        assertEquals(2, f.player.vars[repaired.state])
        assertNotEquals(SleepingGiantsWorkshop.ENTRANCE, firstRoom)
        f.events.publish(SessionStateEvent.Logout(f.player))
    }

    @Test
    fun `supplies require twenty slots atomically and cannot be claimed twice or after forfeiting`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, 25)
        f.player.sleepingGiantsTutorial = 10
        f.player.sleepingGiantsTutorialCommission = true
        repeat(9) { f.player.inv[it] = InvObj("obj.knife") }
        val before = f.player.inv.toList()
        assertFalse(f.world.service.takeSupplies(f.player))
        assertEquals(before, f.player.inv.toList())
        assertEquals(10, f.player.sleepingGiantsTutorial)
        f.player.inv[8] = null
        assertTrue(f.world.service.takeSupplies(f.player))
        assertEquals(0, f.player.inv.freeSpace())
        assertFalse(f.world.service.takeSupplies(f.player))
        f.game.cancel(f.player)
        assertFalse(f.world.service.takeSupplies(f.player))
        assertEquals(15, f.player.sleepingGiantsTutorial)
    }

    @Test
    fun `an interrupted thanks dialogue can resume without returning the sword or repeating rewards`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, 25)
        f.player.sleepingGiantsTutorial = 50
        f.player.sleepingGiantsTutorialCommission = true
        f.player.foundryWord1 = 3
        f.player.foundryWord2 = 4
        f.player.foundryStartQuality = 75
        f.player.foundryQuality = 75
        f.player.foundryCompletion = 1000
        f.player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        f.action { assertTrue(f.world.service.handIn(this)) }
        f.cancel()
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(55, f.player.sleepingGiantsTutorial)
        assertEquals(25, f.player.sleepingGiantsStage)
        f.talk()
        f.finishDialogue()
        assertEquals(30, f.player.sleepingGiantsStage)
        assertEquals(6000, f.player.statMap.getXP("stat.smithing"))
        assertEquals(1, f.player.vars["varp.qp"])
    }

    @Test
    fun `cancelled tutorial can use ordinary commissions and rewards before resuming the quest`() {
        val f = Fixture()
        f.quest.jumpToStage(f.player, 25)
        f.player.sleepingGiantsTutorial = 15
        f.talk()
        assertTrue(f.player.sleepingGiantsTutorialCommission)
        f.game.cancel(f.player)
        assertFalse(f.player.sleepingGiantsTutorialCommission)
        val kovac = Npc("npc.giants_foundry_kovac", CoordGrid(3369, 11486))
        f.action { assertTrue(f.events.publish(this, NpcEvents.Op4(kovac))) }
        assertTrue(FoundryGame.hasCommission(f.player))
        assertFalse(f.player.sleepingGiantsTutorialCommission)
        val words = f.player.foundryWord1 to f.player.foundryWord2
        f.talk()
        f.finishDialogue()
        assertEquals(words, f.player.foundryWord1 to f.player.foundryWord2)
        assertFalse(f.world.service.takeSupplies(f.player))
        f.player.foundryStartQuality = 75
        f.player.foundryQuality = 75
        f.player.foundryCompletion = 1000
        f.player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        f.action { assertTrue(f.events.publish(this, NpcEvents.Op3(kovac))) }
        f.finishDialogue(choice = 2)
        assertEquals(25, f.player.sleepingGiantsStage)
        assertEquals(15, f.player.sleepingGiantsTutorial)
        assertEquals(1, f.player.foundryCompleted)
        assertEquals(75, f.player.foundryReputation)
        assertEquals(0, f.player.vars["varp.qp"])
        f.talk()
        assertTrue(f.player.sleepingGiantsTutorialCommission)
        assertEquals(3, f.player.foundryWord1)
        assertEquals(4, f.player.foundryWord2)
        assertFalse(f.world.service.takeSupplies(f.player))
        assertTrue(f.output().contains("replacement bars"))
    }

    @Test
    fun `opening cinematic follows recorded camera shots and walks back before repairs`() {
        val f = Fixture()
        f.world.loadMap()
        f.quest.jumpToStage(f.player, SleepingGiantsState.ACCEPTED)
        VarPlayerIntMapSetter.set(f.player, "varbit.option_acceptaid", 1)
        f.action { f.world.service.enterQuest(this) }
        f.finishDialogue()
        val destination = f.world.workshop.map(f.player, SleepingGiantsCutscene.TALK_TILE)
        assertEquals(destination, f.player.coords)
        assertEquals(15, f.player.sleepingGiantsStage)
        assertEquals(1, f.player.vars["varbit.option_acceptaid"])
        assertEquals(6, f.messages.count { it.javaClass.simpleName == "CamMoveToV3" })
        assertEquals(6, f.messages.count { it.javaClass.simpleName == "CamLookAtV3" })
        assertEquals(2, f.sequences.count { it == "seq.emote_think".asRSCM() })
        assertTrue(f.visited.contains(f.world.workshop.map(f.player, SleepingGiantsCutscene.WALK_END)))
        val firstStep = f.visited.indexOf(f.world.workshop.map(f.player, SleepingGiantsCutscene.ENTRY))
        assertTrue(firstStep >= 0)
        assertTrue(f.visited.drop(firstStep).zipWithNext().all { (a, b) -> a.chebyshevDistance(b) <= 1 })
        assertTrue(f.output().contains("Giants... not just... killers."))
        f.assertSceneRestored()
    }

    @Test
    fun `cancelling the fade camera pan or dialogue restores control and allows replay`() {
        for (ticks in listOf(0, 10, 33)) {
            val f = Fixture()
            f.world.loadMap()
            f.quest.jumpToStage(f.player, SleepingGiantsState.ACCEPTED)
            VarPlayerIntMapSetter.set(f.player, "varbit.option_acceptaid", 1)
            f.action { f.world.service.enterQuest(this) }
            f.tick(ticks)
            if (ticks >= 2) {
                val hammer = f.world.workshop.map(f.player, CoordGrid(3363, 11496))
                assertTrue(f.world.locs.findAll(hammer).any {
                    it.id == "loc.giants_foundry_trip_hammer_broken_cutscene".asRSCM()
                })
            }
            f.cancel()
            f.assertSceneRestored()
            assertEquals(1, f.player.vars["varbit.option_acceptaid"])
            assertEquals(SleepingGiantsState.INSIDE, f.player.sleepingGiantsStage)
            assertEquals(0, f.player.vars["varp.qp"])
            for (entry in SleepingGiantsData.scenery) for (source in entry.coords) {
                val original = f.world.workshop.map(f.player, source.copy(level = entry.originalLevel))
                assertTrue(f.world.locs.findAll(original).any { it.id == entry.original.asRSCM() }, entry.original)
                assertFalse(f.world.locs.findAll(f.world.workshop.map(f.player, source)).any {
                    it.id == entry.cutscene.asRSCM()
                })
            }
            f.action { f.world.service.enterQuest(this) }
            f.finishDialogue()
            assertEquals(SleepingGiantsState.REPAIRS, f.player.sleepingGiantsStage)
            f.assertSceneRestored()
        }
    }

    @Test
    fun `disconnect during the introductory pan releases its private actors and can resume`() {
        val f = Fixture()
        f.world.loadMap()
        f.quest.jumpToStage(f.player, SleepingGiantsState.ACCEPTED)
        f.action { f.world.service.enterQuest(this) }
        f.tick(10)
        f.cancel()
        f.events.publish(SessionStateEvent.Logout(f.player))
        f.assertSceneRestored()
        assertFalse(f.world.workshop.contains(f.player))
        assertEquals(SleepingGiantsWorkshop.OUTSIDE, f.player.coords)
        assertNull(f.player.loginExitCoord)
        assertTrue(f.world.npcs.none { it.isSlotAssigned })
        f.action { f.world.service.enterQuest(this) }
        f.finishDialogue()
        assertEquals(SleepingGiantsState.REPAIRS, f.player.sleepingGiantsStage)
    }

    private class Fixture {
        val events = EventBus()
        val interfaces = FoundryInterfaces(events)
        val game = FoundryGame(interfaces, events, XpModifiers(emptySet()))
        val world = FoundryQuestTestWorld(game, events)
        private val script = SleepingGiants(world.service, world.workshop)
        val quest = script.quest
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            coords = SleepingGiantsWorkshop.OUTSIDE
            currentMapClock = 100
            processedMapClock = 100
            statMap.setBaseLevel("stat.smithing", 99)
            statMap.setCurrentLevel("stat.smithing", 99)
        }
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        private val interactions = LocInteractions(BoundValidator(world.collision), events)
        private val movement = PlayerMovementProcessor(world.collision, RouteFactory(world.collision),
            StepFactory(world.collision), events)
        val sequences = mutableListOf<Int>()
        val visited = mutableListOf<CoordGrid>()
        val messages get() = client.messages
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getRandom = { random }, getAlignment = { TextAlignment() },
            getCollision = { world.collision }, getLocInteractions = { interactions },
            getAreaChecker = { AreaChecker(world.regions, AreaIndex()) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        )
        private lateinit var coroutine: GameCoroutine
        private var result: Result<Unit>? = null

        init {
            val startup = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(script) { startup.startup() }
            with(FoundryWorkshopScript(game, interfaces, events, world.service, world.workshop)) {
                startup.startup()
            }
            with(KovacScript(game, FoundryRewardService(interfaces, random), world.service)) {
                startup.startup()
            }
        }

        fun action(block: suspend ProtectedAccess.() -> Unit) {
            result = null
            coroutine = GameCoroutine("sleeping-giants-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val task: suspend () -> Unit = { access.block() }
            task.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { this@Fixture.result = result }
            })
            result?.getOrThrow()
        }

        fun operate(symbol: String, coords: CoordGrid) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 0)), type)
            val event = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            action { assertTrue(events.publish(this, event)) }
        }

        fun talk() = action {
            world.service.talk(this, Npc("npc.giants_foundry_kovac_quest", player.coords.translateX(1)))
        }

        fun materials() {
            for ((slot, item) in listOf("obj.hammer", "obj.chisel", "obj.oak_logs", "obj.oak_logs",
                "obj.oak_logs", "obj.wool").withIndex()) player.inv[slot] = InvObj(item)
            player.inv[6] = InvObj("obj.nails_bronze", 3)
            player.inv[7] = InvObj("obj.nails_iron", 7)
        }

        fun finishDialogue(choice: Int = 1) {
            var continued = 0
            while (coroutine.isSuspended && continued++ < 200) {
                if (!coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                    tick()
                    continue
                }
                val component = when {
                    player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                    player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                    player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                    player.ui.containsModal("interface.messagebox") -> "component.messagebox:continue"
                    else -> error("Unexpected quest dialogue: ${player.ui.modals}")
                }
                coroutine.resumeWith(ResumePauseButtonInput(component, if (component.endsWith(":options")) choice else -1))
                result?.getOrThrow()
            }
            assertTrue(coroutine.isIdle)
        }

        fun tick(count: Int = 1) = repeat(count) {
            player.pendingSequence = EntitySeq.NULL
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            coroutine.advance()
            if (player.pendingSequence != EntitySeq.NULL) sequences += player.pendingSequence.id
            movement.process(player)
            if (visited.lastOrNull() != player.coords) visited += player.coords
            result?.getOrThrow()
        }

        fun cancel() {
            coroutine.cancel()
            player.activeCoroutine = null
            result = null
        }

        fun output() = client.messages.joinToString("\n")

        fun assertSceneRestored() {
            for (field in listOf("cutscene_status", "minimap_state", "fov_clamp",
                "gravestone_tli_hide", "draw_distance_max_override")) {
                assertEquals(0, player.vars["varbit.$field"], field)
            }
            assertTrue(player.routeDestination.isEmpty())
            assertFalse(player.ui.containsOverlay("interface.fade_overlay"))
            assertTrue(messages.any { it.javaClass.simpleName == "CamReset" })
        }
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
