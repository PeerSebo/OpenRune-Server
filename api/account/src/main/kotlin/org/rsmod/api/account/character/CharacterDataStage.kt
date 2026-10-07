package org.rsmod.api.account.character

import com.fasterxml.jackson.databind.JsonNode
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

public class CharacterDataStage {
    public interface Segment

    /**
     * Register pipeline implementations with ExtendedModule.addSetBinding so login and save
     * discover them.
     */
    public interface Pipeline {
        public fun append(connection: DatabaseConnection, metadata: CharacterMetadataList)

        public val recoveryKey: String? get() = null
        public fun restoreSave(data: JsonNode): PreparedSave =
            error("Pipeline does not support journal recovery: ${javaClass.name}")

        public fun save(connection: DatabaseConnection, player: Player, characterId: Int)

        /** Override to capture immutable data before the database worker runs. */
        public fun prepareSave(player: Player): PreparedSave =
            PreparedSave { connection, characterId -> save(connection, player, characterId) }
    }

    public fun interface PreparedSave {
        public fun save(connection: DatabaseConnection, characterId: Int)

        public val recoveryData: Map<String, Any?> get() = emptyMap()
    }

    /** Runs during login after Player construction and before world registration. */
    public interface Applier<T : Segment> {
        /** Runs on the game thread; unrecoverable failures terminate login. */
        public fun apply(player: Player, data: T)
    }
}
