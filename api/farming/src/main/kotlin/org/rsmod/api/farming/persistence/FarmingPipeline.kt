package org.rsmod.api.farming.persistence

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.michaelbull.logging.InlineLogger
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.farming.FarmingAttributes
import org.rsmod.api.farming.FarmingBins
import org.rsmod.api.farming.FarmingPatches
import org.rsmod.api.parsers.json.Json
import org.rsmod.game.entity.Player

/**
 * Patches and bins share one character_attrs row. Upsert after the account repository replaces
 * attributes, and never overwrite one half merely because the other is empty.
 */
public class FarmingPipeline
@Inject
constructor(private val applier: FarmingApplier, @Json private val objectMapper: ObjectMapper) :
    CharacterDataStage.Pipeline {
    private val logger = InlineLogger()

    override val recoveryKey: String = "farmingJson"

    override fun restoreSave(data: JsonNode): CharacterDataStage.PreparedSave {
        require(data.isNull || data.isTextual)
        val json = data.takeUnless { it.isNull }?.textValue()
        if (json != null) {
            require(objectMapper.readTree(json).required("v").intValue() == FarmingCodec.VERSION)
            checkNotNull(FarmingCodec.decode(objectMapper, json))
        }
        return preparedJson(json)
    }

    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val characterId = metadata.characterId
        val json = selectJson(connection, characterId)
        val document = json?.let { decode(it, characterId) }
        metadata.add(applier, FarmingCharacterData(document?.patches, document?.bins))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        prepareSave(player).save(connection, characterId)
    }

    override fun prepareSave(player: Player): CharacterDataStage.PreparedSave {
        val patches = player.attr[FarmingAttributes.PATCHES]
        val bins = player.attr[FarmingAttributes.BINS]
        val json = if (patches == null && bins == null) null else encode(patches, bins, player)
        return preparedJson(json)
    }

    private fun preparedJson(json: String?): CharacterDataStage.PreparedSave {
        return CharacterDataStage.PreparedSave { connection, characterId ->
            if (json != null) upsertJson(connection, characterId, json)
        }.withRecoveryData("farmingJson", json)
    }

    private fun selectJson(connection: DatabaseConnection, characterId: Int): String? {
        val select =
            connection.prepareStatement(OpenRuneSql.text("game/farming/select_patches.sql"))
        return select.use { statement ->
            statement.setInt(1, characterId)
            statement.setString(2, FarmingAttributes.PATCHES_PERSISTENCE_KEY)
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
            connection.prepareStatement(OpenRuneSql.text("game/farming/upsert_patches.sql"))
        upsert.use { statement ->
            statement.setInt(1, characterId)
            statement.setString(2, FarmingAttributes.PATCHES_PERSISTENCE_KEY)
            statement.setString(3, json)
            statement.executeUpdate()
        }
    }

    private fun decode(json: String, characterId: Int): FarmingDocument? {
        val result = runCatching { FarmingCodec.decode(objectMapper, json) }
        val document = result.getOrNull()
        if (document == null) {
            logger.error(result.exceptionOrNull()) {
                "Could not decode stored farming patches. " +
                    "(characterId=$characterId, json=${json.take(MAX_LOGGED_JSON_LENGTH)})"
            }
        }
        return document
    }

    private fun encode(patches: FarmingPatches?, bins: FarmingBins?, player: Player): String? {
        val result = runCatching { FarmingCodec.encode(objectMapper, patches, bins) }
        val json = result.getOrNull()
        if (json == null) {
            logger.error(result.exceptionOrNull()) {
                "Could not encode farming patches for: $player"
            }
        }
        return json
    }

    private companion object {
        private const val MAX_LOGGED_JSON_LENGTH: Int = 8192
    }
}
