package org.rsmod.content.skills.thieving.configs

import dtx.rs.RSTable
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.content.skills.thieving.drops.PickpocketDropTables
import org.rsmod.game.entity.Player

internal object ThievingPickpocketLoot {
    fun callback(row: String): (Player) -> RSTable<Player, DropRollItem>? = when (row) {
        "dbrow.thieving_pickpocket_farmer" -> { _ -> PickpocketDropTables.farmer }
        "dbrow.thieving_pickpocket_ham_member" -> { _ -> PickpocketDropTables.hamMember }
        "dbrow.thieving_pickpocket_rogue" -> { _ -> PickpocketDropTables.rogue }
        "dbrow.thieving_pickpocket_cave_goblin" -> { _ -> PickpocketDropTables.caveGoblin }
        "dbrow.thieving_pickpocket_master_farmer" -> { player ->
            if (player.farmingLvl >= HIGH_FARMING_LEVEL) {
                PickpocketDropTables.masterFarmerHighFarming
            } else {
                PickpocketDropTables.masterFarmer
            }
        }
        "dbrow.thieving_pickpocket_wealthy_citizen" -> { _ -> PickpocketDropTables.wealthyCitizen }
        "dbrow.thieving_pickpocket_desert_bandit" -> { _ -> PickpocketDropTables.desertBandit }
        "dbrow.thieving_pickpocket_yanille_watchman" -> { _ -> PickpocketDropTables.yanilleWatchman }
        "dbrow.thieving_pickpocket_paladin" -> { _ -> PickpocketDropTables.paladin }
        "dbrow.thieving_pickpocket_gnome" -> { _ -> PickpocketDropTables.gnome }
        "dbrow.thieving_pickpocket_hero" -> { _ -> PickpocketDropTables.hero }
        "dbrow.thieving_pickpocket_vyre" -> { _ -> PickpocketDropTables.vyre }
        "dbrow.thieving_pickpocket_elf", "dbrow.thieving_pickpocket_prifddinas_elf" ->
            { _ -> PickpocketDropTables.elf }
        "dbrow.thieving_pickpocket_tzhaar_hur" -> { _ -> PickpocketDropTables.tzhaarHur }
        "dbrow.thieving_pickpocket_digsite_workman" -> { player ->
            PickpocketDropTables.digsiteWorkman(player.vars["varp.itexamlevel"])
        }
        else -> { _ -> null }
    }

    private const val HIGH_FARMING_LEVEL = 85
}
