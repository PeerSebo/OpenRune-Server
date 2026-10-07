package org.rsmod.content.other.pets.cats

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CatPetStateTest {
    @Test
    fun `old saves import all fields including signed wool attention without changing other progress`() {
        val player = Player()
        val pet = CatPet(CatStage.KITTEN, CatColour.HELL)
        player.attr.putAllFromPersistence(legacy(pet.objId) + mapOf("quest.progress" to 4))

        CatPetState.migrate(player)

        assertEquals(pet.objId, player.catFollowingObj)
        assertEquals(1200, player.catGrowth)
        assertEquals(400, player.catHunger)
        assertEquals(-2600, player.catAttention)
        assertEquals(1, player.vars[MIGRATED])
        assertEquals(mapOf("quest.progress" to 4), player.attr.toPersistentMap())
    }

    @Test
    fun `each explicit modern field including zero wins while other fields import`() {
        for (field in FIELDS) {
            for (value in listOf(0, 12)) {
                val player = Player()
                player.attr.putAllFromPersistence(legacy(123))
                VarPlayerIntMapSetter.set(player, "varp.pets_cat_$field", value)

                CatPetState.migrate(player)

                for ((name, old) in legacy(123)) {
                    val suffix = name.removePrefix("pets.cat.")
                    assertEquals(
                        if (suffix == field) value else old,
                        player.vars["varp.pets_cat_$suffix"],
                        "$field=$value, imported $suffix",
                    )
                }
                assertTrue(player.attr.toPersistentMap().isEmpty())
            }
        }
    }

    @Test
    fun `migration marker rejects stale attributes even when modern fields are absent`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, MIGRATED, 1)
        player.attr.putAllFromPersistence(legacy(123))

        CatPetState.migrate(player)

        FIELDS.forEach { assertEquals(0, player.vars["varp.pets_cat_$it"]) }
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `partial and empty legacy saves establish migration without inventing a following cat`() {
        for (saved in listOf(emptyMap(), mapOf("pets.cat.attention" to -17))) {
            val player = Player().apply { attr.putAllFromPersistence(saved) }

            CatPetState.migrate(player)

            assertEquals(0, player.catFollowingObj)
            assertEquals(0, player.catGrowth)
            assertEquals(0, player.catHunger)
            assertEquals(saved["pets.cat.attention"] ?: 0, player.catAttention)
            assertEquals(1, player.vars[MIGRATED])
        }
    }

    @Test
    fun `clearing before migration and save reload cannot revive an old follower or needs`() {
        val original = Player().apply {
            attr.putAllFromPersistence(legacy(123) + mapOf("unrelated" to 7))
            catChasing = true
            catResyncFollower = true
        }

        CatPetState.clear(original)
        val saved = original.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }
        val restored = Player().apply {
            vars.backing.putAll(saved)
            attr.putAllFromPersistence(original.attr.toPersistentMap() + legacy(456))
        }
        CatPetState.migrate(restored)
        CatPetState.migrate(restored)

        FIELDS.forEach { assertEquals(0, restored.vars["varp.pets_cat_$it"]) }
        assertEquals(mapOf("unrelated" to 7), restored.attr.toPersistentMap())
        assertFalse(original.catChasing)
        assertFalse(original.catResyncFollower)
        assertFalse(restored.catChasing)
        assertFalse(restored.catResyncFollower)
    }

    @Test
    fun `migration imports once and rejects later stale counter updates`() {
        val player = Player().apply { attr.putAllFromPersistence(legacy(123)) }
        CatPetState.migrate(player)
        player.catGrowth = 23
        player.catHunger = 0
        player.catAttention = -2500
        player.attr.putAllFromPersistence(legacy(456))

        CatPetState.migrate(player)

        assertEquals(123, player.catFollowingObj)
        assertEquals(23, player.catGrowth)
        assertEquals(0, player.catHunger)
        assertEquals(-2500, player.catAttention)
        assertTrue(player.attr.toPersistentMap().isEmpty())
    }

    @Test
    fun `counter state persists while chasing and native follower resync remain private session state`() {
        val scopes = FIELDS.associateWith { VarpLifetime.Perm } + mapOf(
            "migrated" to VarpLifetime.Perm,
            "chasing" to VarpLifetime.Temp,
            "resync_follower" to VarpLifetime.Temp,
        )
        for ((name, scope) in scopes) {
            val symbol = "varp.pets_cat_$name"
            val definition = checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM()))
            assertEquals(scope, definition.scope, symbol)
            assertEquals(VarpTransmitLevel.Never, definition.transmit, symbol)
        }
    }

    companion object {
        private const val MIGRATED = "varp.pets_cat_migrated"
        private val FIELDS = listOf("following_obj", "growth", "hunger", "attention")

        private fun legacy(obj: Int) = mapOf(
            "pets.cat.following_obj" to obj,
            "pets.cat.growth" to 1200,
            "pets.cat.hunger" to 400,
            "pets.cat.attention" to -2600,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
