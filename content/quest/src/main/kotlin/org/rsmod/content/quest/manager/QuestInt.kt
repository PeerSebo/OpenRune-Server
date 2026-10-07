package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class QuestInt internal constructor(
    override val name: String,
    questKey: String,
    private val varp: String,
    private val migratedVarbit: String,
    private val default: Int,
) : QuestField<Int> {
    private val legacyKey = AttributeKey<Int>(persistenceKey = "quest.$questKey.$name")

    fun get(player: Player): Int {
        migrate(player)
        return player.vars[varp]
    }

    override fun getOrNull(player: Player): Int = get(player)

    fun set(player: Player, value: Int) {
        VarPlayerIntMapSetter.set(player, varp, value)
        VarPlayerIntMapSetter.set(player, migratedVarbit, 1)
        player.attr.remove(legacyKey)
    }

    override fun clear(player: Player) = set(player, default)

    override fun migrate(player: Player) {
        if (player.vars[migratedVarbit] == 0) {
            if (varp !in player.vars) {
                VarPlayerIntMapSetter.set(player, varp, player.attr[legacyKey] ?: default)
            }
            VarPlayerIntMapSetter.set(player, migratedVarbit, 1)
        }
        if (player.attr.has(legacyKey)) player.attr.remove(legacyKey)
    }

    override fun assertResolves() {
        val valueType = checkNotNull(ServerCacheManager.getVarp(varp.asRSCM(RSCMType.VARP))) {
            "Quest integer '$name' requires a packed varp: $varp"
        }
        val marker = checkNotNull(ServerCacheManager.getVarbit(migratedVarbit.asRSCM(RSCMType.VARBIT))) {
            "Quest integer '$name' requires a packed migration marker: $migratedVarbit"
        }
        check(marker.startBit == marker.endBit) { "Quest integer '$name' marker must occupy one bit." }
        check(marker.baseVar.id != valueType.id) {
            "Quest integer '$name' cannot store its migration marker inside its value."
        }
        for (type in listOf(valueType, marker.baseVar)) {
            check(type.scope == VarpLifetime.Perm) { "Quest integer '$name' must persist." }
            check(!type.transmit.always && !type.transmit.onDiff) {
                "Quest side-state '$name' must remain server-only."
            }
        }
    }
}
