package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.nothing
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val vanguardDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Vanguard Drops",
    npcs = npcs("npc.raids_vanguard_magic", "npc.raids_vanguard_melee", "npc.raids_vanguard_ranged", "npc.raids_vanguard_walking"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.raids_vanguard_book" count 1 condition { player ->
            // TODO: The book is dropped once all Vanguards are dead. No longer dropped after reading the book.
             true
        }
        "obj.raids_vial_elder_strong_4" count 1 condition { player ->
            // TODO: The Vanguard's combat style determines which potion is dropped.
             true
        }
        "obj.raids_vial_twisted_strong_4" count 1
        "obj.raids_vial_kodai_strong_4" count 1
        "obj.raids_vial_xericaid_strong_4" count (1..2) condition { player ->
            // TODO: 2 from the melee, 1 from the other two.
             true
        }
        "obj.raids_vial_revitalisation_strong_4" count 2 condition { player ->
            // TODO: Only from the ranged vanguard.
             true
        }
        "obj.raids_vial_prayer_strong_4" count 1 condition { player ->
            // TODO: Only from the magic vanguard.
             true
        }
    },
    mainTable = rsPlayerWeightedTable(total = 3) {
        name("Vanguard Drops")
        1 weight "obj.raids_vial_overload_strong_4" count 1 condition { player ->
            // TODO: At least one Overload (+)(4) is given per Vanguard room, up to a maximum of 3.
             true
        }
        2 weight nothing()
    },
)
