package org.rsmod.content.areas.misc.barbarianvillage.configs

import org.rsmod.map.CoordGrid

internal object StrongholdRefs {

    const val Entrance: String = "loc.sos_dung_ent_open"

    const val ExplorerBag: String = "loc.sos_skelly_bag"

    const val CreditsPlaque: String = "loc.sos_thankplayers"

    val FirstLevelEntry: CoordGrid = CoordGrid(1859, 5243, 0)

    val SurfaceExit: CoordGrid = CoordGrid(3081, 3421, 0)

    val WarStart: CoordGrid = FirstLevelEntry

    val FamineStart: CoordGrid = CoordGrid(2042, 5245, 0)

    val PestilenceStart: CoordGrid = CoordGrid(2123, 5252, 0)

    val DeathStart: CoordGrid = CoordGrid(2358, 5215, 0)

    const val WarChest: String = "loc.sos_war_chest"
    const val FamineSack: String = "loc.sos_fam_sack"
    const val PestilenceChest: String = "loc.sos_pest_chest"
    const val DeathCradle: String = "loc.sos_death_pram"

    const val WarLadderDown: String = "loc.sos_war_ladd_down"
    const val FamineLadderDown: String = "loc.sos_fam_ladd_down"
    const val PestilenceLadderDown: String = "loc.sos_pest_ladd_down"

    const val WarLadderUp: String = "loc.sos_war_ladd_up"
    const val FamineLadderUp: String = "loc.sos_fam_ladd_up"
    const val PestilenceLadderUp: String = "loc.sos_pest_ladd_up"

    const val DeathLadderUp: String = "loc.sos_death_ladd_up"

    const val WarPortal: String = "loc.sos_war_portal"
    const val FaminePortal: String = "loc.sos_fam_portal"
    const val PestilencePortal: String = "loc.sos_pest_portal"
    const val DeathPortal: String = "loc.sos_death_portal"

    const val WarChain: String = "loc.sos_war_chainbottom"

    const val FamineRope: String = "loc.sos_fam_rope_up"

    const val PestilenceRope: String = "loc.sos_pest_rope_up"

    // The two rope placements differ: the treasure-room rope exits the dungeon; the maze rope does not.
    const val DeathRope: String = "loc.sos_death_rope_up"

    val DeathExitRope: CoordGrid = CoordGrid(2350, 5215, 0)

    const val WarDoor: String = "loc.sos_war_door_face"
    const val WarDoorMirror: String = "loc.sos_war_door_face_mirr"
    const val FamineDoor: String = "loc.sos_fam_door_face"
    const val FamineDoorMirror: String = "loc.sos_fam_door_face_mirr"
    const val PestilenceDoor: String = "loc.sos_pest_door_face"
    const val PestilenceDoorMirror: String = "loc.sos_pest_door_face_mirr"
    const val DeathDoor: String = "loc.sos_death_door_face"
    const val DeathDoorMirror: String = "loc.sos_death_door_face_mirr"

    const val WarDoorNpc: String = "npc.sos_door_war"

    const val FamineDoorNpc: String = "npc.sos_door_fam"
    const val PestilenceDoorNpc: String = "npc.sos_door_pest"
    const val DeathDoorNpc: String = "npc.sos_door_death"

    const val WarDoorTitle: String = "Gate of War"

    const val FamineDoorTitle: String = "Rickety Door"
    const val PestilenceDoorTitle: String = "Oozing Barrier"
    const val DeathDoorTitle: String = "Portal of Death"

    // Spawn the multinpc parent and bind its visible leaf as well.
    const val Litara: String = "npc.sos_barb"

    const val Solztun: String = "npc.sos_barb_spirit"

    const val DoorDragSeq: String = "seq.sos_security_door_drag"

    const val DoorAppearSeq: String = "seq.sos_security_door_appear"

    const val ClimbSeq: String = "seq.human_reachforladder"

    const val ThroughDoorSound: String = "synth.SOS_THROUGH_DOOR"
    const val CreakSound: String = "synth.SOS_CREAKS"
    const val ChoirSound: String = "synth.SOS_CHOIR"

    const val BagSound: String = "synth.PICK"

    const val Coins: String = "obj.coins"

    const val NotesBook: String = "obj.sos_stronghold_book"

    const val FancyBoots: String = "obj.sos_boots"
    const val FightingBoots: String = "obj.sos_boots2"
    const val FancierBoots: String = "obj.sos_boots3"

    const val CreditsInterface: String = "interface.messagescroll2"

    const val FlapVarbit: String = "varbit.sos_emote_flap"
    const val SlapHeadVarbit: String = "varbit.sos_emote_doh"
    const val IdeaVarbit: String = "varbit.sos_emote_idea"
    const val StampVarbit: String = "varbit.sos_emote_stamp"

    const val BrotherFoundVarbit: String = "varbit.sos_brother_found"

    // These are archive-11 group IDs, not jingle gameval IDs.
    const val WarJingle: Int = 157

    const val FamineJingle: Int = 179
    const val PestilenceJingle: Int = 177
    const val DeathJingle: Int = 158
}
