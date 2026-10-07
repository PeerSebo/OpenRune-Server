package org.rsmod.content.bosses.thermy

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.baseSlayerLvl
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.ScriptContext

class ThermonuclearSmokeDevil @Inject constructor(
    deps: BossDeps,
    private val ray: RayCastValidator,
) : BossPluginScript(deps) {
    override val spec = boss("npc.smoke_devil_boss") {
        stats(attackRate = 2)
        val smoke = ability("smoke") { include(external("thermy.smoke")) }
        phase("combat") { rotationSelector { +then(smoke) } }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onModifyHit = {
            // Covers projectiles already in flight when the final Slayer kill was credited.
            val source = if (hit.isFromPlayer) hit.sourceUid?.let { PlayerUid(it).resolve(deps.playerList) } else null
            if (source != null && ThermyRequirements.denial(source, npc) != null) hit.damage = 0
        })
        deps.extensionRegistry.register("thermy.smoke") { _, npc, target, _ ->
            if (target.isValidTarget() && target.coords.level == npc.coords.level &&
                footprintDistance(npc, target) in 1..8 &&
                ray.hasLineOfSight(target.coords, npc.coords, destWidth = npc.size, destLength = npc.size)
            ) {
                npc.anim("seq.lore_dust_devil_attack")
                val projectile = ProjAnim.fromNpcToPlayer(npc, target,
                    "spotanim.smoke_devil_smoke_proj".asRSCM(), FLIGHT)
                deps.worldRepo.projAnim(projectile)
                // OSRS uses ranged accuracy against magic defence; overheads do not reduce it.
                val accurate = deps.accuracy.rollMagicalRangedAccuracy(npc, target, deps.random)
                target.finishNpcHit(npc, 2, HitType.Typeless,
                    if (accurate) deps.random.of(9) else 0, deps.playerHitModifier)
                target.spotanim("spotanim.smoke_devil_smoke_player_spotanim", delay = 60)
            }
        }
    }

    private fun footprintDistance(npc: Npc, player: Player): Int = maxOf(
        (npc.coords.x - player.coords.x).coerceAtLeast(0),
        (player.coords.x - npc.coords.x - npc.size + 1).coerceAtLeast(0),
        (npc.coords.z - player.coords.z).coerceAtLeast(0),
        (player.coords.z - npc.coords.z - npc.size + 1).coerceAtLeast(0),
    )

    private companion object {
        val FLIGHT = ProjAnimType(startHeight = 40, endHeight = 30, delay = 0, angle = 10,
            lengthAdjustment = 20, progress = 0, stepMultiplier = 3)
    }
}

internal object ThermyRequirements {
    fun canFight(baseSlayer: Int, onTask: Boolean, kills: Int): Boolean =
        baseSlayer >= 93 && (onTask || kills == 0)

    fun denial(player: Player, npc: Npc): String? {
        if (player.baseSlayerLvl < 93) return "You need a Slayer level of 93 to fight this creature."
        val onTask = player.vars["varp.slayer_count"] > 0 &&
            SlayerTaskManager.countsAsTaskKill(npc, player.vars["varp.slayer_target"],
                player.vars["varbit.slayer_target_bossid"])
        return if (canFight(player.baseSlayerLvl, onTask, player.vars["varp.kc_thermonuclear_smoke_devil"])) null
        else "You can only fight this creature on a smoke devil Slayer task."
    }
}

class ThermyModule : PluginModule() {
    override fun bind() { addSetBinding<NpcAttackValidateHook>(ThermyAttackGate::class.java) }
}

class ThermyAttackGate @Inject constructor() : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != "npc.smoke_devil_boss".asRSCM()) return NpcAttackValidateResult.Pass
        return ThermyRequirements.denial(player, npc)?.let(NpcAttackValidateResult::Deny)
            ?: NpcAttackValidateResult.Pass
    }
}
