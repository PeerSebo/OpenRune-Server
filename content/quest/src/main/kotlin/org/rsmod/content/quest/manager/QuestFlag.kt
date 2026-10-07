package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class QuestFlag internal constructor(
    override val name: String,
    questKey: String,
    private val varbit: String,
    private val migratedVarbit: String,
) : QuestField<Boolean> {
    private val legacyKey = AttributeKey<Boolean>(persistenceKey = "quest.$questKey.$name")

    init {
        require(varbit != migratedVarbit) { "Quest flag and migration marker must use different bits." }
    }

    fun get(player: Player): Boolean {
        migrate(player)
        return player.vars[varbit] != 0
    }

    override fun getOrNull(player: Player): Boolean = get(player)

    fun set(player: Player, value: Boolean) {
        VarPlayerIntMapSetter.set(player, varbit, if (value) 1 else 0)
        VarPlayerIntMapSetter.set(player, migratedVarbit, 1)
        player.attr.remove(legacyKey)
    }

    override fun clear(player: Player) = set(player, false)

    override fun migrate(player: Player) {
        if (player.vars[migratedVarbit] == 0) {
            if (player.attr[legacyKey] == true && player.vars[varbit] == 0) {
                VarPlayerIntMapSetter.set(player, varbit, 1)
            }
            VarPlayerIntMapSetter.set(player, migratedVarbit, 1)
        }
        if (player.attr.has(legacyKey)) player.attr.remove(legacyKey)
    }

    override fun assertResolves() {
        for (symbol in listOf(varbit, migratedVarbit)) {
            val type = checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM(RSCMType.VARBIT))) {
                "Quest flag '$name' requires a packed varbit: $symbol"
            }
            check(type.startBit == type.endBit) { "Quest flag '$symbol' must occupy one bit." }
            check(type.baseVar.scope == VarpLifetime.Perm) { "Quest flag '$symbol' must persist." }
            check(!type.baseVar.transmit.always && !type.baseVar.transmit.onDiff) {
                "Quest side-state '$symbol' must remain server-only."
            }
        }
    }
}
