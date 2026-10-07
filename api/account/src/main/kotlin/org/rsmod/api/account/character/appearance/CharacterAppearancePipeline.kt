package org.rsmod.api.account.character.appearance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.parsers.json.Json
import org.rsmod.game.entity.Player

/**
 * Upsert after CharacterAccountRepository replaces character_attrs in the same transaction. An
 * appearance encode failure must not roll back inventory/stat saves.
 */
public class CharacterAppearancePipeline
@Inject
constructor(
    private val applier: CharacterAppearanceApplier,
    @Json private val objectMapper: ObjectMapper,
) : CharacterDataStage.Pipeline {
    private val logger = InlineLogger()

    override val recoveryKey: String = "appearanceJson"

    override fun restoreSave(data: JsonNode): CharacterDataStage.PreparedSave {
        require(data.isNull || data.isTextual)
        val json = data.takeUnless { it.isNull }?.textValue()
        if (json != null) {
            require(objectMapper.readTree(json).required("v").intValue() == CharacterAppearanceCodec.VERSION)
            checkNotNull(CharacterAppearanceCodec.decode(objectMapper, json))
        }
        return preparedJson(json)
    }

    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val characterId = metadata.characterId
        val json = selectJson(connection, characterId)
        val appearance = json?.let { decode(it, characterId) }
        metadata.add(applier, CharacterAppearanceData(appearance))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        prepareSave(player).save(connection, characterId)
    }

    override fun prepareSave(player: Player): CharacterDataStage.PreparedSave {
        val json = encode(player)
        return preparedJson(json)
    }

    private fun preparedJson(json: String?): CharacterDataStage.PreparedSave {
        return CharacterDataStage.PreparedSave { connection, characterId ->
            if (json != null) upsertJson(connection, characterId, json)
        }.withRecoveryData("appearanceJson", json)
    }

    private fun selectJson(connection: DatabaseConnection, characterId: Int): String? {
        val select =
            connection.prepareStatement(OpenRuneSql.text("game/appearance/select_appearance.sql"))
        return select.use { statement ->
            statement.setInt(1, characterId)
            statement.setString(2, PERSISTENCE_KEY)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    return@use null
                }
                resultSet.getString("value_json")
            }
        }
    }

    private fun upsertJson(connection: DatabaseConnection, characterId: Int, json: String) {
        val upsert =
            connection.prepareStatement(OpenRuneSql.text("game/appearance/upsert_appearance.sql"))
        upsert.use { statement ->
            statement.setInt(1, characterId)
            statement.setString(2, PERSISTENCE_KEY)
            statement.setString(3, json)
            statement.executeUpdate()
        }
    }

    /** Malformed appearance data falls back to defaults without preventing login. */
    private fun decode(json: String, characterId: Int): CharacterAppearanceDto? {
        val result = runCatching { CharacterAppearanceCodec.decode(objectMapper, json) }
        val appearance = result.getOrNull()
        if (appearance == null) {
            logger.error(result.exceptionOrNull()) {
                "Could not decode stored appearance. " +
                    "(characterId=$characterId, json=${json.take(MAX_LOGGED_JSON_LENGTH)})"
            }
        }
        return appearance
    }

    private fun encode(player: Player): String? {
        val result = runCatching {
            CharacterAppearanceCodec.encode(objectMapper, player.appearance)
        }
        val json = result.getOrNull()
        if (json == null) {
            logger.error(result.exceptionOrNull()) { "Could not encode appearance for: $player" }
        }
        return json
    }

    private companion object {
        /**
         * `character_attrs.attr` key for the appearance document. Shares the namespace with every
         * `AttributeKey.persistenceKey`, so it must stay unique against those.
         */
        private const val PERSISTENCE_KEY: String = "character_appearance"

        private const val MAX_LOGGED_JSON_LENGTH: Int = 8192
    }
}
