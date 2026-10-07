package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.stringOptional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.player.stat.sailingLvl
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.api.table.sailing.SailingCrewRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.game.entity.Player

@Singleton
internal class CrewService @Inject constructor() {

    val roster: List<CrewMate>
        get() = CrewRoster.ALL

    fun byUniqueId(uniqueId: Int): CrewMate? =
        CrewRoster.ALL.firstOrNull { it.uniqueId == uniqueId }

    fun isRecruited(player: Player, mate: CrewMate): Boolean =
        player.readSailingVar(mate.unlockedVarbit) != 0

    fun hasMet(player: Player, mate: CrewMate): Boolean {
        val symbol = mate.metVarbit ?: return false
        return player.readSailingVar(symbol) != 0
    }

    fun markMet(player: Player, mate: CrewMate) {
        val symbol = mate.metVarbit ?: return
        player.writeSailingVar(symbol, 1)
    }

    fun canRecruit(player: Player, mate: CrewMate): Boolean =
        player.sailingLvl >= mate.wikiSailingLevel

    fun recruit(player: Player, mate: CrewMate) {
        player.writeSailingVar(mate.unlockedVarbit, 1)
        markMet(player, mate)
    }

    fun recruited(player: Player): List<CrewMate> =
        CrewRoster.ALL.filter { isRecruited(player, it) }

    fun unassignedRecruits(player: Player): List<CrewMate> {
        val assigned = assignedUniqueIds(player)
        return recruited(player).filter { it.uniqueId !in assigned }
    }

    fun unlockedCrewSlots(player: Player): Int {
        val base = player.baseSailingLvl
        return SailingConstants.CREW_SLOT_UNLOCK_LEVELS.count { base >= it }
    }

    fun crewSlotRequirement(nth: Int): Int {
        require(nth in 1..CrewRoster.MAX_CREW_SLOTS) { "Crew slot out of range: $nth" }
        return SailingConstants.CREW_SLOT_UNLOCK_LEVELS[nth - 1]
    }

    fun assignableCrewSlots(player: Player, boat: SailingBoatRow): Int =
        minOf(unlockedCrewSlots(player), crewCapacity(boat))

    fun crewCapacity(boat: SailingBoatRow): Int = boat.crewCapacity ?: 0

    fun assignedUniqueId(player: Player, slot: Int): Int =
        player.readSailingVar(CrewRoster.slotVar(slot))

    fun assigned(player: Player, slot: Int): CrewMate? = byUniqueId(assignedUniqueId(player, slot))

    fun assignedUniqueIds(player: Player): Set<Int> {
        val ids = (1..CrewRoster.MAX_CREW_SLOTS).map { assignedUniqueId(player, it) }
        return ids.filterTo(HashSet()) { it != CrewRoster.SLOT_EMPTY }
    }

    fun slotOf(player: Player, mate: CrewMate): Int? =
        (1..CrewRoster.MAX_CREW_SLOTS).firstOrNull { assignedUniqueId(player, it) == mate.uniqueId }

    fun assign(player: Player, slot: Int, mate: CrewMate) {
        val existing = slotOf(player, mate)
        if (existing != null && existing != slot) {
            clear(player, existing)
        }
        player.writeSailingVar(CrewRoster.slotVar(slot), mate.uniqueId)
    }

    fun clear(player: Player, slot: Int) {
        player.writeSailingVar(CrewRoster.slotVar(slot), CrewRoster.SLOT_EMPTY)
        player.writeSailingVar(CrewRoster.slotPositionVar(slot), CrewRoster.SLOT_POSITION_DEFAULT)
    }

    fun selectedCrew(player: Player): CrewMate? =
        byUniqueId(player.readSailingVar(CrewRoster.VARBIT_SELECTED_CREW_ID))

    fun selectedSlot(player: Player): Int? =
        player.readSailingVar(CrewRoster.VARBIT_SELECTED_SLOT_ID).takeIf {
            it in 1..CrewRoster.MAX_CREW_SLOTS
        }

    fun selectCrew(player: Player, mate: CrewMate?) {
        val id = mate?.uniqueId ?: CrewRoster.SLOT_EMPTY
        player.writeSailingVar(CrewRoster.VARBIT_SELECTED_CREW_ID, id)
    }

    fun selectSlot(player: Player, slot: Int?) {
        player.writeSailingVar(CrewRoster.VARBIT_SELECTED_SLOT_ID, slot ?: CrewRoster.SLOT_EMPTY)
    }

    fun clearSelection(player: Player) {
        selectCrew(player, null)
        selectSlot(player, null)
    }

    fun helmsmanship(mate: CrewMate): Int = mate.row.statHelmsmanship

    fun privateering(mate: CrewMate): Int = mate.row.statPrivateering

    fun deckhandiness(mate: CrewMate): Int = mate.row.statDeckhandiness

    fun crateCapacity(mate: CrewMate): Int =
        if (deckhandiness(mate) >= CrewRoster.CRATES_2_MIN_DECKHANDINESS) 2 else 1

    fun canBankCargo(mate: CrewMate): Boolean =
        deckhandiness(mate) >= CrewRoster.BANK_CARGO_MIN_DECKHANDINESS

    fun canPilot(mate: CrewMate, boat: SailingBoatRow): Boolean =
        if (boat.typeId == SailingBoats.sloop.typeId) {
            helmsmanship(mate) >= CrewRoster.HELMSMANSHIP_MIN_FOR_SLOOP
        } else {
            crewCapacity(boat) > 0
        }

    fun orderString(mate: CrewMate, order: CrewOrder): String? =
        mate.helper.stringOptional(order.dbcol)

    fun panicString(mate: CrewMate): String? =
        mate.helper.stringOptional("dbcol.sailing_crew:panic_string")

    fun encounterString(mate: CrewMate): String? =
        mate.helper.stringOptional("dbcol.sailing_crew:encounter_string")

    // Run cache assertions at script startup, after Guice configuration.
    fun assertCacheAnchors() {
        val rows = SailingCrewRow.all()
        check(rows.size == CrewRoster.EXPECTED_ROW_COUNT) {
            "dbtable.sailing_crew expected ${CrewRoster.EXPECTED_ROW_COUNT} rows, got ${rows.size}."
        }
        check(CrewRoster.ALL.size == CrewRoster.EXPECTED_ROW_COUNT) {
            "Crew roster expected ${CrewRoster.EXPECTED_ROW_COUNT} entries, got " +
                "${CrewRoster.ALL.size}."
        }

        val uniqueIds = CrewRoster.ALL.map { it.uniqueId }
        check(uniqueIds == (1..CrewRoster.EXPECTED_ROW_COUNT).toList()) {
            "dbcol.sailing_crew:unique_id expected 1..${CrewRoster.EXPECTED_ROW_COUNT} in roster " +
                "order, got $uniqueIds."
        }

        for (mate in CrewRoster.ALL) {
            val stats = Triple(helmsmanship(mate), privateering(mate), deckhandiness(mate))
            check(stats == mate.expectedStats) {
                "${mate.displayName} (${mate.dbrow}) expected Helmsmanship/Privateering/" +
                    "Deckhandiness ${mate.expectedStats}, got $stats."
            }

            val worldId = mate.worldNpc.asRSCM(RSCMType.NPC)
            val cacheName = ServerCacheManager.getNpc(worldId)?.name.orEmpty()
            check(cacheName.isEmpty() || cacheName == mate.displayName) {
                "${mate.worldNpc} ($worldId) expected name '${mate.displayName}', got '$cacheName'."
            }
        }

        check(SailingConstants.CREW_SLOT_UNLOCK_LEVELS.size == CrewRoster.MAX_CREW_SLOTS) {
            "CREW_SLOT_UNLOCK_LEVELS has ${SailingConstants.CREW_SLOT_UNLOCK_LEVELS.size} rungs " +
                "but there are ${CrewRoster.MAX_CREW_SLOTS} crew slot varbits."
        }

        check(crewCapacity(SailingBoats.sloop) == CrewRoster.MAX_CREW_SLOTS) {
            "Sloop crew_capacity expected ${CrewRoster.MAX_CREW_SLOTS} to match the crew slot " +
                "count, got ${crewCapacity(SailingBoats.sloop)}."
        }

        val registrarId = CrewRoster.REGISTRAR_2OP.asRSCM(RSCMType.NPC)
        val registrarName = ServerCacheManager.getNpc(registrarId)?.name.orEmpty()
        check(registrarName.isEmpty() || registrarName == CrewRoster.REGISTRAR_NAME) {
            "${CrewRoster.REGISTRAR_2OP} expected name '${CrewRoster.REGISTRAR_NAME}', got " +
                "'$registrarName'."
        }
    }
}

internal data class CrewMate(
    val key: String,
    val dbrow: String,
    val displayName: String,
    val location: String,
    val wikiSailingLevel: Int,
    val expectedStats: Triple<Int, Int, Int>,
    val unrecruitedNpc: String?,
    val recruitedNpc: String?,
    val worldNpc: String,
    val dockNpc: String,
    val shipNpc: String,
    val shipOpNpc: String,
    val shipNoOpNpc: String,
    val cargoNpcs: List<String>,
    val unlockedVarbit: String,
    val metVarbit: String?,
    // TODO: connect recruited/dock/deck state to the crew visibility varbits.
    val visibilityVarbits: List<String>,
) {
    val row: SailingCrewRow by lazy { SailingCrewRow.getRow(dbrow) }

    val helper: DbHelper by lazy { DbHelper.row(row.rowId) }

    val uniqueId: Int
        get() = row.uniqueId

    val talkToNpcs: List<String>
        get() = listOfNotNull(worldNpc, unrecruitedNpc, recruitedNpc)
}

internal enum class CrewOrder(val dbcol: String) {
    Idle("dbcol.sailing_crew:order_string_idle"),
    Sails("dbcol.sailing_crew:order_string_sails"),
    Helm("dbcol.sailing_crew:order_string_helm"),
    HelmAndSails("dbcol.sailing_crew:order_string_helm_and_sails"),
    Salvage("dbcol.sailing_crew:order_string_salvage"),
    Cargo("dbcol.sailing_crew:order_string_cargo"),
    Cannon("dbcol.sailing_crew:order_string_cannon"),
    Repairs("dbcol.sailing_crew:order_string_repairs"),
    RepairFailure("dbcol.sailing_crew:order_string_repairfailure"),
    ChumStation("dbcol.sailing_crew:order_string_chum_station"),
    TrawlingNet("dbcol.sailing_crew:order_string_trawling_net"),
    TrawlingNetCatch("dbcol.sailing_crew:order_string_trawling_net_catch"),
}

internal object CrewRoster {

    const val REGISTRAR_1OP: String = "npc.sailing_crew_manager_1op"

    const val REGISTRAR_2OP: String = "npc.sailing_crew_manager_2op"

    const val REGISTRAR_NAME: String = "Crew Registrar"

    const val INTERFACE_CREW: String = "interface.sailing_crew"

    const val MAX_CREW_SLOTS: Int = 5

    const val SLOT_EMPTY: Int = 0

    const val SLOT_POSITION_DEFAULT: Int = 0

    const val VARBIT_SELECTED_CREW_ID: String = "varbit.sailing_crew_selected_crew_id"

    const val VARBIT_SELECTED_SLOT_ID: String = "varbit.sailing_crew_selected_slot_id"

    const val VARBIT_CREW_ON_DOCK: String = "varbit.sailing_crew_on_dock"

    fun slotVar(slot: Int): String {
        require(slot in 1..MAX_CREW_SLOTS) { "Crew slot out of range: $slot" }
        return "varbit.sailing_crew_slot_$slot"
    }

    fun slotPositionVar(slot: Int): String {
        require(slot in 1..MAX_CREW_SLOTS) { "Crew slot out of range: $slot" }
        return "varbit.sailing_crew_slot_${slot}_position"
    }

    fun heldCargoVar(index: Int): String {
        require(index in 0 until MAX_CREW_SLOTS) { "Held cargo index out of range: $index" }
        return "varbit.sailing_crew_held_cargo_${index}_amount"
    }

    const val EXPECTED_ROW_COUNT: Int = 10

    const val OQ13_CREW_LEVELS_ARE_WIKI_ONLY: Boolean = true

    const val CRATES_2_MIN_DECKHANDINESS: Int = 3

    const val BANK_CARGO_MIN_DECKHANDINESS: Int = 2

    const val HELMSMANSHIP_MIN_FOR_SLOOP: Int = 3

    val ALL: List<CrewMate> =
        listOf(
            mate("generic_1", "Jobless Jim", "Port Sarim", 40, Triple(2, 1, 3)),
            // Ada shares one parent across both recruitment states; her NPC block is shorter.
            mate(
                "generic_2",
                "Adventurer Ada",
                "The Pandemonium",
                55,
                Triple(3, 2, 1),
                unrecruitedNpc = "npc.sailing_crew_generic_2",
                recruitedNpc = null,
            ),
            mate("generic_3", "Jittery Jim", "Port Khazard", 70, Triple(1, 4, 1)),
            mate("generic_4", "Jolly Jim", "Brimhaven", 85, Triple(1, 1, 4)),
            // Jakob's ship variant is itself a multinpc parent.
            mate("generic_5", "Sailor Jakob", "Catherby", 95, Triple(4, 1, 1)),
            mate("werewolf", "Oarswoman Olga", "Burgh de Rott", 65, Triple(2, 4, 2)),
            mate("fremennik", "Bosun Zarah", "Etceteria", 80, Triple(3, 3, 2)),
            mate("spirit_angler", "Spotter Virginia", "Ruins of Unkah", 90, Triple(4, 2, 2)),
            // Siad has no unrecruited parent or met flag.
            mate(
                "captain_siad",
                "Ex-Captain Siad",
                "Desert Mining Camp",
                50,
                Triple(3, 2, 3),
                unrecruitedNpc = null,
                metVarbit = null,
            ),
            // Jenkins has two visibility varbits.
            mate(
                "ghost_jenkins",
                "Cabin Boy Jenkins",
                "Crandor",
                60,
                Triple(2, 2, 4),
                visibilityVarbits =
                    listOf(
                        "varbit.sailing_crew_ghost_jenkins_visibility_crandor",
                        "varbit.sailing_crew_ghost_jenkins_visibility_control",
                    ),
            ),
        )

    private fun mate(
        key: String,
        displayName: String,
        location: String,
        wikiSailingLevel: Int,
        expectedStats: Triple<Int, Int, Int>,
        unrecruitedNpc: String? = "npc.sailing_crew_${key}_unrecruited",
        recruitedNpc: String? = "npc.sailing_crew_${key}_recruited",
        metVarbit: String? = "varbit.sailing_crew_${key}_met",
        visibilityVarbits: List<String> = listOf("varbit.sailing_crew_${key}_visibility"),
    ): CrewMate =
        CrewMate(
            key = key,
            dbrow = "dbrow.sailing_crew_$key",
            displayName = displayName,
            location = location,
            wikiSailingLevel = wikiSailingLevel,
            expectedStats = expectedStats,
            unrecruitedNpc = unrecruitedNpc,
            recruitedNpc = recruitedNpc,
            worldNpc = "npc.sailing_crew_${key}_world",
            dockNpc = "npc.sailing_crew_${key}_dock",
            shipNpc = "npc.sailing_crew_${key}_ship",
            shipOpNpc = "npc.sailing_crew_${key}_ship_op",
            shipNoOpNpc = "npc.sailing_crew_${key}_ship_no_op",
            cargoNpcs =
                listOf(
                    "npc.sailing_crew_${key}_cargo",
                    "npc.sailing_crew_${key}_cargo_2",
                    "npc.sailing_crew_${key}_cargo_3",
                ),
            unlockedVarbit = "varbit.sailing_crew_${key}_unlocked",
            metVarbit = metVarbit,
            visibilityVarbits = visibilityVarbits,
        )
}
