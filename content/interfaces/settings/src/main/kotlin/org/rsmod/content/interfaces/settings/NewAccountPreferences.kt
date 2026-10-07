package org.rsmod.content.interfaces.settings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import java.io.Reader
import java.util.Properties
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.vars.VarPlayerIntMap

internal class NewAccountPreferences private constructor(
    private val preferences: List<(Player) -> Unit>,
) {
    fun apply(player: Player) {
        if (player.vars["varbit.new_player_account"] != 1) return
        preferences.forEach { it(player) }
    }

    companion object {
        fun load(): NewAccountPreferences {
            val resource = checkNotNull(
                NewAccountPreferences::class.java.getResourceAsStream(
                    "/settings/new-account-preferences.properties"
                )
            ) { "Missing new-account preference profile" }
            return resource.bufferedReader(Charsets.UTF_8).use { load(it) }
        }

        internal fun load(reader: Reader): NewAccountPreferences {
            val properties = object : Properties() {
                override fun put(key: Any, value: Any): Any? {
                    require(!containsKey(key)) { "Duplicate new-account preference: $key" }
                    return super.put(key, value)
                }
            }
            properties.load(reader)
            require(properties.isNotEmpty()) { "Empty new-account preference profile" }
            val preferences = properties.stringPropertyNames().sorted().map { symbol ->
                val value = requireNotNull(properties.getProperty(symbol).toIntOrNull()) {
                    "Invalid new-account preference value: $symbol"
                }
                resolve(symbol, value)
            }
            return NewAccountPreferences(preferences)
        }

        private fun resolve(symbol: String, value: Int): (Player) -> Unit {
            if (symbol.startsWith("varp.")) {
                val varp = requireNotNull(ServerCacheManager.getVarp(symbol.asRSCM(RSCMType.VARP))) {
                    "Unknown new-account preference: $symbol"
                }
                require(varp.scope == VarpLifetime.Perm) {
                    "New-account preference must be permanent: $symbol"
                }
                return { player -> VarPlayerIntMapSetter.set(player, varp, value) }
            }
            require(symbol.startsWith("varbit.")) { "Invalid new-account preference: $symbol" }
            val varbit = requireNotNull(ServerCacheManager.getVarbit(symbol.asRSCM(RSCMType.VARBIT))) {
                "Unknown new-account preference: $symbol"
            }
            require(varbit.baseVar.scope == VarpLifetime.Perm) {
                "New-account preference must be permanent: $symbol"
            }
            VarPlayerIntMap.assertVarBitBounds(varbit, value)
            return { player -> VarPlayerIntMapSetter.set(player, varbit, value) }
        }
    }
}
