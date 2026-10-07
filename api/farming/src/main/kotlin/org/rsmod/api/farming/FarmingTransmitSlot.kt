package org.rsmod.api.farming

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * The sixteen render slots are shared by distant patches; they are not persistent patch
 * identifiers.
 */
public enum class FarmingTransmitSlot(public val varbit: String) {
    A("varbit.farming_transmit_a"),
    B("varbit.farming_transmit_b"),
    C("varbit.farming_transmit_c"),
    D("varbit.farming_transmit_d"),
    E("varbit.farming_transmit_e"),
    F("varbit.farming_transmit_f"),
    G("varbit.farming_transmit_g"),
    H("varbit.farming_transmit_h"),
    I("varbit.farming_transmit_i"),
    J("varbit.farming_transmit_j"),
    K("varbit.farming_transmit_k"),
    L("varbit.farming_transmit_l"),
    M("varbit.farming_transmit_m"),
    N("varbit.farming_transmit_n"),
    O("varbit.farming_transmit_o"),
    P("varbit.farming_transmit_p");

    public val varbitId: Int by lazy { varbit.asRSCM(RSCMType.VARBIT) }

    public companion object {
        private val byVarbitId: Map<Int, FarmingTransmitSlot> by lazy {
            entries.associateBy(FarmingTransmitSlot::varbitId)
        }

        public fun ofVarbitId(varbitId: Int): FarmingTransmitSlot? = byVarbitId[varbitId]
    }
}
