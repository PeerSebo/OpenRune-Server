package org.rsmod.content.drops.tables.monsters

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.rs.RSDropTable
import dtx.rs.RSTable
import dtx.rs.npcs
import org.rsmod.api.droptable.*
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.tables.shared.SharedDropTables
import org.rsmod.content.drops.tables.shared.giantUncommonSeedTable
import org.rsmod.game.entity.Player

public class GiantWorldTable(
    private val membersTable: RSTable<Player, DropRollItem>,
    private val freeTable: RSTable<Player, DropRollItem>,
) : RSTable<Player, DropRollItem> by membersTable {
    override val tableEntries: Collection<Rollable<Player, DropRollItem>> = listOf(membersTable, freeTable)
    override fun roll(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> =
        (if (target.members) membersTable else freeTable).roll(target, otherArgs)
    override fun selectResult(target: Player, otherArgs: ArgMap): RollResult<DropRollItem> = roll(target, otherArgs)
}

private fun oborMain(members: Boolean) = rsPlayerWeightedTable(total = 135) {
    name("Obor common giant loot")
    4 weight "obj.iron_dagger" count 1
    5 weight "obj.iron_full_helm" count 1
    3 weight "obj.iron_kiteshield" count 1
    2 weight "obj.steel_longsword" count 1
    6 weight "obj.iron_arrow" count 3
    3 weight "obj.firerune" count 15
    3 weight "obj.waterrune" count 7
    3 weight "obj.lawrune" count 2
    2 weight "obj.steel_arrow" count 10
    2 weight "obj.mindrune" count 3
    2 weight "obj.cosmicrune" count 2
    2 weight "obj.naturerune" count 6
    1 weight "obj.chaosrune" count 2
    1 weight "obj.deathrune" count 2
    14 weight "obj.coins" count 38
    10 weight "obj.coins" count 52
    8 weight "obj.coins" count 15
    6 weight "obj.coins" count 8
    2 weight "obj.coins" count 88
    11 weight "obj.limpwurt_root" count 1
    6 weight "obj.beer" count 1
    2 weight "obj.body_talisman" count 1
    1 weight ringNothing()
    3 weight SharedDropTables.gem
    if (members) {
        7 weight SharedDropTables.herb
        8 weight SharedDropTables.seed
        18 weight giantUncommonSeedTable
    } else {
        7 weight "obj.coins" count 10
        18 weight "obj.coins" count 5
        8 weight nothing()
    }
}

private fun bryophytaMain(members: Boolean) = rsPlayerWeightedTable(total = 128) {
    name("Bryophyta common giant loot")
    5 weight "obj.black_sq_shield" count 1
    2 weight "obj.magic_staff" count 1
    2 weight "obj.steel_med_helm" count 1
    2 weight "obj.mithril_sword" count 1
    2 weight (if (members) "obj.mithril_spear" else "obj.coins") count (if (members) 1 else 2)
    1 weight "obj.steel_kiteshield" count 1
    4 weight "obj.lawrune" count 3
    3 weight "obj.airrune" count 18
    3 weight "obj.earthrune" count 27
    3 weight "obj.chaosrune" count 7
    3 weight "obj.naturerune" count 6
    2 weight "obj.cosmicrune" count 3
    2 weight "obj.iron_arrow" count 15
    1 weight "obj.steel_arrow" count 30
    1 weight "obj.deathrune" count 3
    1 weight (if (members) "obj.bloodrune" else "obj.coins") count (if (members) 1 else 2)
    19 weight "obj.coins" count 37
    8 weight "obj.coins" count 2
    10 weight "obj.coins" count 119
    2 weight "obj.coins" count 300
    6 weight "obj.steel_bar" count 1
    1 weight "obj.coal" count 1
    1 weight "obj.spinach_roll" count 1
    4 weight SharedDropTables.gem
    if (members) {
        5 weight SharedDropTables.herb
        35 weight giantUncommonSeedTable
    } else {
        5 weight "obj.coins" count 10
        35 weight "obj.coins" count 5
    }
}

@field:RegisterDropTable
@JvmField
public val oborDropTable = RSDropTable<Player, DropRollItem>(
    tableIdentifier = "Obor direct loot", npcs = npcs("npc.hillgiant_boss"),
    mainTable = GiantWorldTable(oborMain(true), oborMain(false)),
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 16 weight "obj.hillgiant_boss_key" count 1
        1 outOf 25 weight "obj.arceuus_corpse_giant" count 1 condition { it.members }
        1 outOf 400 weight "obj.dorgesh_construction_bone" count 1 condition { it.members }
        1 outOf 5000 weight "obj.champions_challenge_giant" count 1 condition { it.members }
        2 outOf 10025 weight "obj.dorgesh_construction_bone_curved" count 1 condition { it.members }
        1 outOf 50 weight "obj.trail_clue_beginner" count 1 transformObj { it.clueScrollTransformObj("obj.trail_clue_beginner") }
    })

@field:RegisterDropTable
@JvmField
public val bryophytaDropTable = RSDropTable<Player, DropRollItem>(
    tableIdentifier = "Bryophyta direct loot", npcs = npcs("npc.gb_mossgiant"),
    mainTable = GiantWorldTable(bryophytaMain(true), bryophytaMain(false)),
    tertiaries = rsPlayerTertiaryTable {
        1 outOf 16 weight "obj.mossy_key" count 1
        1 outOf 24 weight "obj.arceuus_corpse_giant" count 1 condition { it.members }
        1 outOf 400 weight "obj.dorgesh_construction_bone" count 1 condition { it.members }
        1 outOf 5000 weight "obj.champions_challenge_giant" count 1 condition { it.members }
        2 outOf 10025 weight "obj.dorgesh_construction_bone_curved" count 1 condition { it.members }
        1 outOf 45 weight "obj.trail_clue_beginner" count 1 transformObj { it.clueScrollTransformObj("obj.trail_clue_beginner") }
    })
