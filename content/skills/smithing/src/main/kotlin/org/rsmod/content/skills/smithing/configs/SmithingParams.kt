package org.rsmod.content.skills.smithing.configs

object SmithingParams {

    const val ANVIL_TICKS = 5

    const val ANVIL_TICKS_UNIFORM_PROC = 4

    // TODO: Verify the assumed four-tick bar-smelting cadence.
    const val SMELT_TICKS = 4

    const val CANNONBALL_TICKS = 10

    const val CANNONBALL_TICKS_GRIMSTONE = 5

    // TODO: Verify crystal-singing cadence; four ticks currently matches one animation loop.
    const val CRYSTAL_SING_TICKS = 4

    // TODO: Verify Dragon Forge cadence; it currently shares the assumed smelting delay.
    const val DRAGON_FORGE_TICKS = 4

    const val ANVIL_ANIM = "seq.human_smithing"

    // This loop contains no audio; send FURNACE_SOUND separately.
    const val FURNACE_ANIM = "seq.human_furnace"

    const val FURNACE_SOUND = "synth.FURNACE"

    const val CRYSTAL_SING_ANIM = "seq.prif_crystal_singing"

    // TODO: Verify the singing-bowl synth; the configured loop has no baked sound.
    const val CRYSTAL_SING_SOUND = "synth.SOTE_SINGING_BOWL_LOOP"

    const val ANVIL_QUEUE = "queue.smithing_anvil"
    const val SMELT_QUEUE = "queue.smithing_bar_smelt"
    const val CANNONBALL_QUEUE = "queue.smithing_cannonball_smelt"
    const val CRYSTAL_SING_QUEUE = "queue.smithing_crystal_singing"
    const val DRAGON_FORGE_QUEUE = "queue.smithing_dragon_forge"
}
