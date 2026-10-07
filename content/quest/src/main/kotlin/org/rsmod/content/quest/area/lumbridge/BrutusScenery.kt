package org.rsmod.content.quest.area.lumbridge

import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

internal object BrutusScenery {
    fun nearEntrance(coords: CoordGrid, size: Int = 1, wanderRange: Int = 0): Boolean {
        if (coords.level != 0) return false
        val minX = IdesOfMilkCoords.BullSpawn.x - 2
        val maxX = IdesOfMilkCoords.EntryGateRight.x + 2
        val minZ = IdesOfMilkCoords.BullSpawn.z - 2
        val maxZ = IdesOfMilkCoords.FightEntry.z + 2
        return coords.x - wanderRange <= maxX &&
            coords.x + size - 1 + wanderRange >= minX &&
            coords.z - wanderRange <= maxZ &&
            coords.z + size - 1 + wanderRange >= minZ
    }

    fun shouldClone(npc: Npc): Boolean {
        if (BrutusCombatGate.TYPES.any(npc::isType) || npc.type.isFollower) return false
        val cow = npc.type.name.equals("Cow", ignoreCase = true) ||
            npc.type.name.equals("Cow calf", ignoreCase = true)
        return !cow || !nearEntrance(npc.coords, npc.size, npc.wanderRange)
    }
}
