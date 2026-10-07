package org.rsmod.content.minigames.tempoross

import org.rsmod.api.instances.InstanceId
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

class TemporossEncounter(val instanceId: InstanceId) {
    var phase: TemporossPhase = TemporossPhase.DEPARTING

    var solo: Boolean = false

    var createdCycle: Int = 0

    var scheduledCycle: Int = 0

    var startPlayers: Int = 1

    var energyMax: Int = 0
    var energy: Int = 0
    var essenceMax: Int = 0
    var essence: Int = 0

    var intensity: Int = 0

    var cycle: Int = 0

    val cannonFlags: IntArray = intArrayOf(1, 1, 1, 1)

    var boss: Npc? = null

    val spiritPools: MutableList<Npc> = mutableListOf()

    val points: MutableMap<Long, Int> = HashMap()

    var enter: CoordGrid = CoordGrid.NULL
    var energyRegenerationRemainder: Int = 0
    var phaseStarted: Int = 0
    var lastProcessedTick: Int = -1
    val cannons = List(4) { TemporossCannon() }
    val crateNpcs = mutableListOf<Npc>()
    val fishingSpots = mutableListOf<Npc>()
    val hosts = mutableListOf<Npc>()
    val anchors = mutableMapOf<Int, LocInfo>()
    val brokenAnchors = mutableSetOf<Int>()
    val tethers = mutableMapOf<Player, TemporossTether>()
    val loaders = mutableMapOf<Player, TemporossLoader>()
    val fires = mutableMapOf<CoordGrid, TemporossFire>()
    val lightning = mutableListOf<TemporossLightning>()
    val shots = mutableListOf<TemporossShot>()
    var nextLightning = 0
    var nextWave = 0
    var waveImpact = -1
    var waveVisualStart = -1
    var waveVisualRecede = -1
    var waveVisualActive = false
    var nextTorrent = 0
    var torrentImpact = -1
    var torrentCannon = -1
    var specialSpot = -1
    var nextSpotChange = 0

    fun translate(source: CoordGrid) = CoordGrid(source.x + enter.x - org.rsmod.content.minigames.tempoross.configs.TemporossConstants.ARENA_ENTER.x,
        source.z + enter.z - org.rsmod.content.minigames.tempoross.configs.TemporossConstants.ARENA_ENTER.z, source.level)

    fun pointsFor(playerId: Long): Int = points[playerId] ?: 0

    fun addPoints(playerId: Long, delta: Int) {
        require(delta >= 0)
        points[playerId] = (pointsFor(playerId).toLong() + delta).coerceAtMost(32_000).toInt()
    }
}

// Loading and firing commit separately; ammunition survives vulnerability.
class TemporossCannon {
    val ammunition = ArrayDeque<TemporossAmmo>()
    var nextShot = 0
    var disabledUntil = 0
    var displayedFill = -1
    var displayedDisabled = false
    fun takeShot(tick: Int, surfaced: Boolean): TemporossAmmo? {
        if (!surfaced || tick < disabledUntil || tick < nextShot) return null
        val fish = ammunition.removeFirstOrNull() ?: return null
        nextShot = tick + org.rsmod.content.minigames.tempoross.configs.TemporossConstants.CANNON_INTERVAL
        return fish
    }
}
enum class TemporossAmmo(val damage: Int, val projectile: String) {
    Raw(10, "spotanim.tempoross_harpoonfish_projectile_raw"),
    Cooked(15, "spotanim.tempoross_harpoonfish_projectile"),
    Crystal(10, "spotanim.tempoross_harpoonfish_projectile_crystallised")
}
data class TemporossTether(val anchor: Int, val wasFrozen: Boolean)
data class TemporossLoader(val cannon: Int, val tick: Int, val coords: CoordGrid)
data class TemporossShot(val fish: TemporossAmmo, val impact: Int)
data class TemporossFire(val npc: Npc, val loc: LocInfo, var nextSpread: Int, var spreads: Int = 0)
data class TemporossLightning(val cloud: Npc, val shadow: LocInfo, val impact: Int)
