package org.rsmod.content.skills.sailing

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal object SailingSessionState {
    private var Player.helmWorldPlusOne by intVarp("varp.sailing_helm_interaction_world")
    private var Player.returnPacked by intVarp("varp.sailing_shipyard_return_coord")
    private var Player.returnSaved by boolVarp("varp.sailing_shipyard_return_saved")
    private var Player.returnMigrated by boolVarp("varp.sailing_shipyard_return_migrated")
    private val legacyReturn = AttributeKey<Int>("sailing_shipyard_return_coord")

    var Player.helmInteractionWorld: Int?
        get() = helmWorldPlusOne.takeIf { it > 0 }?.minus(1)
        set(value) {
            require(value == null || value in 0 until Int.MAX_VALUE)
            helmWorldPlusOne = value?.plus(1) ?: 0
        }

    var Player.shipyardReturn: CoordGrid?
        get() {
            migrateShipyardReturn()
            return if (returnSaved) CoordGrid(returnPacked) else null
        }
        set(value) {
            returnPacked = value?.packed ?: 0
            returnSaved = value != null
            returnMigrated = true
            attr.remove(legacyReturn)
        }

    fun Player.migrateShipyardReturn() {
        if (!returnMigrated) {
            if ("varp.sailing_shipyard_return_saved" !in vars) {
                val legacy = attr[legacyReturn]
                if (legacy != null && "varp.sailing_shipyard_return_coord" !in vars) {
                    returnPacked = legacy
                }
                returnSaved = "varp.sailing_shipyard_return_coord" in vars
            }
            returnMigrated = true
        }
        attr.remove(legacyReturn)
    }
}
