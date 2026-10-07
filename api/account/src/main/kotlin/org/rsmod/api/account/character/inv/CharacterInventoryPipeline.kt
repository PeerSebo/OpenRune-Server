package org.rsmod.api.account.character.inv

import com.fasterxml.jackson.databind.JsonNode
import dev.openrune.types.InvScope
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.account.persistence.GamePersistenceRscmKeys
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

private typealias CharacterInventory = CharacterInventoryData.Inventory

private typealias CharacterObj = CharacterInventoryData.Obj

private data class InventoryParent(
    val characterId: Int,
    val invDbKey: String,
)

private data class InventorySnapshot(val internalName: String, val objs: List<InvObj?>) {
    val indices get() = objs.indices
    operator fun get(slot: Int): InvObj? = objs[slot]
}

public class CharacterInventoryPipeline
@Inject
constructor(private val applier: CharacterInventoryApplier) : CharacterDataStage.Pipeline {
    override val recoveryKey: String = "inventories"

    override fun restoreSave(data: JsonNode): CharacterDataStage.PreparedSave {
        require(data.isArray)
        val inventories = data.map { inventory ->
            InventorySnapshot(inventory.required("type").textValue(), inventory.required("slots").map { obj ->
                if (obj.isNull) null else InvObj(GamePersistenceRscmKeys.decodeObjKey(obj.required("obj").textValue()),
                    obj.required("count").intValue(), obj.required("vars").intValue())
            })
        }
        require(inventories.map { it.internalName }.distinct().size == inventories.size)
        return CharacterDataStage.PreparedSave { connection, characterId ->
            saveInventories(connection, inventories, characterId)
        }
    }

    override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {
        val inventories = selectInventories(connection, metadata)

        if (inventories.isEmpty()) {
            metadata.add(applier, CharacterInventoryData(inventories))
            return
        }

        val rowInventories = inventories.associateBy { "${it.characterId}|${it.invDbKey}" }

        val placeholders = (0 until inventories.size).joinToString(",") { "?" }
        val select =
            connection.prepareStatement(
                OpenRuneSql.text(
                    "game/inventory/select_objs_in_ids.sql",
                    "__IN__" to placeholders,
                ),
            )

        select.use {
            it.setInt(1, metadata.characterId)
            inventories.forEachIndexed { index, inventory ->
                it.setString(2 + index, inventory.invDbKey)
            }
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val cid = resultSet.getInt("character_id")
                    val invDb = resultSet.getString("inv")
                    val slot = resultSet.getInt("slot")
                    val objKey = GamePersistenceRscmKeys.decodeObjKey(resultSet.getString("obj"))
                    val count = resultSet.getInt("count")
                    val vars = resultSet.getInt("vars")

                    val inventory = rowInventories.getValue("$cid|$invDb")
                    inventory.objs[slot] = CharacterObj(objKey, count, vars)
                }
            }
        }

        metadata.add(applier, CharacterInventoryData(inventories))
    }

    private fun selectInventories(
        connection: DatabaseConnection,
        metadata: CharacterMetadataList,
    ): List<CharacterInventory> {
        val inventories = ArrayList<CharacterInventory>(4)

        val select =
            connection.prepareStatement(
                OpenRuneSql.text("game/inventory/select_inventories_for_character.sql"),
            )

        select.use {
            it.setInt(1, metadata.characterId)
            it.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val characterId = resultSet.getInt("character_id")
                    val invDbKey = resultSet.getString("inv_type")
                    val invKey = GamePersistenceRscmKeys.decodeInvTypeKey(invDbKey)
                    inventories += CharacterInventory(characterId, invDbKey, invKey)
                }
            }
        }

        return inventories
    }

    override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
        prepareSave(player).save(connection, characterId)
    }

    override fun prepareSave(player: Player): CharacterDataStage.PreparedSave {
        val inventories = player.invMap.values.filter { it.type.scope == InvScope.Perm }.map { inv ->
            InventorySnapshot(inv.internalName, inv.indices.map { inv[it] })
        }
        return CharacterDataStage.PreparedSave { connection, characterId ->
            saveInventories(connection, inventories, characterId)
        }.withRecoveryData("inventories", inventories.map { inventory ->
            mapOf("type" to inventory.internalName, "slots" to inventory.objs.map { obj ->
                obj?.let { mapOf("obj" to GamePersistenceRscmKeys.encodeObjKey(it.id), "count" to it.count, "vars" to it.vars) }
            })
        })
    }

    private fun saveInventories(
        connection: DatabaseConnection,
        persistentInvs: List<InventorySnapshot>,
        characterId: Int,
    ) {
        deleteStaleInventories(connection, characterId, persistentInvs)

        val delete =
            connection.prepareStatement(
                OpenRuneSql.text("game/inventory/delete_obj_by_inventory_slot.sql"),
            )

        val upsert =
            connection.prepareStatement(
                OpenRuneSql.text("game/inventory/upsert_inventory_obj.sql"),
            )

        delete.use { delete ->
            upsert.use { upsert ->
                for (inventory in persistentInvs) {
                    val invTypeKey = GamePersistenceRscmKeys.encodeInvTypeKey(inventory.internalName)

                    val parent = ensureInventoryRow(connection, characterId, invTypeKey)
                    if (parent == null) {
                        val message =
                            "Could not fetch ${inventory.internalName} inventory row for character $characterId"
                        throw IllegalStateException(message)
                    }

                    for (i in inventory.indices) {
                        if (inventory[i] == null) {
                            delete.setInt(1, parent.characterId)
                            delete.setString(2, parent.invDbKey)
                            delete.setInt(3, i)
                            delete.addBatch()
                        }
                    }

                    for (i in inventory.indices) {
                        val obj = inventory[i]
                        if (obj != null) {
                            upsert.setInt(1, parent.characterId)
                            upsert.setString(2, parent.invDbKey)
                            upsert.setInt(3, i)
                            upsert.setString(4, GamePersistenceRscmKeys.encodeObjKey(obj.id))
                            upsert.setInt(5, obj.count)
                            upsert.setInt(6, obj.vars)
                            upsert.addBatch()
                        }
                    }
                }
                delete.executeBatch()
                upsert.executeBatch()
            }
        }
    }

    private fun deleteStaleInventories(
        connection: DatabaseConnection,
        characterId: Int,
        inventories: Collection<InventorySnapshot>,
    ) {
        // The inventory_objs foreign key cascades deletion of stale parent inventories.
        if (inventories.isNotEmpty()) {
            val activeInvPlaceholders = (0 until inventories.size).joinToString(",") { "?" }
            val deleteStaleInventories =
                connection.prepareStatement(
                    OpenRuneSql.text(
                        "game/inventory/delete_inventories_not_in_types.sql",
                        "__IN__" to activeInvPlaceholders,
                    ),
                )

            deleteStaleInventories.use {
                it.setInt(1, characterId)
                inventories.forEachIndexed { index, inv ->
                    it.setString(2 + index, GamePersistenceRscmKeys.encodeInvTypeKey(inv.internalName))
                }
                it.executeUpdate()
            }
        } else {
            val deleteAllInventories =
                connection.prepareStatement(
                    OpenRuneSql.text("game/inventory/delete_all_inventories_for_character.sql"),
                )

            deleteAllInventories.use {
                it.setInt(1, characterId)
                it.executeUpdate()
            }
        }
    }

    private fun ensureInventoryRow(
        connection: DatabaseConnection,
        characterId: Int,
        invTypeKey: String,
    ): InventoryParent? {
        val insert =
            connection.prepareStatement(
                OpenRuneSql.text("game/inventory/insert_inventory_row_conflict_do_nothing.sql"),
            )

        insert.use {
            it.setInt(1, characterId)
            it.setString(2, invTypeKey)
            it.executeUpdate()
        }

        val select =
            connection.prepareStatement(
                OpenRuneSql.text("game/inventory/select_inventory_id.sql"),
            )

        select.use {
            it.setInt(1, characterId)
            it.setString(2, invTypeKey)
            return select.executeQuery().use { rs ->
                if (rs.next()) {
                    InventoryParent(
                        characterId = rs.getInt("character_id"),
                        invDbKey = rs.getString("inv"),
                    )
                } else {
                    null
                }
            }
        }
    }
}
