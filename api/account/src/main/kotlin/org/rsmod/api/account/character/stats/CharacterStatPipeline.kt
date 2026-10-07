package org.rsmod.api.account.character.stats

import com.fasterxml.jackson.databind.JsonNode
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player

private typealias Stat = CharacterStatData.Stat

public class CharacterStatPipeline @Inject constructor(private val applier: CharacterStatApplier) :
    CharacterDataStage.Pipeline {
    override val recoveryKey: String = "stats"

    override fun restoreSave(data: JsonNode): CharacterDataStage.PreparedSave {
        require(data.isArray)
        return preparedStats(data.map { Stat(it.required("type").textValue().asRSCM(RSCMType.STAT),
            it.required("vis").intValue(), it.required("base").intValue(), it.required("fineXp").intValue()) })
    }

    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val select =
            connection.prepareStatement(
                OpenRuneSql.text("game/stats/select_for_character.sql"),
            )

        val stats = ArrayList<Stat>(25)
        select.use {
            select.setInt(1, metadata.characterId)
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val statId = resultSet.getInt("stat_id")
                    val visLevel = resultSet.getInt("vis_level")
                    val baseLevel = resultSet.getInt("base_level")
                    val fineXp = resultSet.getInt("fine_xp")

                    val stat = Stat(statId, visLevel, baseLevel, fineXp)
                    stats += stat
                }
            }
        }

        metadata.add(applier, CharacterStatData(stats))
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        prepareSave(player).save(connection, characterId)
    }

    override fun prepareSave(player: Player): CharacterDataStage.PreparedSave {
        val stats = ServerCacheManager.getStats().values.map { stat ->
            val type = RSCM.getReverseMapping(RSCMType.STAT, stat.id)
            Stat(stat.id, player.statMap.getCurrentLevel(type).toInt(),
                player.statMap.getBaseLevel(type).toInt(), player.statMap.getFineXP(type))
        }
        return preparedStats(stats)
    }

    private fun preparedStats(stats: List<Stat>): CharacterDataStage.PreparedSave {
        return CharacterDataStage.PreparedSave { connection, characterId ->
            connection.prepareStatement(OpenRuneSql.text("game/stats/upsert_stat.sql")).use {
                for (stat in stats) {
                    it.setInt(1, characterId)
                    it.setInt(2, stat.type)
                    it.setInt(3, stat.vis)
                    it.setInt(4, stat.base)
                    it.setInt(5, stat.fineXp)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }.withRecoveryData("stats", stats.map { mapOf(
            "type" to RSCM.getReverseMapping(RSCMType.STAT, it.type),
            "vis" to it.vis, "base" to it.base, "fineXp" to it.fineXp,
        ) })
    }
}
