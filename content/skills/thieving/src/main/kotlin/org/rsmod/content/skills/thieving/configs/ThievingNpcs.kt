package org.rsmod.content.skills.thieving.configs

import dtx.rs.RSTable
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.game.entity.Player

// Targets already use their single contentGroup for other behavior; pickpocketing binds by NPC ID.
data class PickpocketNpc(
    val npcs: List<String>,
    val displayName: String,
    val levelReq: Int,
    val xp: Double,
    val low: Int,
    val high: Int,
    val damage: Int,
    val failBark: String = ThievingBarks.GENERIC,
    val pouch: String? = null,
    val loot: (Player) -> RSTable<Player, DropRollItem>? = { null },
    val inArdougne: Boolean = false,
    val prifddinas: Boolean = false,
    val pickpocketOp: Int = 3,
    val blackjack: Boolean = false,
    val stunMovement: Int = ThievingRates.PICKPOCKET_STUN_MOVEMENT,
    val stunAction: Int = ThievingRates.PICKPOCKET_STUN_ACTION,
    val actionDelay: Int = ThievingRates.PICKPOCKET_ACTION_DELAY,
    // Direct coin rewards bypass pouches; leave pouch unset for these targets.
    val directCoins: Int? = null,
    val usesCoinPouches: Boolean = true,
    val koXp: Double = 0.0,
    val alwaysDamagesWithoutIceGloves: Boolean = false,
)

object ThievingNpcs {
    val manWoman: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_man_woman")
    val villager: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_villager")
    val farmer: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_farmer")
    val hamMember: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_ham_member")
    val warrior: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_warrior")
    val rogue: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_rogue")
    val caveGoblin: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_cave_goblin")
    val masterFarmer: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_master_farmer")
    val pirate: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_pirate")
    val guard: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_guard")
    val beardedBandit: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_bearded_bandit")
    val wealthyCitizen: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_wealthy_citizen")
    val desertBandit: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_desert_bandit")
    val knightOfArdougne: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_knight_of_ardougne")
    val knightOfVarlamore: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_knight_of_varlamore")
    val pollnivnianBandit: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_pollnivnian_bandit")
    val yanilleWatchman: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_yanille_watchman")
    val menaphiteThug: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_menaphite_thug")
    val paladin: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_paladin")
    val gnome: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_gnome")
    val hero: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_hero")
    val vyre: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_vyre")
    val elf: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_elf")
    val prifddinasElf: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_prifddinas_elf")
    val tzhaarHur: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_tzhaar_hur")
    val fremennikCitizen: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_fremennik_citizen")
    val digsiteWorkman: PickpocketNpc get() = ThievingNpcData.profile("dbrow.thieving_pickpocket_digsite_workman")

    val all: List<PickpocketNpc> get() = ThievingNpcData.all

    val blackjackTargets: List<PickpocketNpc> by lazy { all.filter(PickpocketNpc::blackjack) }

    val blackjacks: List<String> =
        listOf(
            "obj.blackjack_oak",
            "obj.roguetrader_bj_assault_oak",
            "obj.roguetrader_bj_defend_oak",
            "obj.blackjack_willow",
            "obj.roguetrader_bj_assault_willow",
            "obj.roguetrader_bj_defend_willow",
            "obj.roguetrader_bj_maple",
            "obj.roguetrader_bj_assault_maple",
            "obj.roguetrader_bj_defend_maple",
        )

    val rogueEquipment: List<String> =
        listOf(
            "obj.roguesden_helm",
            "obj.roguesden_body",
            "obj.roguesden_legs",
            "obj.roguesden_gloves",
            "obj.roguesden_boots",
        )

    val thievingCapes: List<String> =
        listOf("obj.skillcape_thieving", "obj.skillcape_thieving_trimmed", "obj.skillcape_max")

    const val GLOVES_OF_SILENCE: String = "obj.hunting_silent_gloves"
    const val DODGY_NECKLACE: String = "obj.dodgy_necklace"
    const val SHADOW_VEIL_VARBIT: String = "varbit.arceuus_shadow_veil_active"

    const val ICE_GLOVES: String = "obj.ice_gloves"
}
