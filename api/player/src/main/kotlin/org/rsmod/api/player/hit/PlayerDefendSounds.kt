package org.rsmod.api.player.hit

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.SynthType
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.torso
import org.rsmod.api.random.GameRandom

/**
 * Resolve sound symbols lazily so a bad symbol cannot permanently disable player damage.
 * HUMAN_HIT_05 is absent; numbering skips from 04 to 06.
 */
public object PlayerDefendSounds {
    private val hitSoundsBodyA: List<SynthType> by lazy {
        listOf(
                "synth.HUMAN_HIT",
                "synth.HUMAN_HIT2",
                "synth.HUMAN_HIT3",
                "synth.HUMAN_HIT4",
                "synth.HUMAN_HIT5",
                "synth.HUMAN_HIT_01",
                "synth.HUMAN_HIT_02",
                "synth.HUMAN_HIT_03",
                "synth.HUMAN_HIT_04",
                "synth.HUMAN_HIT_06",
                "synth.HUMAN_HIT_07",
            )
            .map { SynthType(it.asRSCM(RSCMType.SYNTH)) }
    }

    private val hitSoundsBodyB: List<SynthType> by lazy {
        listOf(
                "synth.FEMALE_HIT",
                "synth.FEMALE_HIT1",
                "synth.FEMALE_HIT2",
                "synth.FEMALE_HIT_01",
                "synth.FEMALE_HIT_02",
            )
            .map { SynthType(it.asRSCM(RSCMType.SYNTH)) }
    }

    private val defaultBlockSound: SynthType by lazy {
        SynthType("synth.HUMAN_BLOCK_01".asRSCM(RSCMType.SYNTH))
    }

    /**
     * Resolves the synth a player of [bodyType] plays when taking [damage].
     *
     * When [damage] is `0` the "block" sound is used instead, sourced from the equipped [lefthand]
     * (shield) params first, then [torso], then a default.
     */
    public fun resolve(
        lefthand: ItemServerType?,
        torso: ItemServerType?,
        damage: Int,
        bodyType: Int,
        random: GameRandom,
    ): SynthType =
        when {
            damage == 0 -> resolveBlockSound(lefthand, torso, random)
            bodyType == constants.bodytype_a -> random.pick(hitSoundsBodyA)
            bodyType == constants.bodytype_b -> random.pick(hitSoundsBodyB)
            else -> throw NotImplementedError("Sound for body type is not implemented: $bodyType")
        }

    private fun resolveBlockSound(
        lefthand: ItemServerType?,
        torso: ItemServerType?,
        random: GameRandom,
    ): SynthType {
        val lefthandSound = lefthand?.randomBlockSound(random)
        if (lefthandSound != null) {
            return lefthandSound
        }

        val torsoSound = torso?.randomBlockSound(random)
        if (torsoSound != null) {
            return torsoSound
        }

        return defaultBlockSound
    }

    private fun ItemServerType.randomBlockSound(random: GameRandom): SynthType? {
        val sounds =
            listOfNotNull(
                paramOrNull(BaseParams.item_block_sound1),
                paramOrNull(BaseParams.item_block_sound2),
                paramOrNull(BaseParams.item_block_sound3),
                paramOrNull(BaseParams.item_block_sound4),
                paramOrNull(BaseParams.item_block_sound5),
            )
        return random.pickOrNull(sounds)
    }
}

public fun ProtectedAccess.resolveDefendSound(damage: Int): SynthType {
    val lefthandType = player.lefthand?.let(::ocType)
    val torsoType = player.torso?.let(::ocType)
    return PlayerDefendSounds.resolve(
        lefthand = lefthandType,
        torso = torsoType,
        damage = damage,
        bodyType = player.appearance.bodyType,
        random = random,
    )
}

/**
 * Plays the standard "took a hit" (or "blocked", when [damage] is `0`) sound for this player.
 *
 * This is what `StandardPlayerHitProcessor` plays for a queued hit; content dealing an instant hit
 * (`takeInstantHit` with `DamageOnlyPlayerHitProcessor`) can call this to get the same sound on the
 * same tick as the hit.
 *
 * @param delay client-side synth delay, in client ticks. Defaults to `20`, matching the queued hit
 *   processor.
 * @return the [SynthType] that was played, in case it should be relayed to another player.
 */
public fun ProtectedAccess.playDefendSound(damage: Int, delay: Int = 20): SynthType {
    val sound = resolveDefendSound(damage)
    soundSynth(sound, delay = delay)
    return sound
}
