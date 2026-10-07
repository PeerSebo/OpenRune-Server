package org.rsmod.content.areas.city.alkharid

import jakarta.inject.Inject
import org.rsmod.api.invtx.invTakeFee
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BorderGuards
@Inject
constructor(private val locRepo: LocRepository, private val npcRepo: NpcRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WestGuard) { tollGate(it.npc) }
        onOpNpc1(EastGuard) { tollGate(it.npc) }

        for (leaf in TollGateLeaves) {
            onOpLoc1(leaf) { openGateOp() }
            onOpLoc4(leaf) { payTollOp() }
        }

        for (leaf in FreePassGateLeaves) {
            onOpLoc1(leaf) { openGateOp() }
        }
    }

    private suspend fun ProtectedAccess.openGateOp() {
        val guard = nearestGuard()
        if (guard == null) {
            mes("The gate is locked.")
            return
        }
        tollGate(guard)
    }

    private suspend fun ProtectedAccess.payTollOp() {
        arriveDelay()

        if (!player.invTakeFee(fee = TollCoins)) {
            mes("You don't have enough money to pay the toll.")
            return
        }

        mes("You quickly hand over $TollCoins coins to the guard.")
        crossGate()
    }

    private fun ProtectedAccess.nearestGuard(): Npc? =
        npcRepo
            .findAll(ZoneKey.from(player.coords), zoneRadius = 1)
            .filter { it.isType(WestGuard) || it.isType(EastGuard) }
            .minByOrNull { it.coords.chebyshevDistance(player.coords) }

    private suspend fun ProtectedAccess.tollGate(npc: Npc) {
        // Use face-close cleanup; permanent NPCs otherwise keep following the last player with their gaze.
        npcPlayerFaceClose(npc)

        // Face again on arrival because movement overwrites the approach-facing angle.
        faceSquare(npc.coords)

        if (player.vars[PrinceQuestVarp] >= PrinceQuestComplete) {
            startDialogue(npc) {
                chatPlayer(neutral, "Can I come through this gate?")
                chatNpc(neutral, "You may pass for free! You are a friend of Al Kharid.")
            }
            crossGate()
            return
        }

        var paid = false

        startDialogue(npc) {
            chatPlayer(neutral, "Can I come through this gate?")
            chatNpc(neutral, "You must pay a toll of ten gold coins to pass.")

            val option =
                choice3(
                    "Yes, okay.",
                    1,
                    "Who does my money go to?",
                    2,
                    "No thank you, I'll walk around.",
                    3,
                )

            when (option) {
                1 -> {
                    chatPlayer(neutral, "Yes, okay.")

                    if (player.invTakeFee(fee = TollCoins)) {
                        paid = true
                    } else {
                        chatPlayer(sad, "Oh dear, I don't actually seem to have enough money.")
                    }
                }
                2 -> {
                    chatPlayer(quiz, "Who does my money go to?")
                    chatNpc(neutral, "The money goes to the city of Al Kharid.")
                }
                3 -> {
                    chatPlayer(neutral, "No thank you, I'll walk around.")
                    chatNpc(neutral, "Okay, suit yourself.")
                }
            }
        }

        if (!paid) {
            return
        }

        spam("You pay the guards and pass through the gate.")
        crossGate()
    }

    private suspend fun ProtectedAccess.crossGate() {
        val eastbound = player.coords.x <= WestCrossX

        val row = crossRow()
        val west = CoordGrid(WestCrossX, row, 0)
        val east = CoordGrid(EastCrossX, row, 0)
        val route = if (eastbound) listOf(west, east) else listOf(east, west)

        // Drop a leading waypoint the player already occupies - keeping it spends a tick "arriving"
        // in place, eating into the open window for no movement.
        val trimmed = route.dropWhile { it == player.coords }
        if (trimmed.isEmpty()) {
            return
        }

        val tiles = crossingTiles(trimmed)
        val openTicks = maxOf(MinOpenTicks, tiles + OpenTailTicks)

        openGate(openTicks)
        forcedWalk(trimmed, crossTiles = tiles)
    }

    private fun ProtectedAccess.openGate(openTicks: Int) {
        for (leaf in GateLeaves) {
            locRepo.add(leaf.closedTile, InvisWall, openTicks, LocAngle.West, LocShape.WallStraight)
            locRepo.add(
                leaf.openTile,
                leaf.openLoc,
                openTicks,
                leaf.openAngle,
                LocShape.WallStraight,
            )
        }
        soundSynth(GateOpenSound)
    }

    private fun ProtectedAccess.crossRow(): Int =
        if (player.coords.z >= NorthCrossRow) NorthCrossRow else SouthCrossRow

    private fun ProtectedAccess.crossingTiles(route: List<CoordGrid>): Int {
        var tiles = 0
        var from = player.coords
        for (waypoint in route) {
            tiles += from.chebyshevDistance(waypoint)
            from = waypoint
        }
        return maxOf(1, tiles)
    }

    private class GateLeaf(
        val closedTile: CoordGrid,
        val openTile: CoordGrid,
        val openLoc: String,
        val openAngle: LocAngle,
    )

    private companion object {
        private const val WestGuard = "npc.borderguard1"
        private const val EastGuard = "npc.borderguard2"

        private val TollGateLeaves =
            listOf("loc.kharidmetalgateclosedl_2op", "loc.kharidmetalgateclosedr_2op")

        private val FreePassGateLeaves =
            listOf("loc.kharidmetalgateclosedl_1op", "loc.kharidmetalgateclosedr_1op")

        // Free passage starts at stage 100, when the Prince escapes, before quest completion at 110.
        private const val PrinceQuestVarp = "varp.princequest"

        private const val PrinceQuestComplete = 100

        private const val TollCoins = 10

        private const val InvisWall = "loc.inviswall"

        private const val GateOpenSound = "synth.GRATE_OPEN"

        private const val WestCrossX = 3267
        private const val EastCrossX = 3268

        private const val SouthCrossRow = 3227
        private const val NorthCrossRow = 3228

        private val GateLeaves =
            listOf(
                GateLeaf(
                    closedTile = CoordGrid(3268, 3227, 0),
                    openTile = CoordGrid(3267, 3227, 0),
                    openLoc = "loc.inacmetalgateopenl",
                    openAngle = LocAngle.South,
                ),
                GateLeaf(
                    closedTile = CoordGrid(3268, 3228, 0),
                    openTile = CoordGrid(3267, 3228, 0),
                    openLoc = "loc.inacmetalgateopenr",
                    openAngle = LocAngle.North,
                ),
            )

        private const val MinOpenTicks = 4

        private const val OpenTailTicks = 3
    }
}
