package org.rsmod.content.skills.magic.spell.teleports.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

internal fun standardSpellTeleportsTable() = dbTable("dbtable.standard_spell_teleports", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("spell", 1, VarType.OBJ)
    column("destination", 2, VarType.COORDGRID)
    column("destination_level", 3, VarType.INT)
    column("has_alternate", 4, VarType.BOOLEAN)
    column("alternate_destination", 5, VarType.COORDGRID)
    column("alternate_destination_level", 6, VarType.INT)
    column("required_quest", 7, VarType.STRING)
    column("locked_message", 8, VarType.STRING)
    column("missing_destination_message", 9, VarType.STRING)
    column("alternate_missing_destination_message", 10, VarType.STRING)
    column("is_home", 11, VarType.BOOLEAN)
    column("ancient_style", 12, VarType.BOOLEAN)

    for ((index, teleport) in teleports.withIndex()) {
        row("dbrow.standard_spell_teleport_${teleport.key}") {
            column(0, index)
            columnRSCM(1, teleport.spellObj)
            teleport.destination?.let { columnCoord(2, it) }
            teleport.destinationLevel?.let { column(3, it) }
            column(4, teleport.alternate != null)
            teleport.alternate?.destination?.let { columnCoord(5, it) }
            teleport.alternate?.destinationLevel?.let { column(6, it) }
            teleport.requiredQuest?.let { column(7, it) }
            column(8, teleport.lockedMessage)
            column(9, teleport.missingDestinationMessage)
            teleport.alternate?.let { column(10, it.missingDestinationMessage) }
            column(11, teleport.key == "home" || teleport.key == "edgeville_home")
            column(12, teleport.ancientStyle)
        }
    }
}

private data class TeleportDefinition(
    val key: String,
    val spellObj: String,
    val destination: CoordGrid? = null,
    val destinationLevel: Int? = null,
    val alternate: TeleportOptionDefinition? = null,
    val requiredQuest: String? = null,
    val lockedMessage: String = "You need to complete the required quest to cast this spell.",
    val missingDestinationMessage: String = "That teleport is not implemented yet.",
    val ancientStyle: Boolean = false,
)

private data class TeleportOptionDefinition(
    val destination: CoordGrid? = null,
    val destinationLevel: Int? = null,
    val missingDestinationMessage: String = "That teleport is not implemented yet.",
)

private val teleports = listOf(
    TeleportDefinition("home", "obj.48_home_teleport", CoordGrid(3222, 3218, 0)),
    TeleportDefinition(
        "varrock", "obj.25_varrock_teleport",
        alternate = TeleportOptionDefinition(destination = CoordGrid(3164, 3487, 0)),
    ),
    TeleportDefinition("lumbridge", "obj.31_lumbridge_teleport"),
    TeleportDefinition("falador", "obj.37_falador_teleport"),
    TeleportDefinition(
        "house", "obj.67_house_teleport",
        alternate = TeleportOptionDefinition(
            missingDestinationMessage = "You need to purchase a house before you can teleport outside it.",
        ),
        missingDestinationMessage = "You need to purchase a house before you can use this spell.",
    ),
    TeleportDefinition(
        "camelot", "obj.45_camelot_teleport",
        alternate = TeleportOptionDefinition(destination = CoordGrid(2725, 3485, 0)),
    ),
    TeleportDefinition(
        "kourend_castle", "obj.cert_deadman_level99_lamp",
        requiredQuest = "quest_clientofkourend",
        lockedMessage = "You need to complete Client of Kourend to cast this spell.",
    ),
    TeleportDefinition(
        "ardougne", "obj.51_ardougne_teleport",
        requiredQuest = "quest_plaguecity",
        lockedMessage = "You need to complete Plague City to cast this spell.",
    ),
    TeleportDefinition(
        "civitas_illa_fortis", "obj.placeholder_blighted_sack_snare",
        requiredQuest = "quest_twilightspromise",
        lockedMessage = "You need to complete Twilight's Promise to cast this spell.",
    ),
    TeleportDefinition(
        "watchtower", "obj.58_watchtower_teleport",
        alternate = TeleportOptionDefinition(destination = CoordGrid(2544, 3095, 0)),
        requiredQuest = "quest_watchtowerquest",
        lockedMessage = "You need to complete Watchtower to cast this spell.",
    ),
    TeleportDefinition(
        "trollheim", "obj.61_trollheim_teleport",
        requiredQuest = "quest_eadgarsruse",
        lockedMessage = "You need to complete Eadgar's Ruse to cast this spell.",
    ),
    TeleportDefinition("ape_atoll", "obj.64_ape_atoll_teleport", destinationLevel = 1),
    TeleportDefinition(
        "boat_to_me", "obj.56_teleport_boat_to_me",
        requiredQuest = "quest_pandemonium",
        lockedMessage = "You need to complete Pandemonium to cast this spell.",
        missingDestinationMessage = "Boat teleports need boat-location support before they can be cast.",
    ),
    TeleportDefinition(
        "me_to_boat", "obj.67_teleport_me_to_boat",
        alternate = TeleportOptionDefinition(
            missingDestinationMessage = "Last boat teleports need boat-location support before they can be cast.",
        ),
        requiredQuest = "quest_pandemonium",
        lockedMessage = "You need to complete Pandemonium to cast this spell.",
        missingDestinationMessage = "Boat teleports need boat-location support before they can be cast.",
    ),
    TeleportDefinition(
        "edgeville_home", "obj.01_zaros_home_tele", CoordGrid(3087, 3496, 0),
        ancientStyle = true,
    ),
    TeleportDefinition("paddewwa", "obj.54_paddewwa_teleport", ancientStyle = true),
    TeleportDefinition("senntisten", "obj.60_senntisten_teleport", ancientStyle = true),
    TeleportDefinition("kharyrll", "obj.66_kharyllyl_teleport", ancientStyle = true),
    TeleportDefinition("lassar", "obj.72_lassar_teleport", ancientStyle = true),
    TeleportDefinition("dareeyak", "obj.78_dareeyak_teleport", ancientStyle = true),
    TeleportDefinition("carrallanger", "obj.84_carrallagar_teleport", ancientStyle = true),
    TeleportDefinition("annakarl", "obj.90_annakarl_teleport", ancientStyle = true),
    TeleportDefinition("ghorrock", "obj.96_ghorrock_teleport", ancientStyle = true),
    TeleportDefinition("moonclan", "obj.69_tele_moonclan"),
    TeleportDefinition("ourania", "obj.71_tele_zmialtar"),
    TeleportDefinition("waterbirth", "obj.72_tele_waterbirth"),
    TeleportDefinition("barbarian_outpost", "obj.75_tele_barb_outpost"),
    TeleportDefinition("port_khazard", "obj.78_tele_port_khazard"),
    TeleportDefinition("fishing_guild", "obj.85_tele_fish_guild"),
    TeleportDefinition("catherby", "obj.87_tele_catherby"),
    TeleportDefinition("ice_plateau", "obj.89_tele_ghorrock"),
    TeleportDefinition("arceuus_library", "obj.br_mithril_platebody"),
    TeleportDefinition("draynor_manor", "obj.br_mithril_platelegs"),
    TeleportDefinition("battlefront", "obj.23_teleport_battlefront"),
    TeleportDefinition("mind_altar", "obj.br_greendhide_body"),
    TeleportDefinition("salve_graveyard", "obj.br_greendhide_chaps"),
    TeleportDefinition("fenkenstrains_castle", "obj.br_moonclan_body"),
    TeleportDefinition("west_ardougne", "obj.br_moonclan_legs"),
    TeleportDefinition("harmony_island", "obj.br_xeric_body"),
    TeleportDefinition("forgotten_cemetery", "obj.br_xeric_legs"),
    TeleportDefinition("barrows", "obj.br_air_staff"),
    TeleportDefinition("ape_atoll_dungeon", "obj.br_dragon_helm"),
)
