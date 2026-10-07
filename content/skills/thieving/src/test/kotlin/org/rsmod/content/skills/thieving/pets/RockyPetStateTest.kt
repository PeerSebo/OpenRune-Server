package org.rsmod.content.skills.thieving.pets

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.thieving.pets.RockyPetState.followingRocky
import org.rsmod.content.skills.thieving.pets.RockyPetState.resyncRockyFollower
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class RockyPetStateTest {
    @Test
    fun `every legacy Rocky form imports and survives reconnect while runtime resync does not`() {
        for (pet in RockyPet.entries) {
            val player = Player()
            player.attr.putAllFromPersistence(mapOf(Legacy to pet.objId, "other.pet" to 8))
            player.resyncRockyFollower = true
            assertEquals(pet, player.followingRocky)
            assertEquals(mapOf("other.pet" to 8), player.attr.toPersistentMap())
            val restored = reload(player)
            assertEquals(pet, restored.followingRocky)
            assertFalse(restored.resyncRockyFollower)
        }
    }

    @Test
    fun `released pet cannot be resurrected from legacy data before or after reconnect`() {
        val player = Player()
        player.followingRocky = RockyPet.RACCOON
        player.followingRocky = null
        player.attr.putAllFromPersistence(mapOf(Legacy to RockyPet.TANUKI.objId))
        assertNull(player.followingRocky)
        val restored = reload(player)
        restored.attr.putAllFromPersistence(mapOf(Legacy to RockyPet.TANUKI.objId))
        assertNull(restored.followingRocky)
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `explicit zero and current forms win over older saved forms before migration`() {
        for (obj in listOf(0, RockyPet.RED_PANDA.objId)) {
            val player = Player()
            VarPlayerIntMapSetter.set(player, "varp.rocky_following_obj", obj)
            player.attr.putAllFromPersistence(mapOf(Legacy to RockyPet.TANUKI.objId))
            assertEquals(RockyPet.fromObj(obj), player.followingRocky)
        }
    }

    @Test
    fun `unknown legacy forms stay unfollowed and do not replace other saved pet state`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(Legacy to -1, "pets.cat.following_obj" to 123))
        assertNull(player.followingRocky)
        assertEquals(mapOf("pets.cat.following_obj" to 123), player.attr.toPersistentMap())
    }

    @Test
    fun `cache keeps Rocky following permanent and resync temporary without client transmission`() {
        for ((name, scope) in listOf(
            "varp.rocky_following_obj" to VarpLifetime.Perm,
            "varp.rocky_following_migrated" to VarpLifetime.Perm,
            "varp.rocky_resync_follower" to VarpLifetime.Temp,
        )) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(scope, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    private fun reload(player: Player): Player = Player().also { restored ->
        for ((id, value) in player.vars) {
            if (ServerCacheManager.getVarp(id)?.scope == VarpLifetime.Perm) restored.vars.backing[id] = value
        }
        restored.attr.putAllFromPersistence(player.attr.toPersistentMap())
    }

    companion object {
        private const val Legacy = "pets.rocky.following_obj"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
