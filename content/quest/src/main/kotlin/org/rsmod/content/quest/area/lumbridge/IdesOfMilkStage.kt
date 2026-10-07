package org.rsmod.content.quest.area.lumbridge

import org.rsmod.map.CoordGrid

internal object IdesOfMilkStage {
    const val NotStarted: Int = 0

    const val Accepted: Int = 1

    const val IntroMilk: Int = 2

    const val IntroDone: Int = 3

    const val Interviewed: Int = 4

    const val BookSnatched: Int = 5

    const val SampleOneHeld: Int = 6

    const val SampleOneDrunk: Int = 8

    const val SampleTwoHeld: Int = 10

    const val DukeConsulted: Int = 12

    const val GillieJudging: Int = 14

    const val SampleTwoDrunk: Int = 16

    const val BullTaskGiven: Int = 18

    const val BullSlain: Int = 20

    const val ToldGillie: Int = 21

    const val Complete: Int = 22
}

internal object IdesOfMilkRefs {
    const val QuestKey: String = "quest_idesofmilk"

    const val VarbitStage: String = "varbit.cowquest"
    const val VarbitGillieInformed: String = "varbit.cowquest_gillie_information"
    const val VarbitSethInformed: String = "varbit.cowquest_seth_information"

    const val VarbitRewardClaimed: String = "varbit.cowquest_reward"

    const val VarbitRewardLamp: String = "varbit.cowboss_reward_lamp"

    const val VarbitDontAskAgain: String = "varbit.cowboss_regular_dontaskagain"

    const val Cassius: String = "npc.cowboss_farmer"

    const val Gillie: String = "npc.gillie_the_milkmaid"

    const val Seth: String = "npc.favour_seth_groats"

    const val Duke: String = "npc.duke_of_lumbridge"

    const val Bull: String = "npc.cowboss"

    // The pen uses a shared parent; use a separate fight actor instead of copying that parent into the instance.
    const val PenBull: String = "npc.cowboss_mapped"

    const val Book: String = "obj.cowquest_husbandry_book"
    const val Sample1: String = "obj.cowquest_milk_sample_1"
    const val Sample2: String = "obj.cowquest_milk_sample_2"

    const val CowbellAmulet: String = "obj.cowbell_amulet_empty"

    const val CowbellAmuletCharged: String = "obj.cowbell_amulet"

    const val AirRune: String = "obj.airrune"

    const val VarObjCowbellCharges: String = "varobj.cowbell_charges"

    const val CowbellMaxCharges: Int = 1000

    const val RewardLamp: String = "obj.cowboss_reward_lamp"

    // The teleport sequence embeds two sounds; send only its separate cast and arrival sounds.
    const val SeqCowbellTeleport: String = "seq.human_cowboss_teleport"

    const val SpotanimCowbellTeleport: String = "spotanim.vfx_human_cowboss_teleport01"

    const val SynthCowbellRing: String = "synth.BELL"

    const val SynthCowbellCharge: String = "synth.CRYSTAL_ARMOUR_04"

    const val SynthCowbellUncharge: String = "synth.CRYSTAL_ARMOUR_05"

    const val SynthCowbellTeleportCast: String = "synth.POOL_PLOP"

    const val SynthCowbellTeleportArrive = "synth.ides_cowbell_teleport_arrive"

    const val SynthCowbellTeleportCastDelay: Int = 50

    const val SynthCowbellTeleportArriveDelay: Int = 57

    val SynthCowbellRingDelays: List<Int> = listOf(0, 8, 16)

    const val CowbellTeleportDelay: Int = 3

    // Keep the arrival frame visible before resetting the animation.
    const val CowbellTeleportResetDelay: Int = 2

    const val Shelf: String = "loc.cowquest_seth_shelf"

    const val GateEntryLeft: String = "loc.fencegate_l_cowboss_start"

    const val GateEntryRight: String = "loc.fencegate_r_cowboss_start"

    const val GateEntryLeftNoop: String = "loc.fencegate_l_cowboss_start_noop"

    const val GateEntryRightNoop: String = "loc.fencegate_r_cowboss_start_noop"

    const val GateExitLeft: String = "loc.fencegate_l_cowboss_exit"

    const val GateExitRight: String = "loc.fencegate_r_cowboss_exit"

    const val SeqBullSpawn: String = "seq.cow_boss_spawn"
    const val SeqBullAttack: String = "seq.cow_boss_attack"
    const val SeqBullDeath: String = "seq.cow_boss_death"
    const val SeqBullStomp: String = "seq.cow_boss_stomp"
    const val SeqBullCharge: String = "seq.cow_boss_charge"

    const val SeqDrink: String = "seq.human_eat"

    const val SpotanimStomp: String = "spotanim.vfx_cowboss_stomp_impact01"

    const val SynthPick2: String = "synth.PICK2"
    const val SynthLiquid: String = "synth.LIQUID"
    const val SynthLeatherHit: String = "synth.LEATHER_HIT"

    const val SynthBullSpawn = "synth.ides_brutus_spawn"

    const val SynthSnort = "synth.ides_brutus_snort"

    const val SynthGrowl = "synth.ides_brutus_growl"

    const val MusicBossMoosic: String = "dbrow.music_brutus_cow_boss"

    const val CompletionJingle: Int = 154

    const val QueueStomp: String = "queue.cowquest_bull_stomp"

    const val QueueCharge: String = "queue.cowquest_bull_charge"

    // Restore movement after recovery, not on the special impact tick.
    const val QueueRecover: String = "queue.cowquest_bull_recover"

    const val TimerMilkSplat: String = "timer.cowquest_milk_splat"

    const val TimerBossRespawn: String = "timer.cowquest_brutus_respawn"

    const val RecommendedCombat: Int = 15

    const val InstanceSettingsRow: String = "dbrow.instance_cowboss"
}

internal object IdesOfMilkCoords {
    val BullSpawn: CoordGrid = CoordGrid(3261, 3287, 0)

    val FightEntry: CoordGrid = CoordGrid(3262, 3294, 0)

    val FightEntryFace: CoordGrid = CoordGrid(3262, 3292, 0)

    val FightExit: CoordGrid = CoordGrid(3262, 3290, 0)

    val EntryGateLeft: CoordGrid = CoordGrid(3262, 3294, 0)

    val EntryGateRight: CoordGrid = CoordGrid(3263, 3294, 0)

    val ExitGateRight: CoordGrid = CoordGrid(3253, 3267, 0)

    val ExitGateLeft: CoordGrid = CoordGrid(3253, 3266, 0)

    val CowbellTeleport: CoordGrid = CoordGrid(3259, 3276, 0)
}
