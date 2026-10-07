package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.events.PlayerNpcClick
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.*
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HesporiScript @Inject constructor(
    private val garden: HesporiGarden,
    private val death: NpcDeath,
    private val drops: DropTableRegistry,
    private val objs: ObjRepository,
    private val xp: XpModifiers,
    private val reclaim: HesporiReclaim,
) : PluginScript() {
    override fun ScriptContext.startup() {
        verifyNativePatch()
        val deps = garden.deps
        val spec = SPEC
        deps.extensionRegistry.register("hespori.attack") { _, npc, target, _ -> garden.attack(npc, target) }
        BossCombat.register(this, spec, deps)
        val bossType = ServerCacheManager.getNpc(HesporiGarden.BOSS.asRSCM(RSCMType.NPC))!!
        val flowerType = ServerCacheManager.getNpc(HesporiGarden.ACTIVE.asRSCM(RSCMType.NPC))!!
        onNpcHit(bossType) { garden.afterHit(npc) }
        for (type in listOf(bossType, flowerType)) onEvent<NpcHitEvents.BeforeImpact>(type.id) {
            val source = if (hit.isFromPlayer) hit.resolvePlayerSource(deps.playerList) else null
            damage = garden.incoming(npc, damage, source)
        }
        onNpcQueue(bossType, "queue.death") {
            // Reward readiness is persistent before the animation suspends. No automatic ground loot.
            garden.defeated(npc)
            death.deathNoDrops(this)
        }
        onNpcQueue(flowerType, "queue.death") {
            noneMode()
            hideAllOps()
            anim("seq.hespori_healer_alive_to_dead")
            delay(ServerCacheManager.getAnim("seq.hespori_healer_alive_to_dead".asRSCM(RSCMType.SEQ))!!)
            // Finish owned flower replacement before deletion invalidates/cancels this access.
            garden.flowerDied(npc)
            deps.npcRepo.del(npc, Int.MAX_VALUE)
        }
        onOpLoc1("loc.kebos_farming_guild_hespori_entrance") { enter() }
        for (exit in listOf("loc.hespori_cave_exit", "loc.hespori_cave_exit_instance")) {
            onOpLoc1(exit) { exit() }
            if (exit.endsWith("_instance")) onOpLoc2(exit) { exit() }
        }
        for (patch in PATCHES) {
            onOpLoc1(patch) { primary() }
            onOpLoc2(patch) { inspect() }
            onOpLoc4(patch) { mes("A Hespori seed requires level 65 Farming. Rake, plant, then harvest the fully grown plant with a spade to awaken it.") }
            onOpLocU(patch, "obj.hespori_seed") { plant() }
            onOpLocU(patch, "obj.rake") { rake() }
            onOpLocU(patch, "obj.spade") { primary() }
        }
        onOpNpc1("npc.farming_guild_hespori_farmer") { reclaim.talk(this) }
        onPlayerLogin { garden.login(player) }
        onPlayerLogout { garden.cleanup(player, returnOutside = true) }
        onEvent<PlayerMoveClick> { garden.move(this) }
        onEvent<PlayerNpcClick> { garden.npcClick(this) }
        onEvent<GameLifecycle.LateCycle> { garden.tick() }
    }

    private suspend fun ProtectedAccess.enter() {
        if (stat("stat.farming") < HesporiCrop.REQUIRED_LEVEL) { mes("You need level 65 Farming to enter this cave."); return }
        if (reclaim.hasItems(player)) { mes("Arno is holding your belongings. Collect them before entering again."); return }
        arriveDelay()
        val room = garden.create(player) ?: run { mes("There is no room available. Please try again shortly."); return }
        val dest = room.at(HesporiGarden.ARRIVAL)
        telejump(dest, TeleportType.Exempt)
        if (coords != dest) { garden.cleanup(player); return }
        garden.entered(player)
    }

    private fun ProtectedAccess.exit() {
        telejump(HesporiGarden.EXIT, TeleportType.Exempt)
        if (coords == HesporiGarden.EXIT) garden.cleanup(player)
    }

    private fun ProtectedAccess.ownsPatch(): Boolean {
        val room = garden.garden(player) ?: return false
        return room.contains(coords) && !room.active
    }

    private suspend fun ProtectedAccess.primary() {
        if (!ownsPatch()) return
        when (HesporiCrop.stage(player)) {
            0, 1, 2 -> rake()
            7 -> {
                if (invTotal(inv, "obj.spade") == 0) { mes("You need a spade to harvest this plant."); return }
                anim("seq.human_dig")
                delay(2)
                if (ownsPatch()) garden.start(player)
            }
            8 -> harvest()
            else -> inspect()
        }
    }

    private fun ProtectedAccess.inspect() {
        HesporiCrop.render(player)
        mes(when (HesporiCrop.stage(player)) {
            0, 1, 2 -> "The Hespori patch needs weeding."
            3 -> "The patch is ready for a Hespori seed."
            7 -> "The Hespori is fully grown. Harvesting it will awaken it."
            8 -> "The Hespori has been defeated. Clear the patch with a spade to collect your harvest."
            else -> "Your Hespori is growing healthily. It needs no compost, water or protection."
        })
    }

    private suspend fun ProtectedAccess.rake() {
        if (!ownsPatch()) return
        if (invTotal(inv, "obj.rake") == 0) { mes("You need a rake to weed this patch."); return }
        try {
            while (ownsPatch() && HesporiCrop.stage(player) in 0..2) {
                anim("seq.farming_raking")
                delay(3)
                if (!ownsPatch() || invTotal(inv, "obj.rake") == 0) return
                player.hesporiRakeProgress++
                HesporiCrop.render(player)
                invAddOrDrop(objs, "obj.weeds", 1)
                statAdvance("stat.farming", 4.0 * xp.get(player, "stat.farming"))
                mes("You rake some weeds from the patch.")
            }
        } finally { resetAnim() }
    }

    private suspend fun ProtectedAccess.plant() {
        if (!ownsPatch()) return
        if (stat("stat.farming") < 65) { mes("You need level 65 Farming to plant a Hespori seed."); return }
        if (HesporiCrop.stage(player) != 3) { mes("You need an empty, weeded patch to plant this seed."); return }
        if (invTotal(inv, "obj.dibber") == 0) { mes("You need a seed dibber to plant this seed."); return }
        if (invTotal(inv, "obj.hespori_seed") == 0) return
        anim("seq.farming_seed_dibbing")
        delay(2)
        if (!ownsPatch() || HesporiCrop.stage(player) != 3 || invTotal(inv, "obj.hespori_seed") == 0) return
        if (!invDel(inv, "obj.hespori_seed").success) return
        check(HesporiCrop.plant(player))
        mes("You plant the Hespori seed in the patch.")
    }

    private suspend fun ProtectedAccess.harvest() {
        if (invTotal(inv, "obj.spade") == 0) { mes("You need a spade to clear this patch."); return }
        val lootNpc = Npc(HesporiGarden.BOSS, coords)
        val table = checkNotNull(drops.forNpc(lootNpc)) { "Missing Hespori harvest table" }
        anim("seq.human_dig")
        delay(2)
        if (!ownsPatch() || !HesporiCrop.claimHarvest(player)) return
        statAdvance("stat.farming", HesporiCrop.HARVEST_XP * xp.get(player, "stat.farming"))
        if (random.of(35) == 0) give("obj.bottomless_compost_bucket", 1)
        else when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> give(result.result)
            is RollResult.ListOf -> result.results.forEach { give(it) }
        }
        give(ANIMA[random.of(3)], 1 + random.of(2))
        if (random.of(7000 - statBase("stat.farming").coerceIn(1, 99) * 25) == 0) give("obj.skillpetfarming", 1)
        mes("You clear the Hespori patch and collect your harvest.")
    }

    private fun ProtectedAccess.give(drop: DropRollItem) {
        if (!drop.isNothing && drop.condition(player)) give(drop.transformObj(player) ?: drop.obj, drop.rollCount(random))
    }

    private fun ProtectedAccess.give(obj: String, count: Int) {
        CollectionLog.grant(player, obj, count)
        invAddOrDrop(objs, obj, count)
    }

    private fun verifyNativePatch() {
        val native = ServerCacheManager.getObject(HesporiGarden.PARENT.asRSCM(RSCMType.LOC))!!
        check(native.multiVarBit == HesporiCrop.TRANSMIT.asRSCM(RSCMType.VARBIT))
        check(native.transforms?.take(9) == PATCHES.map { it.asRSCM(RSCMType.LOC) }) { "Hespori patch native transform anchors changed" }
    }

    companion object {
        val SPEC = boss(HesporiGarden.BOSS) {
            stats(attackRate = 6)
            val attack = ability("growth") { include(external("hespori.attack")) }
            phase("combat") { rotationSelector { +then(attack) } }
        }
        val ANIMA = listOf("obj.attas_seed", "obj.iasor_seed", "obj.kronos_seed")
        val PATCHES = listOf("loc.hespori_patch_weeds_3", "loc.hespori_patch_weeds_2", "loc.hespori_patch_weeds_1", "loc.hespori_patch_weeded", "loc.hespori_patch_seed", "loc.hespori_plant_1", "loc.hespori_plant_2", "loc.hespori_plant_fullygrown", "loc.hespori_plant_dead")
    }
}
