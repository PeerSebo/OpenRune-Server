package org.rsmod.content.drops

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.entity.Npc

public fun Npc.isNpcType(internalName: String): Boolean = id == internalName.asRSCM(RSCMType.NPC)

public object MelzarsMazeKeyHolders {
    public const val RedKey: String = "npc.dragonslayer_giantrat_1_key"

    public const val OrangeKey: String = "npc.dragonslayer_ghost_1_key"

    public const val YellowKey: String = "npc.dragonslayer_skeleton_1_key"

    public const val BlueKey: String = "npc.dragonslayer_zombie_1_key"
}
