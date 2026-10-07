package org.rsmod.content.drops.tables.locs

import dtx.rs.RSDropTable
import dtx.rs.locs
import dtx.rs.npcs
import org.rsmod.api.droptable.*
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.monsters.GiantWorldTable
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.content.drops.tables.shared.giantUncommonSeedTable
import org.rsmod.game.entity.Player

private val giantChestGuaranteed = rsPlayerGuaranteedTable {
    "obj.trail_clue_beginner" count 1 transformObj { it.clueScrollTransformObj("obj.trail_clue_beginner") }
    "obj.arceuus_corpse_giant" count 1 condition { it.members }
}

private fun giantChestTertiaries(key: String) = rsPlayerTertiaryTable {
    1 outOf 16 weight key count 1
    1 outOf 400 weight "obj.dorgesh_construction_bone" count 1 condition { it.members }
    1 outOf 5000 weight "obj.champions_challenge_giant" count 1 condition { it.members }
    2 outOf 10025 weight "obj.dorgesh_construction_bone_curved" count 1 condition { it.members }
}

@field:RegisterDropTable
@JvmField
public val oborChestDropTable = RSDropTable<Player, DropRollItem>(
    tableIdentifier = "Obor key chest", npcs = npcs("npc.gb_hillgiant_chest"),
    locs = locs("loc.giantkey_chest_closed"), guaranteed = giantChestGuaranteed,
    mainTable = rsPlayerWeightedTable(total = 118) {
        name("Obor key chest")
        1 weight "obj.hillgiant_boss_club" count 1
        6 weight "obj.rune_med_helm" count 1
        5 weight "obj.rune_full_helm" count 1
        3 weight "obj.rune_chainbody" count 1
        3 weight "obj.rune_platelegs" count 1
        3 weight "obj.rune_plateskirt" count 1
        3 weight "obj.rune_kiteshield" count 1
        4 weight "obj.rune_battleaxe" count 1
        5 weight "obj.rune_longsword" count 1
        3 weight "obj.rune_2h_sword" count 1
        12 weight "obj.lawrune" count 50..99
        12 weight "obj.cosmicrune" count 60..119
        10 weight "obj.chaosrune" count 100..199
        10 weight "obj.deathrune" count 40..79
        7 weight "obj.naturerune" count 40..79
        8 weight "obj.cert_limpwurt_root" count 20
        8 weight "obj.cert_big_bones" count 50
        5 weight rsPlayerGuaranteedTable {
            "obj.cert_uncut_ruby" count 5
            "obj.cert_uncut_diamond" count 5
        }
        6 weight "obj.coins" count 10000..15000
        4 weight "obj.coins" count 12000..20000
    },
    tertiaries = giantChestTertiaries("obj.hillgiant_boss_key"))

private fun mossyChestMain(members: Boolean) = rsPlayerWeightedTable(total = 118) {
    name("Bryophyta key chest")
    1 weight "obj.gb_moss_essence" count 1
    6 weight "obj.rune_longsword" count 1
    6 weight "obj.rune_med_helm" count 2
    6 weight "obj.rune_chainbody" count 1
    6 weight "obj.rune_plateskirt" count 1
    6 weight "obj.rune_platelegs" count 1
    6 weight "obj.rune_sq_shield" count 1
    5 weight "obj.rune_sword" count 2
    3 weight "obj.cert_adamant_platebody" count 5
    if (members) { 3 weight "obj.cert_battlestaff" count 3 } else { 3 weight "obj.rune_med_helm" count 1 }
    1 weight "obj.adamant_kiteshield" count 3
    8 weight "obj.naturerune" count 100
    6 weight "obj.cosmicrune" count 100
    6 weight "obj.lawrune" count 100
    5 weight "obj.deathrune" count 100
    6 weight "obj.chaosrune" count 200
    5 weight "obj.chaosrune" count 100
    1 weight (if (members) "obj.bloodrune" else "obj.deathrune") count 100
    1 weight "obj.adamant_arrow" count 100
    6 weight "obj.runite_bar" count 2
    4 weight rsPlayerGuaranteedTable {
        "obj.cert_uncut_ruby" count 5
        "obj.cert_uncut_diamond" count 5
    }
    3 weight "obj.cert_steel_bar" count 25
    5 weight "obj.coins" count 10000
    2 weight "obj.coins" count 8000
    if (members) {
        5 weight SharedDropTables.herb
        6 weight giantUncommonSeedTable
    } else {
        5 weight "obj.cert_strength4" count 15
        6 weight "obj.rune_platelegs" count 1
    }
}

@field:RegisterDropTable
@JvmField
public val bryophytaChestDropTable = RSDropTable<Player, DropRollItem>(
    tableIdentifier = "Bryophyta key chest", npcs = npcs("npc.gb_mossgiant_chest"),
    locs = locs("loc.mossykey_chest_closed"), guaranteed = giantChestGuaranteed,
    mainTable = GiantWorldTable(mossyChestMain(true), mossyChestMain(false)),
    tertiaries = giantChestTertiaries("obj.mossy_key"))
