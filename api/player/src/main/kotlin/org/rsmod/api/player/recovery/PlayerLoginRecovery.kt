package org.rsmod.api.player.recovery

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public var Player.loginExitCoord: CoordGrid?
    get() {
        migrateLoginExitCoord()
        return if (loginExitPending) CoordGrid(loginExitPacked) else null
    }
    set(value) {
        loginExitPacked = value?.packed ?: 0
        loginExitPending = value != null
        loginExitMigrated = true
        if (attr.has(legacyLoginExitCoord)) attr.remove(legacyLoginExitCoord)
    }

public fun Player.takeLoginExitCoord(): CoordGrid? {
    val exit = loginExitCoord
    loginExitCoord = null
    return exit
}

private fun Player.migrateLoginExitCoord() {
    if (!loginExitMigrated) {
        if ("varp.player_login_exit_pending" !in vars && "varp.player_login_exit_coord" !in vars) {
            val packed = attr[legacyLoginExitCoord] as? Int
            if (packed != null) {
                loginExitPacked = packed
                loginExitPending = true
            }
        }
        loginExitMigrated = true
    }
    if (attr.has(legacyLoginExitCoord)) attr.remove(legacyLoginExitCoord)
}

private val legacyLoginExitCoord = AttributeKey<Any>(persistenceKey = "instance_exit_coord")
private var Player.loginExitPacked by intVarp("varp.player_login_exit_coord")
private var Player.loginExitPending by boolVarp("varp.player_login_exit_pending")
private var Player.loginExitMigrated by boolVarp("varp.player_login_exit_migrated")
