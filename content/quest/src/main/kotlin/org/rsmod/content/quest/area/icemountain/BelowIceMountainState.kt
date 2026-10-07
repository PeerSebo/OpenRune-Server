package org.rsmod.content.quest.area.icemountain

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.map.CoordGrid

internal object BelowIceMountainStage {
    const val NotStarted: Int = 0

    const val Started: Int = 5

    const val AskedWhatHappened: Int = 7

    const val CrewNamed: Int = 10

    const val FirstRecruited: Int = 15

    const val AllRecruited: Int = 20

    const val ReadyToEnter: Int = 25

    const val TrapDisarmed: Int = 30

    const val GuardianAwake: Int = 35

    const val GuardianSlain: Int = 40

    const val Complete: Int = 120
}

internal object BelowIceMountainTracks {

    const val CheckalSentToAtlas: Int = 5

    const val CheckalTrainingOffered: Int = 10

    const val CheckalTrainingDone: Int = 15

    const val CheckalAskedToFlex: Int = 20

    const val MarleyWantsSandwich: Int = 5

    const val MarleyRecipeKnown: Int = 10

    const val MarleyFed: Int = 35

    const val BurntofWantsDrink: Int = 5

    const val BurntofDrinking: Int = 10

    const val BurntofGameOffered: Int = 15

    const val Recruited: Int = 40

    const val InsideRuins: Int = 45

    const val Fled: Int = 50
}

internal var ProtectedAccess.bimMarley by intVarBit("varbit.bim_marley")

internal var ProtectedAccess.bimCheckal by intVarBit("varbit.bim_checkal")

internal var ProtectedAccess.bimBurntof by intVarBit("varbit.bim_burntof")

internal val Dialogue.bimMarley: Int
    get() = access.bimMarley

internal val Dialogue.bimCheckal: Int
    get() = access.bimCheckal

internal val Dialogue.bimBurntof: Int
    get() = access.bimBurntof

internal var ProtectedAccess.bimClaimedReward by intVarBit("varbit.bim_claimed_reward")

internal var ProtectedAccess.camdozaalRamarnoIntro by intVarBit("varbit.camdozaal_ramarno_intro")

internal var ProtectedAccess.emoteFlexUnlocked by intVarBit("varbit.emote_flex")

internal val Dialogue.ladOrLass: String
    get() = if (access.isBodyTypeA()) "lad" else "lass"

internal val Dialogue.sirOrMadam: String
    get() = if (access.isBodyTypeA()) "sir" else "madam"

internal val Dialogue.manOrWoman: String
    get() = if (access.isBodyTypeA()) "man" else "woman"

internal object BelowIceMountainRefs {
    const val QuestKey: String = "quest_belowicemountain"

    const val QuestVarp: String = "varp.bim_main"

    const val StageVarbit: String = "varbit.bim"

    const val CompletionJingle: Int = 152

    const val WillowPath: String = "npc.bim_willow_outside"

    const val WillowEntrance: String = "npc.bim_willow_outside_entrace"

    const val WillowLeaf: String = "npc.bim_willow"

    const val CheckalVillage: String = "npc.bim_checkal_barb"

    const val CheckalEntrance: String = "npc.bim_checkal_temple_outside"

    const val CheckalLeaf: String = "npc.bim_checkal"

    const val Atlas: String = "npc.bim_atlas"

    const val MarleyEdgeville: String = "npc.bim_marley_edge"

    const val MarleyEntrance: String = "npc.bim_marley_temple_outside"

    const val MarleyLeaf: String = "npc.bim_marley"

    const val BurntofPub: String = "npc.bim_burntof_pub"

    const val BurntofEntrance: String = "npc.bim_burntof_temple_outside"

    const val BurntofLeaf: String = "npc.bim_burntof"

    const val BurntofNoDrinkLeaf: String = "npc.bim_burntof_nodrink"

    const val Cook: String = "npc.fai_varrock_bluemoon_chef"

    const val Charlie: String = "npc.tramppg"

    const val RamarnoEntrance: String = "npc.camdozaal_ramarno_entrance_multi"

    const val RamarnoEntranceLeaf: String = "npc.camzodaal_ramarno_entrance"

    const val RamarnoWorkshop: String = "npc.camdozaal_ramarno_multi"

    const val RamarnoWorkshopLeaf: String = "npc.camzodaal_ramarno"

    const val WillowDesert: String = "npc.bim_willow_postquest"

    const val CheckalDesert: String = "npc.bim_checkal_postquest"

    const val MarleyDesert: String = "npc.bim_marley_postquest"

    const val BurntofDesert: String = "npc.bim_burntof_postquest"

    const val CutsceneAtlas: String = "npc.bim_cutscene_atlas"

    const val CutsceneWillow: String = "npc.bim_cutscene_willow"

    const val CutsceneMarley: String = "npc.bim_cutscene_marley"

    const val CutsceneCheckal: String = "npc.bim_cutscene_checkal"

    const val CutsceneBurntof: String = "npc.bim_cutscene_burntof"

    const val CutsceneTina: String = "npc.bim_cutscene_tina"

    const val InvisibleNpc: String = "npc.bim_invisnpc"

    const val GuardianCutscene: String = "npc.bim_golem_guardian_cutscene"

    const val Guardian: String = "npc.bim_golem_boss"

    const val RamarnoCutscene: String = "npc.bim_camzodaal_ramarno_cutscene"

    const val Hobo: String = "npc.bim_hobo_cutscene"
    const val Rat: String = "npc.bim_rat"
    const val Aubury: String = "npc.bim_aubury"
    const val Man: String = "npc.bim_man"
    const val Man2: String = "npc.bim_man_2"
    const val Woman: String = "npc.bim_woman"
    const val Baraek: String = "npc.bim_baraek"
    const val StrayDog: String = "npc.bim_dog_stray"
    const val BarbarianFemale: String = "npc.bim_barbarian_female"
    const val BarbarianFemale2: String = "npc.bim_barbarian_female_2"
    const val BarbarianMale: String = "npc.bim_barbarian_male"
    const val BarbarianMale2: String = "npc.bim_barbarian_male_2"
    const val VarrockGuard: String = "npc.bim_fai_varrock_guard02"
    const val VarrockGuardF: String = "npc.bim_fai_varrock_guard02_f"
    const val VarrockGuard2: String = "npc.bim_fai_varrock_guard02_variant02"

    const val BossRock: String = "loc.bim_boss_rock"

    const val BossRockMined: String = "loc.bim_boss_rock_mined"

    const val BossRockCutscene: String = "loc.bim_boss_rock_cutscene"

    const val Explosion: String = "loc.barbassault_egg_explosion"

    const val DoorCutscene: String = "loc.bim_door_cutscene"

    const val Rubble: String = "loc.bim_rubble_3"

    const val ExitCutscene: String = "loc.bim_exit_cutscene"

    const val Tripwire: String = "loc.bim_tripwire"

    const val RuinsDoorClosed: String = "loc.bim_ruins_door_closed"

    const val RuinsDoorOpenLeft: String = "loc.bim_ruins_door_open_left"

    const val RuinsDoorOpenRight: String = "loc.bim_ruins_door_open_right"

    const val WillowsBag: String = "loc.bim_willows_bag_cutscene"

    const val EntranceMulti: String = "loc.bim_entrance"

    const val EntranceDoor: String = "loc.bim_door"

    const val RuinsExit: String = "loc.bim_exit"

    const val WillowsBagStatic: String = "loc.bim_willows_bag"

    const val SteakSandwich: String = "obj.bim_steak_sandwich"

    const val Knife: String = "obj.knife"
    const val Bread: String = "obj.bread"
    const val CookedMeat: String = "obj.cooked_meat"
    const val Cabbage: String = "obj.cabbage"
    const val Coins: String = "obj.coins"

    const val AsgarnianAle: String = "obj.asgarnian_ale"

    const val DwarvenStout: String = "obj.dwarven_stout"

    const val MindBomb: String = "obj.wizards_mind_bomb"

    const val Beer: String = "obj.beer"

    const val MiningPickaxe: String = "content.mining_pickaxe"

    const val FlexSeq: String = "seq.emote_flex"

    const val FlexLoopSeq: String = "seq.emote_flex_loop"

    const val PushupsSeq: String = "seq.bim_pushups"

    const val PushupsStandSeq: String = "seq.bim_pushups_stand"

    const val GuardianWakeSeq: String = "seq.ancient_guardian_awaken"

    const val GuardianAttackSeq: String = "seq.ancient_guardian_attack_melee"

    const val GuardianDeathSeq: String = "seq.ancient_guardian_death"

    const val WorkoutMusic: String = "Barbarian Workout"

    const val CamdozaalMusic: String = "The Ruins of Camdozaal"

    val CamdozaalArrival: CoordGrid = CoordGrid(2951, 5769, 0)

    val RamarnoEntranceSpawn: CoordGrid = CoordGrid(2951, 5779, 0)

    val RuinsEntranceReturn: CoordGrid = CoordGrid(2997, 3495, 0)

    val RuinsSurfaceReturn: CoordGrid = CoordGrid(2998, 3494, 0)
}
