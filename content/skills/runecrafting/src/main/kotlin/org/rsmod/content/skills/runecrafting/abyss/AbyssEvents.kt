package org.rsmod.content.skills.runecrafting.abyss

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.skills.runecrafting.ABYSS_MINIQUEST_STAGE_COMPLETE
import org.rsmod.content.skills.runecrafting.abyssalMiniquestStage
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AbyssEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        registerWildernessEntry()
        registerObstacleRing()
        registerRifts()
    }

    private fun ScriptContext.registerWildernessEntry() {
        onOpNpc1("npc.rcu_zammy_mage1a") { grantAbyssAccess() }
        onOpNpc4("npc.rcu_zammy_mage1b") { enterAbyss() }
    }

    // TODO: replace this immediate unlock with the Enter the Abyss miniquest.
    private suspend fun ProtectedAccess.grantAbyssAccess() {
        if (player.abyssalMiniquestStage >= ABYSS_MINIQUEST_STAGE_COMPLETE) {
            return
        }
        player.abyssalMiniquestStage = ABYSS_MINIQUEST_STAGE_COMPLETE
        mesbox(
            "The Mage of Zamorak studies you for a moment, then nods. \"You may enter the Abyss.\""
        )
    }

    private suspend fun ProtectedAccess.enterAbyss() {
        if (player.abyssalMiniquestStage < ABYSS_MINIQUEST_STAGE_COMPLETE) {
            return
        }

        // TODO: implement the abyssal bracelet's entry-drain exception.
        player.statSub(
            "stat.prayer",
            constant = PRAYER_DRAIN_CONSTANT,
            percent = PRAYER_DRAIN_PERCENT,
        )

        val roll = rollAbyssLayout()

        telejump(landingTileFor(roll))
    }

    private fun landingTileFor(roll: Int): CoordGrid {
        val pin = OBSTACLE_RING_POSITIONS[roll]
        val dx = (pin.x - RING_CENTROID.x).coerceIn(-1, 1)
        val dz = (pin.z - RING_CENTROID.z).coerceIn(-1, 1)
        val x = pin.x + dx * LANDING_OFFSET_TILES
        val z = pin.z + dz * LANDING_OFFSET_TILES
        return CoordGrid(x, z, pin.level)
    }

    private fun ScriptContext.registerObstacleRing() {
        for (obstacle in AbyssObstacle.entries) {
            onOpLoc1(obstacle.loc) { op -> attemptObstacle(op.loc, obstacle) }
        }
    }

    // Only indices 0..11 are layouts; the final transform entry is the fallback.
    private fun ProtectedAccess.rollAbyssLayout(): Int {
        val roll = random.of(RING_POSITION_COUNT)
        VarPlayerIntMapSetter.set(player, "varbit.rcu_abyssal_generator", roll)
        return roll
    }

    private suspend fun ProtectedAccess.attemptObstacle(loc: BoundLocInfo, obstacle: AbyssObstacle) {
        if (obstacle == AbyssObstacle.PASSAGE) {
            crossObstacle(loc)
            return
        }

        val requiredTool = obstacle.requiredTool
        if (requiredTool != null && !inv.contains(requiredTool)) {
            mes("You need a Tinderbox to do this.")
            return
        }

        val statKey = obstacle.statKey ?: return

        if (!statRandom(statKey, low = 0, high = 255, invisibleBoost = 0)) {
            mes("You fail to get past the obstacle.")
            return
        }
        crossObstacle(loc)
        statAdvance(statKey, obstacle.xp * xpMods.get(player, statKey))
    }

    // Land beyond the rotated blockage's entire footprint.
    private suspend fun ProtectedAccess.crossObstacle(loc: BoundLocInfo) {
        val origin = loc.adjustedCentre
        val dx = (RING_CENTROID.x - origin.x).coerceIn(-1, 1)
        val dz = (RING_CENTROID.z - origin.z).coerceIn(-1, 1)
        val clearance = maxOf(loc.adjustedWidth, loc.adjustedLength) + 1
        val dest = CoordGrid(origin.x + dx * clearance, origin.z + dz * clearance, origin.level)
        val bounds = loc.bounds()
        check(dest.x !in bounds.x until bounds.x + bounds.width || dest.z !in bounds.z until bounds.z + bounds.length) {
            "crossObstacle landed inside the obstacle's own footprint: dest=$dest bounds=$bounds"
        }
        val crossTiles = maxOf(1, player.coords.chebyshevDistance(dest))
        forcedWalk(dest, crossTiles)
    }

    private fun ScriptContext.registerRifts() {
        val entranceByRune =
            RunecraftingAltarsRow.all()
                .mapNotNull { row -> row.entrance?.let { row.rune.output.internalName to it } }
                .toMap()

        for ((riftLoc, runeSymbol) in RIFT_TO_RUNE) {
            val entrance = entranceByRune[runeSymbol]
            if (entrance != null) {
                onOpLoc1(riftLoc) { telejump(entrance) }
            }
        }

        // TODO: add blood/soul rifts after verifying their altar entrances.
    }

    private companion object {
        const val PRAYER_DRAIN_CONSTANT = 999
        const val PRAYER_DRAIN_PERCENT = 0

        const val RING_POSITION_COUNT = 12

        val RING_CENTROID = CoordGrid(3039, 4831, 0)

        const val LANDING_OFFSET_TILES = 3

        val OBSTACLE_RING_POSITIONS =
            listOf(
                CoordGrid(3057, 4821, 0),
                CoordGrid(3049, 4813, 0),
                CoordGrid(3041, 4811, 0),
                CoordGrid(3026, 4813, 0),
                CoordGrid(3018, 4821, 0),
                CoordGrid(3018, 4833, 0),
                CoordGrid(3021, 4842, 0),
                CoordGrid(3028, 4849, 0),
                CoordGrid(3038, 4853, 0),
                CoordGrid(3049, 4849, 0),
                CoordGrid(3058, 4839, 0),
                CoordGrid(3060, 4830, 0),
            )

        val RIFT_TO_RUNE =
            mapOf(
                "loc.abyss_exit_to_air" to "obj.airrune",
                "loc.abyss_exit_to_mind" to "obj.mindrune",
                "loc.abyss_exit_to_water" to "obj.waterrune",
                "loc.abyss_exit_to_earth" to "obj.earthrune",
                "loc.abyss_exit_to_fire" to "obj.firerune",
                "loc.abyss_exit_to_body" to "obj.bodyrune",
                "loc.abyss_exit_to_cosmic" to "obj.cosmicrune",
                "loc.abyss_exit_to_chaos" to "obj.chaosrune",
                "loc.abyss_exit_to_nature" to "obj.naturerune",
                "loc.abyss_exit_to_law" to "obj.lawrune",
                "loc.abyss_exit_to_death" to "obj.deathrune",
            )
    }
}
