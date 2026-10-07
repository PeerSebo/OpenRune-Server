package org.rsmod.api.account.character

import org.rsmod.api.db.DatabaseConnection

/** Values must come from the frozen SQL snapshot; encoding runs on the writer after a failure. */
public fun CharacterDataStage.PreparedSave.withRecoveryData(
    name: String,
    data: Any?,
): CharacterDataStage.PreparedSave {
    val writer = this
    return object : CharacterDataStage.PreparedSave {
        override val recoveryData: Map<String, Any?> = mapOf(name to data)

        override fun save(connection: DatabaseConnection, characterId: Int) =
            writer.save(connection, characterId)
    }
}
