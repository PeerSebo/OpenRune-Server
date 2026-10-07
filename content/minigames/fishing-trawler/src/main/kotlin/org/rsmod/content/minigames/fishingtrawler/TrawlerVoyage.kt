package org.rsmod.content.minigames.fishingtrawler

import org.rsmod.api.instances.InstanceSession
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

enum class TrawlerPhase {
    LOBBY,
    SAILING,
    SINKING,
    DOCKED,
}

class TrawlerVoyage(val session: InstanceSession) {
    var phase: TrawlerPhase = TrawlerPhase.LOBBY

    var lobbyStartTick: Int = -1
    var sailStartTick: Int = -1
    var dockStartTick: Int = -1

    var waterLevel: Int = 0
    var nextWaterRiseTick: Int = -1

    val leakState = IntArray(TrawlerConstants.HULL_SLOT_COUNT)

    var activeLeakSlot: Int = -1
    var nextLeakOpenTick: Int = -1

    var tentaclePortCoord: CoordGrid? = null
    var tentacleStarboardCoord: CoordGrid? = null
    var tentacleNpc: Npc? = null
    var tentacleAttacksUsed: Int = 0
    var tentacleOnStarboardNext: Boolean = false
    var nextTentacleAttackTick: Int = -1
    var tentacleDespawnTick: Int = -1

    val contribution = HashMap<Long, Int>()

    val actionsPerformed = HashMap<Long, Int>()

    val startOccupants = mutableSetOf<Long>()

    val pendingFishReward = HashMap<Long, Int>()

    var totalCatch: Int = 0

    fun addContribution(playerId: Long, points: Int) {
        val current = contribution[playerId] ?: 0
        contribution[playerId] = (current + points).coerceAtMost(TrawlerConstants.MAX_CONTRIBUTION)
        actionsPerformed[playerId] = (actionsPerformed[playerId] ?: 0) + 1
    }
}
