package org.rsmod.content.skills.thieving.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.front
import org.rsmod.api.player.hit.playDefendSound
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.thieving.configs.CoinPouches
import org.rsmod.content.skills.thieving.configs.PickpocketNpc
import org.rsmod.content.skills.thieving.configs.ThievingNpcs
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

internal object ThievingGear {
    private const val CHARGE_VAROBJ = "varobj.charges_16383"

    fun necklaceCharges(necklace: InvObj): Int {
        val varobj = checkNotNull(ServerCacheManager.getVarObj(CHARGE_VAROBJ.asRSCM(RSCMType.VAROBJ)))
        val stored = necklace.vars.getBits(varobj.bits)
        return if (stored == 0) ThievingRates.DODGY_NECKLACE_CHARGES else stored
    }

    // Floor each parameter bonus separately; the diary replaces gloves rather than stacking with them.
    fun ProtectedAccess.applyBonuses(base: Int, target: PickpocketNpc): Int =
        ThievingRates.applyPickpocketBonuses(
            base = base,
            glovesOfSilence = hasWorkingGlovesOfSilence(),
            thievingCape = ThievingNpcs.thievingCapes.any { it in worn },
            ardougneDiary = hasArdougneBonus(target),
        )

    private fun ProtectedAccess.hasArdougneBonus(target: PickpocketNpc): Boolean {
        if (vars["varbit.ardougne_diary_hard_complete"] == 1) {
            return true
        }
        return target.inArdougne && vars["varbit.ardougne_diary_medium_complete"] == 1
    }

    private fun ProtectedAccess.hasWorkingGlovesOfSilence(): Boolean {
        if (ThievingNpcs.GLOVES_OF_SILENCE !in worn) {
            return false
        }
        return vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT] <
            ThievingRates.SILENT_GLOVES_BREAK_THRESHOLD
    }

    fun ProtectedAccess.degradeGlovesOfSilence() {
        if (ThievingNpcs.GLOVES_OF_SILENCE !in worn) {
            return
        }
        val failures = vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT]
        if (failures >= ThievingRates.SILENT_GLOVES_BREAK_THRESHOLD) {
            return
        }
        val updated = failures + 1
        vars[ThievingRates.SILENT_GLOVES_CHARGE_VARBIT] = updated
        if (updated >= ThievingRates.SILENT_GLOVES_BREAK_THRESHOLD) {
            mes("Your gloves of silence have worn out. They will need repairing before they work again.")
        }
    }

    fun ProtectedAccess.negatesFailure(): Boolean {
        if (
            wearingDodgyNecklace() && random.of(100) < ThievingRates.DODGY_NECKLACE_NEGATE_PERCENT
        ) {
            consumeDodgyNecklaceCharge()
            return true
        }
        if (
            vars[ThievingNpcs.SHADOW_VEIL_VARBIT] == 1 &&
                random.of(100) < ThievingRates.SHADOW_VEIL_NEGATE_PERCENT
        ) {
            spam("Your shadow veil hides you from harm.")
            return true
        }
        return false
    }

    private fun ProtectedAccess.wearingDodgyNecklace(): Boolean =
        player.front.isType(ThievingNpcs.DODGY_NECKLACE)

    // Charges belong to each necklace's varobj bits. Zero means unused, so initialize it to ten.
    private fun ProtectedAccess.consumeDodgyNecklaceCharge() {
        val necklace = player.front ?: return
        val varobj =
            ServerCacheManager.getVarObj(CHARGE_VAROBJ.asRSCM(RSCMType.VAROBJ))
                ?: run {
                    spam("Your dodgy necklace protects you.")
                    return
                }

        val remaining = necklaceCharges(necklace)
        val left = remaining - 1

        if (left <= 0) {
            player.front = null
            mes("Your dodgy necklace protects you. It then crumbles to dust.")
            return
        }

        player.front = necklace.copy(vars = necklace.vars.withBits(varobj.bits, left))
        spam(
            "Your dodgy necklace protects you. It has $left charge${if (left == 1) "" else "s"} left."
        )
    }

    fun ProtectedAccess.applyTzHaarSuccessDamage(target: PickpocketNpc) {
        if (!target.alwaysDamagesWithoutIceGloves) {
            return
        }
        if (ThievingNpcs.ICE_GLOVES in worn) {
            return
        }
        val damage = ThievingRates.TZHAAR_HUR_DAMAGE
        takeInstantHit(type = HitType.Typeless, damage = damage)
        playDefendSound(damage)
    }

    fun ProtectedAccess.rollRogueDoubleLoot(): Boolean {
        val pieces = ThievingNpcs.rogueEquipment.count { it in worn }
        if (pieces == 0) {
            return false
        }
        val percent =
            if (pieces == ThievingNpcs.rogueEquipment.size) {
                ThievingRates.ROGUE_FULL_SET_DOUBLE_PERCENT
            } else {
                pieces * ThievingRates.ROGUE_PIECE_DOUBLE_PERCENT
            }
        return random.of(100) < percent
    }

    fun ProtectedAccess.coinPouchCap(): Int =
        when {
            vars["varbit.ardougne_diary_elite_complete"] == 1 -> ThievingRates.COIN_POUCH_CAP_ELITE
            vars["varbit.ardougne_diary_hard_complete"] == 1 -> ThievingRates.COIN_POUCH_CAP_HARD
            vars["varbit.ardougne_diary_medium_complete"] == 1 ->
                ThievingRates.COIN_POUCH_CAP_MEDIUM
            else -> ThievingRates.COIN_POUCH_CAP_BASE
        }

    fun ProtectedAccess.coinPouchCount(): Int =
        CoinPouches.all.sumOf { pouch -> invTotal(inv, pouch) }
}
