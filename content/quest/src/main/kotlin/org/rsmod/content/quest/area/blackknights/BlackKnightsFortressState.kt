package org.rsmod.content.quest.area.blackknights

internal object BlackKnightsFortressState {
    const val QuestKey = "quest_blackknightsfortress"

    const val QuestVarp = "varp.spy"

    const val NotStarted = 0
    const val Started = 1
    const val OverheardMeeting = 2
    const val Sabotaged = 3

    const val Complete = 4

    const val RequiredQuestPoints = 12

    const val RecommendedCombat = 15

    const val RewardCoins = 2500

    const val CompletionJingle = 152

    const val SirAmik = "npc.sir_amik_varze"
    const val SirAmikTitle = "Sir Amik Varze"

    val FortressGuards: List<String> =
        listOf(
            "npc.fortressguard",
            "npc.fortressguard_01",
            "npc.fortressguard_02",
            "npc.fortressguard_03",
            "npc.fortressguard_04",
        )

    const val GuardTitle = "Fortress Guard"

    const val EntranceGuard = "npc.fortressguard_04"

    const val MeetingGuard = "npc.fortressguard_03"

    const val Witch = "npc.fortwitch"
    const val WitchTitle = "Witch"
    const val Captain = "npc.grillknight"
    const val CaptainTitle = "Black Knight Captain"
    const val Greldo = "npc.greldo"
    const val GreldoTitle = "Greldo"
    const val BlackCat = "npc.bkf_cat"

    const val BlackCatTitle = "Black Cat"

    const val Dossier = "obj.bk_dossier"
    const val Coins = "obj.coins"

    const val RewardScrollObj = "obj.bkfortress_cauldron_broken"

    const val Cabbage = "obj.cabbage"

    const val MagicCabbage = "obj.magic_cabbage"

    const val IronChainbody = "obj.iron_chainbody"
    const val BronzeMedHelm = "obj.bronze_med_helm"

    const val BlackFullHelm = "obj.black_full_helm"
    const val BlackPlatebody = "obj.black_platebody"
    const val BlackPlatelegs = "obj.black_platelegs"
    const val BlackPlateskirt = "obj.black_plateskirt"

    const val EntranceDoor = "loc.bkfortressdoor1"

    const val MeetingDoor = "loc.bkfortressdoor2"

    const val InnerDoor = "loc.bkfortressdoor3"

    const val OpenDoor = "loc.inacbkfortressdoor"

    const val SecretWall = "loc.bksecretdoor"

    const val OpenSecretWall = "loc.bksecretdoor_inactive"

    const val Grill = "loc.witchgrill"

    const val CauldronHole = "loc.blackknighthole"

    const val Boxes = "loc.dk_boxes"

    const val LootCrate = "loc.fever_crate"

    val FrontDoors: List<String> =
        listOf("loc.inaclefayeunopenabledoorl", "loc.inaclefayeunopenabledoorr")

    const val CauldronVarbit = "varbit.spy_cauldron_multi"

    const val ArmourHintVarbit = "varbit.spy_armour_hint"

    const val QuestPointsVarp = "varp.qp"

    const val ThrowCabbageAnim = "seq.bkf_throw_cabbage"

    const val ThrowCabbageSpotanim = "spotanim.bkfortress_cabbage_thrown"

    const val CabbageProjectile = "spotanim.bkfortress_cabbage_travel"

    const val CauldronSpotanim = "spotanim.bkfortress_cauldron_cabbage_bubbles"

    const val CryAnim = "seq.emote_cry"

    const val PanicAnim = "seq.emote_panic"

    const val ReadDossierAnim = "seq.reading_dossier"

    const val ExplodeDossierAnim = "seq.exploding_dossier"

    const val ExplosionSpotanim = "spotanim.dossier_explosion_spotanim"

    const val ExplosionSound = "synth.BK_EXPLOSION"

    const val ExplosionSoundDelay = 22

    const val DossierStepTicks = 2

    const val BendToListenAnim = "seq.dttd_bend_to_listen_at_door"

    // A negative frameStep stops the sequence at the end despite maxLoops=99.
    const val ListenIdleAnim = "seq.bkf_bent_to_listen_at_door_idle"

    // Reply mode 2 resets only the loop counter; alternating sequence IDs is needed to restart the pose.
    const val ListenIdleHoldAnim = "seq.dttd_bent_to_listen_at_door_idle"

    const val GrillPoseTimer = "timer.bkf_grill_listen_pose"

    const val GrillPoseTicks = 4

    const val StandAfterListenAnim = "seq.dttd_standing_after_listen_at_door"

    const val ListenFaceToBendTicks = 1

    const val ListenBendToIdleTicks = 2

    const val ThrowSound = "synth.BK_THROW_CABBAGE"

    const val NoiseSound = "synth.BK_CABBAGE_HIT"

    const val SplashSound = "synth.BK_CABBAGE_LAND"

    const val CatSound = "synth.CAT_ATTACK"

    const val DoorSound = "synth.BIGDOOR_OPEN"

    const val WallSound = "synth.SCRAPE"

    const val StopMusic = 147

    const val CutsceneJingle = 5

    const val CutsceneMusic = 374
}
