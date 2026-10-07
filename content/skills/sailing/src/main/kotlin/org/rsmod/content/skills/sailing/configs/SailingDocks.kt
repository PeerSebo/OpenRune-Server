package org.rsmod.content.skills.sailing.configs

import org.rsmod.api.table.sailing.SailingDockRow
import org.rsmod.map.CoordGrid

object SailingDocks {

    data class SailingDock(
        val dbrow: String,
        val gangplank: String?,
        val landCoords: CoordGrid?,
        val seaCoords: CoordGrid? = null,
        val verified: Boolean = false,
        val seaCoordsByBoatType: Map<Int, CoordGrid> = emptyMap(),
    ) {
        val row: SailingDockRow by lazy { SailingDockRow.getRow(dbrow) }

        val levelRequired: Int
            get() = row.levelRequired

        val isStandardDock: Boolean
            get() = gangplank != null

        // Boarding requires both a water berth and a land return tile.
        val canSpawnBoat: Boolean
            get() = landCoords != null && seaCoords != null

        fun seaCoordsFor(boatTypeId: Int): CoordGrid? =
            if (boatTypeId in 0..2) seaCoordsByBoatType[boatTypeId] ?: seaCoords else null
    }

    const val LOC_MOORING_EMBARK: String = "loc.sailing_mooring_embark"

    const val LOC_MOORING_DISEMBARK: String = "loc.sailing_mooring_disembark"

    const val LOC_GANGPLANK_EMBARK: String = "loc.sailing_gangplank_embark"

    const val LOC_GANGPLANK_DISEMBARK: String = "loc.sailing_gangplank_disembark"

    val PORT_SARIM: SailingDock =
        SailingDock(
            dbrow = "dbrow.sailing_dock_port_sarim",
            gangplank = "loc.sailing_gangplank_port_sarim",
            landCoords = CoordGrid(3050, 3193),
            seaCoords = CoordGrid(3055, 3193),
            seaCoordsByBoatType = mapOf(
                0 to CoordGrid(3054, 3193),
                1 to CoordGrid(3055, 3193),
                2 to CoordGrid(3059, 3193),
            ),
        )

    val THE_PANDEMONIUM: SailingDock =
        SailingDock(
            dbrow = "dbrow.sailing_dock_the_pandemonium",
            gangplank = "loc.sailing_gangplank_the_pandemonium",
            landCoords = CoordGrid(3069, 2987),
            seaCoords = CoordGrid(3074, 2987),
            seaCoordsByBoatType = mapOf(
                0 to CoordGrid(3073, 2987),
                1 to CoordGrid(3074, 2987),
                2 to CoordGrid(3078, 2983),
            ),
        )

    val CATHERBY: SailingDock =
        SailingDock(
            dbrow = "dbrow.sailing_dock_catherby",
            gangplank = "loc.sailing_gangplank_catherby",
            landCoords = CoordGrid(2796, 3413),
            seaCoords = CoordGrid(2796, 3408),
            seaCoordsByBoatType = mapOf(
                0 to CoordGrid(2796, 3409),
                1 to CoordGrid(2796, 3408),
                2 to CoordGrid(2796, 3401),
            ),
        )

    val PORT_KHAZARD: SailingDock =
        SailingDock(
            dbrow = "dbrow.sailing_dock_port_khazard",
            gangplank = "loc.sailing_gangplank_port_khazard",
            landCoords = CoordGrid(2685, 3162),
            seaCoords = CoordGrid(2690, 3162),
            seaCoordsByBoatType = mapOf(
                0 to CoordGrid(2689, 3162),
                1 to CoordGrid(2690, 3162),
                2 to CoordGrid(2694, 3166),
            ),
        )

    val AUTHORED: List<SailingDock> = listOf(PORT_SARIM, THE_PANDEMONIUM, CATHERBY, PORT_KHAZARD)

    // TODO: author land and berth coordinates for the remaining gangplank docks.
    val TODO_GANGPLANK_DOCKS: List<SailingDock> =
        listOf(
                "lands_end",
                "musa_point",
                "hosidius",
                "rimmington",
                "port_piscarilius",
                "brimhaven",
                "ardougne",
                "witchaven",
                "entrana",
                "civitas_illa_fortis",
                "corsair_cove",
                "cairn_isle",
                "sunset_coast",
                "the_summer_shore",
                "aldarin",
                "ruins_of_unkah",
                "void_knights_outpost",
                "port_roberts",
                "red_rock",
                "rellekka",
                "etceteria",
                "port_tyras",
                "deepfin_point",
                "jatizso",
                "neitiznot",
                "prifddinas",
                "piscatoris",
                "lunar_isle",
            )
            .map {
                SailingDock(
                    dbrow = "dbrow.sailing_dock_$it",
                    gangplank = "loc.sailing_gangplank_$it",
                    landCoords = null, // TODO: author docking coordinates.
                )
            }

    val TODO_WYRMSCRAIG_DOCKS: List<SailingDock> =
        listOf(
            SailingDock(
                dbrow = "dbrow.sailing_dock_wyrmscraig",
                gangplank = "loc.sailing_gangplank_wyrmscraig",
                landCoords = null, // TODO: author docking coordinates.
            ),
            SailingDock(
                dbrow = "dbrow.sailing_dock_wyrmscraig_cave",
                gangplank = "loc.sailing_gangplank_wyrmscraig_cave",
                landCoords = null, // TODO: author docking coordinates.
            ),
        )

    // TODO: author return coordinates for every mooring-point island.
    val TODO_MOORING_DOCKS: List<SailingDock> =
        listOf(
                "isle_of_souls",
                "waterbirth_island",
                "weiss",
                "dognose_island",
                "remote_island",
                "the_little_pearl",
                "the_onyx_crest",
                "last_light",
                "charred_island",
                "vatrachos_island",
                "anglers_retreat",
                "minotaurs_rest",
                "isle_of_bones",
                "tear_of_the_soul",
                "wintumber_island",
                "the_crown_jewel",
                "rainbows_end",
                "sunbleak_island",
                "shimmering_atoll",
                "laguna_aurorae",
                "chinchompa_island",
                "lledrith_island",
                "ynysdail",
                "buccaneers_haven",
                "drumstick_isle",
                "brittle_isle",
                "grimstone",
            )
            .map {
                SailingDock(
                    dbrow = "dbrow.sailing_dock_$it",
                    gangplank = null,
                    landCoords = null, // TODO: author docking coordinates.
                )
            }

    val ALL: List<SailingDock> by lazy {
        AUTHORED + TODO_GANGPLANK_DOCKS + TODO_WYRMSCRAIG_DOCKS + TODO_MOORING_DOCKS
    }

    val IMPLEMENTED: List<SailingDock> by lazy { ALL.filter { it.landCoords != null } }

    val SPAWNS_BOATS: List<SailingDock> by lazy { ALL.filter(SailingDock::canSpawnBoat) }

    fun byDbRow(dbrow: String): SailingDock? = ALL.firstOrNull { it.dbrow == dbrow }

    fun byGangplank(gangplank: String): SailingDock? = ALL.firstOrNull { it.gangplank == gangplank }

    fun byDockId(dockId: Int): SailingDock? = byDockId[dockId]

    private val byDockId: Map<Int, SailingDock> by lazy { ALL.associateBy { it.row.dockId } }

    fun canDock(dock: SailingDock, baseSailingLvl: Int): Boolean =
        baseSailingLvl >= dock.levelRequired

    fun assertCacheAnchors() {
        val cacheRows = SailingDockRow.all()
        check(cacheRows.size == SailingConstants.OQ15_DOCK_ROW_COUNT) {
            "dbtable.sailing_dock expected ${SailingConstants.OQ15_DOCK_ROW_COUNT} rows " +
                "(research doc open question 15 - the wiki says 56 or 57), got ${cacheRows.size}."
        }
        check(ALL.size == SailingConstants.OQ15_DOCK_ROW_COUNT) {
            "SailingDocks.ALL lists ${ALL.size} docks but the cache has " +
                "${SailingConstants.OQ15_DOCK_ROW_COUNT}; a dock is missing from this file."
        }

        for (dock in ALL) {
            check(dock.row.dockId >= 0) {
                "${dock.dbrow} resolved to a row with a negative dock_id."
            }
        }

        check(PORT_SARIM.levelRequired == 1) {
            "dbrow.sailing_dock_port_sarim:level_required expected 1, got " +
                "${PORT_SARIM.levelRequired}."
        }
        check(THE_PANDEMONIUM.levelRequired == 1) {
            "dbrow.sailing_dock_the_pandemonium:level_required expected 1, got " +
                "${THE_PANDEMONIUM.levelRequired}."
        }

        val duplicates = ALL.groupBy { it.dbrow }.filterValues { it.size > 1 }.keys
        check(duplicates.isEmpty()) { "Duplicate dock dbrow symbols: $duplicates" }

        val stranding = ALL.filter { it.seaCoords != null && it.landCoords == null }
        check(stranding.isEmpty()) {
            "Docks with seaCoords but no landCoords would strand a boarded player: " +
                "${stranding.map(SailingDock::dbrow)}"
        }

        checkNotNull(PORT_SARIM.landCoords) {
            "Port Sarim must keep an authored landCoords - it is the fallback every disembark " +
                "falls back to when no other dock resolves."
        }

        check(SPAWNS_BOATS.isNotEmpty()) {
            "No dock has both landCoords and seaCoords authored, so no boat can ever be spawned."
        }
    }
}
