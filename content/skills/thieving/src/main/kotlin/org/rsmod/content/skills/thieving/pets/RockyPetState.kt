package org.rsmod.content.skills.thieving.pets

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal object RockyPetState {
    private var Player.followingObj by intVarp("varp.rocky_following_obj")
    private var Player.migrated by boolVarp("varp.rocky_following_migrated")
    var Player.resyncRockyFollower by boolVarp("varp.rocky_resync_follower")
    private val legacyFollowing = AttributeKey<Int>("pets.rocky.following_obj")

    var Player.followingRocky: RockyPet?
        get() {
            migrate()
            return RockyPet.fromObj(followingObj)
        }
        set(value) {
            followingObj = value?.objId ?: 0
            migrated = true
            attr.remove(legacyFollowing)
        }

    fun Player.migrate() {
        if (!migrated) {
            if ("varp.rocky_following_obj" !in vars) {
                followingObj = attr[legacyFollowing] ?: 0
            }
            migrated = true
        }
        attr.remove(legacyFollowing)
    }
}
