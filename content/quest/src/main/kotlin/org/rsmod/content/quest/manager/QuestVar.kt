package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

class QuestVar<T> private constructor(
    override val name: String,
    private val varp: String,
    private val default: T,
    private val lifetime: VarpLifetime,
    legacyName: String?,
    private val migratedVarp: String?,
    private val encode: (T) -> Int,
    private val decode: (Int) -> T,
    private val importValue: (Any?) -> T?,
) : QuestField<T> {
    private val legacy = legacyName?.let { AttributeKey<Any>(persistenceKey = it) }

    init {
        require((legacyName == null) == (migratedVarp == null))
        require(migratedVarp == null || (lifetime == VarpLifetime.Perm && migratedVarp != varp))
    }

    fun get(player: Player): T = getOrNull(player) ?: default

    override fun getOrNull(player: Player): T? {
        migrate(player)
        return if (varp in player.vars) decode(player.vars[varp]) else null
    }

    fun set(player: Player, value: T) {
        VarPlayerIntMapSetter.set(player, varp, encode(value))
        migratedVarp?.let { VarPlayerIntMapSetter.set(player, it, 1) }
        legacy?.let(player.attr::remove)
    }

    override fun clear(player: Player) = set(player, default)

    override fun migrate(player: Player) {
        val key = legacy ?: return
        val marker = checkNotNull(migratedVarp)
        if (player.vars[marker] == 0) {
            if (varp !in player.vars) {
                importValue(player.attr[key])?.let { VarPlayerIntMapSetter.set(player, varp, encode(it)) }
            }
            VarPlayerIntMapSetter.set(player, marker, 1)
        }
        player.attr.remove(key)
    }

    override fun assertResolves() {
        for (symbol in listOfNotNull(varp, migratedVarp)) {
            val type = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM())) {
                "Quest field '$name' requires a packed varp: $symbol"
            }
            check(type.scope == lifetime) { "Quest field '$symbol' must have lifetime $lifetime." }
            check(type.transmit == VarpTransmitLevel.Never) {
                "Quest side-state '$symbol' must remain server-only."
            }
        }
    }

    companion object {
        fun boolean(
            name: String,
            varp: String,
            legacyKey: String? = null,
            migratedVarp: String? = null,
            temp: Boolean = false,
        ): QuestVar<Boolean> = QuestVar(
            name, varp, false, if (temp) VarpLifetime.Temp else VarpLifetime.Perm,
            legacyKey, migratedVarp, { if (it) 1 else 0 }, { it != 0 }, { it as? Boolean },
        )

        fun integer(
            name: String,
            varp: String,
            default: Int = 0,
            legacyKey: String? = null,
            migratedVarp: String? = null,
            temp: Boolean = false,
            legacyValue: (Any?) -> Int? = { it as? Int },
        ): QuestVar<Int> = QuestVar(
            name, varp, default, if (temp) VarpLifetime.Temp else VarpLifetime.Perm,
            legacyKey, migratedVarp, { it }, { it }, legacyValue,
        )
    }
}
