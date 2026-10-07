package org.rsmod.api.account.saver.journal

import java.util.UUID
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

public object SaveRevisionStore {
    public fun install(connection: DatabaseConnection) {
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext('openrune.save-revisions.schema'))")
            .use { it.execute() }
        connection.prepareStatement(SCHEMA).use { it.executeUpdate() }
        connection.prepareStatement(ATOMIC_SCHEMA).use { it.executeUpdate() }
    }

    public fun lock(connection: DatabaseConnection, characterId: Int): SaveRevision {
        selectLocked(connection, characterId)?.let { return it }
        connection.prepareStatement("""
            INSERT INTO public.character_save_revisions(character_id, generation, sequence)
            VALUES (?, ?, 0) ON CONFLICT (character_id) DO NOTHING
        """.trimIndent()).use {
            it.setInt(1, characterId)
            it.setString(2, UUID.randomUUID().toString())
            it.executeUpdate()
        }
        return checkNotNull(selectLocked(connection, characterId))
    }

    private fun selectLocked(connection: DatabaseConnection, characterId: Int): SaveRevision? =
        connection.prepareStatement("""
            SELECT generation, sequence FROM public.character_save_revisions
            WHERE character_id = ? FOR UPDATE
        """.trimIndent()).use {
            it.setInt(1, characterId)
            it.executeQuery().use { row ->
                if (row.next()) SaveRevision(row.getString(1), row.getLong(2)) else null
            }
        }

    public fun claim(connection: DatabaseConnection, player: Player): AccountSaveLease {
        val loaded = AccountSaveLease.get(player)
        check(!loaded.claimed) { "Save lease already claimed" }
        check(lock(connection, player.characterId) == loaded.revision) {
            "Character changed during login; retry login: ${player.characterId}"
        }
        val claimed = AccountSaveLease(SaveRevision(UUID.randomUUID().toString(), 0), true)
        update(connection, player.characterId, claimed.revision)
        return claimed
    }

    public fun update(connection: DatabaseConnection, characterId: Int, revision: SaveRevision) {
        connection.prepareStatement("""
            UPDATE public.character_save_revisions SET generation = ?, sequence = ?
            WHERE character_id = ?
        """.trimIndent()).use {
            it.setString(1, revision.generation)
            it.setLong(2, revision.sequence)
            it.setInt(3, characterId)
            check(it.executeUpdate() == 1)
        }
    }

    private val SCHEMA = checkNotNull(SaveRevisionStore::class.java.getResource("/account/save-revisions.sql"))
        .readText()
    private val ATOMIC_SCHEMA = checkNotNull(SaveRevisionStore::class.java.getResource("/account/atomic-saves.sql"))
        .readText()
}
