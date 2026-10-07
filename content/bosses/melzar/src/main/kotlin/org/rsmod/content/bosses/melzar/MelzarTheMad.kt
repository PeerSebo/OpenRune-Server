package org.rsmod.content.bosses.melzar

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

// Spell splashes send no hitsplat. External effects choose hit/splash visuals and attach them to the player.
class MelzarTheMad
@Inject
constructor(deps: BossDeps, private val objRepo: ObjRepository) : BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(FIRE_STRIKE_EXT) { _, npc, target, _ ->
            castFireStrike(npc, target)
        }
        deps.extensionRegistry.register(CURSE_EXT) { _, npc, target, _ -> castCurse(npc, target) }
        deps.extensionRegistry.register(WEAKEN_EXT) { _, npc, target, _ -> castWeaken(npc, target) }
        deps.extensionRegistry.register(CABBAGE_EXT) { _, npc, _, _ -> summonCabbage(npc) }
        deps.extensionRegistry.register(KICK_SOUND_EXT) { _, _, target, _ ->
            target.soundSynth(KICK_SYNTH)
        }
        BossCombat.register(this, spec, deps)

        // Reschedule first so early returns cannot retire the chatter timer.
        onAiTimer("npc.melzar_the_mad") { npc.chatterTick() }
    }

    private fun Npc.chatterTick() {
        aiTimer(1)
        if (isDelayed) {
            return
        }
        facingTarget(deps.playerList) ?: return
        if (deps.random.of(CHATTER_ODDS) == 0) {
            say(CHATTER_LINES[deps.random.of(CHATTER_LINES.size)])
        }
    }

    // The cabbage lands after three ticks as a public ground item.
    private fun summonCabbage(npc: Npc) {
        val origin = npc.spawnCoords
        deps.worldQueues.add(CABBAGE_DELAY) {
            objRepo.add(CABBAGE_OBJ, rollCabbageTile(origin), duration = CABBAGE_DURATION)
        }
    }

    private fun rollCabbageTile(origin: CoordGrid): CoordGrid {
        repeat(CABBAGE_TILE_ATTEMPTS) {
            val tile = deps.random.of(origin, CABBAGE_RADIUS)
            if (!deps.collision.isWalkBlocked(tile)) {
                return tile
            }
        }
        return origin
    }

    private fun castFireStrike(npc: Npc, target: Player) {
        castSpell(
            npc = npc,
            target = target,
            castSynth = FIRESTRIKE_CAST_SYNTH,
            travelSpot = FIRESTRIKE_TRAVEL,
            proj = SPELL_PROJ,
            impactSpot = FIRESTRIKE_IMPACT,
            impactSynth = FIRESTRIKE_HIT_SYNTH,
            impactDelay = SPELL_IMPACT_DELAY,
        ) {
            target.queueHit(npc, FIRESTRIKE_HIT_DELAY, HitType.Magic, deps.random.of(MAX_HIT + 1), modifier = deps.playerHitModifier)
        }
    }

    private fun castCurse(npc: Npc, target: Player) {
        castSpell(
            npc = npc,
            target = target,
            castSynth = CURSE_CAST_SYNTH,
            travelSpot = CURSE_TRAVEL,
            proj = SPELL_PROJ,
            impactSpot = CURSE_IMPACT,
            impactSynth = CURSE_HIT_SYNTH,
            impactDelay = SPELL_IMPACT_DELAY,
        ) {
            target.statSub(DEFENCE_STAT, constant = 0, percent = DRAIN_PERCENT)
        }
    }

    private fun castWeaken(npc: Npc, target: Player) {
        castSpell(
            npc = npc,
            target = target,
            castSynth = WEAKEN_CAST_SYNTH,
            travelSpot = WEAKEN_TRAVEL,
            proj = WEAKEN_PROJ,
            impactSpot = WEAKEN_IMPACT,
            impactSynth = WEAKEN_HIT_SYNTH,
            impactDelay = WEAKEN_IMPACT_DELAY,
        ) {
            target.statSub(STRENGTH_STAT, constant = 0, percent = DRAIN_PERCENT)
        }
    }

    // Impact effects follow the player; a splash applies neither damage nor a stat drain.
    private fun castSpell(
        npc: Npc,
        target: Player,
        castSynth: String,
        travelSpot: String,
        proj: ProjAnimType,
        impactSpot: String,
        impactSynth: String,
        impactDelay: Int,
        onLand: () -> Unit,
    ) {
        target.soundSynth(castSynth)
        val spotanim = travelSpot.asRSCM(RSCMType.SPOTANIM)
        deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, target, spotanim, proj))

        val landed = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
        if (landed) {
            target.spotanim(impactSpot, delay = impactDelay, height = IMPACT_HEIGHT, slot = 0)
            target.soundSynth(impactSynth, delay = impactDelay)
            onLand()
        } else {
            target.spotanim(SPLASH_SPOT, delay = impactDelay, height = IMPACT_HEIGHT, slot = 0)
            target.soundSynth(SPELLFAIL_SYNTH, delay = impactDelay)
        }
    }

    override val spec =
        boss("npc.melzar_the_mad") {
            stats(attackRate = ATTACK_RATE)

            val kick =
                ability("kick") {
                    anim("seq.human_unarmedkick")
                    include(external(KICK_SOUND_EXT))
                    hit {
                        damage(0..MAX_HIT).rollWithAccuracy()
                        type(Melee)
                        delay = MELEE_HIT_DELAY
                    }
                }

            val punch =
                ability("punch") {
                    anim("seq.human_unarmedpunch")
                    hit {
                        damage(0..MAX_HIT).rollWithAccuracy()
                        type(Melee)
                        delay = MELEE_HIT_DELAY
                    }
                }

            val fireStrike =
                ability("fire_strike") {
                    // Anim first on principle: only one anim attempt per tick reaches the wire.
                    anim("seq.human_caststrike_walkmerge")
                    spotanim("spotanim.firestrike_casting", height = CAST_SPOT_HEIGHT)
                    include(external(FIRE_STRIKE_EXT))
                }

            val curse =
                ability("curse") {
                    anim("seq.human_castcurse_walkmerge")
                    spotanim("spotanim.curse_casting", height = CAST_SPOT_HEIGHT)
                    include(external(CURSE_EXT))
                }

            val weaken =
                ability("weaken") {
                    anim("seq.human_castweaken_walkmerge")
                    spotanim("spotanim.weaken_casting", height = CAST_SPOT_HEIGHT)
                    include(external(WEAKEN_EXT))
                }

            val cabbage =
                ability("cabbage") {
                    anim("seq.human_castentangle")
                    spotanim("spotanim.entangle_casting", height = CAST_SPOT_HEIGHT)
                    include(external(CABBAGE_EXT))
                }

            phase("combat") {
                weightedSelectorRandom {
                    +random(kick, weight = 1, requires = WithinMeleeRange)
                    +random(punch, weight = 1, requires = WithinMeleeRange)
                    +random(fireStrike, weight = 1)
                    +random(curse, weight = 1)
                    +random(weaken, weight = 1)
                    +random(cabbage, weight = 1)
                }
            }
        }

    private companion object {
        const val FIRE_STRIKE_EXT = "melzar.fire_strike"
        const val CURSE_EXT = "melzar.curse"
        const val WEAKEN_EXT = "melzar.weaken"
        const val CABBAGE_EXT = "melzar.cabbage"
        const val KICK_SOUND_EXT = "melzar.kick_sound"

        const val ATTACK_RATE = 4

        const val AGGRESSION_RADIUS = 8

        const val MAX_HIT = 5

        /** Hitsplat on the attack tick. */
        const val MELEE_HIT_DELAY = 1

        // Hit delay includes the queue offset: damage lands three ticks after casting.
        const val FIRESTRIKE_HIT_DELAY = 4

        const val DRAIN_PERCENT = 5
        const val DEFENCE_STAT = "stat.defence"
        const val STRENGTH_STAT = "stat.strength"

        const val CAST_SPOT_HEIGHT = 92
        const val IMPACT_HEIGHT = 124

        const val SPELL_IMPACT_DELAY = 90
        const val WEAKEN_IMPACT_DELAY = 60

        const val FIRESTRIKE_TRAVEL = "spotanim.firestrike_travel"
        const val FIRESTRIKE_IMPACT = "spotanim.firestrike_impact"
        const val CURSE_TRAVEL = "spotanim.curse_travel"
        const val CURSE_IMPACT = "spotanim.curse_impact"
        const val WEAKEN_TRAVEL = "spotanim.weaken_travel"
        const val WEAKEN_IMPACT = "spotanim.weaken_impact"

        const val SPLASH_SPOT = "spotanim.failedspell_impact"

        const val KICK_SYNTH = "synth.UNARMED_KICK"
        const val FIRESTRIKE_CAST_SYNTH = "synth.FIRESTRIKE_CAST_AND_FIRE"
        const val FIRESTRIKE_HIT_SYNTH = "synth.FIRESTRIKE_HIT"
        const val CURSE_CAST_SYNTH = "synth.CURSE_CAST_AND_FIRE"
        const val CURSE_HIT_SYNTH = "synth.CURSE_HIT"
        const val WEAKEN_CAST_SYNTH = "synth.WEAKEN_CAST_AND_FIRE"
        const val WEAKEN_HIT_SYNTH = "synth.WEAKEN_HIT"
        const val SPELLFAIL_SYNTH = "synth.SPELLFAIL"

        val SPELL_PROJ =
            ProjAnimType(
                startHeight = 172,
                endHeight = 124,
                delay = 51,
                angle = 16,
                lengthAdjustment = 39,
                progress = 64,
                stepMultiplier = 0,
            )

        val WEAKEN_PROJ =
            ProjAnimType(
                startHeight = 172,
                endHeight = 124,
                delay = 44,
                angle = 16,
                lengthAdjustment = 16,
                progress = 64,
                stepMultiplier = 0,
            )

        // The packet despawn value 99 corresponds to a 100-tick ground-item lifetime.
        const val CABBAGE_OBJ = "obj.cabbage"
        const val CABBAGE_DELAY = 3
        const val CABBAGE_DURATION = 100

        const val CABBAGE_RADIUS = 3
        const val CABBAGE_TILE_ATTEMPTS = 10

        const val CHATTER_ODDS = 4

        val CHATTER_LINES =
            listOf(
                "Feel the wrath of my feet!",
                "Leave me alone, I need to feed my pet rock.",
                "By the power of custard!",
                "Let me drink my tea in peace.",
            )
    }
}
