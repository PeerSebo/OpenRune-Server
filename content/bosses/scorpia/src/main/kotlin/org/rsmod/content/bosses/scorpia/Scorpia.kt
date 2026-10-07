package org.rsmod.content.bosses.scorpia

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class Scorpia @Inject constructor(deps: BossDeps, private val ray: RayCastValidator) : BossPluginScript(deps) {
    private data class Fight(val life: ScorpiaLife = ScorpiaLife(), val guardians: MutableMap<Npc, GuardianCycle> = mutableMapOf())
    private val fights = mutableMapOf<Npc, Fight>()
    private var Npc.aggressivePlayer by typePlayerUidVarn("varn.aggressive_player")

    override val spec = boss("npc.scorpia") {
        stats(attackRate = 4)
        val attack = ability("tail") { include(external("scorpia.tail")) }
        phase("combat") { rotationSelector { +then(attack) } }
    }

    private val offspring = boss("npc.scorpia_minion") {
        stats(attackRate = 4)
        val attack = ability("sting") { include(external("scorpia.offspring_sting")) }
        phase("combat") { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("scorpia.tail") { _, npc, target, _ -> melee(npc, target) }
        deps.extensionRegistry.register("scorpia.offspring_sting") { _, npc, target, _ -> ranged(npc, target) }
        BossCombat.register(this, spec, deps, onModifyHit = {
            val fight = fights.getOrPut(npc) { Fight() }
            // The incoming damage has not been applied here: an attack crossing 100 HP alone
            // must not summon. The following player attack may summon even when it misses.
            if (fight.life.claimSummon(npc.hitpoints, hit.isFromPlayer)) summon(npc, fight)
        })
        BossCombat.register(this, offspring, deps)
        val queenType = checkNotNull(ServerCacheManager.getNpc("npc.scorpia".asRSCM(RSCMType.NPC)))
        onEvent<NpcStateEvents.Hide>(queenType.id) { remove(npc) }
        onEvent<NpcStateEvents.Respawn> { remove(npc) }
        onEvent<NpcStateEvents.Delete> { remove(npc) }
        onEvent<GameLifecycle.LateCycle> { tickGuardians() }
    }

    private fun melee(npc: Npc, target: Player) {
        if (!target.isValidTarget() || npc.hitpoints <= 0) return
        npc.anim("seq.scorpion_update_attack_tail")
        val accurate = deps.accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Stab, deps.random)
        val damage = if (accurate) deps.random.of(17) else 0
        val protecting = target.vars["varbit.prayer_protectfrommelee"] == 1
        target.finishNpcHit(npc, 1, HitType.Melee, damage, deps.playerHitModifier)
        if (protecting) target.statSub("stat.prayer", constant = 2, percent = 0)
        if (accurate && !protecting && damage > 0 && deps.random.of(6) == 0) CombatEffects.poison(target, 20)
    }

    private fun ranged(npc: Npc, target: Player) {
        if (!target.isValidTarget() || npc.hitpoints <= 0) return
        // Recheck at launch, including direct/retaliation callbacks. Aggression and chase
        // distances describe target selection, not how far an offspring can shoot.
        if (!offspringCanReach(npc, target.coords, ray)) return
        val queenAttacking = deps.npcRepo.findAll(ZoneKey.from(npc.coords), 5).any {
            it.id == "npc.scorpia".asRSCM(RSCMType.NPC) && it.isVisible && it.hitpoints > 0 &&
                (it.interaction as? InteractionPlayer)?.target === target
        }
        if (!ScorpiaRules.offspringAggressive(PlayerPoison.isPoisoned(target), queenAttacking, npc.aggressivePlayer == target.uid)) {
            npc.clearInteractionRoute()
            npc.defaultMode()
            return
        }
        npc.anim("seq.small_scorpion_update_attack")
        val accurate = deps.accuracy.rollRangedAccuracy(npc, target, deps.random)
        val damage = if (accurate) deps.random.of(3) else 0
        target.finishNpcHit(npc, 1, HitType.Ranged, damage, deps.playerHitModifier)
        if (accurate && damage > 0 && target.vars["varbit.prayer_protectfrommissiles"] == 0 && deps.random.of(6) == 0) {
            CombatEffects.poison(target, 6)
        }
    }

    private fun summon(owner: Npc, fight: Fight) {
        val sites = ScorpiaRules.guardianSites(owner.coords, owner.size) {
            !deps.collision.scorpionBlocked(it) && deps.npcRepo.findAll(it).none { npc -> npc.isVisible && npc.hitpoints > 0 }
        }
        for (tile in sites) {
            val guardian = Npc("npc.scorpia_guardian", tile).apply {
                mode = NpcMode.None
                ignoreCombatInteractions = true
            }
            deps.npcRepo.add(guardian, Int.MAX_VALUE)
            // Repository's permanent duration means respawn by default. Guardians are one life.
            guardian.respawns = false
            fight.guardians[guardian] = GuardianCycle(deps.mapClock.cycle)
        }
    }

    internal fun tickGuardians() {
        for ((owner, fight) in fights.toMap()) {
            if (!owner.isSlotAssigned || !owner.isVisible || owner.hitpoints <= 0) {
                remove(owner)
                continue
            }
            for ((guardian, cycle) in fight.guardians.toMap()) {
                if (!guardian.isSlotAssigned || !guardian.isVisible || guardian.hitpoints <= 0) {
                    fight.guardians.remove(guardian)
                    continue
                }
                val close = ScorpiaRules.footprintDistance(owner.coords, owner.size, guardian.coords) <= 3 &&
                    ray.hasLineOfSight(guardian.coords, owner.coords, destWidth = owner.size, destLength = owner.size)
                when (cycle.tick(deps.mapClock.cycle, ownerAlive = true, inRange = close)) {
                    GuardianAction.REMOVE -> {
                        fight.guardians.remove(guardian)
                        deps.npcRepo.del(guardian, Int.MAX_VALUE)
                    }
                    GuardianAction.FOLLOW -> {
                        guideGuardian(guardian, owner, inRange = false)
                    }
                    GuardianAction.WAIT -> guideGuardian(guardian, owner, inRange = true)
                    GuardianAction.HEAL -> {
                        guideGuardian(guardian, owner, inRange = true)
                        guardian.anim("seq.small_scorpion_update_attack")
                        val proj = ProjAnim.fromNpcToNpc(guardian, owner, "spotanim.curse_travel".asRSCM(RSCMType.SPOTANIM), HEAL_FLIGHT)
                        deps.worldRepo.projAnim(proj)
                        owner.heal(8, showHitsplat = true)
                    }
                }
            }
        }
    }

    private fun remove(owner: Npc) {
        val fight = fights.remove(owner) ?: return
        for (guardian in fight.guardians.keys) {
            if (guardian.isSlotAssigned && guardian.isVisible && guardian.hitpoints > 0) deps.npcRepo.del(guardian, Int.MAX_VALUE)
        }
        fight.guardians.clear()
    }

    private companion object {
        val HEAL_FLIGHT = ProjAnimType(startHeight = 20, endHeight = 35, delay = 0, angle = 10,
            lengthAdjustment = 15, progress = 0, stepMultiplier = 3)
    }
}
