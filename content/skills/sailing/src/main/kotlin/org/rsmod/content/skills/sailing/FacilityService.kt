package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.sailing.SailingBoatFacilityRow
import org.rsmod.api.table.sailing.SailingBoatHotspotRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

@Singleton
internal class FacilityService
@Inject
constructor(private val locRepo: LocRepository, private val ownership: BoatOwnershipService) {

    fun boardedBoat(player: Player): SailingBoatRow? {
        if (!player.isOnPlayerBoat()) {
            return null
        }
        return SailingBoats.byTypeId(player.boardedBoatType)
    }

    fun boardedSlot(player: Player): Int? =
        if (!player.isOnPlayerBoat()) null else ownership.activeSlot(player)

    fun hotspot(boat: SailingBoatRow, hotspotIndex: Int): SailingBoats.BoatHotspot? =
        SailingBoats.hotspots(boat).firstOrNull { it.hotspotIndex == hotspotIndex }

    fun layout(boat: SailingBoatRow, hotspotIndex: Int): SailingBoatHotspotRow? {
        val hotspot = hotspot(boat, hotspotIndex) ?: return null
        return runCatching { SailingBoatHotspotRow.getRow(hotspot.layoutRowId) }.getOrNull()
    }

    fun options(boat: SailingBoatRow, hotspotIndex: Int): List<SailingBoatFacilityRow> =
        layout(boat, hotspotIndex)?.option.orEmpty()

    fun placeholderLoc(boat: SailingBoatRow, hotspotIndex: Int): String? {
        val family = HOTSPOT_FAMILY_BY_TYPE_ID[boat.typeId] ?: return null
        val count = HOTSPOT_COUNT_BY_FAMILY[family] ?: return null
        if (hotspotIndex !in 0 until count) {
            return null
        }
        return "$PLACEHOLDER_PREFIX${family}_$hotspotIndex"
    }

    fun placeholderKey(locId: Int): PlaceholderKey? = placeholderKeys[locId]

    fun builtFacility(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
        hotspotIndex: Int,
    ): SailingBoatFacilityRow? {
        val stored = player.readSailingVar(SailingBoatVars.hotspot(slot, hotspotIndex))
        if (stored == EMPTY_HOTSPOT) {
            return null
        }
        return facilityFor(boat, hotspotIndex, stored)
    }

    fun builtFacilities(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
    ): Map<Int, SailingBoatFacilityRow> = buildMap {
        for (hotspot in SailingBoats.hotspots(boat)) {
            val built = builtFacility(player, slot, boat, hotspot.hotspotIndex) ?: continue
            put(hotspot.hotspotIndex, built)
        }
    }

    fun setBuiltFacility(
        player: Player,
        slot: Int,
        hotspotIndex: Int,
        row: SailingBoatFacilityRow?,
    ): Boolean {
        val value = if (row == null) EMPTY_HOTSPOT else facilityIdOf(row) ?: return false
        player.writeSailingVar(SailingBoatVars.hotspot(slot, hotspotIndex), value)
        return true
    }

    fun clearExtraData(player: Player, slot: Int, hotspotIndex: Int) {
        player.writeSailingVar(SailingBoatVars.hotspotExtraData(slot, hotspotIndex), 0)
    }

    fun facilityIdOf(row: SailingBoatFacilityRow): Int? =
        row.facilityBottleId?.takeIf { it != EMPTY_HOTSPOT }

    fun facilityFor(
        boat: SailingBoatRow,
        hotspotIndex: Int,
        facilityId: Int,
    ): SailingBoatFacilityRow? =
        options(boat, hotspotIndex).firstOrNull { facilityIdOf(it) == facilityId }

    // Count the whole upgrade family toward build_max, not just identical rows.
    fun builtFamilyCount(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
        row: SailingBoatFacilityRow,
    ): Int {
        val family = familyKey(row)
        return builtFacilities(player, slot, boat).count { familyKey(it.value) == family }
    }

    fun buildMaxReached(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
        row: SailingBoatFacilityRow,
    ): Boolean {
        val max = buildMax(row) ?: return false
        return builtFamilyCount(player, slot, boat, row) >= max
    }

    fun buildMax(row: SailingBoatFacilityRow): Int? {
        row.buildMax?.let {
            return it
        }
        val root = familyKey(row).takeIf { it != row.rowId } ?: return null
        return runCatching { SailingBoatFacilityRow.getRow(root) }.getOrNull()?.buildMax
    }

    fun materials(row: SailingBoatFacilityRow): List<Pair<String, Int>> =
        row.material.mapNotNull { pair ->
            val name = runCatching { pair.t0.internalName }.getOrNull() ?: return@mapNotNull null
            name to pair.t1
        }

    fun constructionXp(row: SailingBoatFacilityRow): Double? =
        FacilityXp.lookup(row.name, row.sailingRequirement)?.toDouble()

    // Crew stats gate operation, not facility construction.
    fun crewStatRequirement(row: SailingBoatFacilityRow): CrewStatRequirement? {
        val requirement = row.crewStatRequirement
        if (requirement.size < 2) {
            return null
        }
        return CrewStatRequirement(statId = requirement[0], level = requirement[1])
    }

    fun shipwrightWillBuild(row: SailingBoatFacilityRow): Boolean =
        row.constructionCoinsAlternative == true

    fun facilityLoc(row: SailingBoatFacilityRow): String? {
        val loc = row.loc.firstOrNull() ?: return null
        return runCatching { RSCM.getReverseMapping(RSCMType.LOC, loc.id) }
            .getOrNull()
            ?.takeIf { it.startsWith("loc.") }
    }

    fun draw(coords: CoordGrid, locSymbol: String, angle: LocAngle, shape: LocShape) {
        locRepo.add(coords, locSymbol, PERMANENT, angle, shape)
    }

    // Normalize bottle-ID variants before walking upgrade-parent families.
    fun familyKey(row: SailingBoatFacilityRow): Int {
        val canonical = canonicalRowIds[facilityIdOf(row) ?: return row.rowId] ?: row.rowId
        var current = canonical
        var guard = 0
        while (guard++ < FAMILY_WALK_LIMIT) {
            val parent = variantParents[current] ?: return current
            if (parent == current) {
                return current
            }
            current = parent
        }
        return current
    }

    private val canonicalRowIds: Map<Int, Int> by lazy {
        buildMap {
            for (row in SailingBoatFacilityRow.all()) {
                val id = row.facilityBottleId ?: continue
                val existing = get(id)
                if (existing == null || row.rowId < existing) {
                    put(id, row.rowId)
                }
            }
        }
    }

    private val variantParents: Map<Int, Int> by lazy {
        buildMap {
            for (row in SailingBoatFacilityRow.all()) {
                val parent = canonicalRowIds[row.facilityBottleId ?: continue] ?: row.rowId
                for (variant in row.variants) {
                    val bottleId = variant.facilityBottleId ?: continue
                    val canonical = canonicalRowIds[bottleId] ?: continue
                    if (canonical != parent) {
                        put(canonical, parent)
                    }
                }
            }
        }
    }

    private val placeholderKeys: Map<Int, PlaceholderKey> by lazy {
        buildMap {
            for ((family, count) in HOTSPOT_COUNT_BY_FAMILY) {
                for (index in 0 until count) {
                    val symbol = "$PLACEHOLDER_PREFIX${family}_$index"
                    val id = runCatching { symbol.asRSCM(RSCMType.LOC) }.getOrNull() ?: continue
                    put(id, PlaceholderKey(family, index))
                }
            }
        }
    }

    fun assertCacheAnchors() {
        val cannon = SailingBoatFacilityRow.getRow(DBROW_BRONZE_CANNON)
        check(cannon.buildMax == CANNON_BUILD_MAX) {
            "dbrow.sailing_boat_facility_bronze_cannon:build_max expected $CANNON_BUILD_MAX, got " +
                "${cannon.buildMax}. Cannons must stay capped server-side."
        }
        check(crewStatRequirement(cannon)?.statId == SailingConstants.CREW_STAT_PRIVATEERING) {
            "Bronze cannon crew_stat_requirement expected stat " +
                "${SailingConstants.CREW_STAT_PRIVATEERING} (Privateering), got " +
                "${crewStatRequirement(cannon)}."
        }
        check(facilityIdOf(cannon) != null) {
            "Bronze cannon has no facility_bottle_id; the hotspot varbit encoding this service " +
                "relies on no longer holds. See the FacilityService class docs."
        }

        for (boat in SailingBoats.all) {
            val hotspots = SailingBoats.hotspots(boat)
            check(hotspots.size == boat.facilityAmount) {
                "${boat.name} declares facility_amount ${boat.facilityAmount} but " +
                    "${hotspots.size} hotspot entries."
            }
            val family = HOTSPOT_FAMILY_BY_TYPE_ID[boat.typeId]
            check(family != null) {
                "No hotspot placeholder family authored for boat type_id ${boat.typeId}."
            }
            check(HOTSPOT_COUNT_BY_FAMILY[family] == boat.facilityAmount) {
                "Placeholder family '$family' is authored with " +
                    "${HOTSPOT_COUNT_BY_FAMILY[family]} hotspots but ${boat.name} declares " +
                    "${boat.facilityAmount}."
            }
            for (hotspot in hotspots) {
                val symbol = placeholderLoc(boat, hotspot.hotspotIndex)
                check(symbol != null) {
                    "${boat.name} hotspot ${hotspot.hotspotIndex} has no placeholder loc symbol."
                }
                check(runCatching { symbol.asRSCM(RSCMType.LOC) }.isSuccess) {
                    "Placeholder loc symbol '$symbol' does not resolve."
                }
            }
        }
    }

    data class PlaceholderKey(val family: String, val hotspotIndex: Int)

    data class CrewStatRequirement(val statId: Int, val level: Int)

    companion object {
        const val DBROW_BRONZE_CANNON: String = "dbrow.sailing_boat_facility_bronze_cannon"

        const val CANNON_BUILD_MAX: Int = 2

        const val EMPTY_HOTSPOT: Int = 0

        // Built facilities live until removal or deck release; no timed reversion.
        const val PERMANENT: Int = Int.MAX_VALUE

        const val PLACEHOLDER_PREFIX: String = "loc.sailing_boat_facility_placeholder_"

        const val LOC_PLACEHOLDER_GENERIC: String = "loc.sailing_boat_facility_placeholder"

        const val LOC_PLACEHOLDER_GENERIC_NONBLOCKING: String =
            "loc.sailing_boat_facility_placeholder_nonblocking"

        val HOTSPOT_FAMILY_BY_TYPE_ID: Map<Int, String> = mapOf(0 to "raft", 1 to "2x5", 2 to "3x8")

        val HOTSPOT_COUNT_BY_FAMILY: Map<String, Int> = mapOf("raft" to 1, "2x5" to 7, "3x8" to 13)

        private const val FAMILY_WALK_LIMIT: Int = 8
    }
}

private object FacilityXp {
    private val FAMILIES: List<Pair<String, Map<Int, Int>>> =
        listOf(
            "salvaging hook" to
                mapOf(
                    15 to 151,
                    21 to 312,
                    27 to 499,
                    44 to 769,
                    59 to 919,
                    74 to 1_233,
                    86 to 1_537,
                ),
            "salvaging station" to mapOf(42 to 366),
            "trawling net" to mapOf(56 to 458, 65 to 783, 76 to 1_003, 84 to 1_289),
            "cargo hold" to
                mapOf(
                    1 to 232,
                    18 to 491,
                    29 to 738,
                    46 to 1_153,
                    60 to 1_326,
                    80 to 1_492,
                    89 to 1_634,
                ),
            "cannon" to
                mapOf(
                    28 to 157,
                    35 to 328,
                    47 to 526,
                    57 to 816,
                    69 to 986,
                    80 to 1_359,
                    92 to 1_586,
                ),
            "chum" to mapOf(56 to 1_522, 68 to 1_738, 82 to 2_028),
            "noculation" to mapOf(40 to 765),
            "bosun" to mapOf(63 to 1_003),
            "wind catcher" to mapOf(53 to 741),
            "gale catcher" to mapOf(79 to 1_517),
            "crystal extractor" to mapOf(73 to 3_066),
            "ballistic attractor" to mapOf(50 to 2_141),
            "teleport focus" to mapOf(55 to 2_154, 75 to 3_582),
            "fathom stone" to mapOf(70 to 1_652),
            "fathom pearl" to mapOf(91 to 2_307),
            "anchor" to mapOf(37 to 176),
            "eternal brazier" to mapOf(78 to 1_373),
            "keg" to mapOf(33 to 313),
            "range" to mapOf(16 to 90),
        )

    fun lookup(name: String, sailingRequirement: Int): Int? {
        val lowered = name.lowercase()
        val family = FAMILIES.firstOrNull { lowered.contains(it.first) } ?: return null
        return family.second[sailingRequirement]
    }
}
