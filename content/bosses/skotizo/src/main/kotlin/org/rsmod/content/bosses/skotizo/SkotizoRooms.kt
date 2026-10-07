package org.rsmod.content.bosses.skotizo

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.*
import org.rsmod.api.instances.*
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule

class SkotizoModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(SkotizoDeathHook::class.java)
        addSetBinding<NpcDeathKillHook>(CatacombsTotemDrops::class.java)
        addSetBinding<PlayerDeathRedirectHook>(SkotizoGraveHook::class.java)
        addSetBinding<NpcAttackValidateHook>(SkotizoAttackHook::class.java)
    }
}

internal class SkotizoDeathHook @Inject constructor(private val rooms: SkotizoRooms) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) { rooms.bossDied(context.npc) }
}
internal class SkotizoGraveHook @Inject constructor(private val rooms: SkotizoRooms) : PlayerDeathRedirectHook {
    override fun destination(player: Player): CoordGrid? = rooms.forPlayer(player)?.takeIf { it.contains(player.coords) }?.let { SkotizoRules.Exit }
}
internal class SkotizoAttackHook @Inject constructor(private val rooms: SkotizoRooms) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in 7286..7296) return NpcAttackValidateResult.Pass
        val room = rooms.forNpc(npc)
        if (room == null || room.owner !== player || !room.contains(player.coords) || !room.alive) return NpcAttackValidateResult.Deny("This is not your Skotizo encounter.")
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }
}

internal class SkotizoRoom(val id: InstanceId, val owner: Player, val enter: CoordGrid) {
    lateinit var boss: Npc
    val altars = mutableListOf<Npc>()
    val demons = mutableListOf<Npc>()
    var ankou: Npc? = null
    val eyes = AltarCycle()
    var alive = true
    var started = false
    var nextAltar = 0
    var nextSummon = 0
    fun tile(source: CoordGrid) = enter.translate(source.x - SkotizoRules.Entry.x, source.z - SkotizoRules.Entry.z)
    fun contains(tile: CoordGrid) = tile.level == enter.level &&
        tile.x - enter.x + SkotizoRules.Entry.x in 2240..2303 &&
        tile.z - enter.z + SkotizoRules.Entry.z in 5632..5695
}

@Singleton
internal class SkotizoRooms @Inject constructor(
    val manager: InstanceManager,
    private val deps: BossDeps,
    private val interactions: AiPlayerInteractions,
) {
    private val rooms = mutableMapOf<InstanceId, SkotizoRoom>()
    fun forPlayer(player: Player) = manager.sessionForPlayer(player)?.id?.let(rooms::get)
    fun forNpc(npc: Npc) = manager.instanceForNpc(npc)?.let(rooms::get)
    fun remove(id: InstanceId) { rooms.remove(id) }

    fun create(player: Player): InstanceManager.Result {
        val entry = SkotizoRules.Entry
        val spec = InstanceSettings(maxPlayers = 1, destroyWhenEmpty = true, bossName = "Skotizo")
            .withArea(InstanceArea.copyRegions(regionIds = listOf(9048),
                enterCoord = RegionLocal(entry.level, entry.mx, entry.mz, entry.lx, entry.lz),
                exitCoord = SkotizoRules.Exit), settingsRowId = -1)
        val result = manager.create(player, SkotizoRules.Key, spec, InstanceAccess.Private, deps.mapClock.cycle)
        if (result is InstanceManager.Result.Created) rooms[result.session.id] = SkotizoRoom(result.session.id, player, result.enter)
        return result
    }

    fun start(room: SkotizoRoom) {
        if (room.started) return
        room.boss = spawn(room, SkotizoRules.Boss, SkotizoRules.BossSpawn)
        for (index in 0..3) room.altars += spawn(room, SkotizoRules.Closed[index], SkotizoRules.Altars[index])
        room.boss.apPlayer2(room.owner, interactions)
        room.nextAltar = deps.mapClock.cycle + 30
        room.started = true
    }

    private fun spawn(room: SkotizoRoom, symbol: String, source: CoordGrid): Npc {
        val npc = Npc(symbol, room.tile(source)).apply { mode = NpcMode.None }
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        manager.attachNpc(room.id, npc)
        return npc
    }

    fun altarDied(npc: Npc) {
        val room = forNpc(npc) ?: return
        val index = room.altars.indexOf(npc)
        if (index < 0) return
        room.eyes.deactivate(index, deps.mapClock.cycle)
        npc.resetTransmog()
        npc.copyStats(npc.type)
        npc.showAllOps()
        npc.anim("seq.altar_death")
    }

    fun bossDied(npc: Npc) {
        val room = forNpc(npc) ?: return
        if (room.boss !== npc || !room.alive) return
        room.alive = false
        for (helper in room.altars + room.demons + listOfNotNull(room.ankou)) if (helper.isSlotAssigned) deps.npcRepo.del(helper, Int.MAX_VALUE)
        room.altars.clear(); room.demons.clear(); room.ankou = null
    }

    fun summon(room: SkotizoRoom) {
        if (!room.alive || deps.mapClock.cycle < room.nextSummon) return
        room.demons.removeAll { !it.isSlotAssigned || !it.isVisible || it.hitpoints <= 0 }
        if (SkotizoRules.minionsUnlocked(room.boss.hitpoints) && room.demons.isEmpty()) {
            for (site in SkotizoRules.Demons) room.demons += spawn(room, SkotizoRules.Minion, site).also { it.apPlayer2(room.owner, interactions) }
            room.owner.mes("Skotizo summons reanimated demon spawns!")
            room.nextSummon = deps.mapClock.cycle + 30
        }
        val ankou = room.ankou
        if (room.eyes.count > 0 && (ankou == null || !ankou.isSlotAssigned || ankou.hitpoints <= 0) && deps.random.of(10) == 0) {
            room.ankou = spawn(room, SkotizoRules.Ankou, CoordGrid(2276, 5659)).also { it.apPlayer2(room.owner, interactions) }
            room.nextSummon = deps.mapClock.cycle + 30
        }
    }

    fun tick() {
        for (room in rooms.values.toList()) {
            if (manager.sessionForId(room.id) == null) { rooms.remove(room.id); continue }
            if (!room.started || !room.alive || !room.owner.isValidTarget() || !room.contains(room.owner.coords)) continue
            room.eyes.tick(deps.mapClock.cycle)
            if (room.boss.hitpoints <= 0 || room.eyes.exhausted || deps.mapClock.cycle < room.nextAltar) continue
            room.nextAltar = deps.mapClock.cycle + 50 + deps.random.of(31)
            val candidates = (0..3).filter { !room.eyes.isActive(it) }
            if (candidates.isEmpty()) continue
            val index = candidates[deps.random.of(candidates.size)]
            if (!room.eyes.activate(index, deps.mapClock.cycle)) continue
            val altar = room.altars[index]
            altar.transmog(checkNotNull(ServerCacheManager.getNpc(SkotizoRules.Open[index].asRSCM())), Int.MAX_VALUE)
            altar.copyStats(altar.visType)
            altar.showAllOps()
            altar.anim("seq.altar_spawn")
            room.owner.mes("An Awakened Altar empowers Skotizo!")
        }
    }
}
