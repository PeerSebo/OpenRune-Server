package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

// Shield of Arrav shares varp 3403 with Scorpion Catcher; use individual varbits for its fields.
internal object SoaRefs {
    const val QuestKey = "quest_shieldofarrav"

    /** varp 3403 - shared with Scorpion Catcher; the quest owns only bits 6-7 and 15-24. */
    const val QuestVarp = "varp.scorpcatcher_secondary"

    const val StageVarbit = "varbit.shieldofarrav"

    const val CompletionJingle = 154

    const val Reldo = "npc.reldo"

    const val Baraek = "npc.baraek"

    const val Charlie = "npc.tramppg"

    const val Katrine = "npc.katrine"

    const val Weaponsmaster = "npc.weaponsmaster"

    const val Straven = "npc.straven"

    const val Jonny = "npc.jonny_the_beard"

    const val Curator = "npc.curator"

    const val KingRoald = "npc.king_roald"

    const val QuestBookcase = "loc.questbookcase"

    val BookcaseTile = CoordGrid(3212, 3493, 0)

    const val PhoenixInnerDoor = "loc.phoenixdoor"

    const val StashDoor = "loc.phoenixdoor2"

    const val StashDoorOpenLeaf = "loc.fai_varrock_poor_door_inactive"

    const val BlackArmDoor = "loc.blackarmdoor"

    const val BlackArmDoorOpenLeaf = "loc.blackarmdoor_open"

    const val CupboardShut = "loc.blackarmcupboardshut"

    const val CupboardOpen = "loc.blackarmcupboardopen"

    const val PhoenixChestShut = "loc.phoenixshutchest"

    const val PhoenixChestOpen = "loc.phoenixopenchest"

    const val PhoenixExitLadder = "loc.phoenixladder"

    const val VtamSign = "loc.qip_soa_vtam_corporation_sign"

    const val Book = "obj.the_shield_of_arrav"

    const val StoreKey = "obj.phoenixkey2"

    const val IntelReport = "obj.intelligence_report"

    const val ShieldHalfLeft = "obj.arravshield1"

    const val ShieldHalfRight = "obj.arravshield2"

    const val Crossbow = "obj.phoenix_crossbow"

    const val Certificate = "obj.arravcertificate"

    const val CertificateLeft = "obj.arravcertificate_lft"

    const val CertificateRight = "obj.arravcertificate_rht"

    const val ScrollModel = "obj.qip_soa_phoenix_crossbow_1"

    const val Coins = "obj.coins"

    const val CoinReward = 600

    const val NiceDoorOpenSound = "synth.NICEDOOR_OPEN"

    const val LockedSound = "synth.LOCKED"

    const val PickupSound = "synth.PICK2"

    const val PutDownSound = "synth.PUT_DOWN"

    const val CupboardOpenSound = "synth.CUPBOARD_OPEN"

    const val CupboardCloseSound = "synth.CUPBOARD_CLOSE"

    const val PaperTurnSound = "synth.PAPER_TURN"

    const val ClimbDownSeq = "seq.human_pickupfloor"

    const val ClimbUpSeq = "seq.human_reachforladder"

    const val RecommendedCombat = 10
}

internal object SoaStage {
    const val NotStarted = 0

    const val Started = 1

    const val Complete = 2
}

internal object PhoenixState {
    const val NotStarted = 0

    const val Accepted = 1

    const val BookRead = 2

    const val GangsNamed = 3

    const val BaraekTold = 4

    const val JonnyTask = 8

    const val Member = 9

    const val Complete = 10
}

internal object BlackArmState {
    const val NotStarted = 0

    const val GangsNamed = 1

    const val CrossbowTask = 2

    const val Member = 3

    const val Complete = 4
}

internal var ProtectedAccess.phoenixGang by intVarp("varp.phoenixgang")

internal var ProtectedAccess.blackArmGang by intVarp("varp.blackarmgang")

internal var ProtectedAccess.reldoMet by intVarBit("varbit.reldo_met")

internal var ProtectedAccess.soaCharlieMet by intVarBit("varbit.soa_charlie_met")

internal var ProtectedAccess.soaCharliePaid by intVarBit("varbit.soa_charlie_paid")

internal var ProtectedAccess.soaCharlieHint by intVarBit("varbit.soa_charlie_hint")

internal var ProtectedAccess.weaponsmasterDead by intVarBit("varbit.soa_weaponmaster_dead")

internal var Player.weaponsmasterDeadVar by intVarBit("varbit.soa_weaponmaster_dead")

internal var ProtectedAccess.soaShieldGiven by intVarBit("varbit.soa_shield_given")

internal const val ShieldGivenLeftBit = 1

internal const val ShieldGivenRightBit = 2

internal var ProtectedAccess.soaStravenTrampChat by intVarBit("varbit.soa_straven_tramp_chat")

internal var ProtectedAccess.soaStravenShieldChat by intVarBit("varbit.soa_straven_shield_chat")

internal var ProtectedAccess.soaPhoenixSource by intVarBit("varbit.soa_phoenix_source")

internal fun ProtectedAccess.soaCarries(obj: String): Boolean = invTotal(inv, obj) > 0
