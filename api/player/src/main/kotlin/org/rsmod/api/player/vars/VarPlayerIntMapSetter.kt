package org.rsmod.api.player.vars

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.VarBitType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import org.rsmod.api.player.output.VarpSync
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints
import org.rsmod.game.vars.VarPlayerIntMap
import org.rsmod.utils.bits.withBits

/** Dynamic var types still require synchronized setters; do not mutate VarPlayerIntMap directly. */
public object VarPlayerIntMapSetter {

    public fun clear(player: Player, varp: String) {
        val type = checkNotNull(ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))) {
            "Unable to find varp: $varp"
        }
        if (type !in player.vars) return
        val previous = player.vars.backing.remove(type.id)
        if (player.processedMapClock <= 0) return
        if (type.scope == VarpLifetime.Perm) {
            PlayerPersistenceHints.notify(player)
        }
        if (type.transmit.always || type.transmit.onDiff && previous != 0) {
            VarpSync.writeVarp(player, type, 0)
        }
    }

    public fun set(player: Player, varp: String, value: Int) {
        if (varp.startsWith("varp.")) {
            set(player, ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))!!, value)
        } else {
            set(player, ServerCacheManager.getVarbit(varp.asRSCM(RSCMType.VARBIT))!!, value)
        }
    }

    public fun set(player: Player, varp: VarpServerType, value: Int) {
        val existed = player.vars.backing.containsKey(varp.id)
        val previous = player.vars.backing[varp.id]

        player.vars.backing[varp.id] = value

        val engineLoggedIn = player.processedMapClock > 0
        if (engineLoggedIn && varp.scope == VarpLifetime.Perm && (!existed || previous != value)) {
            PlayerPersistenceHints.notify(player)
        }

        if (!engineLoggedIn) {
            return
        }

        val transmit = varp.transmit
        if (transmit.always) {
            VarpSync.writeVarp(player, varp, value)
        } else if (transmit.onDiff && previous != value) {
            VarpSync.writeVarp(player, varp, value)
        }
    }

    public fun toggle(player: Player, varp: String) {
        if (varp.startsWith("varp.")) {
            toggle(player, ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))!!)
        } else {
            toggle(player, ServerCacheManager.getVarbit(varp.asRSCM(RSCMType.VARBIT))!!)
        }
    }

    public fun toggle(player: Player, varp: VarpServerType) {
        val current = player.vars[varp]
        set(player, varp, if (current == 0) 1 else 0)
    }

    public fun toggle(player: Player, varbit: VarBitType) {
        val current = player.vars[varbit]
        set(player, varbit, if (current == 0) 1 else 0)
    }

    public fun set(player: Player, varbit: VarBitType, value: Int) {
        VarPlayerIntMap.assertVarBitBounds(varbit, value)
        val mappedValue = player.vars[varbit.baseVar]
        val packedValue = mappedValue.withBits(varbit.bits, value)
        set(player, varbit.baseVar, packedValue)
    }

    public fun toggleBit(player: Player, varbit: VarBitType, bit: Int) {
        val current = player.vars[varbit]
        set(player, varbit, current xor (1 shl bit))
    }
}
