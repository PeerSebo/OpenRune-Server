package org.rsmod.content.minigames.blastfurnace.configs

internal object BlastFurnaceParams {

    const val COFFER_DRAIN_TIMER = "timer.blast_furnace_coffer_drain"
    const val FOREMAN_FEE_TIMER = "timer.blast_furnace_foreman_fee"
    const val SMELT_QUEUE = "queue.blast_furnace_smelt"
    const val BELT_QUEUE = "queue.blast_furnace_belt"
    const val HOT_TIMER = "timer.blast_furnace_hot"
    const val FOREMAN_PERMISSION_VARBIT = "varbit.blast_furnace_foreman_permission"
    const val AREA = "area.blast_furnace"

    const val FREE_ACCESS_LEVEL = 60

    const val FOREMAN_FEE_GP = 2500

    const val FOREMAN_FEE_GP_CHAROS = 1250

    const val FOREMAN_FEE_WINDOW_TICKS = 1000

    // Coffer occupies bits 1-31; a varp decrement of 24 spends 12 coins.
    const val COFFER_DRAIN_PER_TICK = 12

    // Bronze's primary input is tin (SmithingBar orders tin before copper).
    const val COAL_CAP = 254
    const val TIN_CAP = 254

    const val COPPER_CAP = 254

    const val PRIMARY_ORE_CAP = 28

    // Smelting pauses at the per-metal dispenser cap; taking bars allows waiting ore to convert.
    const val BAR_DISPENSER_CAP = 28

    const val BAR_CAP_MESSAGE = "You should collect your bars before making any more."

    const val ORE_CAP_MESSAGE = "You should make sure all your ore smelts before adding any more."

    const val BELT_TRAVEL_TICKS = 5

    const val POT_SMELT_TICKS = 1

    const val POURING_TO_HOT_TICKS = 2

    // 30 seconds at 0.6-second ticks.
    const val AUTO_COOL_TICKS = 50

    // Play once per ore type arriving, including separate ore and coal sounds.
    const val SOUND_ORE_IN_FURNACE = "synth.BF_OREINFURNACE"

    const val SOUND_COOLDOWN = "synth.BF_COOLDOWN"

    // Native sound is area-wide; this implementation is player-local.
    const val SOUND_SMELT = "synth.BF_SMELT"
}
