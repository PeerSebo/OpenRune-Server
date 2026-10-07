package org.rsmod.api.combat.formulas

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getOrNull

/**
 * Cache black-mask/Slayer-helm params are unassigned, so match item ids. Resolve lazily to
 * avoid a missing symbol permanently poisoning all combat calls.
 */
internal object SlayerHelmCombatChecks {
    private val logger = InlineLogger()

    private val BLACK_MASK_CHARGES: List<String> = listOf("") + (1..10).map { "_$it" }

    private val SLAYER_HELM_RECOLOURS: List<String> =
        listOf(
            "",
            "_black",
            "_green",
            "_red",
            "_purple",
            "_turquoise",
            "_hydra",
            "_twisted",
            "_jad",
            "_verzik",
            "_zuk",
            "_araxyte",
            "_hooded",
        )

    /**
     * Non-imbued masks and helmets. These give the melee boost only.
     *
     * Note `obj.harmless_black_mask` (8921) is the **plain** Black mask; the imbued one is
     * `obj.nzone_black_mask` (11784, "Black mask (i)"). The two families are easy to swap.
     */
    private val PLAIN_KEYS: List<String> =
        BLACK_MASK_CHARGES.map { "obj.harmless_black_mask$it" } +
            SLAYER_HELM_RECOLOURS.map { "obj.slayer_helm$it" } +
            listOf("obj.league_6_slayer_helm1", "obj.league_6_slayer_helm2")

    private val IMBUED_KEYS: List<String> =
        listOf("nzone", "sw", "pvpa").flatMap { family ->
            BLACK_MASK_CHARGES.map { "obj.${family}_black_mask$it" }
        } +
            listOf("", "sw_", "pvpa_").flatMap { family ->
                SLAYER_HELM_RECOLOURS.map { "obj.${family}slayer_helm_i$it" }
            } +
            listOf(
                "obj.league_6_slayer_helm1_i",
                "obj.league_6_slayer_helm2_i",
                "obj.sw_league_6_slayer_helm1_i",
                "obj.sw_league_6_slayer_helm2_i",
                "obj.pvpa_league_6_slayer_helm1_i",
                "obj.pvpa_league_6_slayer_helm2_i",
            )

    private val imbuedIds: Set<Int> by lazy { resolveObjIds(IMBUED_KEYS) }

    private val meleeBoostIds: Set<Int> by lazy { resolveObjIds(PLAIN_KEYS) + imbuedIds }

    fun providesMeleeTaskBoost(obj: InvObj?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj.id in meleeBoostIds) {
            return true
        }
        val type = getOrNull(obj) ?: return false
        return type.param(BaseParams.blackmask) != 0 ||
            type.param(BaseParams.blackmask_imbued) != 0 ||
            type.param(BaseParams.slayer_helm) != 0 ||
            type.param(BaseParams.slayer_helm_imbued) != 0
    }

    fun providesImbuedRangedMagicTaskBoost(obj: InvObj?): Boolean {
        if (obj == null) {
            return false
        }
        if (obj.id in imbuedIds) {
            return true
        }
        val type = getOrNull(obj) ?: return false
        return type.param(BaseParams.blackmask_imbued) != 0 ||
            type.param(BaseParams.slayer_helm_imbued) != 0
    }

    private fun resolveObjIds(keys: List<String>): Set<Int> {
        val ids = HashSet<Int>(keys.size * 2)
        val unresolved = ArrayList<String>()
        for (key in keys) {
            val id = runCatching { key.asRSCM(RSCMType.OBJ) }.getOrNull()
            if (id == null) {
                unresolved += key
            } else {
                ids += id
            }
        }
        if (unresolved.isNotEmpty()) {
            logger.warn {
                "Unresolved slayer helm/black mask obj symbols; the task boost will not apply to " +
                    "them: $unresolved"
            }
        }
        return ids
    }
}
