package org.rsmod.content.bosses.elvarg

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.CurrentTarget
import org.rsmod.api.bosses.dsl.Dragonfire
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.ScriptContext

// Bind the 816 parent; the 8033 visual leaf has one HP. Breath impacts are tile-anchored.
class Elvarg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(DRAGONFIRE_BURST) { _, npc, target, _ ->
            fireDragonfireBurst(npc, target)
        }
        BossCombat.register(this, spec, deps)
    }

    // Zero distance scaling keeps all six fireballs on the fixed staggered timing ladder.
    private fun fireDragonfireBurst(npc: Npc, target: Player) {
        val spotanim = FIREBALL_SPOTANIM.asRSCM(RSCMType.SPOTANIM)
        for ((startTime, endTime) in BURST_LADDER) {
            val type =
                ProjAnimType(
                    startHeight = BURST_START_HEIGHT,
                    endHeight = BURST_END_HEIGHT_MIN + deps.random.of(BURST_END_HEIGHT_SPAN),
                    delay = startTime,
                    angle = BURST_ANGLE_MIN + deps.random.of(BURST_ANGLE_SPAN),
                    lengthAdjustment = endTime - startTime,
                    progress = BURST_PROGRESS,
                    stepMultiplier = 0,
                )
            deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, target, spotanim, type))
        }
    }

    override val spec =
        boss("npc.elvarg") {
            stats(attackRate = ATTACK_RATE)

            // Sequence 80 already embeds the attack sound; sending a synth would play it twice.
            val melee =
                ability("melee") {
                    anim("seq.dragon_attack")
                    hit {
                        // Accuracy misses still queue a zero hitsplat.
                        damage(0..MELEE_MAX).rollWithAccuracy()
                        type(Melee)
                        delay = MELEE_HIT_DELAY
                    }
                }

            val dragonfire =
                ability("dragonfire") {
                    anim("seq.dragon_firebreath_all_attack")
                    sound(LAUNCH_SYNTH, radius = LAUNCH_SOUND_RADIUS)
                    include(external(DRAGONFIRE_BURST))
                    include(
                        Effect.MapSpotanim(
                            spot = IMPACT_SPOTANIM,
                            at = CurrentTarget,
                            height = IMPACT_HEIGHT,
                            delay = IMPACT_DELAY,
                        )
                    )
                    hit {
                        // Dragonfire has no accuracy gate; shield protection changes the damage cap.
                        damage(0..DRAGONFIRE_MAX).roll()
                        type(Dragonfire)
                        delay = DRAGONFIRE_HIT_DELAY
                    }
                }

            phase("combat") {
                weightedSelectorRandom {
                    +random(melee, weight = 1, requires = WithinMeleeRange)
                    +random(dragonfire, weight = 1)
                }
            }
        }

    private companion object {
        const val DRAGONFIRE_BURST = "elvarg.dragonfire_burst"

        const val ATTACK_RATE = 4

        const val AGGRESSION_RADIUS = 8

        const val MELEE_MAX = 8

        const val DRAGONFIRE_MAX = 70

        /** Hitsplat on the attack tick, matching `NvPCombat.MELEE_HIT_DELAY`. */
        const val MELEE_HIT_DELAY = 1

        /** Hitsplat two ticks after the breath. */
        const val DRAGONFIRE_HIT_DELAY = 3

        const val LAUNCH_SYNTH = "synth.DRAGONSLAYER_DRAGONBREATH"
        const val LAUNCH_SOUND_RADIUS = 15

        const val FIREBALL_SPOTANIM = "spotanim.fireblast_travel"

        const val IMPACT_SPOTANIM = "spotanim.fireblast_impact"
        const val IMPACT_HEIGHT = 90
        const val IMPACT_DELAY = 80

        const val BURST_START_HEIGHT = 70
        const val BURST_PROGRESS = 200

        const val BURST_END_HEIGHT_MIN = 70
        const val BURST_END_HEIGHT_SPAN = 40

        const val BURST_ANGLE_MIN = 8
        const val BURST_ANGLE_SPAN = 16

        val BURST_LADDER = listOf(51 to 80, 56 to 85, 61 to 90, 66 to 95, 71 to 100, 75 to 105)
    }
}
