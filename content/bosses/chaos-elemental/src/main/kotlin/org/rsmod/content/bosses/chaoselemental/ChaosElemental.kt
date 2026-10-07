package org.rsmod.content.bosses.chaoselemental

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ProjAnimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.EffectInterpreter
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class ChaosElemental @Inject constructor(
    deps: BossDeps,
    private val unequip: WornUnequipOp,
    private val ray: RayCastValidator,
) : BossPluginScript(deps) {
    private val lives = IdentityHashMap<Npc, Any>()
    override val spec = boss("npc.chaoselemental") {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("chaos_elemental.attack")) }
        phase("combat") { weightedSelectorRandom { +random(attack, weight = 1) } }
    }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("chaos_elemental.attack") { access, npc, target, _ ->
            attack(checkNotNull(access), npc, target)
        }
        BossCombat.register(this, spec, deps)
        val type = "npc.chaoselemental".asRSCM()
        onEvent<NpcStateEvents.Hide>(type) { lives.remove(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.id == type) lives.remove(npc) }
        onEvent<NpcStateEvents.Delete> { lives.remove(npc) }
    }

    private fun attack(access: StandardNpcAccess, npc: Npc, target: Player) {
        val selection = ChaosElementalRules.chooseAttack(deps.random, canDisarm(target))
        access.anim("seq.chaoselemental_attack")
        when (selection) {
            ChaosAttack.DISCORD -> {
                access.spotanim("spotanim.chaoselemental_spotanim_discord_casting", height = 100)
                val type = when (deps.random.of(5)) { 0 -> Melee; 1 -> Ranged; else -> Magic }
                EffectInterpreter(npc, target, spec, deps.encounterRegistry.of(npc), deps).run(
                    access,
                    Effect.Projectile(
                        spotanim = "spotanim.chaoselemental_spotanim_discord_travel",
                        config = FLIGHT,
                        hit = Effect.Hit(
                            damage = Roll(0..28).withAccuracy(), type = type,
                            spotanim = "spotanim.chaoselemental_spotanim_discord_impact",
                            spotanimHeight = 100,
                        ),
                    ),
                )
            }
            ChaosAttack.MADNESS -> special(access, npc, target, "madness") {
                val removed = ChaosElementalRules.disarm(it, deps.random, unequip)
                if (removed > 0) it.mes("The Chaos Elemental's attack removes some of your equipment!")
            }
            ChaosAttack.CONFUSION -> special(access, npc, target, "confusion") {
                val destination = ChaosElementalRules.destination(npc.coords, it.coords, deps.random) { tile ->
                    deps.collision[tile.x, tile.z, tile.level] and
                        (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) == 0 &&
                        ray.hasLineOfWalk(it.coords, tile)
                }
                if (destination != null) {
                    it.clearInteraction()
                    PathingEntityCommon.telejump(it, deps.collision, destination)
                    it.mes("The Chaos Elemental teleports you away!")
                }
            }
        }
    }

    private fun special(
        access: StandardNpcAccess, npc: Npc, target: Player, family: String,
        impact: (Player) -> Unit,
    ) {
        access.spotanim("spotanim.chaoselemental_spotanim_${family}_casting", height = 100)
        val projectile = ProjAnim.fromNpcToPlayer(
            npc, target, "spotanim.chaoselemental_spotanim_${family}_travel".asRSCM(),
            ProjAnimType(startHeight = FLIGHT.startHeight, endHeight = FLIGHT.endHeight,
                delay = FLIGHT.startDelay, angle = FLIGHT.angle,
                lengthAdjustment = FLIGHT.travelTime, progress = FLIGHT.progress,
                stepMultiplier = FLIGHT.stepMultiplier),
        )
        deps.worldRepo.projAnim(projectile)
        val targetUid = target.uid
        val bossUid = npc.uid
        val life = lives.getOrPut(npc) { Any() }
        ChaosSpecialImpact.schedule(deps.worldQueues, projectile.serverCycles.coerceAtLeast(1),
            valid = { lives[npc] === life && npc.isSlotAssigned && npc.isVisible && npc.uid == bossUid && npc.hitpoints > 0 },
            resolve = { targetUid.resolve(deps.playerList)?.takeIf { it.isValidTarget() && it.level == npc.level } },
            impact = {
                it.spotanim("spotanim.chaoselemental_spotanim_${family}_impact", height = 100)
                impact(it)
            })
    }

    private fun canDisarm(player: Player): Boolean =
        player.inv.freeSpace() > 0 && player.worn.indices.any { player.worn[it] != null }

    private companion object {
        val FLIGHT = ProjectileConfig(startHeight = 60, endHeight = 30, startDelay = 30,
            travelTime = 10, angle = 10, progress = 0, stepMultiplier = 5)
    }
}

internal enum class ChaosAttack { DISCORD, MADNESS, CONFUSION }

internal object ChaosElementalRules {
    fun chooseAttack(random: GameRandom, canDisarm: Boolean): ChaosAttack =
        when (random.of(if (canDisarm) 10 else 9)) {
            8 -> ChaosAttack.CONFUSION
            9 -> ChaosAttack.MADNESS
            else -> ChaosAttack.DISCORD
        }

    fun disarm(player: Player, random: GameRandom, unequip: WornUnequipOp): Int {
        if (player.isAccessProtected || player.inv.freeSpace() == 0) return 0
        val slots = player.worn.indices.filter { player.worn[it] != null }.toMutableList()
        var removed = 0
        repeat(random.of(1, 4)) {
            if (slots.isEmpty() || player.inv.freeSpace() == 0) return removed
            val slot = if (Wearpos.RightHand.slot in slots) Wearpos.RightHand.slot
                else slots[random.of(slots.size)]
            slots.remove(slot)
            if (unequip.unequip(player, slot, player.worn, player.inv) == WornUnequipResult.Success) removed++
        }
        return removed
    }

    fun destination(boss: CoordGrid, source: CoordGrid, random: GameRandom, canStand: (CoordGrid) -> Boolean): CoordGrid? {
        if (boss.level != source.level) return null
        repeat(40) {
            val candidate = source.translate(random.of(-8, 8), random.of(-8, 8))
            if (source.chebyshevDistance(candidate) in 3..8 &&
                candidate.chebyshevDistance(boss) > source.chebyshevDistance(boss) && canStand(candidate)) {
                return candidate
            }
        }
        return null
    }
}

internal object ChaosSpecialImpact {
    fun schedule(queues: WorldQueueList, delay: Int, valid: () -> Boolean,
        resolve: () -> Player?, impact: (Player) -> Unit, retries: Int = 8) {
        queues.add(delay) {
            if (!valid()) return@add
            val player = resolve() ?: return@add
            if (player.isAccessProtected) {
                // Appended queues may decrement this pass; two cycles guarantee an intervening tick.
                if (retries > 0) schedule(queues, 2, valid, resolve, impact, retries - 1)
            } else impact(player)
        }
    }
}
