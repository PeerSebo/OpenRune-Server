package org.rsmod.content.skills.thieving.configs

object ThievingRates {
    const val STALL_STEAL_ANIM: String = "seq.human_pickuptable"

    const val STALL_ACTION_DELAY: Int = 2

    val EMPTY_STALLS_WITH_OPS: List<String> =
        listOf("loc.rag_market_stall_empty", "loc.empty_rock_cake_counter")

    const val SAFE_LOW: Int = 8

    const val SAFE_HIGH: Int = 160

    const val SAFE_STETHOSCOPE_LOW: Int = 16

    const val SAFE_STETHOSCOPE_HIGH: Int = 192

    // TODO: Verify door success curves; current endpoints target roughly 50% at the required level.
    fun fittedDoorRates(levelReq: Int): Pair<Int, Int> {
        val high = 255
        if (levelReq >= MAX_LEVEL) {
            return high to high
        }
        val numerator = FIFTY_PERCENT_TARGET - high * (levelReq - 1)
        val low = Math.floorDiv(numerator, MAX_LEVEL - levelReq)
        return low to high
    }

    const val PICKPOCKET_ANIM: String = "seq.human_pickpocket"

    const val PICKPOCKET_KNOCKOUT_ANIM: String = "seq.human_pickupfloor"

    const val PICKPOCKET_SUCCESS_QUEUE: String = "queue.thieving_pickpocket_success"

    const val PICKPOCKET_FAIL_REACT_QUEUE: String = "queue.thieving_pickpocket_fail_react"

    const val PICKPOCKET_FAIL_STRIKE_QUEUE: String = "queue.thieving_pickpocket_fail_strike"

    const val PICKPOCKET_FAIL_STUN_QUEUE: String = "queue.thieving_pickpocket_fail_stun"

    // Use actionDelay with a leading wait; trailing suspension adds an extra approach tick.
    const val PICKPOCKET_ACTION_DELAY: Int = 2

    const val PICKPOCKET_STUN_MOVEMENT: Int = 9

    const val PICKPOCKET_STUN_ACTION: Int = 8

    const val PICKPOCKET_STUN_SEQ: String = "seq.stunned_thieving"

    const val PICKPOCKET_STUN_SPOTANIM: String = "spotanim.stunned_thieving"

    const val PICKPOCKET_FALLBACK_ATTACK_ANIM: String = "seq.human_unarmedpunch"

    const val PICKPOCKET_BLOCK_ANIM: String = "seq.human_unarmedblock"

    const val PICKPOCKET_SUCCESS_SOUND: String = "synth.PICK"

    const val PICKPOCKET_STUN_SOUND: String = "synth.STUNNED"

    // Release facing on the NPC queue so the player can still eat and switch equipment while stunned.
    const val PICKPOCKET_RELEASE_FACE_QUEUE: String = "queue.thieving_release_face"

    const val PICKPOCKET_FOCUS_CYCLES: Int = 4

    const val BLACKJACK_STUN_SEQ: String = "seq.stunned_blackjack"
    const val BLACKJACK_STUN_SPOTANIM: String = "spotanim.stunned_blackjack"
    const val BLACKJACK_CRUSH_SOUND: String = "synth.BLACKJACK_CRUSH"
    const val BLACKJACK_SWING_ANIM: String = "seq.human_blunt_pound"

    const val BLACKJACK_KO_LOW: Int = 80

    const val BLACKJACK_KO_HIGH: Int = 240

    const val BLACKJACK_FREE_PICKPOCKETS: Int = 2

    const val BLACKJACK_KO_CYCLES: Int = 8

    const val BLACKJACK_ACTION_DELAY: Int = 3

    const val BLACKJACK_MULTI_VARBIT: String = "varbit.feud_npc_multi"

    const val BLACKJACK_MULTI_ENABLED: Int = 2

    // Apply success bonuses to low/high in sequence, flooring each step. Gloves and diary bonuses do not stack.
    fun applyPickpocketBonuses(
        base: Int,
        glovesOfSilence: Boolean,
        thievingCape: Boolean,
        ardougneDiary: Boolean,
    ): Int {
        var value = base
        val glovesOrDiaryPercent =
            when {
                ardougneDiary && glovesOfSilence ->
                    maxOf(GLOVES_OF_SILENCE_PERCENT, ARDOUGNE_DIARY_PERCENT)
                ardougneDiary -> ARDOUGNE_DIARY_PERCENT
                glovesOfSilence -> GLOVES_OF_SILENCE_PERCENT
                else -> null
            }
        if (glovesOrDiaryPercent != null) {
            value = Math.floorDiv(value * glovesOrDiaryPercent, 100)
        }
        if (thievingCape) {
            value = Math.floorDiv(value * THIEVING_CAPE_PERCENT, 100)
        }
        return value
    }

    const val GLOVES_OF_SILENCE_PERCENT: Int = 105

    const val THIEVING_CAPE_PERCENT: Int = 110

    const val ARDOUGNE_DIARY_PERCENT: Int = 110

    // The shared glove counter stores failures accumulated, not charges remaining; zero means fresh.
    const val SILENT_GLOVES_CHARGE_VARBIT: String = "varbit.hunting_silent_gloves"

    const val SILENT_GLOVES_BREAK_THRESHOLD: Int = 62

    const val ROGUE_PIECE_DOUBLE_PERCENT: Int = 15

    const val ROGUE_FULL_SET_DOUBLE_PERCENT: Int = 100

    const val DODGY_NECKLACE_NEGATE_PERCENT: Int = 25

    const val DODGY_NECKLACE_CHARGES: Int = 10

    // Roll independently of the necklace; their failure protection stacks multiplicatively.
    const val SHADOW_VEIL_NEGATE_PERCENT: Int = 15

    const val COIN_POUCH_CAP_BASE: Int = 28

    const val COIN_POUCH_CAP_MEDIUM: Int = 56

    const val COIN_POUCH_CAP_HARD: Int = 84

    const val COIN_POUCH_CAP_ELITE: Int = 140

    const val TZHAAR_HUR_DAMAGE: Int = 4

    private const val MAX_LEVEL = 99
    private const val FIFTY_PERCENT_TARGET = 12446
}
