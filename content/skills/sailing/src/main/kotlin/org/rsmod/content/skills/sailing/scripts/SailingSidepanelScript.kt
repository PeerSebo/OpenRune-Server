package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onIfClose
import org.rsmod.api.table.sailing.SailingBoatBrazierRow
import org.rsmod.api.table.sailing.SailingBoatFacilityRow
import org.rsmod.api.table.sailing.SailingBoatFacilityStatsRow
import org.rsmod.api.table.sailing.SailingBoatHotspotRow
import org.rsmod.api.table.sailing.SailingBoatHullRow
import org.rsmod.api.table.sailing.SailingBoatKeelRow
import org.rsmod.api.table.sailing.SailingBoatNameOptionsRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.api.table.sailing.SailingBoatSailRow
import org.rsmod.api.table.sailing.SailingBoatSteeringRow
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.ShipPartCategory
import org.rsmod.content.skills.sailing.activeBoatSlot
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SailingSidepanelScript : PluginScript() {
    override fun ScriptContext.startup() {
        SailingSidepanel.assertCacheAnchors()

        onIfClose(SailingSidepanel.INTERFACE) { SailingSidepanel.onClosed(player) }
    }
}

internal object SailingSidepanel {

    const val INTERFACE: String = "interface.sailing_sidepanel"
    const val PARENT: String = "component.toplevel_osrs_stretch:side0"

    const val COMPONENT_UNIVERSE: String = "component.sailing_sidepanel:universe"

    const val COMPONENT_BOAT_NAME: String = "component.sailing_sidepanel:boat_name"

    const val COMPONENT_HEALTH_BAR: String = "component.sailing_sidepanel:health_bar"

    const val COMPONENT_FACILITIES_TAB: String = "component.sailing_sidepanel:facilities_tab"

    const val COMPONENT_STATS_TAB: String = "component.sailing_sidepanel:stats_tab"

    const val COMPONENT_CREW_TAB: String = "component.sailing_sidepanel:crew_tab"

    const val COMPONENT_CREW_CAPACITY_TEXT: String =
        "component.sailing_sidepanel:crew_capacity_text"

    const val COMPONENT_FACILITIES_SCROLLABLE: String =
        "component.sailing_sidepanel:facilities_scrollable"

    const val COMPONENT_STATS_SCROLLABLE: String = "component.sailing_sidepanel:stats_scrollable"

    const val COMPONENT_CREW_SCROLLABLE: String = "component.sailing_sidepanel:crew_scrollable"

    const val COMPONENT_FACILITIES_CLICKLAYER: String =
        "component.sailing_sidepanel:facilities_content_clicklayer"

    const val VAR_VISIBLE: String = "varbit.sailing_sidepanel_visible"

    const val VAR_TABS: String = "varbit.sailing_sidepanel_tabs"

    const val VAR_VISIBLE_FROM_COMBAT: String = "varbit.sailing_sidepanel_visible_from_combat_tab"
    const val VAR_PLAYER_ROLE: String = "varbit.sailing_sidepanel_player_role"
    const val VAR_FACILITY_SAIL: String = "varbit.sailing_sidepanel_facility_sail"
    const val VAR_FACILITY_HELM: String = "varbit.sailing_sidepanel_facility_helm"
    const val VAR_FACILITY_HULL: String = "varbit.sailing_sidepanel_facility_hull"

    const val VAR_BOAT_TYPE: String = "varp.sailing_sidepanel_boat_type"

    const val VAR_BOAT_HP: String = "varbit.sailing_sidepanel_boat_hp"

    const val VAR_BOAT_HP_MAX: String = "varbit.sailing_sidepanel_boat_hp_max"

    const val VAR_BOAT_ARMOUR: String = "varp.sailing_sidepanel_boat_armour"

    const val VAR_BOAT_BASESPEED: String = "varbit.sailing_sidepanel_boat_basespeed"

    const val VAR_BOAT_SPEEDCAP: String = "varbit.sailing_sidepanel_boat_speedcap"

    const val VAR_BOAT_ACCELERATION: String = "varbit.sailing_sidepanel_boat_acceleration"

    const val VAR_BOAT_SPEEDBOOST_DURATION: String =
        "varbit.sailing_sidepanel_boat_speedboost_duration"

    const val VAR_BOAT_STORM_RESISTANCE: String = "varbit.sailing_sidepanel_boat_stormresistance"

    const val VAR_BOAT_RAPID_RESISTANCE: String = "varbit.sailing_sidepanel_boat_rapidresistance"

    const val VAR_BOAT_CRYSTALHELM_RESISTANCE: String =
        "varbit.sailing_sidepanel_boat_crystalhelm_resistance"

    const val VAR_BOAT_FETIDWATER_RESISTANT: String =
        "varbit.sailing_sidepanel_boat_fetidwater_resistant"

    const val VAR_BOAT_CRYSTALFLECKED_RESISTANT: String =
        "varbit.sailing_sidepanel_boat_crystalflecked_resistant"

    const val VAR_BOAT_TANGLEDKELP_RESISTANT: String =
        "varbit.sailing_sidepanel_boat_tangledkelp_resistant"

    const val VAR_BOAT_ICYSEAS_RESISTANT: String = "varbit.sailing_sidepanel_boat_icyseas_resistant"

    const val VAR_BOAT_MAX_WIND_CHARGES: String = "varbit.sailing_sidepanel_boat_max_wind_charges"

    const val VAR_BOAT_AMMO_SAVE_PERCENTAGE: String =
        "varbit.sailing_sidepanel_boat_ammo_save_percentage"

    const val VAR_BOAT_AUTO_REPAIR_RATE: String = "varbit.sailing_sidepanel_boat_auto_repair_rate"

    const val VAR_BOAT_STATS_NEEDS_UPDATE: String =
        "varbit.sailing_sidepanel_boat_stats_needs_update"

    const val VAR_PLAYERS_ON_BOARD_TOTAL: String = "varbit.sailing_sidepanel_players_on_board_total"

    private val WRITTEN_VARS: List<String> =
        listOf(
            VAR_VISIBLE,
            VAR_TABS,
            VAR_VISIBLE_FROM_COMBAT,
            VAR_PLAYER_ROLE,
            VAR_FACILITY_SAIL,
            VAR_FACILITY_HELM,
            VAR_FACILITY_HULL,
            VAR_BOAT_TYPE,
            VAR_BOAT_HP,
            VAR_BOAT_HP_MAX,
            VAR_BOAT_ARMOUR,
            VAR_BOAT_BASESPEED,
            VAR_BOAT_SPEEDCAP,
            VAR_BOAT_ACCELERATION,
            VAR_BOAT_SPEEDBOOST_DURATION,
            VAR_BOAT_STORM_RESISTANCE,
            VAR_BOAT_RAPID_RESISTANCE,
            VAR_BOAT_CRYSTALHELM_RESISTANCE,
            VAR_BOAT_FETIDWATER_RESISTANT,
            VAR_BOAT_CRYSTALFLECKED_RESISTANT,
            VAR_BOAT_TANGLEDKELP_RESISTANT,
            VAR_BOAT_ICYSEAS_RESISTANT,
            VAR_BOAT_MAX_WIND_CHARGES,
            VAR_BOAT_AMMO_SAVE_PERCENTAGE,
            VAR_BOAT_AUTO_REPAIR_RATE,
            VAR_BOAT_STATS_NEEDS_UPDATE,
            VAR_PLAYERS_ON_BOARD_TOTAL,
        ) + (0 until SailingBoatVars.MAX_HOTSPOTS).map(::hotspotSidepanelVar)

    private val ADDRESSED_COMPONENTS: List<String> =
        listOf(
            PARENT,
            COMPONENT_UNIVERSE,
            COMPONENT_BOAT_NAME,
            COMPONENT_HEALTH_BAR,
            COMPONENT_FACILITIES_TAB,
            COMPONENT_STATS_TAB,
            COMPONENT_CREW_TAB,
            COMPONENT_CREW_CAPACITY_TEXT,
            COMPONENT_FACILITIES_SCROLLABLE,
            COMPONENT_STATS_SCROLLABLE,
            COMPONENT_CREW_SCROLLABLE,
            COMPONENT_FACILITIES_CLICKLAYER,
        )

    enum class Tab(val varValue: Int) {
        FACILITIES(0),
        STATS(1),
        CREW(2),
    }

    fun open(access: ProtectedAccess, slot: Int, tab: Tab = Tab.FACILITIES) {
        val status = status(access.player, slot) ?: return
        selectTab(access, tab)
        prepareFacilityDisplay(access.player, status)
        // Use an overlay at 161:76; ordinary actions close a side modal.
        access.ifOpenOverlay(INTERFACE, PARENT)
        // Reopening first invokes the existing panel's close hook.
        access.player.writeSidepanelVar(VAR_VISIBLE, 1)
        access.player.writeSidepanelVar(VAR_VISIBLE_FROM_COMBAT, 1)
        refresh(access, status)
        access.ifSetEvents(
            COMPONENT_FACILITIES_CLICKLAYER,
            0..40,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
        )
    }

    fun open(access: ProtectedAccess) {
        val slot = currentBoatSlot(access.player) ?: return
        open(access, slot)
    }

    fun openTutorial(access: ProtectedAccess) {
        val player = access.player
        val boat = checkNotNull(SailingBoats.byTypeId(1))
        player.writeSidepanelVar(VAR_BOAT_TYPE, boat.rowId)
        player.writeSidepanelVar(VAR_PLAYER_ROLE, 10)
        player.writeSidepanelVar(VAR_TABS, 0)
        player.writeSidepanelVar(VAR_FACILITY_SAIL, 0)
        player.writeSidepanelVar(VAR_FACILITY_HELM, 0)
        player.writeSidepanelVar(VAR_FACILITY_HULL, 0)
        for (index in 0 until SailingBoatVars.MAX_HOTSPOTS) {
            player.writeSidepanelVar(hotspotSidepanelVar(index), 0)
        }
        access.ifOpenOverlay(INTERFACE, PARENT)
        player.writeSidepanelVar(VAR_VISIBLE, 1)
        player.writeSidepanelVar(VAR_VISIBLE_FROM_COMBAT, 1)
        access.ifSetText(COMPONENT_BOAT_NAME, "Anne and Will's boat")
        access.ifSetEvents(COMPONENT_FACILITIES_CLICKLAYER, 0..40, IfEvent.Op1)
    }

    fun close(access: ProtectedAccess) {
        if (INTERFACE !in access.player.ui) {
            onClosed(access.player)
            return
        }
        access.ifCloseSub(INTERFACE)
        onClosed(access.player)
        access.ifOpenOverlay("interface.combat_interface", PARENT)
    }

    fun close(player: Player, eventBus: EventBus) {
        val wasOpen = INTERFACE in player.ui
        if (wasOpen) player.ifCloseSub(INTERFACE, eventBus)
        onClosed(player)
        if (wasOpen) player.ifOpenOverlay("interface.combat_interface", PARENT, eventBus)
    }

    fun onClosed(player: Player) {
        player.writeSidepanelVar(VAR_VISIBLE, 0)
        player.writeSidepanelVar(VAR_VISIBLE_FROM_COMBAT, 0)
    }

    fun selectTab(access: ProtectedAccess, tab: Tab) {
        access.player.writeSidepanelVar(VAR_TABS, tab.varValue)
    }

    fun refresh(access: ProtectedAccess, slot: Int) {
        val status = status(access.player, slot) ?: return
        refresh(access, status)
    }

    private fun refresh(access: ProtectedAccess, status: BoatStatus) {
        val player = access.player
        val stats = status.statRows

        access.ifSetText(COMPONENT_BOAT_NAME, status.displayName)

        val crewCapacity = status.boat.crewCapacity ?: 0
        access.ifSetText(COMPONENT_CREW_CAPACITY_TEXT, "0/$crewCapacity")

        prepareFacilityDisplay(player, status)
        player.writeSidepanelVar(VAR_BOAT_HP_MAX, status.hpMax)
        player.writeSidepanelVar(VAR_BOAT_HP, status.currentHp)
        player.writeSidepanelVar(VAR_PLAYERS_ON_BOARD_TOTAL, 1)

        player.writeSidepanelVar(VAR_BOAT_ARMOUR, stats.sum { it.boatArmour })
        player.writeSidepanelVar(VAR_BOAT_BASESPEED, stats.sum { it.boatBasespeed })
        player.writeSidepanelVar(VAR_BOAT_SPEEDCAP, stats.sum { it.boatSpeedcap })
        player.writeSidepanelVar(VAR_BOAT_ACCELERATION, stats.sum { it.boatAcceleration })
        player.writeSidepanelVar(
            VAR_BOAT_SPEEDBOOST_DURATION,
            stats.sum { it.boatSpeedboostDuration },
        )
        player.writeSidepanelVar(VAR_BOAT_STORM_RESISTANCE, stats.sum { it.boatStormresistance })
        player.writeSidepanelVar(VAR_BOAT_RAPID_RESISTANCE, stats.sum { it.boatRapidresistance })
        player.writeSidepanelVar(
            VAR_BOAT_CRYSTALHELM_RESISTANCE,
            stats.sum { it.boatCrystalhelmResistance },
        )
        player.writeSidepanelVar(
            VAR_BOAT_FETIDWATER_RESISTANT,
            stats.sum { it.boatFetidWaterResistant },
        )
        player.writeSidepanelVar(
            VAR_BOAT_CRYSTALFLECKED_RESISTANT,
            stats.sum { it.boatCrystalFleckedResistant },
        )
        player.writeSidepanelVar(
            VAR_BOAT_TANGLEDKELP_RESISTANT,
            stats.sum { it.boatTangledKelpResistant },
        )
        player.writeSidepanelVar(VAR_BOAT_ICYSEAS_RESISTANT, stats.sum { it.boatIcySeasResistant })
        player.writeSidepanelVar(VAR_BOAT_MAX_WIND_CHARGES, stats.sum { it.boatMaxWindMotes })
        player.writeSidepanelVar(
            VAR_BOAT_AMMO_SAVE_PERCENTAGE,
            stats.sum { it.boatAmmoSavePercentage },
        )
        player.writeSidepanelVar(VAR_BOAT_AUTO_REPAIR_RATE, stats.sum { it.boatAutoRepairRate })

        // Toggle the dirty flag; setting the same value would emit no update.
        player.writeSidepanelVar(VAR_BOAT_STATS_NEEDS_UPDATE, 0)
        player.writeSidepanelVar(VAR_BOAT_STATS_NEEDS_UPDATE, 1)
    }

    // Script 9026 reads core options by list index; optional hotspots use index + 1.
    private fun prepareFacilityDisplay(player: Player, status: BoatStatus) {
        val boat = status.boat
        // Clientscript 9026 passes this to db_getfield; boat type_id is not a dbrow ID.
        player.writeSidepanelVar(VAR_BOAT_TYPE, boat.rowId)
        player.writeSidepanelVar(VAR_PLAYER_ROLE, 10)
        player.writeSidepanelVar(
            VAR_FACILITY_SAIL,
            boat.sailOption.indexOfFirst { it.rowId == status.sail?.rowId }.coerceAtLeast(0),
        )
        player.writeSidepanelVar(
            VAR_FACILITY_HELM,
            boat.steeringOption.indexOfFirst { it.rowId == status.steering?.rowId }.coerceAtLeast(0),
        )
        player.writeSidepanelVar(
            VAR_FACILITY_HULL,
            boat.hullOption.indexOfFirst { it.rowId == status.hull.rowId }.coerceAtLeast(0),
        )
        val hotspots = SailingBoats.hotspots(boat).associateBy { it.hotspotIndex }
        for (index in 0 until SailingBoatVars.MAX_HOTSPOTS) {
            val hotspot = hotspots[index]
            val stored = player.readSailingVar(SailingBoatVars.hotspot(status.slot, index))
            val optionIndex =
                if (hotspot == null || stored <= 0) {
                    -1
                } else {
                    SailingBoatHotspotRow.getRow(hotspot.layoutRowId).option.indexOfFirst {
                        it.facilityBottleId == stored
                    }
                }
            player.writeSidepanelVar(hotspotSidepanelVar(index), optionIndex + 1)
        }
    }

    private fun hotspotSidepanelVar(index: Int): String =
        "varbit.sailing_sidepanel_facility_hotspot$index"

    data class BoatStatus(
        val slot: Int,
        val boat: SailingBoatRow,
        val hull: SailingBoatHullRow,
        val keel: SailingBoatKeelRow?,
        val sail: SailingBoatSailRow?,
        val steering: SailingBoatSteeringRow?,
        val brazier: SailingBoatBrazierRow?,
        val facilities: List<SailingBoatFacilityRow>,
        val displayName: String,
        val storedHp: Int,
        val storedMaxHp: Int,
    ) {
        val statRows: List<SailingBoatFacilityStatsRow> = buildList {
            add(hull.facilityStats)
            keel?.let { add(it.facilityStats) }
            sail?.let { add(it.facilityStats) }
            steering?.facilityStats?.let { add(it) }
            brazier?.let { add(it.facilityStats) }
            for (facility in facilities) {
                facility.facilityStats?.let { add(it) }
            }
        }

        val hpMax: Int = (hull.facilityStats.boatHpMax ?: 0) + (keel?.facilityStats?.boatHpMax ?: 0)

        val currentHp: Int = if (storedMaxHp == 0) hpMax else storedHp.coerceIn(0, hpMax)
    }

    fun status(player: Player, slot: Int): BoatStatus? {
        if (player.readSailingVar(SailingBoatVars.owned(slot)) == 0) {
            return null
        }
        val typeId = player.readSailingVar(SailingBoatVars.field(slot, "type"))
        val boat = SailingBoats.byTypeId(typeId) ?: return null
        val hull = fittedHull(player, slot, boat)
        return BoatStatus(
            slot = slot,
            boat = boat,
            hull = hull,
            keel =
                fittedOptional(
                    player,
                    SailingBoatVars.keel(slot),
                    ShipPartCategory.KEEL,
                    boat.keelOption,
                ) {
                    it.sailingRequirement
                },
            sail =
                fittedOptional(
                    player,
                    SailingBoatVars.sail(slot),
                    ShipPartCategory.SAIL,
                    boat.sailOption,
                ) {
                    it.sailingRequirement
                },
            steering =
                fittedOptional(
                    player,
                    SailingBoatVars.field(slot, "steering"),
                    ShipPartCategory.STEERING,
                    boat.steeringOption,
                ) {
                    it.sailingRequirement
                },
            brazier = fittedBrazier(player, slot, boat),
            facilities = fittedFacilities(player, slot, boat),
            displayName = displayName(player, slot, boat),
            storedHp = player.readSailingVar(SailingBoatVars.field(slot, "stored_hp")),
            storedMaxHp = player.readSailingVar(SailingBoatVars.field(slot, "stored_maxhp")),
        )
    }

    fun currentBoatSlot(player: Player): Int? = player.activeBoatSlot()

    private fun fittedHull(player: Player, slot: Int, boat: SailingBoatRow): SailingBoatHullRow {
        val tiers = boat.hullOption.sortedBy { it.sailingRequirement }
        val stored = player.readSailingVar(SailingBoatVars.hull(slot))
        val index = stored - ShipPartCategory.HULL.firstOptionVarValue
        return tiers.getOrNull(index) ?: SailingBoats.baseHull(boat)
    }

    private fun <T> fittedOptional(
        player: Player,
        symbol: String,
        category: ShipPartCategory,
        options: List<T>,
        requirement: (T) -> Int,
    ): T? {
        val index = player.readSailingVar(symbol) - category.firstOptionVarValue
        if (index < 0 || options.isEmpty()) {
            return null
        }
        return options.sortedBy(requirement).getOrNull(index)
    }

    private fun fittedBrazier(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
    ): SailingBoatBrazierRow? {
        val option = boat.brazierOption ?: return null
        val stored = player.readSailingVar(SailingBoatVars.brazier(slot))
        return if (stored >= ShipPartCategory.BRAZIER.firstOptionVarValue) option else null
    }

    private fun fittedFacilities(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
    ): List<SailingBoatFacilityRow> =
        SailingBoats.hotspots(boat)
            .filter { it.hotspotIndex in 0 until SailingBoatVars.MAX_HOTSPOTS }
            .mapNotNull { hotspot ->
                val symbol = SailingBoatVars.hotspot(slot, hotspot.hotspotIndex)
                val value = player.readSailingVar(symbol)
                if (value <= 0) {
                    null
                } else {
                    SailingBoatHotspotRow.getRow(hotspot.layoutRowId).option.firstOrNull {
                        it.facilityBottleId == value
                    }
                }
            }

    private fun displayName(player: Player, slot: Int, boat: SailingBoatRow): String {
        val fallback = boat.displayname ?: boat.name
        val words =
            NAME_OPTION_DBROWS.mapIndexedNotNull { index, dbrow ->
                val value = player.readSailingVar(SailingBoatVars.name(slot, index + 1))
                val row = runCatching { SailingBoatNameOptionsRow.getRow(dbrow) }.getOrNull()
                when {
                    row == null -> null
                    value <= 0 -> row.default.takeIf { it.isNotBlank() }
                    else -> row.option.getOrNull(value - 1)
                }
            }
        return if (words.size == NAME_OPTION_DBROWS.size) words.joinToString(" ") else fallback
    }

    private val NAME_OPTION_DBROWS: List<String> =
        listOf(
            "dbrow.sailing_boat_name_prefix_options",
            "dbrow.sailing_boat_name_descriptor_options",
            "dbrow.sailing_boat_name_noun_options",
        )

    // Clamp to the field width before writing; the var setter throws on overflow.
    private fun Player.writeSidepanelVar(symbol: String, value: Int) {
        writeSailingVar(symbol, clampToVar(symbol, value))
    }

    private fun clampToVar(symbol: String, value: Int): Int {
        if (value <= 0) {
            return 0
        }
        if (symbol.startsWith("varp.")) {
            return value
        }
        val varbit = ServerCacheManager.getVarbit(symbol.asRSCM(RSCMType.VARBIT)) ?: return value
        val bitCount = varbit.bits.last - varbit.bits.first + 1
        if (bitCount >= Int.SIZE_BITS - 1) {
            return value
        }
        return value.coerceAtMost((1 shl bitCount) - 1)
    }

    fun assertCacheAnchors() {
        check(INTERFACE.asRSCM(RSCMType.INTERFACE) > 0) { "$INTERFACE does not resolve." }
        for (component in ADDRESSED_COMPONENTS) {
            check(component.asRSCM(RSCMType.COMPONENT) > 0) { "$component does not resolve." }
        }
        for (symbol in WRITTEN_VARS) {
            if (symbol.startsWith("varp.")) {
                checkNotNull(ServerCacheManager.getVarp(symbol.asRSCM(RSCMType.VARP))) {
                    "$symbol does not resolve to a varp."
                }
            } else {
                checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM(RSCMType.VARBIT))) {
                    "$symbol does not resolve to a varbit."
                }
            }
        }
        val tabs = checkNotNull(ServerCacheManager.getVarbit(VAR_TABS.asRSCM(RSCMType.VARBIT)))
        val tabsMax = (1 shl (tabs.bits.last - tabs.bits.first + 1)) - 1
        check(Tab.entries.all { it.varValue in 0..tabsMax }) {
            "$VAR_TABS holds 0..$tabsMax, which cannot express every SailingSidepanel.Tab."
        }
    }
}

private fun List<SailingBoatFacilityStatsRow>.sum(
    selector: (SailingBoatFacilityStatsRow) -> Int?
): Int = sumOf { selector(it) ?: 0 }
