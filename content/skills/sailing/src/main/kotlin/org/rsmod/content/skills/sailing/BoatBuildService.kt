package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.game.entity.Player

internal enum class ShipPartCategory(
    val displayName: String,
    val varbitField: String,
    val isCore: Boolean,
    val affectsStats: Boolean,
    val firstOptionVarValue: Int,
) {
    HULL("Hull", "hull", isCore = true, affectsStats = true, firstOptionVarValue = 0),
    KEEL("Keel", "keel", isCore = true, affectsStats = true, firstOptionVarValue = 0),
    SAIL("Mast and sails", "sail", isCore = true, affectsStats = true, firstOptionVarValue = 0),
    STEERING("Helm", "steering", isCore = true, affectsStats = true, firstOptionVarValue = 0),
    BRAZIER(
        "Eternal brazier",
        "brazier",
        isCore = true,
        affectsStats = true,
        firstOptionVarValue = 1,
    ),
    FLAG("Flag", "flag", isCore = false, affectsStats = false, firstOptionVarValue = 0),
    TRIM("Trim", "trim", isCore = false, affectsStats = false, firstOptionVarValue = 0),
    SAIL_PATTERN(
        "Sail pattern",
        "sail_pattern",
        isCore = false,
        affectsStats = false,
        firstOptionVarValue = 0,
    );

    fun varSymbol(slot: Int): String = SailingBoatVars.field(slot, varbitField)
}

@Singleton
internal class BoatBuildService @Inject constructor(private val ownership: BoatOwnershipService) {
    data class PartOption(
        val category: ShipPartCategory,
        val index: Int,
        val dbrow: String?,
        val name: String,
        val sailingRequirement: Int,
        val constructionRequirement: Int,
        val material: List<Pair<String, Int>>,
        val materialsResolved: Boolean,
        val coinsAlternative: Boolean,
        val hpMax: Int?,
    ) {
        val tierIndex: Int
            get() = index
    }

    fun options(boat: SailingBoatRow, category: ShipPartCategory): List<PartOption> =
        when (category) {
            ShipPartCategory.HULL ->
                boat.hullOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = row.constructionCoinsAlternative == true,
                            hpMax = row.facilityStats.boatHpMax,
                        )
                    }
            ShipPartCategory.KEEL ->
                boat.keelOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = row.constructionCoinsAlternative == true,
                            hpMax = row.facilityStats.boatHpMax,
                        )
                    }
            ShipPartCategory.SAIL ->
                boat.sailOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = row.constructionCoinsAlternative == true,
                            hpMax = null,
                        )
                    }
            ShipPartCategory.STEERING ->
                boat.steeringOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = row.constructionCoinsAlternative == true,
                            hpMax = null,
                        )
                    }
            ShipPartCategory.BRAZIER ->
                listOfNotNull(boat.brazierOption).mapIndexed { i, row ->
                    option(
                        category = category,
                        index = category.firstOptionVarValue + i,
                        rowId = row.rowId,
                        name = row.name,
                        sailingRequirement = row.sailingRequirement,
                        constructionRequirement = row.constructionRequirement,
                        rawMaterial = row.material.map { it.t0 to it.t1 },
                        // Brazier rows have no construction_coins_alternative column.
                        coinsAlternative = false,
                        hpMax = null,
                    )
                }
            ShipPartCategory.FLAG ->
                boat.flagOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = false,
                            hpMax = null,
                        )
                    }
            ShipPartCategory.TRIM ->
                // Missing customisation_buildable inherits the buildable default.
                boat.trimOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row -> i to row }
                    .filter { (_, row) -> row.customisationBuildable != false }
                    .map { (i, row) ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = false,
                            hpMax = null,
                        )
                    }
            ShipPartCategory.SAIL_PATTERN ->
                boat.sailPatternOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        option(
                            category = category,
                            index = category.firstOptionVarValue + i,
                            rowId = row.rowId,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            rawMaterial = row.material.map { it.t0 to it.t1 },
                            coinsAlternative = false,
                            hpMax = null,
                        )
                    }
        }

    fun availableCategories(boat: SailingBoatRow): List<ShipPartCategory> =
        ShipPartCategory.entries.filter { options(boat, it).isNotEmpty() }

    fun resolveCustomisingSlot(player: Player): Int? = ownership.activeSlot(player)

    fun boatOf(player: Player, slot: Int): SailingBoatRow? = ownership.boat(player, slot)

    fun fitted(player: Player, slot: Int, category: ShipPartCategory): Int =
        ownership.partTier(player, slot, category)

    fun fit(player: Player, slot: Int, category: ShipPartCategory, index: Int) {
        ownership.setPartTier(player, slot, category, index)
    }

    @Suppress("UNUSED_PARAMETER")
    fun restoreFullHp(player: Player, slot: Int, boat: SailingBoatRow) {
        ownership.refreshMaxHp(player, slot)
    }

    private fun option(
        category: ShipPartCategory,
        index: Int,
        rowId: Int,
        name: String,
        sailingRequirement: Int,
        constructionRequirement: Int,
        rawMaterial: List<Pair<ItemServerType, Int>>,
        coinsAlternative: Boolean,
        hpMax: Int?,
    ): PartOption {
        val material = resolveMaterial(rawMaterial)
        return PartOption(
            category = category,
            index = index,
            dbrow = dbrowSymbolOrNull(rowId),
            name = name,
            sailingRequirement = sailingRequirement,
            constructionRequirement = constructionRequirement,
            material = material,
            materialsResolved = material.size == rawMaterial.size,
            coinsAlternative = coinsAlternative,
            hpMax = hpMax,
        )
    }

    private fun resolveMaterial(raw: List<Pair<ItemServerType, Int>>): List<Pair<String, Int>> =
        raw.mapNotNull { (obj, count) ->
            val name = runCatching { obj.internalName }.getOrNull()?.takeIf { it.isNotEmpty() }
            name?.let { it to count }
        }

    private fun dbrowSymbolOrNull(rowId: Int): String? =
        runCatching { RSCM.getReverseMapping(RSCMType.DBROW, rowId) }
            .getOrNull()
            ?.takeIf { it.startsWith("dbrow.") }
}

internal const val SAILING_COINS: String = "obj.coins"

internal const val SHIPBUILDING_HAMMER: String = "obj.hammer"

internal const val SHIPBUILDING_SAW: String = "obj.poh_saw"

internal const val SHIPBUILDING_CRYSTAL_SAW: String = "obj.eyeglo_crystal_saw"

internal const val CRYSTAL_SAW_CONSTRUCTION_BOOST: Int = 3

internal fun ProtectedAccess.missingShipbuildingTool(): String? =
    when {
        inv.count(SHIPBUILDING_HAMMER) <= 0 -> ShipbuildingMessages.NEED_HAMMER
        inv.count(SHIPBUILDING_SAW) <= 0 && inv.count(SHIPBUILDING_CRYSTAL_SAW) <= 0 ->
            ShipbuildingMessages.NEED_SAW
        else -> null
    }

internal fun ProtectedAccess.shipbuildingConstructionLvl(): Int {
    val boosted = stat(SailingConstants.CONSTRUCTION_STAT)
    val saw = if (inv.count(SHIPBUILDING_CRYSTAL_SAW) > 0) CRYSTAL_SAW_CONSTRUCTION_BOOST else 0
    return boosted + saw
}

internal fun ProtectedAccess.shipbuildingCoins(): Int = inv.count(SAILING_COINS)

internal fun ProtectedAccess.hasShipbuildingMaterials(
    option: BoatBuildService.PartOption
): Boolean = option.material.all { (name, amount) -> inv.count(name) >= amount }

internal const val SHIPBUILDING_BUILD_ANIM: String = "seq.human_poh_build"

internal fun ProtectedAccess.rollbackShipbuildingMaterials(removed: List<Pair<String, Int>>) {
    for ((name, amount) in removed) {
        invAdd(inv, name, amount)
    }
}

internal object ShipbuildingMessages {
    const val NEED_HAMMER: String = "You need a hammer to work on a boat."

    const val NEED_SAW: String = "You need a saw to work on a boat."

    const val NEED_MATERIALS: String = "You don't have the materials to build that."

    const val NEED_COINS: String = "You don't have enough coins."

    const val NO_BOAT: String = "You don't own a boat to work on."

    const val ALREADY_FITTED: String = "Your boat already has that fitted."

    const val CANNOT_BUILD_YET: String = "You can't build that here yet."
}
