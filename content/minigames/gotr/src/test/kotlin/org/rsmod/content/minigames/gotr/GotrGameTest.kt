package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductPrepareEvent
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.skilling.SkillingAwardResult
import org.rsmod.api.player.skilling.awardSkillingProduct
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.*
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.table.mining.MiningRocksRow
import org.rsmod.content.minigames.gotr.scripts.GotrMiningScript
import org.rsmod.content.minigames.gotr.scripts.GotrScript
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.*
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionLocOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GotrGameTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `victory removes fragments and essence from all participants including altar visitors`() {
        val f = Fixture()
        f.enter()
        val second = f.player(2, "Second", CoordGrid(3616, 9491))
        f.game.tryEnter(second)
        for (player in listOf(f.player, second)) {
            player.inv[0] = InvObj("obj.gotr_guardian_fragment", 300)
            player.inv[1] = InvObj(EssencePouch.GUARDIAN_ESSENCE)
            player.inv[2] = InvObj("obj.airrune", 50)
            EssencePouch.setStoredEssenceType(player, EssencePouch.Tier.Small, EssencePouch.GUARDIAN_ESSENCE)
            EssencePouch.depositUpTo(player, EssencePouch.Tier.Small, 3)
            EssencePouch.setStoredEssenceType(player, EssencePouch.Tier.Medium, EssencePouch.PURE_ESSENCE)
            EssencePouch.depositUpTo(player, EssencePouch.Tier.Medium, 6)
        }
        val altar = CoordGrid(2841, 4829)
        f.game.visitAltar(second, altar)
        second.coords = altar
        val mining = f.miningProduct()
        val source = mining.source as SkillingProductSource.Mining
        f.player.interaction = InteractionLocOp(InteractionOp.Op3, source.rock, true, false)
        repeat(450) { assertTrue(f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL)) }

        f.tick(101)
        assertEquals(GotrPhase.GRACE, f.game.phase)
        f.tick(133)

        assertEquals(GotrPhase.LOBBY, f.game.phase)
        assertNull(f.player.interaction)
        assertEquals(SkillingAwardResult.Cancelled, f.player.awardSkillingProduct(f.events, mining))
        for (player in listOf(f.player, second)) {
            assertEquals(0, player.inv.count("obj.gotr_guardian_fragment"))
            assertEquals(0, player.inv.count(EssencePouch.GUARDIAN_ESSENCE))
            assertEquals(0, EssencePouch.storedAmountForType(player, EssencePouch.GUARDIAN_ESSENCE))
            assertEquals(6, EssencePouch.storedAmountForType(player, EssencePouch.PURE_ESSENCE))
            assertEquals(50, player.inv.count("obj.airrune"))
            assertEquals(1, player.vars["varbit.gotr_player_has_finished_game"])
        }
    }

    @Test fun `logout and login discard round materials even when old playing flag is absent`() {
        val f = Fixture()
        with(GotrScript(f.game)) {
            ScriptContext(f.events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        f.enter()
        for (event in listOf(SessionStateEvent.Logout(f.player), SessionStateEvent.Login(f.player))) {
            f.player.inv[0] = InvObj("obj.gotr_guardian_fragment", 300)
            f.player.inv[1] = InvObj(EssencePouch.GUARDIAN_ESSENCE)
            EssencePouch.setStoredEssenceType(f.player, EssencePouch.Tier.Small, EssencePouch.GUARDIAN_ESSENCE)
            EssencePouch.depositUpTo(f.player, EssencePouch.Tier.Small, 3)
            f.events.publish(event)
            assertEquals(0, f.player.inv.count("obj.gotr_guardian_fragment"))
            assertEquals(0, f.player.inv.count(EssencePouch.GUARDIAN_ESSENCE))
            assertEquals(0, EssencePouch.storedAmountForType(f.player, EssencePouch.GUARDIAN_ESSENCE))
            assertFalse(f.game.isMember(f.player))
        }
    }

    @Test fun `first participant gets preparation then two usable native portal names and creatures`() {
        val f = Fixture()
        f.game.start()
        f.clock.cycle = 9000
        f.enter()
        assertEquals(10, f.game.powerPercent())
        assertEquals(0, f.game.activeElapsedTicks())
        assertFalse(f.game.portalsAvailable())
        val fragments = f.miningProduct()
        f.events.publish(SkillingProductPrepareEvent(fragments))
        assertFalse(fragments.cancelled, "Preparation must still allow mining fragments.")
        assertEquals(1, fragments.count)
        f.tick(9200)
        assertEquals(0, f.defence.creatureCount())
        f.tick(9201)
        assertTrue(f.defence.creatureCount() > 0)
        assertTrue(f.game.isPortalOpen(f.game.currentElementalRune()))
        assertTrue(f.game.isPortalOpen(f.game.currentElementalRune().removePrefix("obj.").removeSuffix("rune")))
        assertTrue(f.game.isPortalOpen(f.game.currentCatalyticRune()))
        assertEquals(2, (GotrGame.ELEMENTAL_RUNES + GotrGame.CATALYTIC_RUNES).count(f.game::isPortalOpen))
    }

    @Test fun `portal pair rotates and first two pairs supply medium or better cells`() {
        val f = Fixture(); f.enter()
        f.tick(301)
        val initial = f.game.currentElementalRune() to f.game.currentCatalyticRune()
        assertFalse(initial.first == "obj.airrune" && initial.second in listOf("obj.mindrune", "obj.bodyrune"))
        f.tick(334)
        assertNotEquals(initial.first, f.game.currentElementalRune())
        assertNotEquals(initial.second, f.game.currentCatalyticRune())
        assertFalse(f.game.currentElementalRune() == "obj.airrune" &&
            f.game.currentCatalyticRune() in listOf("obj.mindrune", "obj.bodyrune"))
    }

    @Test fun `portal refresh includes every closed statue after an altar visit or region reload`() {
        val f = Fixture()
        val presentation = GotrPortalPresentation(f.locRepo, f.world)
        presentation.sync("air", "mind", 100)
        f.updates.clear()
        presentation.sync("water", "chaos", 101)
        assertEquals(14, f.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAnim>().size)
        f.updates.clear()
        presentation.sync("water", "chaos", 104)
        assertEquals(14, f.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAnim>().size)
        f.updates.clear()
        presentation.sync(null, null, 105)
        assertEquals(12, f.updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAnim>().size)
    }

    @Test fun `altar trip retains participation and unrelated teleport clears it and both overlays`() {
        val f = Fixture(); f.enter(); f.tick(102)
        assertTrue(f.player.ui.containsOverlay(GotrHud.HUD))
        val altar = CoordGrid(2841, 4829)
        f.game.visitAltar(f.player, altar); f.player.coords = altar
        f.tick(104)
        assertTrue(f.game.isMember(f.player))
        assertEquals(1, f.player.vars["varbit.gotr_is_playing"])
        assertTrue(f.player.ui.containsOverlay(GotrHud.HUD))
        assertFalse(f.player.ui.containsOverlay(GotrHud.ATMOSPHERE))
        f.player.coords = CoordGrid(3222, 3218)
        f.tick(106)
        assertFalse(f.game.isMember(f.player))
        assertEquals(0, f.player.vars["varbit.gotr_is_playing"])
        assertFalse(f.player.ui.containsOverlay(GotrHud.HUD))
    }

    @Test fun `remaining inside survives payout and next round resets counters and finished flag`() {
        val f = Fixture(); f.enter()
        repeat(225) { assertTrue(f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL)) }
        f.tick(101)
        assertEquals(GotrPhase.GRACE, f.game.phase)
        assertTrue(f.game.applyGuardianStone(f.player, GotrGame.STONE_CATALYTIC))
        f.tick(133)
        assertEquals(GotrPhase.LOBBY, f.game.phase)
        assertTrue(f.game.isMember(f.player))
        assertEquals(1, f.player.vars["varbit.gotr_is_playing"])
        assertEquals(1, f.player.vars["varbit.gotr_player_has_finished_game"])
        assertEquals(1, f.player.gotrRiftsClosed)
        val elementalPoints = f.player.gotrElementalPoints
        val catalyticPoints = f.player.gotrCatalyticPoints
        assertTrue(elementalPoints > 0 && catalyticPoints > 0)
        assertFalse(f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL))
        f.tick(237)
        assertEquals(GotrPhase.ACTIVE, f.game.phase)
        assertEquals(10, f.game.powerPercent())
        assertEquals(0, f.player.vars["varbit.gotr_player_has_finished_game"])
        val fragments = f.miningProduct()
        f.events.publish(SkillingProductPrepareEvent(fragments))
        assertFalse(fragments.cancelled, "The next preparation period must resume mining.")
        assertEquals(0, f.player.vars["varbit.gotr_elemental_earned_this_game"])
        assertTrue(f.game.isMember(f.player))
        assertEquals(elementalPoints, f.player.gotrElementalPoints)
        assertEquals(catalyticPoints, f.player.gotrCatalyticPoints)
        assertEquals(1, f.player.gotrRiftsClosed)
    }

    @Test fun `payout additions import old saved balances and retain existing reward caps`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(
            "gotr_elemental_points" to 123,
            "gotr_catalytic_points" to GotrGame.MAX_SEARCHES - 1,
            "gotr_total_searches" to 42,
            "gotr_rifts_closed" to 17,
        ))

        f.game.addPoints(f.player, 5, 7)

        assertEquals(128, f.player.gotrElementalPoints)
        assertEquals(GotrGame.MAX_SEARCHES, f.player.gotrCatalyticPoints)
        assertEquals(42, f.player.gotrTotalSearches)
        assertEquals(17, f.player.gotrRiftsClosed)
        assertTrue(f.player.attr.toPersistentMap().isEmpty())
        f.game.addPoints(f.player, -200, 0)
        assertEquals(0, f.player.gotrElementalPoints)
    }

    @Test fun `leaving the arena clears round energy while preserving saved reward balances`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(
            "gotr_elemental_points" to 123,
            "gotr_catalytic_points" to 45,
            "gotr_total_searches" to 42,
            "gotr_rifts_closed" to 17,
        ))
        f.player.migrateGotrRewards()
        f.enter()
        f.game.rewardCell(f.player, 100, 100, 0.0)

        f.game.leave(f.player)

        assertEquals(0, f.player.vars["varbit.gotr_elemental_earned_this_game"])
        assertEquals(0, f.player.vars["varbit.gotr_catalytic_earned_this_game"])
        assertEquals(123, f.player.gotrElementalPoints)
        assertEquals(45, f.player.gotrCatalyticPoints)
        assertEquals(42, f.player.gotrTotalSearches)
        assertEquals(17, f.player.gotrRiftsClosed)
    }

    @Test fun `departures never shrink remaining charge target into an automatic victory`() {
        val f = Fixture(); f.enter()
        val second = f.player(2, "Second", CoordGrid(3616, 9491))
        f.game.tryEnter(second)
        assertEquals(500, f.game.target())
        assertEquals(10, f.game.powerPercent())
        repeat(260) { f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL) }
        f.game.leave(second); f.players.remove(2)
        f.tick(102)
        assertEquals(GotrPhase.ACTIVE, f.game.phase)
        assertEquals(500, f.game.target())
    }

    @Test fun `logged out member removed and first next arrival gets a fresh round`() {
        val f = Fixture(); f.enter()
        f.players.remove(1)
        f.tick(102)
        assertEquals(0, f.game.memberCount())
        f.players[1] = f.player
        f.tick(700)
        assertEquals(1, f.game.memberCount())
        assertEquals(0, f.game.activeElapsedTicks())
    }

    @Test fun `native HUD carries eleven args with initial ten percent power and no open altar sprites`() {
        val f = Fixture(); f.enter(); f.tick(102)
        val packet = f.messages.filterIsInstance<RunClientScript>().last { it.id == 5980 }
        assertEquals(RunClientScript(5980, listOf(0, 0, 25, 250, 0, 0, 0, 0, 10, 198, -1)).toString(), packet.toString())
        assertTrue(f.player.ui.containsOverlay(GotrHud.HUD))
        assertTrue(f.player.ui.containsOverlay(GotrHud.ATMOSPHERE))
    }

    @Test fun `spec bonus restores run once per feed and never accelerates guardian charge`() {
        val f = Fixture(); f.enter()
        f.player.runEnergy = 0
        VarPlayerIntMapSetter.set(f.player, "varp.sa_energy", 1000)
        repeat(5) { f.game.applyGuardianStone(f.player, GotrGame.STONE_ELEMENTAL) }
        f.game.finishGuardianFeed(f.player)
        assertEquals(30.0, f.game.power)
        assertEquals(org.rsmod.api.config.constants.run_max_energy * 16 / 100, f.player.runEnergy)
    }

    @Test fun `per-type and combined energy limits apply to cells and guardian stones`() {
        val f = Fixture(); f.enter()
        VarPlayerIntMapSetter.set(f.player, "varbit.gotr_elemental_earned_this_game", 999)
        VarPlayerIntMapSetter.set(f.player, "varbit.gotr_catalytic_earned_this_game", 199)
        f.game.rewardCell(f.player, 15, 15, 0.0)
        assertEquals(1000, f.player.vars["varbit.gotr_elemental_earned_this_game"])
        assertEquals(200, f.player.vars["varbit.gotr_catalytic_earned_this_game"])
        f.game.applyGuardianStone(f.player, GotrGame.STONE_CATALYTIC)
        assertEquals(200, f.player.vars["varbit.gotr_catalytic_earned_this_game"])
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normal = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val locRepo = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val world = WorldRepository(updates)
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val defence = GotrDefence(locRepo, npcRepo, clock, random, RouteFactory(collision), RayCastValidator(collision), players, collision)
        val game = GotrGame(locRepo, players, clock, random, defence, GotrHud(events), GotrPortalPresentation(locRepo, world))
        val messages = mutableListOf<Any>()
        val client = object : Client<Any, Any> {
            override fun write(message: Any) { messages += message }
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
        val player = player(1, "Tester", CoordGrid(3615, 9491))
        init {
            with(GotrMiningScript(random)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
            for (x in 3520..3712 step 8) for (z in 9408..9600 step 8) collision.allocateIfAbsent(x, z, 0)
            npcRepo.add(Npc("npc.gotr_great_guardian", CoordGrid(3613, 9501)), Int.MAX_VALUE)
            for ((rune, coords) in GotrPortalPresentation.PLACEMENTS) {
                locRepo.add(coords, "loc.gotr_portal_$rune", Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
            }
        }
        fun miningProduct(): SkillingProduct {
            val type = checkNotNull(ServerCacheManager.getObject("loc.gotr_essence_tier_1".asRSCM()))
            val rock = BoundLocInfo(LocInfo(2, CoordGrid(3592, 9503), LocEntity(type.id, 10, 0)), type)
            val data = MiningRocksRow.all().single { it.rockObject.any { loc -> loc.id == type.id } }
            return SkillingProduct(player, "stat.mining", "obj.gotr_guardian_fragment",
                experience = 10.0, source = SkillingProductSource.Mining(rock, data))
        }
        fun player(slot: Int, name: String, coords: CoordGrid) = Player(client).apply {
            uuid = slot.toLong(); slotId = slot; displayName = name; currentMapClock = clock.cycle
            this.coords = coords
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
            players[slot] = this
        }
        fun enter() { game.start(); assertEquals(GotrEnterResult.Allowed, game.tryEnter(player)) }
        fun tick(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle; game.tick() }
    }
}
