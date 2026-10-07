package org.rsmod.content.areas.misc.barbarianvillage.configs

import org.rsmod.api.random.GameRandom

internal object StrongholdDoorRules {
    const val QuestionChanceNumerator: Int = 21

    const val QuestionChanceDenominator: Int = 66

    fun shouldAskQuestion(random: GameRandom, allRewardsClaimed: Boolean): Boolean {
        if (allRewardsClaimed) {
            return false
        }
        return random.of(QuestionChanceDenominator) < QuestionChanceNumerator
    }
}
