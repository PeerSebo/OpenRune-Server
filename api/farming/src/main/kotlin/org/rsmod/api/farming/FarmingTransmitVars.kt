package org.rsmod.api.farming

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/**
 * Shared render varbits must reflect the currently visible patch or bin, not all owned patches
 * at once.
 */
@Singleton
public class FarmingTransmitVars @Inject constructor() {
    private var Player.slotA by intVarBit("varbit.farming_transmit_a")
    private var Player.slotB by intVarBit("varbit.farming_transmit_b")
    private var Player.slotC by intVarBit("varbit.farming_transmit_c")
    private var Player.slotD by intVarBit("varbit.farming_transmit_d")
    private var Player.slotE by intVarBit("varbit.farming_transmit_e")
    private var Player.slotF by intVarBit("varbit.farming_transmit_f")
    private var Player.slotG by intVarBit("varbit.farming_transmit_g")
    private var Player.slotH by intVarBit("varbit.farming_transmit_h")
    private var Player.slotI by intVarBit("varbit.farming_transmit_i")
    private var Player.slotJ by intVarBit("varbit.farming_transmit_j")
    private var Player.slotK by intVarBit("varbit.farming_transmit_k")
    private var Player.slotL by intVarBit("varbit.farming_transmit_l")
    private var Player.slotM by intVarBit("varbit.farming_transmit_m")
    private var Player.slotN by intVarBit("varbit.farming_transmit_n")
    private var Player.slotO by intVarBit("varbit.farming_transmit_o")
    private var Player.slotP by intVarBit("varbit.farming_transmit_p")

    public fun get(player: Player, slot: FarmingTransmitSlot): Int =
        when (slot) {
            FarmingTransmitSlot.A -> player.slotA
            FarmingTransmitSlot.B -> player.slotB
            FarmingTransmitSlot.C -> player.slotC
            FarmingTransmitSlot.D -> player.slotD
            FarmingTransmitSlot.E -> player.slotE
            FarmingTransmitSlot.F -> player.slotF
            FarmingTransmitSlot.G -> player.slotG
            FarmingTransmitSlot.H -> player.slotH
            FarmingTransmitSlot.I -> player.slotI
            FarmingTransmitSlot.J -> player.slotJ
            FarmingTransmitSlot.K -> player.slotK
            FarmingTransmitSlot.L -> player.slotL
            FarmingTransmitSlot.M -> player.slotM
            FarmingTransmitSlot.N -> player.slotN
            FarmingTransmitSlot.O -> player.slotO
            FarmingTransmitSlot.P -> player.slotP
        }

    public fun set(player: Player, slot: FarmingTransmitSlot, value: Int) {
        when (slot) {
            FarmingTransmitSlot.A -> player.slotA = value
            FarmingTransmitSlot.B -> player.slotB = value
            FarmingTransmitSlot.C -> player.slotC = value
            FarmingTransmitSlot.D -> player.slotD = value
            FarmingTransmitSlot.E -> player.slotE = value
            FarmingTransmitSlot.F -> player.slotF = value
            FarmingTransmitSlot.G -> player.slotG = value
            FarmingTransmitSlot.H -> player.slotH = value
            FarmingTransmitSlot.I -> player.slotI = value
            FarmingTransmitSlot.J -> player.slotJ = value
            FarmingTransmitSlot.K -> player.slotK = value
            FarmingTransmitSlot.L -> player.slotL = value
            FarmingTransmitSlot.M -> player.slotM = value
            FarmingTransmitSlot.N -> player.slotN = value
            FarmingTransmitSlot.O -> player.slotO = value
            FarmingTransmitSlot.P -> player.slotP = value
        }
    }
}
