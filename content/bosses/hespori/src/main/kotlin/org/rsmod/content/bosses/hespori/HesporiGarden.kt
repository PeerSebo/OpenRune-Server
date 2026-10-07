package org.rsmod.content.bosses.hespori

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.events.PlayerNpcClick
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.table.MusicRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

@Singleton
internal class HesporiGarden @Inject constructor(
    val deps: BossDeps,
    private val regions: RegionRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val musicPlayer: MusicPlayer,
) : NpcAttackValidateHook {
    internal class Garden(val owner: Player, val region: Region) {
        var boss: Npc? = null
        var life = HesporiLife()
        val flowers = mutableMapOf<Npc, Int>()
        var active = false
        fun contains(coords: CoordGrid): Boolean = coords.level == 0 &&
            coords.x in region.southWest.x until region.northEast.x &&
            coords.z in region.southWest.z until region.northEast.z
        fun at(coords: CoordGrid): CoordGrid = region.normal[coords]
    }

    private val gardens = mutableMapOf<Player, Garden>()
    fun garden(player: Player): Garden? = gardens[player]
    fun owning(npc: Npc): Garden? = gardens.values.firstOrNull { it.boss === npc || npc in it.flowers }

    fun create(player: Player): Garden? {
        gardens[player]?.let { return it }
        val region = regions.add(TEMPLATE) ?: return null
        regions.protect(region)
        val garden = Garden(player, region)
        gardens[player] = garden
        showPatch(garden)
        spawnClosedFlowers(garden)
        return garden
    }

    fun entered(player: Player) {
        player.hesporiInArena = true
        VarPlayerIntMapSetter.set(player, "varbit.player_in_instance", 1)
        HesporiCrop.render(player)
    }

    fun login(player: Player) {
        player.migrateHesporiState()
        // A reclaimed dynamic coordinate must never be restored after a restart.
        if (player.hesporiInArena) {
            PathingEntityCommon.telejump(player, deps.collision, EXIT)
            player.hesporiInArena = false
            VarPlayerIntMapSetter.set(player, "varbit.player_in_instance", 0)
        }
        HesporiCrop.render(player)
    }

    fun cleanup(player: Player, returnOutside: Boolean = false) {
        val garden = gardens.remove(player)
        if (garden == null && !player.hesporiInArena) return
        if (garden != null) {
            releaseRoots(garden)
            removeNpcs(garden)
        }
        if (returnOutside && (garden == null || garden.contains(player.coords))) {
            PathingEntityCommon.telejump(player, deps.collision, EXIT)
        }
        player.hesporiInArena = false
        VarPlayerIntMapSetter.set(player, "varbit.player_in_instance", 0)
        if (garden != null) regions.unprotect(garden.region)
        HesporiCrop.render(player)
    }

    fun start(player: Player): Boolean {
        val garden = gardens[player] ?: return false
        if (!garden.contains(player.coords) || garden.active || HesporiCrop.stage(player) != 7) return false
        removeNpcs(garden)
        val patch = locs.findExact(garden.at(PATCH), LocShape.CentrepieceStraight) ?: return false
        if (!locs.del(patch, Int.MAX_VALUE)) return false
        val boss = Npc(BOSS, garden.at(PATCH)).apply { mode = NpcMode.None; movementLocked = true }
        deps.npcRepo.add(boss, Int.MAX_VALUE)
        boss.respawns = false
        garden.boss = boss
        garden.life = HesporiLife()
        garden.active = true
        musicPlayer.startEncounter(player, MusicRow.getRow("dbrow.music_hespori"))
        boss.anim("seq.hespori_spawn")
        player.soundSynth("synth.HESPORI_SPAWN")
        openFlowers(garden)
        boss.opPlayer2(player, interactions)
        deps.encounterRegistry.of(boss).lastAbilityTick = deps.mapClock.cycle
        player.mes("The Hespori awakens!")
        return true
    }

    fun incoming(npc: Npc, proposed: Int, source: Player?): Int {
        val garden = owning(npc) ?: return 0
        if (!garden.active || source !== garden.owner || garden.life.rootDeadline != null) return 0
        val flower = garden.flowers[npc]
        if (flower != null) return if (garden.life.closeFlower(flower)) npc.hitpoints else 0
        return garden.life.damage(npc.hitpoints, proposed)
    }

    fun afterHit(npc: Npc) {
        val garden = owning(npc) ?: return
        if (npc === garden.boss && garden.life.needsWave(npc.hitpoints)) openFlowers(garden)
    }

    fun defeated(npc: Npc) {
        val garden = owning(npc) ?: return
        if (npc !== garden.boss || !garden.active) return
        garden.active = false
        musicPlayer.finishEncounter(garden.owner, MusicRow.getRow("dbrow.music_hespori"))
        releaseRoots(garden)
        if (HesporiCrop.defeat(garden.owner)) {
            VarPlayerIntMapSetter.set(garden.owner, "varp.total_hespori_kills", garden.owner.vars["varp.total_hespori_kills"] + 1)
            garden.owner.mes("You have defeated the Hespori. Clear the patch to collect your harvest.")
        }
        showPatch(garden)
    }

    fun flowerDied(npc: Npc) {
        val garden = owning(npc) ?: return
        val index = garden.flowers.remove(npc) ?: return
        val inactive = spawn(INACTIVE, garden.at(FLOWERS[index]))
        garden.flowers[inactive] = index
    }

    private fun showPatch(garden: Garden) {
        locs.add(garden.at(PATCH), PARENT, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
    }

    private fun spawnClosedFlowers(garden: Garden) {
        for ((index, coord) in FLOWERS.withIndex()) garden.flowers[spawn(INACTIVE, garden.at(coord))] = index
    }

    private fun spawn(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords).apply {
            mode = NpcMode.None
            ignoreCombatInteractions = true
            movementLocked = true
        }
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        return npc
    }

    private fun openFlowers(garden: Garden) {
        garden.flowers.keys.toList().forEach(::remove)
        garden.flowers.clear()
        garden.life.openWave()
        for ((index, coord) in FLOWERS.withIndex()) {
            val flower = spawn(ACTIVE, garden.at(coord))
            flower.anim("seq.hespori_healer_dead_to_alive")
            garden.flowers[flower] = index
        }
    }

    fun attack(npc: Npc, player: Player) {
        val garden = gardens[player] ?: return
        if (garden.boss !== npc || !garden.active || !garden.contains(player.coords) || !player.isValidTarget()) return
        if (garden.life.rootDeadline != null) return
        garden.life.attacks++
        if (garden.life.attacks % 5 == 0) {
            npc.anim("seq.hespori_attack_special")
            player.soundSynth("synth.HESPORI_ENTANGLE")
            player.spotanim("spotanim.hespori_vine_impact")
            player.clearInteractionRoute()
            player.frozen = true
            garden.life.root(deps.mapClock.cycle)
            player.mes("Hespori entangles you in some vines! Click repeatedly to break free.")
            return
        }
        val ranged = deps.random.of(2) == 0
        npc.anim(if (ranged) "seq.hespori_attack_ranged" else "seq.hespori_attack_special")
        player.soundSynth(if (ranged) "synth.HESPORI_RANGE" else "synth.HESPORI_MAGIC_CAST_FIRE")
        val symbol = if (ranged) "spotanim.hespori_range_proj" else "spotanim.hespori_magic_proj"
        val projectile = ProjAnim.fromNpcToPlayer(npc, player, symbol.asRSCM(RSCMType.SPOTANIM), FLIGHT)
        deps.worldRepo.projAnim(projectile)
        val accurate = if (ranged) deps.accuracy.rollRangedAccuracy(npc, player, deps.random)
            else deps.accuracy.rollMagicAccuracy(npc, player, deps.random)
        val prayer = player.vars[if (ranged) "varbit.prayer_protectfrommissiles" else "varbit.prayer_protectfrommagic"] == 1
        val style = if (prayer) HitType.Typeless else if (ranged) HitType.Ranged else HitType.Magic
        repeat(if (ranged) 2 else 1) { shot ->
            val roll = if (accurate) deps.random.of(if (ranged) 9 else 15) else 0
            val damage = if (prayer) roll / 4 else roll
            player.finishNpcHit(npc, projectile.serverCycles + shot, style, damage, deps.playerHitModifier)
        }
        if (!ranged) player.spotanim("spotanim.hespori_magic_impact", delay = projectile.clientCycles)
        if (accurate && deps.random.of(8) == 0) CombatEffects.poison(player, 4)
    }

    fun move(input: PlayerMoveClick) {
        val garden = gardens[input.player] ?: return
        if (!garden.active || garden.life.rootDeadline == null) return
        input.consumed = true
        if (input.destination == input.player.coords) return
        struggle(garden)
    }

    fun npcClick(input: PlayerNpcClick) {
        val garden = gardens[input.player] ?: return
        if (!garden.active || garden.life.rootDeadline == null || garden.boss !== input.npc || input.op != InteractionOp.Op2) return
        input.consumed = true
        struggle(garden)
    }

    private fun struggle(garden: Garden) {
        if (garden.life.struggle()) {
            garden.owner.frozen = false
            garden.owner.mes("You break free from the vines!")
        }
    }

    fun tick() {
        for ((player, garden) in gardens.toMap()) {
            if (!garden.contains(player.coords)) { cleanup(player); continue }
            // Wait for the ordinary death pipeline to finish creating recoverable items.
            if (player.hitpoints <= 0) { releaseRoots(garden); continue }
            val deadline = garden.life.rootDeadline
            if (deadline != null && deps.mapClock.cycle >= deadline) {
                releaseRoots(garden)
                garden.boss?.let { player.finishNpcHit(it, 0, HitType.Typeless, 40 + deps.random.of(11), deps.playerHitModifier) }
                player.mes("The vines tear into you before you can escape!")
            }
        }
        if (deps.mapClock.cycle % 100 == 0) for (player in deps.playerList) HesporiCrop.render(player)
    }

    private fun releaseRoots(garden: Garden) {
        if (garden.life.rootDeadline != null) garden.owner.frozen = false
        garden.life.clearRoots()
    }

    private fun removeNpcs(garden: Garden) {
        garden.boss?.let(::remove)
        garden.boss = null
        garden.flowers.keys.toList().forEach(::remove)
        garden.flowers.clear()
    }

    private fun remove(npc: Npc) { if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val garden = owning(npc) ?: return NpcAttackValidateResult.Pass
        if (garden.owner !== player) return NpcAttackValidateResult.Deny("This is another player's Hespori patch.")
        if (garden.life.rootDeadline != null) return NpcAttackValidateResult.Deny("You must break free from the vines first.")
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    companion object {
        const val BOSS = "npc.hespori"
        const val ACTIVE = "npc.hespori_healer_active"
        const val INACTIVE = "npc.hespori_healer_inactive"
        const val PARENT = "loc.hespori_patch"
        val PATCH = CoordGrid(1246, 10086, 0)
        val ARRIVAL = CoordGrid(1243, 10082, 0)
        val EXIT = CoordGrid(1235, 3728, 0)
        val FLOWERS = listOf(CoordGrid(1243, 10083, 0), CoordGrid(1243, 10091, 0), CoordGrid(1251, 10083, 0), CoordGrid(1251, 10091, 0))
        val TEMPLATE = RegionTemplate.create { copyAllLevels(152, 1256) { zoneWidth = 8; zoneLength = 8 } }
        val FLIGHT = ProjAnimType(startHeight = 60, endHeight = 30, delay = 20, angle = 10, lengthAdjustment = 0, progress = 0, stepMultiplier = 5)
    }
}
