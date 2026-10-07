package org.rsmod.content.skills.hunter

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

private var Player.savedFalconryInvested by boolVarp("varp.hunter_falconry_invested")
private var Player.savedHerbiboarCount by intVarp("varp.hunter_herbiboar_count")
private var Player.savedPursuitRingUsed by intVarp("varp.hunter_pursuit_ring_used")
private var Player.hunterPlayerStateMigrated by boolVarp("varp.hunter_player_state_migrated")

internal var Player.activeMagicBoxCount by intVarp("varp.hunter_magic_box_count")
internal var Player.activePitfallCount by intVarp("varp.hunter_pitfall_count")

internal var Player.falconryInvested: Boolean
    get() {
        migrateHunterPlayerState()
        return savedFalconryInvested
    }
    set(value) {
        migrateHunterPlayerState()
        savedFalconryInvested = value
    }

internal var Player.herbiboarCount: Int
    get() {
        migrateHunterPlayerState()
        return savedHerbiboarCount
    }
    set(value) {
        migrateHunterPlayerState()
        savedHerbiboarCount = value
    }

internal var Player.pursuitRingUsed: Int
    get() {
        migrateHunterPlayerState()
        return savedPursuitRingUsed
    }
    set(value) {
        migrateHunterPlayerState()
        savedPursuitRingUsed = value
    }

internal fun Player.migrateHunterPlayerState() {
    val shouldImport = !hunterPlayerStateMigrated
    val investment = AttributeKey<Boolean>("hunter_falconry_invested")
    if (shouldImport && "varp.hunter_falconry_invested" !in vars) {
        savedFalconryInvested = attr[investment] == true
    }
    attr.remove(investment)
    for (name in listOf("hunter_herbiboar_count", "hunter_pursuit_ring_used")) {
        val key = AttributeKey<Int>(name)
        if (shouldImport && "varp.$name" !in vars) {
            VarPlayerIntMapSetter.set(this, "varp.$name", attr[key] ?: 0)
        }
        attr.remove(key)
    }
    hunterPlayerStateMigrated = true
}
