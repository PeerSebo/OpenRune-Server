package org.rsmod.content.bosses.giants

import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.api.instances.*
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule

class GiantBossesModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(GiantDeathHook::class.java)
        addSetBinding<NpcDeathDropHook>(GiantRemainsHook::class.java)
        addSetBinding<PlayerDeathRedirectHook>(GiantDeathRedirect::class.java)
    }
}

internal class GiantDeathRedirect @Inject constructor(private val rooms: GiantRooms) : PlayerDeathRedirectHook {
    override fun destination(player: Player): CoordGrid? = rooms.forPlayer(player)?.kind?.exit
}

internal class GiantRemainsHook @Inject constructor() : NpcDeathDropHook {
    override fun tryConsume(context: NpcDeathDropContext): Boolean {
        if (context.npc.type.isType("npc.gb_growthling")) return true
        if (GiantBoss.entries.none { context.npc.type.isType(it.npc) } || context.hero.members) return false
        context.objRepo.add("obj.big_bones", context.dropCoords, context.duration, context.hero)
        return true
    }
}

internal class GiantDeathHook @Inject constructor(private val rooms: GiantRooms) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) { rooms.killed(context.npc, context.hero) }
}

@Singleton
internal class GiantRooms @Inject constructor(
    val manager: InstanceManager,
    private val deps: BossDeps,
    private val interactions: AiPlayerInteractions,
) {
    private val rooms = mutableMapOf<InstanceId, GiantRoom>()

    fun forPlayer(player: Player): GiantRoom? = manager.sessionForPlayer(player)?.id?.let(rooms::get)
    fun forNpc(npc: Npc): GiantRoom? = manager.instanceForNpc(npc)?.let(rooms::get)
    fun remove(id: InstanceId) { rooms.remove(id) }

    fun create(player: Player, kind: GiantBoss): InstanceManager.Result {
        val entry = kind.entry
        val spec = InstanceSpec(
            fee = 0, maxPlayers = 1, reclaimTicks = 100, graceTicks = 0, destroyWhenEmpty = true,
            area = InstanceArea.copyRegions(
                regionIds = listOf(((entry.x shr 6) shl 8) or (entry.z shr 6)),
                enterCoord = RegionLocal(entry.level, entry.mx, entry.mz, entry.lx, entry.lz),
                exitCoord = kind.exit),
            settingsRowId = -1, bossName = kind.label)
        val result = manager.create(player, kind.instanceKey, spec, InstanceAccess.Private, deps.mapClock.cycle)
        if (result is InstanceManager.Result.Created) {
            rooms[result.session.id] = GiantRoom(result.session.id, player, kind, result.enter)
        }
        return result
    }

    fun start(room: GiantRoom) {
        if (room.boss != null || manager.sessionForId(room.id) == null) return
        room.life = GiantLife()
        room.boss = spawn(room, room.kind.npc, room.kind.spawn).also {
            it.apPlayer2(room.owner, interactions)
        }
        room.nextGrowthlings = deps.mapClock.cycle
    }

    private fun spawn(room: GiantRoom, type: String, source: CoordGrid): Npc {
        val npc = Npc(type, room.tile(source)).apply { mode = NpcMode.None }
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        manager.attachNpc(room.id, npc)
        return npc
    }

    fun summon(room: GiantRoom) {
        if (room.growthlings.isNotEmpty() || deps.mapClock.cycle < room.nextGrowthlings) return
        for (site in listOf(CoordGrid(3218, 9931), CoordGrid(3224, 9932), CoordGrid(3221, 9937))) {
            val growthling = spawn(room, "npc.gb_growthling", site)
            room.growthlings += growthling
            growthling.apPlayer2(room.owner, interactions)
        }
        room.owner.mes("Bryophyta summons growthlings to protect her!")
    }

    fun growthlingDied(room: GiantRoom, npc: Npc) {
        if (room.growthlings.remove(npc) && room.growthlings.isEmpty()) {
            room.nextGrowthlings = deps.mapClock.cycle + 20
        }
        // Keep the finishing marker until room cleanup: an already queued zero hit must not
        // resurrect this NPC through the defensive impact-time one-HP floor.
    }

    fun killed(npc: Npc, hero: Player) {
        val room = forNpc(npc) ?: return
        if (room.owner !== hero || room.boss !== npc || !room.life.died()) return
        room.owner.mes("You defeat ${room.kind.label}. You can now open the chest with a key.")
        clearGrowthlings(room)
    }

    private fun clearGrowthlings(room: GiantRoom) {
        for (npc in room.growthlings) if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        room.growthlings.clear()
        room.finishing.clear()
    }

    fun tick() {
        for (room in rooms.values.toList()) {
            if (manager.sessionForId(room.id) == null) { rooms.remove(room.id); continue }
            for (npc in room.growthlings.toList()) {
                if (npc.hitpoints <= 0 || !npc.isVisible || !npc.isSlotAssigned) growthlingDied(room, npc)
            }
            if (room.life.ready(deps.mapClock.cycle)) {
                room.boss?.let { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
                room.boss = null
                start(room)
            }
        }
    }
}
