package org.rsmod.content.skills.runecrafting.gotr

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

object GotrImbueHook {
    private var Player.gotrIsPlaying by boolVarBit("varbit.gotr_is_playing")

    private val ELEMENTAL_RUNES =
        setOf("obj.airrune", "obj.waterrune", "obj.earthrune", "obj.firerune")

    private val CATALYTIC_RUNES =
        setOf(
            "obj.mindrune",
            "obj.chaosrune",
            "obj.deathrune",
            "obj.bloodrune",
            "obj.bodyrune",
            "obj.cosmicrune",
            "obj.naturerune",
            "obj.lawrune",
        )

    private val ELEMENTAL_COMBO_OUTPUTS =
        setOf(
            "obj.dustrune",
            "obj.mudrune",
            "obj.mistrune",
            "obj.smokerune",
            "obj.steamrune",
            "obj.lavarune",
        )

    private val TALISMAN_BY_RUNE =
        mapOf(
            "obj.airrune" to ("obj.gotr_portal_talisman_air" to "varbit.gotr_talisman_toggle_air"),
            "obj.waterrune" to
                ("obj.gotr_portal_talisman_water" to "varbit.gotr_talisman_toggle_water"),
            "obj.earthrune" to
                ("obj.gotr_portal_talisman_earth" to "varbit.gotr_talisman_toggle_earth"),
            "obj.firerune" to ("obj.gotr_portal_talisman_fire" to "varbit.gotr_talisman_toggle_fire"),
            "obj.mindrune" to ("obj.gotr_portal_talisman_mind" to "varbit.gotr_talisman_toggle_mind"),
            "obj.chaosrune" to
                ("obj.gotr_portal_talisman_chaos" to "varbit.gotr_talisman_toggle_chaos"),
            "obj.deathrune" to
                ("obj.gotr_portal_talisman_death" to "varbit.gotr_talisman_toggle_death"),
            "obj.bloodrune" to
                ("obj.gotr_portal_talisman_blood" to "varbit.gotr_talisman_toggle_blood"),
            "obj.bodyrune" to ("obj.gotr_portal_talisman_body" to "varbit.gotr_talisman_toggle_body"),
            "obj.cosmicrune" to
                ("obj.gotr_portal_talisman_cosmic" to "varbit.gotr_talisman_toggle_cosmic"),
            "obj.naturerune" to
                ("obj.gotr_portal_talisman_nature" to "varbit.gotr_talisman_toggle_nature"),
            "obj.lawrune" to ("obj.gotr_portal_talisman_law" to "varbit.gotr_talisman_toggle_law"),
        )

    private const val TALISMAN_CHANCE_DENOMINATOR = 250

    // Preview output slots after input removal and before committing resources or XP.
    fun ProtectedAccess.canFitGotrCraft(outputRune: String, guardianEssence: Int, removals: Map<String, Int>): Boolean {
        if (guardianEssence <= 0 || !player.gotrIsPlaying) return true
        val stone = stoneForRune(outputRune) ?: return true
        val fits = player.invTransaction(inv, autoCommit = false) {
            val inventory = select(inv)
            for ((item, count) in removals) if (count > 0) delete(inventory, item.asRSCM(), count)
            add(inventory, outputRune.asRSCM(), 1)
            add(inventory, stone.asRSCM(), guardianEssence)
        }.success
        if (!fits) mes("You need more inventory space to hold the runes and guardian stones.")
        return fits
    }

    private fun stoneForRune(outputRune: String): String? = when {
        outputRune in ELEMENTAL_RUNES -> "obj.gotr_guardian_stone_elemental"
        outputRune in CATALYTIC_RUNES -> "obj.gotr_guardian_stone_catalytic"
        outputRune in ELEMENTAL_COMBO_OUTPUTS -> "obj.gotr_guardian_stone_polyelemental"
        outputRune == "obj.aetherrune" -> "obj.gotr_guardian_stone_polycatalytic"
        else -> null
    }

    fun cellTier(outputRune: String): Int? = when (outputRune) {
        "obj.airrune", "obj.mindrune", "obj.bodyrune" -> 1
        "obj.waterrune", "obj.cosmicrune", "obj.chaosrune" -> 2
        "obj.earthrune", "obj.naturerune", "obj.lawrune" -> 3
        "obj.firerune", "obj.deathrune", "obj.bloodrune" -> 4
        else -> null
    }

    fun ProtectedAccess.canChargeGotrCell(outputRune: String): Boolean =
        player.gotrIsPlaying && vars["varbit.gotr_player_has_finished_game"] == 0 &&
            cellTier(outputRune) != null && inv.contains("obj.gotr_cell_uncharged") &&
            (1..4).none { inv.contains("obj.gotr_cell_tier$it") }

    fun ProtectedAccess.chargeGotrCell(outputRune: String): Boolean {
        if (!canChargeGotrCell(outputRune)) return false
        val tier = checkNotNull(cellTier(outputRune))
        if (invDel(inv, "obj.gotr_cell_uncharged", 1).failure) return false
        if (invAdd(inv, "obj.gotr_cell_tier$tier", 1).failure) {
            check(invAdd(inv, "obj.gotr_cell_uncharged", 1).success)
            mes("You don't have enough inventory space to hold a charged cell.")
            return false
        }
        val amount = listOf("small", "moderate", "high", "immense")[tier - 1]
        mes("Your cell absorbs a $amount amount of excess energy.")
        return true
    }

    fun ProtectedAccess.grantGotrStonesForRune(outputRune: String, essenceConsumed: Int) {
        if (essenceConsumed <= 0 || !player.gotrIsPlaying) return
        val stone =
            when {
                outputRune in ELEMENTAL_RUNES -> "obj.gotr_guardian_stone_elemental"
                outputRune in CATALYTIC_RUNES -> "obj.gotr_guardian_stone_catalytic"
                else -> return
            }
        invAdd(inv, stone, essenceConsumed)
        rollGotrTalisman(outputRune, essenceConsumed)
    }

    fun ProtectedAccess.grantGotrStonesForCombo(outputRune: String, essenceConsumed: Int) {
        if (essenceConsumed <= 0 || !player.gotrIsPlaying) return
        if (outputRune !in ELEMENTAL_COMBO_OUTPUTS) return
        invAdd(inv, "obj.gotr_guardian_stone_polyelemental", essenceConsumed)
    }

    fun ProtectedAccess.grantGotrStonesForAether(essenceConsumed: Int) {
        if (essenceConsumed <= 0 || !player.gotrIsPlaying) return
        invAdd(inv, "obj.gotr_guardian_stone_polycatalytic", essenceConsumed)
    }

    private fun ProtectedAccess.rollGotrTalisman(outputRune: String, essenceConsumed: Int) {
        val (talisman, toggleVarbit) = TALISMAN_BY_RUNE[outputRune] ?: return
        if (vars[toggleVarbit] == 1) return
        // Roll per guardian essence until one talisman is obtained.
        if ((1..essenceConsumed).any { random.of(TALISMAN_CHANCE_DENOMINATOR) == 0 }) {
            invAdd(inv, talisman, 1)
        }
    }
}
