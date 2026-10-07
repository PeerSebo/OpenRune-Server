@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.configs.BirdhouseParams
import org.rsmod.content.skills.hunter.configs.BirdhouseSpot
import org.rsmod.content.skills.hunter.configs.BirdhouseTier
import org.rsmod.content.skills.hunter.configs.HunterCatchRates
import org.rsmod.content.skills.hunter.configs.NetPreyTable
import org.rsmod.content.skills.hunter.configs.NettingParams
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BirdhouseAndNettingTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun inventoryServices() {
        val policy = QuestRequirements.activePolicy()
        restore += { QuestRequirements.install(policy) }
        QuestRequirements.install(QuestRequirementPolicy())
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `emptying destroys the house and duplicate completion cannot return more clockwork or XP`() {
        val f = Fixture()
        val task = f.houseTask(BirdhouseAction.EMPTY)
        with(f.houses) { f.access.finishDismantle(task); f.access.finishDismantle(task) }
        assertEquals(0, f.state())
        assertEquals(1, f.player.inv.count(BirdhouseParams.CLOCKWORK))
        assertEquals((BirdhouseTier.OAK.xp * 10).toInt(), f.player.statMap.getFineXP("stat.hunter"))
        assertEquals(0, f.player.inv.count(BirdhouseParams.RAW_BIRD_MEAT))
        assertEquals(10, f.objRegistry.count(), "All raw meat lands on the ground despite inventory space")
        f.clock.cycle += 1000
        ObjRepository::class.java.declaredMethods.single { it.name.startsWith("processDurations") }.apply { isAccessible = true }.invoke(f.objRepo)
        assertEquals(0, f.objRegistry.count(), "Meat must have a finite lifetime")
    }

    @Test fun `reset with only seeds cannot reuse the old house or consume seeds`() {
        val f = Fixture()
        f.player.inv[0] = f.item("obj.barley_seed", 20)
        with(f.houses) { f.access.finishDismantle(f.houseTask(BirdhouseAction.RESET)) }
        assertEquals(0, f.state())
        assertEquals(20, f.player.inv.count("obj.barley_seed"))
        assertEquals(1, f.player.inv.count(BirdhouseParams.CLOCKWORK))
    }

    @Test fun `reset consumes a premade replacement before logs and leaves it unseeded`() {
        val f = Fixture()
        f.player.inv[0] = f.item(BirdhouseTier.MAGIC.houseObj)
        f.player.inv[1] = f.item("obj.redwood_logs")
        f.player.inv[2] = f.item("obj.hammer")
        f.player.inv[3] = f.item("obj.chisel")
        with(f.houses) { f.access.finishDismantle(f.houseTask(BirdhouseAction.RESET)) }
        assertEquals(BirdhouseTier.MAGIC.builtVarValue, f.state())
        assertEquals(0, f.player.inv.count(BirdhouseTier.MAGIC.houseObj))
        assertEquals(1, f.player.inv.count("obj.redwood_logs"))
        assertEquals(1, f.player.inv.count(BirdhouseParams.CLOCKWORK))
        assertEquals(0, f.player.statMap.getFineXP("stat.crafting"))
    }

    @Test fun `reset crafts from a new log while reusing rather than duplicating the old clockwork`() {
        val f = Fixture()
        f.player.inv[0] = f.item("obj.oak_logs")
        f.player.inv[1] = f.item("obj.chisel")
        f.player.worn[5] = f.item("obj.imcando_hammer_offhand")
        with(f.houses) { f.access.finishDismantle(f.houseTask(BirdhouseAction.RESET)) }
        assertEquals(BirdhouseTier.OAK.builtVarValue, f.state())
        assertEquals(0, f.player.inv.count("obj.oak_logs"))
        assertEquals(0, f.player.inv.count(BirdhouseParams.CLOCKWORK))
        assertEquals(200, f.player.statMap.getFineXP("stat.crafting"))
    }

    @Test fun `walking away from a pending build neither builds remotely nor consumes its house`() {
        val f = Fixture()
        f.player.inv[0] = f.item(BirdhouseTier.OAK.houseObj)
        val task = BirdhouseBuildTask(BirdhouseSpot.ONE, BirdhouseTier.OAK, f.player.coords)
        f.player.coords = CoordGrid(3201, 3201)
        with(f.houses) { f.access.finishBuild(task) }
        assertEquals(0, f.state())
        assertEquals(1, f.player.inv.count(BirdhouseTier.OAK.houseObj))
    }

    @Test fun `expired Hunter boost prevents queued house construction`() {
        val f = Fixture()
        f.player.inv[0] = f.item(BirdhouseTier.MAGIC.houseObj)
        f.player.statMap.setCurrentLevel("stat.hunter", 73)
        with(f.houses) { f.access.finishBuild(BirdhouseBuildTask(BirdhouseSpot.ONE, BirdhouseTier.MAGIC, f.player.coords)) }
        assertEquals(0, f.state())
        assertEquals(1, f.player.inv.count(BirdhouseTier.MAGIC.houseObj))
    }

    @Test fun `on-spot crafting consumes both inputs even with a full inventory`() {
        val f = Fixture()
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[0] = f.item("obj.oak_logs")
        f.player.inv[1] = f.item(BirdhouseParams.CLOCKWORK)
        f.player.inv[2] = f.item("obj.hammer")
        f.player.inv[3] = f.item("obj.chisel")
        with(f.houses) { f.access.finishBuild(BirdhouseBuildTask(BirdhouseSpot.ONE, BirdhouseTier.OAK, f.player.coords, craft = true)) }
        assertEquals(BirdhouseTier.OAK.builtVarValue, f.state())
        assertEquals(0, f.player.inv.count("obj.oak_logs"))
        assertEquals(0, f.player.inv.count(BirdhouseParams.CLOCKWORK))
        assertEquals(200, f.player.statMap.getFineXP("stat.crafting"))
    }

    @Test fun `missing clockwork cannot consume the log or award crafting XP`() {
        val f = Fixture()
        f.player.inv[0] = f.item("obj.oak_logs")
        f.player.inv[1] = f.item("obj.hammer")
        f.player.inv[2] = f.item("obj.chisel")
        with(f.houses) { f.access.finishBuild(BirdhouseBuildTask(BirdhouseSpot.ONE, BirdhouseTier.OAK, f.player.coords, craft = true)) }
        assertEquals(0, f.state())
        assertEquals(1, f.player.inv.count("obj.oak_logs"))
        assertEquals(0, f.player.statMap.getFineXP("stat.crafting"))
    }

    @Test fun `a stale seed action cannot seed a different tier now occupying the spot`() {
        val f = Fixture()
        f.setState(BirdhouseTier.MAGIC.builtVarValue)
        f.player.inv[0] = f.item("obj.barley_seed", 10)
        with(f.houses) { f.access.finishSeeding(BirdhouseSeedTask(BirdhouseSpot.ONE, "obj.barley_seed", 10, f.player.coords, BirdhouseTier.OAK.builtVarValue)) }
        assertEquals(BirdhouseTier.MAGIC.builtVarValue, f.state())
        assertEquals(10, f.player.inv.count("obj.barley_seed"))
    }

    @Test fun `a stale harvest snapshot cannot claim a later house of the same tier`() {
        val f = Fixture()
        val task = f.houseTask(BirdhouseAction.EMPTY)
        f.setMinute(task.seededMinute + 100)
        with(f.houses) { f.access.finishDismantle(task) }
        assertEquals(BirdhouseTier.OAK.finishedVarValue, f.state())
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `login refresh matures all four spots at fifty minutes and clock rollback cannot finish early`() {
        val f = Fixture()
        for (spot in BirdhouseSpot.entries) {
            VarPlayerIntMapSetter.set(f.player, spot.stateVarp, BirdhouseTier.OAK.seededVarValue)
            VarPlayerIntMapSetter.set(f.player, spot.seededMinuteVarp, 100)
        }
        f.houses.refreshBirdhouses(f.player, 99)
        assertEquals(BirdhouseTier.OAK.seededVarValue, f.state())
        f.houses.refreshBirdhouses(f.player, 149)
        assertEquals(BirdhouseTier.OAK.seededVarValue, f.state())
        f.houses.refreshBirdhouses(f.player, 150)
        for (spot in BirdhouseSpot.entries) assertEquals(BirdhouseTier.OAK.finishedVarValue, f.player.vars[spot.stateVarp])
        assertFalse(f.houses.isReady(0, 150))
        assertFalse(BirdhouseTier.isSeeded(29))
    }

    @Test fun `net catch can replace the last jar in a full inventory and pays surface XP once`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_2")
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        val task = f.netTask(npc)
        with(f.nets) { f.access.finishCatch(task); f.access.finishCatch(task) }
        assertEquals(1, f.player.inv.count("obj.ii_captured_impling_2"))
        assertEquals(0, f.player.inv.count(NettingParams.IMPLING_JAR))
        assertEquals(220, f.player.statMap.getFineXP("stat.hunter"))
        assertEquals(0, f.objRegistry.count())
        assertTrue(npc.hidden)
    }

    @Test fun `Puro-Puro has its own lower XP for every impling tier`() {
        val xp = listOf(18, 20, 22, 25, 27, 30, 34, 44, 50, 65, 80)
        for ((index, amount) in xp.withIndex()) {
            val prey = NetPreyTable.ALL.single { "npc.ii_impling_type_${index + 1}_maze" in it.npcs }
            assertEquals(amount.toDouble(), prey.xp)
        }
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_2_maze")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        with(f.nets) { f.access.finishCatch(f.netTask(npc)) }
        assertEquals(200, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `removing the net mid-swing aborts without taking the jar or rolling success`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_1")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        with(f.nets) { f.access.finishCatch(f.netTask(npc)) }
        assertEquals(1, f.player.inv.count(NettingParams.IMPLING_JAR))
        assertEquals(0, f.random.integerRolls)
        assertFalse(npc.hidden)
    }

    @Test fun `lost Hunter level aborts before consuming a jar or rolling success`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_1")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        f.player.statMap.setCurrentLevel("stat.hunter", 16)
        with(f.nets) { f.access.finishCatch(f.netTask(npc)) }
        assertEquals(1, f.player.inv.count(NettingParams.IMPLING_JAR))
        assertEquals(0, f.random.integerRolls)
    }

    @Test fun `flying or walking out of reach cancels a queued catch`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_1")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        val task = f.netTask(npc)
        npc.coords = CoordGrid(3210, 3210)
        with(f.nets) { f.access.finishCatch(task) }
        assertEquals(0, f.random.integerRolls)
        assertEquals(1, f.player.inv.count(NettingParams.IMPLING_JAR))
    }

    @Test fun `barehanded implings require ten extra Hunter levels and still consume a supplied jar`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_1")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.statMap.setCurrentLevel("stat.hunter", 26)
        with(f.nets) { f.access.finishCatch(f.netTask(npc, NetCatchMethod.BAREHANDED)) }
        assertFalse(npc.hidden)
        f.player.statMap.setCurrentLevel("stat.hunter", 27)
        with(f.nets) { f.access.finishCatch(f.netTask(npc, NetCatchMethod.BAREHANDED)) }
        assertTrue(npc.hidden)
        assertEquals(1, f.player.inv.count("obj.ii_captured_impling_1"))
    }

    @Test fun `reclicking Catch replaces rather than stacks pending catch attempts`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_1")
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        with(f.nets) { f.access.startCatch(npc); f.access.startCatch(npc); f.access.startCatch(npc) }
        assertEquals(1, f.player.queueList.size)
        assertEquals(0, f.random.integerRolls)
    }

    @Test fun `a butterfly caught without a jar applies its effect without creating a free jar`() {
        val f = Fixture()
        val npc = f.npc("npc.butterfly_ruby")
        f.player.statMap.setBaseLevel("stat.attack", 40)
        f.player.statMap.setCurrentLevel("stat.attack", 40)
        with(f.nets) { f.access.finishCatch(f.netTask(npc, NetCatchMethod.BAREHANDED)) }
        assertEquals(50, f.player.statMap.getCurrentLevel("stat.attack"))
        assertEquals(0, f.player.inv.count("obj.butterfly_jar_ruby"))
        assertEquals(240, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `magic net supplies the published twenty unit catch bonus`() {
        val f = Fixture()
        val npc = f.npc("npc.ii_impling_type_10")
        f.player.statMap.setCurrentLevel("stat.hunter", 83)
        f.player.inv[0] = f.item(NettingParams.IMPLING_JAR)
        f.random.roll = 106
        f.player.worn[3] = f.item(NettingParams.BUTTERFLY_NET)
        with(f.nets) { f.access.finishCatch(f.netTask(npc)) }
        assertFalse(npc.hidden)
        assertEquals(1, f.player.inv.count(NettingParams.IMPLING_JAR))
        f.player.worn[3] = f.item(NettingParams.MAGIC_NET)
        with(f.nets) { f.access.finishCatch(f.netTask(npc, NetCatchMethod.MAGIC_NET)) }
        assertTrue(npc.hidden)
        assertEquals(1, f.player.inv.count("obj.ii_captured_impling_10"))
    }

    @Test fun `published birdhouse nest curves include the low level floor and August tripling`() {
        for (level in 1..50) {
            assertEquals(0.15, BirdhouseParams.publishedNestChance(BirdhouseTier.NORMAL, level)!!, 1e-9)
            assertEquals(0.255, BirdhouseParams.publishedNestChance(BirdhouseTier.MAGIC, level)!!, 1e-9)
        }
        assertEquals(0.153, BirdhouseParams.publishedNestChance(BirdhouseTier.NORMAL, 51)!!, 1e-9)
        assertEquals(0.3, BirdhouseParams.publishedNestChance(BirdhouseTier.NORMAL, 99)!!, 1e-9)
        assertEquals(0.260, BirdhouseParams.publishedNestChance(BirdhouseTier.MAGIC, 51), 1e-9)
        assertEquals(0.379, BirdhouseParams.publishedNestChance(BirdhouseTier.MAGIC, 74), 1e-9)
        assertEquals(0.51, BirdhouseParams.publishedNestChance(BirdhouseTier.MAGIC, 99)!!, 1e-9)
        assertEquals(0.375, BirdhouseParams.publishedNestChance(BirdhouseTier.OAK, 99), 1e-9)
    }

    @Test fun `normal and magic harvests use the corrected probabilities for all five rolls`() {
        for ((tier, level, roll) in listOf(Triple(BirdhouseTier.NORMAL, 5, 0.1), Triple(BirdhouseTier.MAGIC, 99, 0.4))) {
            val f = Fixture()
            f.player.statMap.setCurrentLevel("stat.hunter", level.toByte())
            f.random.doubleRoll = roll
            with(f.houses) { f.access.finishDismantle(f.houseTask(BirdhouseAction.EMPTY, tier)) }
            assertEquals(5, f.player.inv.count(BirdhouseParams.EMPTY_NEST), "Five rolls should succeed at $tier / $level")
        }
    }

    @Test fun `partial and mixed seeds persist without starting the timer and stale completions do not consume twice`() {
        val f = Fixture()
        f.setState(BirdhouseTier.OAK.builtVarValue)
        f.player.inv[0] = f.item("obj.barley_seed", 7)
        f.player.inv[1] = f.item("obj.hammerstone_hop_seed", 4)
        val first = BirdhouseSeedTask(BirdhouseSpot.ONE, "obj.barley_seed", 7, f.player.coords, f.state())
        with(f.houses) { f.access.finishSeeding(first); f.access.finishSeeding(first) }
        assertEquals(BirdhouseTier.OAK.builtVarValue, f.state())
        assertEquals(-7, f.player.vars[BirdhouseSpot.ONE.seededMinuteVarp])
        f.houses.refreshBirdhouses(f.player, Int.MAX_VALUE)
        assertEquals(BirdhouseTier.OAK.builtVarValue, f.state())
        val second = BirdhouseSeedTask(BirdhouseSpot.ONE, "obj.hammerstone_hop_seed", 3, f.player.coords, f.state(), 7)
        with(f.houses) { f.access.finishSeeding(second); f.access.finishSeeding(second) }
        assertEquals(BirdhouseTier.OAK.seededVarValue, f.state())
        assertTrue(f.player.vars[BirdhouseSpot.ONE.seededMinuteVarp] > 0)
        assertEquals(1, f.player.inv.count("obj.hammerstone_hop_seed"))
    }

    @Test fun `a high level seed fills two units and only enough seeds to finish are consumed`() {
        val f = Fixture()
        f.setState(BirdhouseTier.OAK.builtVarValue)
        f.setMinute(-7)
        f.player.inv[0] = f.item("obj.wildblood_hop_seed", 5)
        val task = BirdhouseSeedTask(BirdhouseSpot.ONE, "obj.wildblood_hop_seed", 2, f.player.coords, f.state(), 7)
        with(f.houses) { f.access.finishSeeding(task) }
        assertEquals(3, f.player.inv.count("obj.wildblood_hop_seed"))
        assertEquals(BirdhouseTier.OAK.seededVarValue, f.state())
    }

    @Test fun `valuable seed warning obeys toggle and the price of one seed rather than stack size`() {
        val f = Fixture()
        val price = checkNotNull(ServerCacheManager.getItem("obj.ranarr_seed".asRSCM())).cost
        VarPlayerIntMapSetter.set(f.player, "varbit.settings_birdhouse_seed_warning_enabled", 1)
        VarPlayerIntMapSetter.set(f.player, "varp.settings_birdhouse_seed_warning_value", price)
        assertFalse(f.houses.shouldWarnAboutSeed(f.player, "obj.ranarr_seed"))
        VarPlayerIntMapSetter.set(f.player, "varp.settings_birdhouse_seed_warning_value", price - 1)
        assertTrue(f.houses.shouldWarnAboutSeed(f.player, "obj.ranarr_seed"))
        VarPlayerIntMapSetter.set(f.player, "varbit.settings_birdhouse_seed_warning_enabled", 0)
        assertFalse(f.houses.shouldWarnAboutSeed(f.player, "obj.ranarr_seed"))
    }

    @Test fun `all nine current nest curves retain the low level floor and benefit from boosting above 99`() {
        val ends = listOf(.300, .375, .384, .390, .420, .450, .480, .510, .525)
        val floors = listOf(.150, .187, .192, .195, .210, .225, .240, .255, .262)
        for (tier in BirdhouseTier.entries) {
            assertEquals(floors[tier.ordinal], BirdhouseParams.publishedNestChance(tier, 1), 1e-9)
            assertEquals(floors[tier.ordinal], BirdhouseParams.publishedNestChance(tier, 50), 1e-9)
            assertEquals(ends[tier.ordinal], BirdhouseParams.publishedNestChance(tier, 99), 1e-9)
            assertTrue(BirdhouseParams.publishedNestChance(tier, 105) > ends[tier.ordinal])
        }
    }

    @Test fun `overworld jarless impling awards its loot and catch event once while Puro-Puro preserves it without a jar`() {
        val f = Fixture()
        f.random.roll = 1
        var catches = 0
        f.events.subscribeUnbound(HunterCatchEvent::class.java) { catches++ }
        val npc = f.npc("npc.ii_impling_type_1")
        val task = f.netTask(npc, NetCatchMethod.BAREHANDED)
        with(f.nets) { f.access.finishCatch(task); f.access.finishCatch(task) }
        assertTrue(npc.hidden)
        assertEquals(1, f.player.inv.count("obj.chisel"))
        assertEquals(0, f.player.inv.count(NettingParams.IMPLING_JAR))
        assertEquals(200, f.player.statMap.getFineXP("stat.hunter"))
        assertEquals(1, catches)
        val maze = f.npc("npc.ii_impling_type_1_maze")
        with(f.nets) { f.access.finishCatch(f.netTask(maze, NetCatchMethod.BAREHANDED)) }
        assertFalse(maze.hidden)
        assertEquals(1, catches)
    }

    @Test fun `opening a jar in a full inventory gives owned overflow and cannot replay an obsolete slot`() {
        val f = Fixture()
        f.random.roll = 1
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[0] = f.item("obj.ii_captured_impling_1")
        val script = ImplingJarScript(f.objRepo, f.random)
        with(script) { f.access.openJar(1, 0); f.access.openJar(1, 0) }
        assertEquals(0, f.player.inv.count("obj.ii_captured_impling_1"))
        assertEquals(1, f.player.inv.count(NettingParams.IMPLING_JAR))
        assertEquals(1, f.objRegistry.count(), "The chisel lands on the ground")
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `sunlight moth jar release preserves boosted stats and does not restore run energy`() {
        val f = Fixture()
        val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_sunmoth" }
        f.player.inv[0] = f.item(prey.filledJar)
        f.player.runEnergy = 5000
        f.player.statMap.setBaseLevel("stat.attack", 50)
        f.player.statMap.setCurrentLevel("stat.attack", 60)
        f.player.statMap.setBaseLevel("stat.defence", 50)
        f.player.statMap.setCurrentLevel("stat.defence", 10)
        f.player.statMap.setBaseLevel("stat.hitpoints", 50)
        f.player.statMap.setCurrentLevel("stat.hitpoints", 20)
        with(f.nets) { f.access.releaseJar(prey, 0); f.access.releaseJar(prey, 0) }
        assertEquals(60, f.player.statMap.getCurrentLevel("stat.attack").toInt())
        assertEquals(26, f.player.statMap.getCurrentLevel("stat.defence").toInt())
        assertEquals(28, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
        assertEquals(5000, f.player.runEnergy)
        assertEquals(1, f.player.inv.count(NettingParams.BUTTERFLY_JAR))
    }

    @Test fun `moth and black warlock catch curves retain their distinct enhanced methods`() {
        val sun = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_sunmoth" }
        val moon = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_moonmoth" }
        val black = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_warlock" }
        assertFalse(sun.barehandedBonus)
        assertEquals(201, HunterCatchRates.chance(sun.rate!!, 65))
        assertEquals(221, HunterCatchRates.chance(sun.enhancedRate!!, 65))
        assertEquals(209, HunterCatchRates.chance(moon.rate!!, 75))
        assertEquals(222, HunterCatchRates.chance(moon.enhancedRate!!, 75))
        assertEquals(145, HunterCatchRates.chance(black.rate!!, 45))
        for (prey in listOf(sun, moon, black)) assertEquals("Release", ServerCacheManager.getItem(prey.filledJar.asRSCM())!!.interfaceOptions[3])
    }

    @Test fun `one successful clue series replaces one nest and cannot repeat within the harvest or on replay`() {
        val f = Fixture()
        f.random.doubleRoll = 0.0
        f.random.clueRoll = 0
        val task = f.houseTask(BirdhouseAction.EMPTY)
        with(f.houses) { f.access.finishDismantle(task); f.access.finishDismantle(task) }
        for (tier in BirdhouseClues.Tier.entries) assertEquals(1, f.player.inv.count(tier.box))
        assertEquals(5, f.random.clueRolls, "All tiers in the first winning series, no later series")
        assertEquals(4, f.player.inv.count(BirdhouseParams.EMPTY_NEST), "The other four nest rolls remain ordinary")
        assertEquals(15, f.objRegistry.count(), "Ten meat and all five empty clue nests stay on the ground")
    }

    @Test fun `unboxed clues in persistent storage or native clue containers block a duplicate tier`() {
        val f = Fixture()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        f.random.clueRoll = 0
        for ((slot, tier) in BirdhouseClues.Tier.entries.withIndex()) f.player.inv[slot] = f.item(tier.nest)
        assertTrue(BirdhouseClues.roll(f.player, f.random).isEmpty())
        f.player.inv[0] = null
        val storage = Inventory(InventoryServerType(size = 1), arrayOfNulls(1))
        storage[0] = f.item(BirdhouseClues.Tier.ELITE.scroll)
        f.player.invMap["inv.bank"] = storage
        assertTrue(BirdhouseClues.roll(f.player, f.random).isEmpty())
        storage[0] = f.item("obj.placeholder_wc_clue_nest_elite")
        assertEquals(listOf(BirdhouseClues.Tier.ELITE), BirdhouseClues.roll(f.player, f.random))
        storage[0] = f.item("obj.trail_reward_casket_elite")
        assertEquals(listOf(BirdhouseClues.Tier.ELITE), BirdhouseClues.roll(f.player, f.random), "Completed reward caskets do not block new clues")
    }

    @Test fun `clue nest CA bonus is exactly five percent and only applies to its rewarded tier`() {
        val f = Fixture()
        for (tier in BirdhouseClues.Tier.entries.filter { it != BirdhouseClues.Tier.BEGINNER }) {
            f.random.clueRoll = tier.weight * 20
            assertFalse(tier in BirdhouseClues.roll(f.player, f.random))
            VarPlayerIntMapSetter.set(f.player, "varbit.ca_lamp_claimed_${tier.suffix}", 1)
            assertTrue(tier in BirdhouseClues.roll(f.player, f.random))
            f.random.clueRoll = tier.weight * 21
            assertFalse(tier in BirdhouseClues.roll(f.player, f.random))
        }
        f.random.clueRoll = 600
        assertFalse(BirdhouseClues.Tier.BEGINNER in BirdhouseClues.roll(f.player, f.random))
    }

    @Test fun `impling clues respect unopened nests while completed reward caskets allow a new clue`() {
        val f = Fixture()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        val tier = BirdhouseClues.Tier.EASY
        val rewards = listOf(ImplingLoot.Reward(tier.scroll, 1))
        f.player.inv[0] = f.item(tier.nest)
        with(ImplingLoot) { f.access.award(f.objRepo, rewards) }
        assertEquals(1, f.player.inv.count(tier.nest))
        assertEquals(0, f.player.inv.count(tier.scroll))
        f.player.inv[0] = f.item("obj.trail_reward_casket_easy")
        with(ImplingLoot) { f.access.award(f.objRepo, rewards) }
        assertEquals(1, f.player.inv.count(tier.scroll))
        assertEquals(1, f.player.inv.count("obj.trail_reward_casket_easy"))
    }

    @Test fun `searching a clue nest preserves its shell with full inventory and cannot pay twice`() {
        val f = Fixture()
        val tier = BirdhouseClues.Tier.EASY
        val script = BirdhouseClueNestScript(f.objRepo)
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[7] = f.item(tier.nest)
        with(script) { f.access.search(tier, 7); f.access.search(tier, 7) }
        assertEquals(1, f.player.inv.count(BirdhouseParams.EMPTY_NEST))
        assertEquals(0, f.player.inv.count(tier.nest))
        assertEquals(1, f.objRegistry.count(), "The scroll box overflows once")
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `unboxed clue nest search rejects an existing clue without consuming the nest`() {
        val f = Fixture()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        val tier = BirdhouseClues.Tier.HARD
        val script = BirdhouseClueNestScript(f.objRepo)
        f.player.inv[0] = f.item(tier.nest)
        f.player.inv[1] = f.item(tier.scroll)
        with(script) { f.access.search(tier, 0) }
        assertEquals(1, f.player.inv.count(tier.nest))
        f.player.inv[1] = null
        with(script) { f.access.search(tier, 0); f.access.search(tier, 0) }
        assertEquals(1, f.player.inv.count(tier.scroll))
        assertEquals(1, f.player.inv.count(BirdhouseParams.EMPTY_NEST))
    }

    @Test fun `sharing consumes only the selected jar in a full inventory and does not repeat or require Hunter levels`() {
        val f = Fixture()
        val target = f.recipient()
        val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_moonmoth" }
        f.player.statMap.setCurrentLevel("stat.hunter", 1)
        f.player.inv.objs.fill(f.item("obj.logs"))
        f.player.inv[0] = f.item(prey.filledJar)
        f.player.inv[7] = f.item(prey.filledJar)
        with(f.aid) { f.access.share(prey, 7, target); f.access.share(prey, 7, target) }
        assertEquals(32, target.statMap.getCurrentLevel("stat.prayer").toInt())
        assertEquals(f.item(prey.filledJar).id, f.player.inv[0]?.id)
        assertEquals(f.item(NettingParams.BUTTERFLY_JAR).id, f.player.inv[7]?.id)
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `sharing affects the primary recipient plus at most three eligible people in its five by five area`() {
        val f = Fixture()
        val target = f.recipient()
        val boundary = f.recipient(3, CoordGrid(3203, 3202))
        val near1 = f.recipient(4, CoordGrid(3201, 3201))
        val near2 = f.recipient(5, CoordGrid(3200, 3201))
        val excess = f.recipient(6, CoordGrid(3201, 3202))
        val outside = f.recipient(7, CoordGrid(3204, 3200))
        val otherPlane = f.recipient(8, CoordGrid(3201, 3200, 1))
        val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_snowy" }
        f.player.inv[0] = f.item(prey.filledJar)
        with(f.aid) { f.access.share(prey, 0, target) }
        for (recipient in listOf(target, boundary, near1, near2)) assertEquals(25, recipient.statMap.getCurrentLevel("stat.hitpoints").toInt())
        for (recipient in listOf(excess, outside, otherPlane)) assertEquals(10, recipient.statMap.getCurrentLevel("stat.hitpoints").toInt())
    }

    @Test fun `refusing or invalid primary recipients preserve the jar and cannot grant bystander aid`() {
        val rejectors: List<(Fixture, Player) -> Unit> = listOf(
            { _, p -> VarPlayerIntMapSetter.set(p, "varbit.option_acceptaid", 0) },
            { _, p -> p.gamemode = 1 },
            { _, p -> p.gamemode = 4 },
            { _, p -> p.hidden = true },
            { _, p -> p.pendingLogout = true },
            { _, p -> p.delay = 101 },
            { _, p -> p.statMap.setCurrentLevel("stat.hitpoints", 0) },
            { _, p -> p.coords = CoordGrid(3210, 3200) },
            { _, p -> p.coords = CoordGrid(3201, 3199) },
            { f, _ -> f.player.coords = CoordGrid(3200, 3199) },
        )
        for (reject in rejectors) {
            val f = Fixture()
            val target = f.recipient()
            val bystander = f.recipient(3)
            val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_moonmoth" }
            f.player.inv[0] = f.item(prey.filledJar)
            reject(f, target)
            with(f.aid) { f.access.share(prey, 0, target) }
            assertEquals(1, f.player.inv.count(prey.filledJar))
            assertEquals(10, bystander.statMap.getCurrentLevel("stat.prayer").toInt())
        }
    }

    @Test fun `shared effects cover all six native butterfly jars and sunlight excludes prayer energy and Sailing`() {
        val effects = mapOf("ruby" to ("attack" to 21), "glacialis" to ("defence" to 21), "warlock" to ("strength" to 21), "snowy" to ("hitpoints" to 25), "moonmoth" to ("prayer" to 32), "sunmoth" to ("defence" to 26))
        for ((suffix, expected) in effects) {
            val f = Fixture()
            val target = f.recipient()
            target.runEnergy = 1000
            val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_$suffix" }
            f.player.inv[0] = f.item(prey.filledJar)
            with(f.aid) { f.access.share(prey, 0, target) }
            assertEquals(expected.second, target.statMap.getCurrentLevel("stat.${expected.first}").toInt(), suffix)
            assertEquals(1000, target.runEnergy)
            assertEquals(10, target.statMap.getCurrentLevel("stat.sailing").toInt())
            if (suffix == "sunmoth") {
                assertEquals(18, target.statMap.getCurrentLevel("stat.hitpoints").toInt())
                assertEquals(10, target.statMap.getCurrentLevel("stat.prayer").toInt())
            }
        }
    }

    @Test fun `a consenting sender remains eligible while its resumed coroutine releases a jar`() {
        val f = Fixture()
        val target = f.recipient()
        val prey = NetPreyTable.ALL.single { it.filledJar == "obj.butterfly_jar_moonmoth" }
        f.player.inv[0] = f.item(prey.filledJar)
        f.player.statMap.setBaseLevel("stat.prayer", 50)
        f.player.statMap.setCurrentLevel("stat.prayer", 10)
        VarPlayerIntMapSetter.set(f.player, "varbit.option_acceptaid", 1)
        var resume = false
        val coroutine = f.player.launch {
            pause { resume }
            assertFalse(f.player.isAccessProtected, "Running a protected action is not a suspended action")
            with(f.aid) { f.access.share(prey, 0, target) }
        }
        assertTrue(f.player.isAccessProtected)
        resume = true
        coroutine.advance()
        assertEquals(32, f.player.statMap.getCurrentLevel("stat.prayer").toInt())
        assertEquals(32, target.statMap.getCurrentLevel("stat.prayer").toInt())
        assertEquals(1, f.player.inv.count(NettingParams.BUTTERFLY_JAR))
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val random = CatchRandom()
        val collision = CollisionFlagMap().apply { for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) allocateIfAbsent(x, z, 0) }
        val player = Player().apply {
            uuid = 1L
            observerUUID = 1L
            slotId = 1
            assignUid()
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hunter", 99)
            statMap.setCurrentLevel("stat.hunter", 99)
            statMap.setBaseLevel("stat.crafting", 99)
            statMap.setCurrentLevel("stat.crafting", 99)
            statMap.setBaseLevel("stat.hitpoints", 50)
            statMap.setCurrentLevel("stat.hitpoints", 50)
            pendingSequence = EntitySeq(5209, 0, 5)
        }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val areaIndex = AreaIndex()
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), LocRegistryNormal(updates, collision, storage), collision, storage, npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val areas = AreaChecker(regions, areaIndex)
        val playerRegistry = PlayerRegistry(players, collision, ZonePlayerActivityBitSet(), events).apply { change(player, ZoneKey.NULL, ZoneKey.from(player.coords)) }
        val aid = ButterflyAidScript(PlayerRepository(playerRegistry), areas)
        val objRegistry = ObjRegistry(ZoneUpdateMap())
        val objRepo = ObjRepository(clock, objRegistry)
        val houses = BirdhouseScript(objRepo, XpModifiers(emptySet()), random, players, object : MarketPrices {
            override fun get(type: ItemServerType): Int = type.cost
        })
        val nets = NetCatchScript(npcRepo, objRepo, random, XpModifiers(emptySet()), events)
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players }, getCollision = { collision },
            getAreaChecker = { areas }, getAlignment = { error("unused") }, getLocInteractions = { error("unused") },
            getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") }, getHeldInteractions = { error("unused") },
            getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") }, getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        ))
        fun item(symbol: String, count: Int = 1) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())), count)
        private val multiCoords = mutableSetOf<CoordGrid>()
        fun multi(coords: CoordGrid) {
            if (multiCoords.add(coords)) areaIndex.registerAll(coords, listOf("area.multiway".asRSCM().toShort()).iterator())
        }
        fun recipient(slot: Int = 2, coords: CoordGrid = CoordGrid(3201, 3200)): Player = Player().apply {
            uuid = slot.toLong(); observerUUID = slot.toLong(); slotId = slot; assignUid()
            this.coords = coords
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            for (stat in listOf("attack", "strength", "defence", "hitpoints", "prayer", "sailing")) {
                statMap.setBaseLevel("stat.$stat", 50)
                statMap.setCurrentLevel("stat.$stat", 10)
            }
            VarPlayerIntMapSetter.set(this, "varbit.option_acceptaid", 1)
            players[slot] = this
            playerRegistry.change(this, ZoneKey.NULL, ZoneKey.from(coords))
            multi(coords)
            multi(player.coords)
        }
        fun npc(symbol: String): Npc = Npc(symbol, CoordGrid(3200, 3201)).apply { hitpoints = 10; npcRepo.add(this, Int.MAX_VALUE) }
        fun netTask(npc: Npc, method: NetCatchMethod = NetCatchMethod.NET) = NetCatchTask(npc.uid, player.coords, npc.visType.id, method)
        fun setState(value: Int) = VarPlayerIntMapSetter.set(player, BirdhouseSpot.ONE.stateVarp, value)
        fun setMinute(value: Int) = VarPlayerIntMapSetter.set(player, BirdhouseSpot.ONE.seededMinuteVarp, value)
        fun state() = player.vars[BirdhouseSpot.ONE.stateVarp]
        fun houseTask(action: BirdhouseAction, tier: BirdhouseTier = BirdhouseTier.OAK): BirdhouseDismantleTask {
            setState(tier.finishedVarValue)
            setMinute(100)
            return BirdhouseDismantleTask(BirdhouseSpot.ONE, action, player.coords, state(), 100)
        }
    }
    private class CatchRandom : GameRandom {
        var integerRolls = 0
        var roll = 0
        var clueRoll = BirdhouseClues.DENOMINATOR - 1
        var clueRolls = 0
        var doubleRoll = 0.999999
        override fun of(maxExclusive: Int): Int {
            integerRolls++
            if (maxExclusive == BirdhouseClues.DENOMINATOR) { clueRolls++; return clueRoll }
            return roll.coerceAtMost(maxExclusive - 1)
        }
        override fun of(minInclusive: Int, maxInclusive: Int): Int { integerRolls++; return maxInclusive }
        override fun randomDouble(): Double = doubleRoll
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }
}
