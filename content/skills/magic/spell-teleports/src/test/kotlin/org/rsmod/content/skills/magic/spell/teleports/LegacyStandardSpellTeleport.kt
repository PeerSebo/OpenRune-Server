package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.config.refs.params
import org.rsmod.map.CoordGrid

// Frozen from the pre-table catalogue, independently of the pack and runtime reader.
internal enum class LegacyStandardSpellTeleport(
    val spellObj: String,
    val destination: CoordGrid? = null,
    val destinationLevel: Int? = null,
    val alternate: LegacyTeleportOption? = null,
    val requiredQuest: String? = null,
    val lockedMessage: String = "You need to complete the required quest to cast this spell.",
    val missingDestinationMessage: String = "That teleport is not implemented yet.",
) {
    Home(
        "obj.48_home_teleport",
        CoordGrid(3222, 3218, 0),
    ),
    Varrock(
        "obj.25_varrock_teleport",
        alternate = LegacyTeleportOption(destination = CoordGrid(3164, 3487, 0)),
    ),
    Lumbridge("obj.31_lumbridge_teleport"),
    Falador("obj.37_falador_teleport"),
    TeleportToHouse(
        "obj.67_house_teleport",
        alternate =
            LegacyTeleportOption(
                missingDestinationMessage =
                    "You need to purchase a house before you can teleport outside it."
            ),
        missingDestinationMessage =
            "You need to purchase a house before you can use this spell.",
    ),
    Camelot(
        "obj.45_camelot_teleport",
        alternate = LegacyTeleportOption(destination = CoordGrid(2725, 3485, 0)),
    ),
    KourendCastle(
        "obj.cert_deadman_level99_lamp",
        requiredQuest = "quest_clientofkourend",
        lockedMessage = "You need to complete Client of Kourend to cast this spell.",
    ),
    Ardougne(
        "obj.51_ardougne_teleport",
        requiredQuest = "quest_plaguecity",
        lockedMessage = "You need to complete Plague City to cast this spell.",
    ),
    CivitasIllaFortis(
        "obj.placeholder_blighted_sack_snare",
        requiredQuest = "quest_twilightspromise",
        lockedMessage = "You need to complete Twilight's Promise to cast this spell.",
    ),
    Watchtower(
        "obj.58_watchtower_teleport",
        alternate = LegacyTeleportOption(destination = CoordGrid(2544, 3095, 0)),
        requiredQuest = "quest_watchtowerquest",
        lockedMessage = "You need to complete Watchtower to cast this spell.",
    ),
    Trollheim(
        "obj.61_trollheim_teleport",
        requiredQuest = "quest_eadgarsruse",
        lockedMessage = "You need to complete Eadgar's Ruse to cast this spell.",
    ),
    ApeAtoll("obj.64_ape_atoll_teleport", destinationLevel = 1),
    TeleportBoatToMe(
        "obj.56_teleport_boat_to_me",
        requiredQuest = "quest_pandemonium",
        lockedMessage = "You need to complete Pandemonium to cast this spell.",
        missingDestinationMessage =
            "Boat teleports need boat-location support before they can be cast.",
    ),
    TeleportMeToBoat(
        "obj.67_teleport_me_to_boat",
        alternate =
            LegacyTeleportOption(
                missingDestinationMessage =
                    "Last boat teleports need boat-location support before they can be cast."
            ),
        requiredQuest = "quest_pandemonium",
        lockedMessage = "You need to complete Pandemonium to cast this spell.",
        missingDestinationMessage =
            "Boat teleports need boat-location support before they can be cast.",
    );

    fun option(op: IfButtonOp): LegacyTeleportOption {
        return if (op == IfButtonOp.Op2 && alternate != null) {
            alternate
        } else {
            LegacyTeleportOption(destination, destinationLevel, missingDestinationMessage)
        }
    }

    fun destination(spell: MagicSpell, option: LegacyTeleportOption): CoordGrid? {
        val coord =
            option.destination ?: destination ?: spell.obj.paramOrNull(params.spell_telecoord)
        val level = option.destinationLevel ?: destinationLevel
        return if (coord != null && level != null) {
            coord.copy(level = level)
        } else {
            coord
        }
    }
}

internal data class LegacyTeleportOption(
    val destination: CoordGrid? = null,
    val destinationLevel: Int? = null,
    val missingDestinationMessage: String = "That teleport is not implemented yet.",
)
