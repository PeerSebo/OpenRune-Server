package org.rsmod.content.areas.misc.goblinvillage.configs

import org.rsmod.api.random.GameRandom

internal enum class GoblinSide {
    Red,
    Green;

    val opposite: GoblinSide
        get() = if (this == Red) Green else Red
}

internal data class Taunt(val text: String, val weight: Int)

internal object GoblinTaunts {
    val Red: List<Taunt> =
        listOf(
            Taunt("Red red red!", 13),
            Taunt("Red armour best!", 11),
            Taunt("Red not green!", 6),
            Taunt("Green armour stupid!", 6),
            Taunt("Stupid greenie!", 4),
        )

    val Green: List<Taunt> =
        listOf(
            Taunt("Stupid reddie!", 9),
            Taunt("Green armour best!", 8),
            Taunt("Green not red!", 7),
            Taunt("Green!", 5),
            Taunt("Red armour stupid!", 4),
        )

    fun of(side: GoblinSide): List<Taunt> =
        when (side) {
            GoblinSide.Red -> Red
            GoblinSide.Green -> Green
        }
}

internal fun List<Taunt>.pick(random: GameRandom): String {
    val total = sumOf { it.weight }
    var roll = random.of(total)
    for (taunt in this) {
        roll -= taunt.weight
        if (roll < 0) {
            return taunt.text
        }
    }
    return last().text
}

internal data class GoblinSoldier(
    val npc: String,
    val side: GoblinSide,
    val attackSeq: String,
    val attackSound: String,
    val defendSeq: String,
    val defendSound: String,
)

internal enum class GoblinStance(
    val attackSeq: String,
    val defendSeq: String,
    val defendSound: String,
) {
    Plain(
        attackSeq = "seq.slice_surface_goblin_squat_unarmed_attack",
        defendSeq = "seq.slice_surface_goblin_defend",
        defendSound = "synth.GOBLIN_HIT",
    ),

    Spear(
        attackSeq = "seq.slice_surface_goblin_squat_attack_spear",
        defendSeq = "seq.slice_surface_goblin_defend_spear",
        defendSound = "synth.GOBLIN_HIT",
    ),

    // The cache has no shield-spear defence sequence; use the plain spear defence.
    ShieldSpear(
        attackSeq = "seq.slice_surface_goblin_squat_spear_attack_shield",
        defendSeq = "seq.slice_surface_goblin_defend_spear",
        defendSound = "synth.GOBLIN_HIT_SHIELD",
    ),
}

internal object GoblinSoldiers {
    private const val ArmedAttackSeq: String = "seq.slice_surface_goblin_armed_attack"

    private const val AxeAttackSound: String = "synth.GOBLIN_AXE_ATTACK"
    private const val WarhammerAttackSound: String = "synth.GOBLIN_WARHAMMER_ATTACK"
    private const val HitShieldSound: String = "synth.GOBLIN_HIT_SHIELD"

    private const val DefaultAttackSound: String = AxeAttackSound

    private fun stance(npc: String, side: GoblinSide, stance: GoblinStance) =
        GoblinSoldier(
            npc = npc,
            side = side,
            attackSeq = stance.attackSeq,
            attackSound = DefaultAttackSound,
            defendSeq = stance.defendSeq,
            defendSound = stance.defendSound,
        )

    private val RedSoldier6 =
        GoblinSoldier(
            npc = "npc.goblin_red_soldier_6",
            side = GoblinSide.Red,
            attackSeq = GoblinStance.Plain.attackSeq,
            attackSound = AxeAttackSound,
            defendSeq = GoblinStance.Plain.defendSeq,
            defendSound = GoblinStance.Plain.defendSound,
        )

    private val GreenSoldier2 =
        GoblinSoldier(
            npc = "npc.goblin_green_soldier_2",
            side = GoblinSide.Green,
            attackSeq = ArmedAttackSeq,
            attackSound = AxeAttackSound,
            defendSeq = GoblinStance.Plain.defendSeq,
            defendSound = HitShieldSound,
        )

    private val GreenSoldier4 =
        GoblinSoldier(
            npc = "npc.goblin_green_soldier_4",
            side = GoblinSide.Green,
            attackSeq = ArmedAttackSeq,
            attackSound = WarhammerAttackSound,
            defendSeq = GoblinStance.Plain.defendSeq,
            defendSound = HitShieldSound,
        )

    val All: List<GoblinSoldier> =
        listOf(
            stance("npc.goblin_red_soldier_1", GoblinSide.Red, GoblinStance.Plain),
            stance("npc.goblin_red_soldier_2", GoblinSide.Red, GoblinStance.Plain),
            stance("npc.goblin_red_soldier_3", GoblinSide.Red, GoblinStance.Spear),
            stance("npc.goblin_red_soldier_4", GoblinSide.Red, GoblinStance.Plain),
            stance("npc.goblin_red_soldier_5", GoblinSide.Red, GoblinStance.ShieldSpear),
            RedSoldier6,
            stance("npc.goblin_red_soldier_7", GoblinSide.Red, GoblinStance.Spear),
            stance("npc.goblin_red_soldier_8", GoblinSide.Red, GoblinStance.Spear),
            stance("npc.goblin_green_soldier_1", GoblinSide.Green, GoblinStance.Spear),
            GreenSoldier2,
            stance("npc.goblin_green_soldier_3", GoblinSide.Green, GoblinStance.Spear),
            GreenSoldier4,
            stance("npc.goblin_green_soldier_5", GoblinSide.Green, GoblinStance.ShieldSpear),
            stance("npc.goblin_green_soldier_6", GoblinSide.Green, GoblinStance.Plain),
            stance("npc.goblin_green_soldier_7", GoblinSide.Green, GoblinStance.Spear),
            stance("npc.goblin_green_soldier_8", GoblinSide.Green, GoblinStance.Spear),
        )
}

internal object GoblinInfightParams {
    const val CycleTicks: Int = 6

    const val LeaderSwingTick: Int = 0
    const val LeaderBlowLandsTick: Int = 1
    const val FollowerSwingTick: Int = 3
    const val FollowerBlowLandsTick: Int = 4

    const val SoundRadius: Int = 10

    const val DamageOneIn: Int = 5

    const val Damage: Int = 1

    const val HitDelay: Int = 1

    const val TauntOneIn: Int = 6

    val BreakOffCooldown: IntRange = 30..50
}
