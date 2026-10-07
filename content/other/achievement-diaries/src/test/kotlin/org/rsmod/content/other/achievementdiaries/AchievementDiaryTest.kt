@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.other.achievementdiaries

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.constants
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class AchievementDiaryTest {
    @Test
    fun `native easy tasks use bits one through twelve and preserve other tiers`() {
        val player = player()
        val tier = DiaryData.easy
        VarPlayerIntMapSetter.set(player, tier.taskVarp, 1 or (1 shl 24))
        record(player, "LearnAge", "npc.hans", CoordGrid(3215, 3219))
        record(player, "LearnAge", "npc.hans", CoordGrid(3215, 3219))
        assertEquals(1 or (1 shl 5) or (1 shl 24), player.vars[tier.taskVarp])
        assertEquals(1, player.vars[tier.countVar])
        assertEquals(1, player.vars[tier.startedVar])
        assertEquals(0, player.vars[tier.completeVar])
    }

    @Test
    fun `wrong creature location item and plane do not complete a task`() {
        val player = player()
        record(player, "NpcKill", "npc.swamp_cave_bug", CoordGrid(3200, 3200))
        record(player, "NpcKill", "npc.zombie_unarmed_sewer1", CoordGrid(3120, 9650, 1))
        record(player, "Mining", "obj.copper_ore", CoordGrid(3303, 3284))
        record(player, "Fishing", "obj.raw_anchovies", CoordGrid(3086, 3230))
        record(player, "Pickpocket", "npc.man2", CoordGrid(3215, 3219), inArea = { _, _ -> false })
        assertEquals(0, DiaryProgress.completedCount(player))
        record(player, "NpcKill", "npc.swamp_cave_bug", CoordGrid(3151, 9574))
        record(player, "NpcKill", "npc.zombie_unarmed_sewer1", CoordGrid(3120, 9650))
        record(player, "Mining", "obj.iron_ore", CoordGrid(3303, 3284))
        record(player, "Fishing", "obj.raw_anchovies", CoordGrid(3267, 3148))
        assertEquals(4, DiaryProgress.completedCount(player))
    }

    @Test
    fun `oak task needs chopping then burning and only counts once`() {
        val player = player()
        val coords = CoordGrid(3219, 3206)
        record(player, "BurnLog", "obj.oak_logs", coords)
        assertEquals(0, DiaryProgress.completedCount(player))
        record(player, "Woodcutting", "obj.oak_logs", coords)
        assertEquals(1, player.vars[DiaryProgress.OakStage])
        record(player, "BurnLog", "obj.logs", coords)
        assertEquals(0, DiaryProgress.completedCount(player))
        record(player, "BurnLog", "obj.oak_logs", coords)
        assertEquals(1, DiaryProgress.completedCount(player))
        assertEquals(0, player.vars[DiaryProgress.OakStage])
        record(player, "Woodcutting", "obj.oak_logs", coords)
        assertEquals(0, player.vars[DiaryProgress.OakStage])
    }

    @Test
    fun `all twelve successful actions complete easy tier without claiming rewards`() {
        val player = player()
        completeAll(player)
        val tier = DiaryData.easy
        assertEquals(12, player.vars[tier.countVar])
        assertEquals(1, player.vars[tier.completeVar])
        assertEquals(0, player.vars[tier.rewardVar])
    }

    @Test
    fun `reward transaction rolls back a single free slot and cannot be claimed twice`() {
        val player = player()
        val rewards = AchievementDiaryRewards()
        assertEquals(AchievementDiaryRewards.ClaimResult.NotComplete, rewards.claimEasy(player))
        completeAll(player)
        for (slot in 0..26) player.inv[slot] = InvObj("obj.bucket_empty")
        assertEquals(AchievementDiaryRewards.ClaimResult.InventoryFull, rewards.claimEasy(player))
        assertEquals(0, player.inv.count(DiaryData.easy.ring))
        assertEquals(0, player.inv.count(DiaryData.easy.lamp))
        assertEquals(0, player.vars[DiaryData.easy.rewardVar])
        player.inv[26] = null
        assertEquals(AchievementDiaryRewards.ClaimResult.Claimed, rewards.claimEasy(player))
        assertEquals(AchievementDiaryRewards.ClaimResult.AlreadyClaimed, rewards.claimEasy(player))
        assertEquals(1, player.inv.count(DiaryData.easy.ring))
        assertEquals(1, player.inv.count(DiaryData.easy.lamp))
    }

    @Test
    fun `native progress and charges survive reconnect while oak stage does not`() {
        val player = player()
        completeAll(player)
        AchievementDiaryRewards().claimEasy(player)
        ExplorerRingCharges.reset(player, 20000)
        VarPlayerIntMapSetter.set(player, ExplorerRingCharges.Energy, 2)
        VarPlayerIntMapSetter.set(player, DiaryProgress.OakStage, 1)
        val reconnected = player()
        reconnected.vars.backing.putAll(player.vars.backing.filter { (id, _) ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        DiaryProgress.synchronize(reconnected)
        assertEquals(12, DiaryProgress.completedCount(reconnected))
        assertEquals(1, reconnected.vars[DiaryData.easy.rewardVar])
        assertEquals(2, reconnected.vars[ExplorerRingCharges.Energy])
        assertEquals(0, reconnected.vars[DiaryProgress.OakStage])
        val resetVar = ServerCacheManager.getVarp(ExplorerRingCharges.ResetDay.asRSCM())!!
        assertEquals(VarpTransmitLevel.Never, resetVar.transmit)
    }

    @Test
    fun `daily reset is persistent and repeated access does not refill charges`() {
        val player = player()
        ExplorerRingCharges.reset(player, 20000)
        VarPlayerIntMapSetter.set(player, ExplorerRingCharges.Energy, 2)
        VarPlayerIntMapSetter.set(player, ExplorerRingCharges.Alchemy, 30)
        ExplorerRingCharges.reset(player, 20000)
        assertEquals(2, player.vars[ExplorerRingCharges.Energy])
        assertEquals(30, player.vars[ExplorerRingCharges.Alchemy])
        ExplorerRingCharges.reset(player, 20001)
        assertEquals(0, player.vars[ExplorerRingCharges.Energy])
        assertEquals(0, player.vars[ExplorerRingCharges.Alchemy])
    }

    @Test
    fun `ring restores half energy twice and a full bar does not consume a use`() {
        val f = RingFixture()
        f.player.runEnergy = constants.run_max_energy
        f.energy()
        assertEquals(0, f.player.vars[ExplorerRingCharges.Energy])
        f.player.runEnergy = 0
        f.energy()
        assertEquals(constants.run_max_energy / 2, f.player.runEnergy)
        f.player.runEnergy = 0
        f.energy()
        assertEquals(2, f.player.vars[ExplorerRingCharges.Energy])
        f.player.runEnergy = 0
        f.energy()
        assertEquals(0, f.player.runEnergy)
    }

    @Test
    fun `lamp rejects a low base level and grants exactly 2500 xp once`() {
        verifyLamp(DiaryData.easy)
    }

    @Test
    fun `medium lamp requires base level forty and grants exactly 7500 xp`() {
        verifyLamp(DiaryData.medium)
    }

    @Test
    fun `hard lamp requires base level fifty and grants exactly 15000 xp`() {
        verifyLamp(DiaryData.hard)
    }

    private fun verifyLamp(tier: DiaryTier) {
        val player = player()
        player.inv[0] = InvObj(tier.lamp)
        VarPlayerIntMapSetter.set(player, tier.rewardVar, 1)
        val events = EventBus()
        with(DiaryLampScript()) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val stat = "stat.agility"
        val slot = (0..23).first { NamedEnums.xpreward_slot_to_stat[it]?.id == stat.asRSCM() }
        fun rubAndChoose() {
            val coroutine = GameCoroutine("diary-lamp-test")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getAlignment = { TextAlignment() },
            )
            val access = ProtectedAccess(player, coroutine, context)
            val item = ServerCacheManager.getItem(tier.lamp.asRSCM())!!
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = {
                val event = HeldObjEvents.Op1(0, player.inv[0]!!, item, player.inv)
                assertTrue(events.publish(access, event))
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
            coroutine.resumeWith(ResumePauseButtonInput("component.xpreward:universe", slot))
            completion?.getOrThrow()
        }
        player.statMap.setBaseLevel(stat, (tier.lampMinLevel - 1).toByte())
        rubAndChoose()
        assertEquals(1, player.inv.count(tier.lamp))
        assertEquals(0, player.statMap.getXP(stat))
        player.statMap.setBaseLevel(stat, 99)
        rubAndChoose()
        assertEquals(0, player.inv.count(tier.lamp))
        assertEquals(tier.lampXp, player.statMap.getXP(stat))
    }

    @Test
    fun `ring alchemy respects magic level item validation quota cooldown and no xp`() {
        val f = RingFixture()
        f.player.inv[1] = InvObj("obj.bronze_dagger")
        val type = ServerCacheManager.getItem("obj.bronze_dagger".asRSCM())!!
        f.alch(1, type.id)
        assertEquals(1, f.player.inv.count("obj.bronze_dagger"))
        f.player.statMap.setCurrentLevel("stat.magic", 21)
        val xp = f.player.statMap.getXP("stat.magic")
        f.alch(1, "obj.iron_dagger".asRSCM())
        assertEquals(0, f.player.vars[ExplorerRingCharges.Alchemy])
        f.alch(1, type.id)
        assertEquals(0, f.player.inv.count("obj.bronze_dagger"))
        assertEquals(type.lowAlch, f.player.inv.count("obj.coins"))
        assertEquals(xp, f.player.statMap.getXP("stat.magic"))
        assertEquals(1, f.player.vars[ExplorerRingCharges.Alchemy])
        f.player.inv[1] = InvObj(type)
        f.alch(1, type.id)
        assertEquals(1, f.player.inv.count("obj.bronze_dagger"))
        f.player.currentMapClock += 3
        VarPlayerIntMapSetter.set(f.player, ExplorerRingCharges.Alchemy, 30)
        f.alch(1, type.id)
        assertEquals(1, f.player.inv.count("obj.bronze_dagger"))
    }

    @Test
    fun `all medium bits are independent and do not overwrite easy or hard progress`() {
        val player = player()
        val tier = DiaryData.medium
        VarPlayerIntMapSetter.set(player, tier.taskVarp, 1 or (1 shl 5) or (1 shl 25))
        for (task in DiaryData.tasks(tier)) {
            val coords = if (task.bounds[4] < 0) CoordGrid(3215, 3219)
                else CoordGrid(task.bounds[0], task.bounds[1], task.bounds[4])
            record(player, task.action, task.targets.first(), coords)
            record(player, task.action, task.targets.first(), coords)
        }
        val mask = (13..24).fold(0) { bits, bit -> bits or (1 shl bit) }
        assertEquals(1 or (1 shl 5) or (1 shl 25) or mask, player.vars[tier.taskVarp])
        assertEquals(12, DiaryProgress.completedCount(player, tier))
        assertEquals(1, DiaryProgress.completedCount(player))
        assertEquals(1, player.vars[tier.completeVar])
        assertEquals(0, player.vars[tier.rewardVar])
    }

    @Test
    fun `medium rejects wrong fish coif location impling location and teleport spell`() {
        val player = player()
        record(player, "Fishing", "obj.raw_trout", CoordGrid(3241, 3248))
        record(player, "Fishing", "obj.raw_salmon", CoordGrid(3100, 3300))
        record(player, "CraftItem", "obj.coif", CoordGrid(3215, 3219))
        record(player, "CraftItem", "obj.cowl", CoordGrid(3257, 3267))
        record(player, "CatchCreature", "npc.ii_impling_type_5", CoordGrid(3100, 3300))
        record(player, "CatchCreature", "npc.ii_impling_type_5", CoordGrid(2592, 4322, 1))
        record(player, "CastTeleport", "obj.48_home_teleport", CoordGrid(3222, 3218))
        record(player, "CraftRune", "obj.lavarune", CoordGrid(3300, 3300))
        assertEquals(0, DiaryProgress.completedCount(player, DiaryData.medium))
        record(player, "CraftItem", "obj.coif", CoordGrid(3257, 3267))
        record(player, "CatchCreature", "npc.ii_impling_type_6_maze", CoordGrid(2592, 4322))
        assertEquals(2, DiaryProgress.completedCount(player, DiaryData.medium))
    }

    @Test
    fun `medium reward requires earlier rewards and rolls back when inventory is full`() {
        val player = player()
        completeAll(player)
        val rewards = AchievementDiaryRewards()
        val tier = DiaryData.medium
        assertEquals(AchievementDiaryRewards.ClaimResult.NotComplete, rewards.claimMedium(player))
        assertEquals(AchievementDiaryRewards.ClaimResult.Claimed, rewards.claimEasy(player))
        for (slot in 2..27) player.inv[slot] = InvObj("obj.bucket_empty")
        assertEquals(AchievementDiaryRewards.ClaimResult.InventoryFull, rewards.claimMedium(player))
        assertEquals(0, player.vars[tier.rewardVar])
        assertEquals(0, player.inv.count(tier.ring))
        player.inv[26] = null
        player.inv[27] = null
        assertEquals(AchievementDiaryRewards.ClaimResult.Claimed, rewards.claimMedium(player))
        assertEquals(AchievementDiaryRewards.ClaimResult.AlreadyClaimed, rewards.claimMedium(player))
        assertEquals(1, player.inv.count(tier.ring))
        assertEquals(1, player.inv.count(tier.lamp))
    }

    @Test
    fun `medium ring grants only the third restore when upgraded during the day`() {
        val f = RingFixture()
        f.player.runEnergy = 0
        f.energy()
        f.player.runEnergy = 0
        f.energy()
        f.player.inv[0] = InvObj(DiaryData.medium.ring)
        VarPlayerIntMapSetter.set(f.player, DiaryData.medium.rewardVar, 1)
        f.player.runEnergy = 0
        f.energy()
        assertEquals(constants.run_max_energy / 2, f.player.runEnergy)
        assertEquals(3, f.player.vars[ExplorerRingCharges.Energy])
        f.player.runEnergy = 0
        f.energy()
        assertEquals(0, f.player.runEnergy)
        f.player.inv[0] = InvObj(DiaryData.easy.ring)
        f.energy()
        assertEquals(0, f.player.runEnergy)
    }

    @Test
    fun `cabbage teleport commits one landing and respects denial quota and cooldown`() {
        val denied = RingFixture(medium = true, denial = "You are teleblocked.")
        denied.teleport()
        assertEquals(0, denied.player.vars[ExplorerRingCharges.Cabbage])
        assertEquals(0, denied.player.queueList.count("queue.diary_explorer_teleport"))
        val f = RingFixture(medium = true)
        repeat(3) { index ->
            f.teleport()
            assertEquals(index + 1, f.player.vars[ExplorerRingCharges.Cabbage])
            assertEquals(1, f.player.queueList.count("queue.diary_explorer_teleport"))
            f.teleport()
            assertEquals(index + 1, f.player.vars[ExplorerRingCharges.Cabbage])
            f.player.currentMapClock += 4
        }
        f.teleport()
        assertEquals(3, f.player.vars[ExplorerRingCharges.Cabbage])
        ExplorerRingCharges.reset(f.player, java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt() + 1)
        assertEquals(0, f.player.vars[ExplorerRingCharges.Cabbage])
    }

    @Test
    fun `native worn menu invokes energy rather than teleport or remove on easy ring`() {
        val f = RingFixture()
        f.player.runEnergy = 0
        f.wornEnergy()
        assertEquals(constants.run_max_energy / 2, f.player.runEnergy)
        assertEquals(0, f.player.queueList.count("queue.diary_explorer_teleport"))
    }

    @Test
    fun `medium task progress and cabbage uses persist across reconnect`() {
        val player = player()
        record(player, "AgilityLap", "varp.agility_laps_alkharid", CoordGrid(3273, 3195))
        VarPlayerIntMapSetter.set(player, ExplorerRingCharges.Cabbage, 2)
        val loaded = player()
        loaded.vars.backing.putAll(player.vars.backing.filter { (id, _) ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        DiaryProgress.synchronize(loaded)
        assertEquals(1, DiaryProgress.completedCount(loaded, DiaryData.medium))
        assertEquals(0, DiaryProgress.completedCount(loaded))
        assertEquals(2, loaded.vars[ExplorerRingCharges.Cabbage])
    }

    @Test
    fun `hard uses two native varps and preserves the signed high bit and elite bits`() {
        val player = player()
        val first = DiaryData.hard.taskVarp
        val second = "varp.lumb_dray_achievement_diary2"
        VarPlayerIntMapSetter.set(player, first, Int.MIN_VALUE or (1 shl 5))
        VarPlayerIntMapSetter.set(player, second, 1 shl 9)
        record(player, "CastUtility", "obj.60_bones_peaches", CoordGrid(3293, 3164))
        record(player, "LightSource", "obj.cave_goblin_mining_helmet_lit", CoordGrid(3210, 9620))
        assertEquals(Int.MIN_VALUE or (1 shl 5) or (1 shl 25), player.vars[first])
        assertEquals((1 shl 9) or (1 shl 1), player.vars[second])
        assertEquals(3, DiaryProgress.completedCount(player, DiaryData.hard))
        assertEquals(1, DiaryProgress.completedCount(player))
        assertEquals(11, DiaryProgress.taskCount(DiaryData.hard))
    }

    @Test
    fun `hard rune quantity must be satisfied by one successful action`() {
        val player = player()
        repeat(2) {
            record(player, "CraftRuneBatch", "obj.cosmicrune", CoordGrid(2137, 4833), count = 28)
        }
        record(player, "CraftRune", "obj.cosmicrune", CoordGrid(2137, 4833), count = 56)
        record(player, "CraftRuneBatch", "obj.cosmicrune", CoordGrid(3200, 3200), count = 56)
        assertEquals(0, DiaryProgress.completedCount(player, DiaryData.hard))
        record(player, "CraftRuneBatch", "obj.cosmicrune", CoordGrid(2137, 4833), count = 56)
        assertEquals(1, DiaryProgress.completedCount(player, DiaryData.hard))
    }

    @Test
    fun `amulet must be cast strung and enchanted locally with independent stage state`() {
        val player = player()
        val coords = CoordGrid(3227, 3257)
        VarPlayerIntMapSetter.set(player, DiaryProgress.OakStage, 1)
        record(player, "EnchantItem", "obj.amulet_of_power", coords)
        record(player, "CraftItem", "obj.strung_diamond_amulet", coords)
        assertEquals(0, player.vars[DiaryProgress.AmuletStage])
        record(player, "CraftItem", "obj.unstrung_diamond_amulet", coords)
        assertEquals(1, player.vars[DiaryProgress.AmuletStage])
        record(player, "EnchantItem", "obj.amulet_of_power", coords)
        assertEquals(0, DiaryProgress.completedCount(player, DiaryData.hard))
        record(player, "CraftItem", "obj.strung_diamond_amulet", coords)
        record(player, "EnchantItem", "obj.amulet_of_power", coords)
        assertEquals(1, DiaryProgress.completedCount(player, DiaryData.hard))
        assertEquals(0, player.vars[DiaryProgress.AmuletStage])
        assertEquals(1, player.vars[DiaryProgress.OakStage])
    }

    @Test
    fun `amulet stage resets outside its table bounds and is temporary on reconnect`() {
        val player = player()
        record(player, "CraftItem", "obj.unstrung_diamond_amulet", CoordGrid(3227, 3257))
        val loaded = player()
        loaded.vars.backing.putAll(player.vars.backing.filter { (id, _) ->
            ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm
        })
        assertEquals(0, loaded.vars[DiaryProgress.AmuletStage])
        player.coords = CoordGrid(3300, 3300)
        DiaryProgress.resetStagesOutsideArea(player) { _, _ -> true }
        assertEquals(0, player.vars[DiaryProgress.AmuletStage])
    }

    @Test
    fun `unimplemented hard dependencies cannot be completed or bypassed for rewards`() {
        val player = player()
        val pending = DiaryData.tasks(DiaryData.hard).filter { it.unavailable.isNotEmpty() }
        assertEquals(3, pending.size)
        for (task in pending) {
            record(player, task.action, task.targets.first(),
                CoordGrid(task.bounds[0], task.bounds[1]), count = task.minimumCount)
            assertFalse(DiaryProgress.isComplete(player, task))
        }
        VarPlayerIntMapSetter.set(player, "varp.lumb_dray_achievement_diary", -1)
        VarPlayerIntMapSetter.set(player, "varp.lumb_dray_achievement_diary2", 15)
        VarPlayerIntMapSetter.set(player, DiaryData.easy.rewardVar, 1)
        VarPlayerIntMapSetter.set(player, DiaryData.medium.rewardVar, 1)
        assertEquals(AchievementDiaryRewards.ClaimResult.NotComplete,
            AchievementDiaryRewards().claimHard(player))
        assertEquals(0, player.inv.count(DiaryData.hard.ring))
        assertEquals(0, player.vars[DiaryData.hard.rewardVar])
    }

    @Test
    fun `tears hard task requires a single completed game of at least one hundred`() {
        val player = player()
        val task = DiaryData.tasks.single { it.action == "CollectTears" }
        record(player, "CollectTears", "varbit.tog_tears_collected", CoordGrid(3251, 9516, 2), count = 99)
        assertFalse(DiaryProgress.isComplete(player, task))
        record(player, "CollectTears", "varbit.tog_tears_collected", CoordGrid(3251, 9516, 2), count = 99)
        assertFalse(DiaryProgress.isComplete(player, task))
        record(player, "CollectTears", "varbit.tog_tears_collected", CoordGrid(3251, 9516, 2), count = 100)
        assertTrue(DiaryProgress.isComplete(player, task))
    }

    @Test
    fun `hard ring retains energy usage and allows teleporting after the medium quota`() {
        val f = RingFixture(medium = true)
        ExplorerRingCharges.reset(f.player)
        VarPlayerIntMapSetter.set(f.player, ExplorerRingCharges.Cabbage, 3)
        VarPlayerIntMapSetter.set(f.player, ExplorerRingCharges.Energy, 3)
        f.player.inv[0] = InvObj(DiaryData.hard.ring)
        VarPlayerIntMapSetter.set(f.player, DiaryData.hard.rewardVar, 1)
        f.player.runEnergy = 0
        f.wornEnergy()
        assertEquals(constants.run_max_energy / 2, f.player.runEnergy)
        assertEquals(4, f.player.vars[ExplorerRingCharges.Energy])
        repeat(6) {
            f.teleport()
            assertEquals(1, f.player.queueList.count("queue.diary_explorer_teleport"))
            f.player.currentMapClock += 4
        }
        assertEquals(3, f.player.vars[ExplorerRingCharges.Cabbage])
        assertEquals(4, f.player.vars[ExplorerRingCharges.Energy])
    }

    private fun testAreas(): org.rsmod.api.area.checker.AreaChecker {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as sun.misc.Unsafe).allocateInstance(org.rsmod.api.area.checker.AreaChecker::class.java) as org.rsmod.api.area.checker.AreaChecker
    }

    private fun player() = Player().apply {
        client = RecordingClient()
        inv = Inventory.create("inv.inv")
        worn = Inventory.create("inv.worn")
        currentMapClock = 100
    }

    private fun record(
        player: Player,
        action: String,
        target: String,
        coords: CoordGrid,
        inArea: (String, CoordGrid) -> Boolean = { _, _ -> true },
        count: Int = 1,
    ) =
        DiaryProgress.record(player, action, target, coords, inArea, count)

    private fun completeAll(player: Player) {
        val stage = DiaryData.tasks.single { it.difficulty == 1 && it.setStage > 0 }
        record(player, stage.action, stage.targets.first(), CoordGrid(3219, 3206))
        for (task in DiaryData.tasks.filter { it.difficulty <= 2 && it.bit >= 0 }) {
            val coords =
                if (task.bounds[4] < 0) CoordGrid(3215, 3219)
                else CoordGrid(task.bounds[0], task.bounds[1], task.bounds[4])
            record(player, task.action, task.targets.first(), coords)
        }
    }

    private inner class RingFixture(medium: Boolean = false, denial: String? = null) {
        val player = player().apply {
            inv[0] = InvObj(if (medium) DiaryData.medium.ring else DiaryData.easy.ring)
            VarPlayerIntMapSetter.set(this, DiaryData.easy.rewardVar, 1)
            if (medium) VarPlayerIntMapSetter.set(this, DiaryData.medium.rewardVar, 1)
        }
        private val events = EventBus()
        init {
            with(ExplorerRingScript(org.rsmod.api.player.hook.PlayerTeleportValidator(setOf(org.rsmod.api.player.hook.PlayerTeleportValidateHook { _, _, _ -> denial })), testAreas())) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
        fun energy() {
            val item = ServerCacheManager.getItem(player.inv[0]!!.id)!!
            if (item.id == DiaryData.medium.ring.asRSCM()) wornEnergy()
            else dispatch(HeldObjEvents.Op3(0, player.inv[0]!!, item, player.inv))
        }
        fun wornEnergy() {
            dispatch(org.rsmod.api.player.events.interact.WornObjEvents.Op3(12, player.inv[0]!!))
        }
        fun teleport() {
            val item = ServerCacheManager.getItem(player.inv[0]!!.id)!!
            dispatch(HeldObjEvents.Op3(0, player.inv[0]!!, item, player.inv))
        }
        fun alch(slot: Int, itemId: Int) {
            dispatch(
                IfModalButton(
                    ServerCacheManager.fromComponent("component.lumbridge_alchemy:items".asRSCM()),
                    slot,
                    ServerCacheManager.getItem(itemId),
                    IfButtonOp.Op1,
                )
            )
        }
        private fun <T : SuspendEvent<ProtectedAccess>> dispatch(event: T) {
            val coroutine = GameCoroutine("diary-test")
            player.activeCoroutine = coroutine
            val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
            val access = ProtectedAccess(player, coroutine, context)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
            player.activeCoroutine = null
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
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic
        @BeforeAll
        fun setup() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field =
                    Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
