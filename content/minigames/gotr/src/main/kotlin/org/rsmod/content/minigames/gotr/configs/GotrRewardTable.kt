package org.rsmod.content.minigames.gotr.configs

import dtx.rs.RSDropTable
import dtx.rs.locs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

// Declare subtables before the main table: top-level initialization follows source order.

private val essencePouchTable =
    rsPlayerWeightedTable(total = 4) {
        name("GOTR pouch reward")
        1 weight "obj.rcu_pouch_small" count 1
        1 weight "obj.rcu_pouch_medium" count 1
        1 weight "obj.rcu_pouch_large" count 1
        1 weight "obj.rcu_pouch_giant" count 1
    }

private val portalTalismanTable =
    rsPlayerWeightedTable(total = 12) {
        name("GOTR talisman reward")
        1 weight "obj.air_talisman" count 1
        1 weight "obj.water_talisman" count 1
        1 weight "obj.earth_talisman" count 1
        1 weight "obj.fire_talisman" count 1
        1 weight "obj.mind_talisman" count 1
        1 weight "obj.body_talisman" count 1
        1 weight "obj.cosmic_talisman" count 1
        1 weight "obj.chaos_talisman" count 1
        1 weight "obj.nature_talisman" count 1
        1 weight "obj.law_talisman" count 1
        1 weight "obj.death_talisman" count 1
        1 weight "obj.blood_talisman" count 1
    }

private val runeStackTable =
    rsPlayerWeightedTable(total = 85) {
        name("GOTR rune reward")
        12 weight "obj.airrune" count 4..10
        10 weight "obj.waterrune" count 4..10
        10 weight "obj.earthrune" count 4..10
        8 weight "obj.firerune" count 4..10
        8 weight "obj.mindrune" count 4..10
        6 weight "obj.bodyrune" count 4..10
        6 weight "obj.chaosrune" count 4..10
        6 weight "obj.cosmicrune" count 4..10
        5 weight "obj.naturerune" count 4..10
        5 weight "obj.lawrune" count 4..10
        5 weight "obj.deathrune" count 4..10
        4 weight "obj.bloodrune" count 4..10
    }

@field:RegisterDropTable
@JvmField
public val gotrRewardTable: RSDropTable<Player, DropRollItem> =
    RSDropTable(
        tableIdentifier = "Guardians of the Rift Rewards",
        locs = locs("loc.gotr_reward_rift"),
        // Do not add a fourth combined dye roll; the three colour rolls already supply its probability.
        tertiaries =
            rsPlayerTertiaryTable {
                1 outOf 20 weight "obj.gotr_book" count 1 condition
                    { player -> player.vars["varbit.gotr_book_obtained"] != 1 }
                1 outOf 200 weight "obj.catalytic_talisman" count 1
                1 outOf 300 weight "obj.abyssal_needle" count 1 condition
                    { player -> player.vars["varbit.gotr_unlocked_needle"] != 1 }
                1 outOf 700 weight "obj.abyssal_lantern" count 1
                1 outOf 1200 weight "obj.abyssal_red_dye" count 1
                1 outOf 1200 weight "obj.abyssal_green_dye" count 1
                1 outOf 1200 weight "obj.abyssal_blue_dye" count 1
                1 outOf 4000 weight "obj.abyssalpet" count 1
            },
        // TODO: verify rune weights and apply the pouch-owned denominator change from 140 to 125.
        mainTable =
            rsPlayerWeightedTable(total = 140) {
                name("Guardians of the Rift Rewards")
                15 weight essencePouchTable
                16 weight portalTalismanTable
                18 weight "obj.abyssal_pearl" count 14..16
                5 weight "obj.gotr_intricate_pouch" count 1
                1 weight "obj.abyssal_ashes" count 1
                85 weight runeStackTable
            },
    )
