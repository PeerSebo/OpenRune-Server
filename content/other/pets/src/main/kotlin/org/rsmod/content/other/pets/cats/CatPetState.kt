package org.rsmod.content.other.pets.cats

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.catFollowingObj by intVarp("varp.pets_cat_following_obj")
internal var Player.catGrowth by intVarp("varp.pets_cat_growth")
internal var Player.catHunger by intVarp("varp.pets_cat_hunger")
internal var Player.catAttention by intVarp("varp.pets_cat_attention")
internal var Player.catChasing by boolVarp("varp.pets_cat_chasing")
internal var Player.catResyncFollower by boolVarp("varp.pets_cat_resync_follower")
private var Player.catStateMigrated by boolVarp("varp.pets_cat_migrated")

internal object CatPetState {
    private val legacyFields =
        listOf("following_obj", "growth", "hunger", "attention").associate { name ->
            AttributeKey<Int>("pets.cat.$name") to "varp.pets_cat_$name"
        }

    fun migrate(player: Player) {
        if (!player.catStateMigrated) {
            for ((legacy, varp) in legacyFields) {
                val value = player.attr[legacy]
                if (value != null && varp !in player.vars) {
                    VarPlayerIntMapSetter.set(player, varp, value)
                }
            }
            player.catStateMigrated = true
        }
        discardLegacy(player)
    }

    fun clear(player: Player) {
        player.catFollowingObj = 0
        resetCounters(player)
        clearSession(player)
        player.catStateMigrated = true
        discardLegacy(player)
    }

    fun resetCounters(player: Player) {
        player.catGrowth = 0
        player.catHunger = 0
        player.catAttention = 0
    }

    fun clearSession(player: Player) {
        player.catChasing = false
        player.catResyncFollower = false
    }

    private fun discardLegacy(player: Player) {
        for (legacy in legacyFields.keys) {
            if (player.attr.has(legacy)) player.attr.remove(legacy)
        }
    }
}
