package org.rsmod.content.quest.area.varrock

import org.rsmod.map.CoordGrid

internal object DemonSlayerStage {
    const val NotStarted: Int = 0

    const val ArisSpoken: Int = 1

    const val KeysNamed: Int = 2

    const val Complete: Int = 3
}

internal object DemonSlayerRefs {
    const val QuestKey: String = "quest_demonslayer"

    const val Aris: String = "npc.aris"
    const val Prysin: String = "npc.sir_prysin"
    const val PrysinSilverlight: String = "npc.sir_prysin_silverlight"
    const val Rovin: String = "npc.captain_rovin"
    const val Traiborn: String = "npc.traiborn"
    const val Wally: String = "npc.qip_ds_wally"
    const val Denath: String = "npc.qip_ds_dark_wizard_denath_sneaky"
    const val Wizard1: String = "npc.qip_ds_young_dark_wizard1"
    const val Wizard2: String = "npc.qip_ds_young_dark_wizard2"
    const val Wizard3: String = "npc.qip_ds_young_dark_wizard3"
    const val Delrith: String = "npc.delrith"
    const val DelrithWeakened: String = "npc.delrith_weakened"

    const val Silverlight: String = "obj.silverlight"

    const val Key1: String = "obj.silverlight_key_1"

    const val Key2: String = "obj.silverlight_key_2"

    const val Key3: String = "obj.silverlight_key_3"

    const val Coins: String = "obj.coins"
    const val Bones: String = "obj.bones"
    const val BucketEmpty: String = "obj.bucket_empty"
    const val BucketWater: String = "obj.bucket_water"

    const val DrainWithKey: String = "loc.qip_ds_questdrain_key"

    const val DrainNoKey: String = "loc.qip_ds_questdrain_nokey"

    const val Drain: String = "loc.questdrain"

    const val SewerKey: String = "loc.qip_ds_rustykey_mud"

    const val SewerPipe: String = "loc.qip_ds_keypipe"

    const val ManholeClosed: String = "loc.manholeclosed"
    const val ManholeOpen: String = "loc.manholeopen"

    const val SilverlightCase: String = "loc.qip_ds_silverlight_case"

    const val Wardrobe: String = "loc.qip_ds_wizards_key_wardrobe_magic"

    const val CrystalBallTable: String = "loc.fai_varrock_crystalballtable"

    const val StoneTable: String = "loc.qip_ds_stone_table"

    const val StoneTableExplosion: String = "loc.qip_ds_stone_table_explosion"
    const val StoneTableSummoning: String = "loc.qip_ds_stone_table_summoning"
    const val StoneTableDestroyed: String = "loc.qip_ds_stone_table_destroyed"

    const val SeqCrystalBallLeave: String = "seq.qip_ds_crystalball_leave"
    const val SeqCrystalBallReturn: String = "seq.qip_ds_crystalball_return"

    const val SeqArisAttain: String = "seq.qip_ds_attaining_crystalball"

    const val SeqArisReturn: String = "seq.qip_ds_returning_crystalball"
    const val SeqWardrobeDisappear: String = "seq.qip_ds_wardrobe_disappear"
    const val SeqCaseOpen: String = "seq.qip_ds_silverlight_case_open"
    const val SeqPrysinOpenCase: String = "seq.demon_silverlight_open"
    const val SeqPrysinTakeSword: String = "seq.demon_silverlight_take"
    const val SeqPrysinPresentStart: String = "seq.qip_ds_presenting_silverlight_start"
    const val SeqPrysinPresentMiddle: String = "seq.qip_ds_presenting_sword_middle"
    const val SeqPrysinPresentEnd: String = "seq.qip_ds_presenting_sword_end"
    const val SeqWizardChanting: String = "seq.qip_ds_dark_wizard_chanting"
    const val SeqTableExplosion: String = "seq.qip_ds_table_explosion"
    const val SeqDelrithSummoned: String = "seq.qip_ds_delrith_summoned"
    const val SeqDelrithBanished: String = "seq.qip_ds_delrith_banished"
    const val SeqReceivingSilverlight: String = "seq.qip_ds_recieving_silverlight"

    const val SpotanimRockFlying: String = "spotanim.qip_ds_rock_flying_spotanim"
    const val SpotanimRockBlast: String = "spotanim.qip_ds_rock_blast_spotanim"
    const val SpotanimSwordGlint: String = "spotanim.qip_ds_sword_glint_spotanim"

    const val SpotanimBallStart: String = "spotanim.qip_ds_crystallball_spotanim_start"

    const val SpotanimBallMiddle: String = "spotanim.qip_ds_crystallball_spotanim_middle"
    const val SpotanimBallEnd: String = "spotanim.qip_ds_crystallball_spotanim_end"

    const val SynthCrystalBallStart: String = "synth.DS_CRYSTAL_BALL_START"
    const val SynthCrystalBallAnim: String = "synth.DS_CRYSTAL_BALL_ANIM"
    const val SynthCrystalBallEnd: String = "synth.DS_CRYSTAL_BALL_END"
    const val SynthRumbling: String = "synth.RUMBLING"
    const val SynthWallySword: String = "synth.DS_WALLY_SWORD"
    const val SynthEquipSilverlight: String = "synth.EQUIP_SILVERLIGHT"
    const val SynthDrain: String = "synth.DS_DRAIN"
    const val SynthKeyFall: String = "synth.DS_KEY_FALL"
    const val SynthCoffinOpen: String = "synth.COFFIN_OPEN"
    const val SynthPick: String = "synth.PICK2"
    const val SynthBoneSpell: String = "synth.DS_BONE_SPELL"
    const val SynthCupboardFall: String = "synth.DS_CUPBOARD_FALL"
    const val SynthChestOpen: String = "synth.CHEST_OPEN"
    const val SynthCupboardDisappear: String = "synth.DS_CUPBOARD_DISSAPPEAR"
    const val SynthCupboardOpen: String = "synth.CUPBOARD_OPEN"
    const val SynthCasketOpen: String = "synth.CASKET_OPEN"
    const val SynthSummonNpc: String = "synth.SUMMON_NPC"
    const val SynthTableExplosion: String = "synth.DS_TABLE_EXPLOSION"
    const val SynthBreakTable: String = "synth.DS_BREAK_TABLE"
    const val SynthDelrithAppear: String = "synth.DS_DELRITH_APPEAR"
    const val SynthPortalOpen: String = "synth.DS_PORTAL_OPEN"
    const val SynthDelrithBanished: String = "synth.DS_DELRITH_BANISHED"
    const val SynthReplaceTable: String = "synth.DS_REPLACE_TABLE"

    const val VarbitStage: String = "varbit.demonslayer_main"
    const val VarbitSilverlightCase: String = "varbit.delrith_silverlight_case"
    const val VarbitDrainKey: String = "varbit.delrith_drain_key"
    const val VarbitDelrithSummoned: String = "varbit.delrith_seen_summoning_cutscene"
    const val VarbitWizardsKilled: String = "varbit.delrith_dark_wizards_killed"

    const val MusicWally: String = "dbrow.music_wally_cutscene"

    const val MusicDelrith: String = "dbrow.music_delrith_summoning"

    const val CompletionJingle: Int = 152

    const val DrainKeyInDrain: Int = 0

    const val DrainKeyInSewer: Int = 1
    const val DrainKeyRecovered: Int = 2
}

internal object DemonSlayerCoords {
    val StoneTable: CoordGrid = CoordGrid(3227, 3369, 0)

    val CircleCorners: List<CoordGrid> =
        listOf(
            CoordGrid(3224, 3366, 0),
            CoordGrid(3224, 3373, 0),
            CoordGrid(3231, 3366, 0),
            CoordGrid(3231, 3373, 0),
        )

    val Wizard1Tile: CoordGrid = CoordGrid(3226, 3371, 0)
    val Wizard2Tile: CoordGrid = CoordGrid(3229, 3371, 0)
    val Wizard3Tile: CoordGrid = CoordGrid(3226, 3368, 0)
    val DenathTile: CoordGrid = CoordGrid(3229, 3368, 0)

    val DenathExit: CoordGrid = CoordGrid(3236, 3368, 0)

    val FightExit: CoordGrid = CoordGrid(3212, 3373, 0)

    val SilverlightCase: CoordGrid = CoordGrid(3204, 3469, 0)

    val Wardrobe: CoordGrid = CoordGrid(3112, 3161, 1)

    val CrystalBallTable: CoordGrid = CoordGrid(3203, 3425, 0)

    val CrystalBallSound: CoordGrid = CoordGrid(3204, 3424, 0)
}
