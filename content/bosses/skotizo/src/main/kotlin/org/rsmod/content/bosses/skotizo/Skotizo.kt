package org.rsmod.content.bosses.skotizo

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Skotizo @Inject internal constructor(
    private val deps: BossDeps,
    private val rooms: SkotizoRooms,
    private val ray: RayCastValidator,
    private val death: NpcDeath,
) : PluginScript() {
    private val spec = boss(SkotizoRules.Boss) {
        stats(attackRate = 6)
        val attack = ability("attack") { include(external("skotizo.attack")) }
        phase("fight", lockMovement = true) { rotationSelector { +then(attack) } }
    }
    private val minions = boss(SkotizoRules.Minion, SkotizoRules.Ankou) {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("skotizo.minion")) }
        phase("fight") { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        val demonbane = listOf("obj.arclight", "obj.emberlight").map { checkNotNull(ServerCacheManager.getItem(it.asRSCM())) }
        deps.extensionRegistry.register("skotizo.attack") { _, npc, target, _ -> attack(npc, target) }
        deps.extensionRegistry.register("skotizo.minion") { _, npc, target, _ -> minion(npc, target) }
        BossCombat.register(this, spec, deps)
        onEvent<NpcHitEvents.BeforeImpact>(SkotizoRules.Boss.asRSCM()) {
            val room = rooms.forNpc(npc)
            damage = if (room == null || !room.alive) 0 else SkotizoRules.reducedDamage(damage, room.eyes.count,
                hit.isFromPlayer && hit.type == HitType.Melee && demonbane.any(hit::isRighthandObj))
        }
        BossCombat.register(this, minions, deps)
        for (symbol in SkotizoRules.Closed + SkotizoRules.Open) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            onEvent<NpcHitEvents.BeforeImpact>(type.id) {
                val room = rooms.forNpc(npc)
                val index = room?.altars?.indexOf(npc) ?: -1
                if (room == null || !room.alive || index < 0 || !room.eyes.isActive(index)) damage = 0
                else if (hit.isFromPlayer && hit.type == HitType.Melee && demonbane.any(hit::isRighthandObj)) damage = npc.hitpoints
            }
            onNpcHit(type) {
                if (npc.hitpoints <= 0) {
                    // The eye stays in its instance slot. Cancel the already scheduled generic
                    // death before closing it; queued projectiles cannot delete a closed altar.
                    npc.queueList.removeAll("queue.death")
                    rooms.altarDied(npc)
                }
            }
        }
        onNpcHit(checkNotNull(ServerCacheManager.getNpc(SkotizoRules.Boss.asRSCM()))) {
            if (npc.hitpoints <= 0) rooms.bossDied(npc)
        }
        for (symbol in listOf(SkotizoRules.Minion, SkotizoRules.Ankou)) {
            onNpcQueue(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), "queue.death") { death.deathNoDrops(this) }
        }
    }

    private fun valid(npc: Npc, target: Player, range: Int): SkotizoRoom? {
        val room = rooms.forNpc(npc) ?: return null
        if (!room.alive || room.owner !== target || !room.contains(target.coords) || !target.isValidTarget() || npc.hitpoints <= 0 ||
            npc.level != target.level || SkotizoRules.distance(npc.coords, npc.size, target.coords) > range) return null
        if (!ray.hasLineOfSight(npc.coords, target.coords, srcWidth = npc.size, srcLength = npc.size)) return null
        return room
    }

    private fun attack(npc: Npc, target: Player) {
        val room = valid(npc, target, 10) ?: return
        rooms.summon(room)
        if (SkotizoRules.distance(npc.coords, npc.size, target.coords) <= 1 && deps.random.of(2) == 0) {
            npc.anim("seq.demon_update_swipe")
            val accurate = deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Slash, deps.random)
            target.finishNpcHit(npc, 1, HitType.Melee, if (accurate) deps.random.of(39) else 0, deps.playerHitModifier)
        } else {
            npc.anim("seq.demon_update_fireball_cast")
            val projectile = ProjAnim.fromNpcToPlayer(npc, target, "spotanim.firewave_travel".asRSCM(), Flight)
            deps.worldRepo.projAnim(projectile)
            val accurate = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
            target.finishNpcHit(npc, projectile.serverCycles, HitType.Magic, if (accurate) deps.random.of(32) else 0, deps.playerHitModifier)
        }
    }

    private fun minion(npc: Npc, target: Player) {
        valid(npc, target, 1) ?: return
        npc.anim(if (npc.id == SkotizoRules.Minion.asRSCM()) "seq.demon_attack" else "seq.human_unarmedpunch")
        val accurate = deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, deps.random)
        target.finishNpcHit(npc, 1, HitType.Melee, if (accurate) deps.random.of(9) else 0, deps.playerHitModifier)
    }

    private companion object {
        val Flight = ProjAnimType(startHeight = 70, endHeight = 30, delay = 30, angle = 15, lengthAdjustment = 20, progress = 0, stepMultiplier = 3)
    }
}
