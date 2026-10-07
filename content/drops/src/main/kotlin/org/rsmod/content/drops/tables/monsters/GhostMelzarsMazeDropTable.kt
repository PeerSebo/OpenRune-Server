package org.rsmod.content.drops.tables.monsters

import dtx.rs.RSDropTable
import dtx.rs.npcs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.content.drops.MelzarsMazeKeyHolders
import org.rsmod.content.drops.isNpcType
import org.rsmod.game.entity.Player

@field:RegisterDropTable
@JvmField
public val ghostMelzarsMazeDropTable: RSDropTable<Player, DropRollItem> = RSDropTable(
    tableIdentifier = "Ghost (Melzar's Maze) Drops",
    npcs = npcs("npc.dragonslayer_ghost_1_key", "npc.dragonslayer_ghost_2", "npc.dragonslayer_ghost_3", "npc.dragonslayer_ghost_4", "npc.dragonslayer_ghost_5"),
    guaranteed = rsPlayerGuaranteedTable {
        "obj.orangekey" count 1 killCondition { _, npc, _ ->
            npc.isNpcType(MelzarsMazeKeyHolders.OrangeKey)
        }
    },
)
