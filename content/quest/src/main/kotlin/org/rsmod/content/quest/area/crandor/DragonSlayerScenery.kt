package org.rsmod.content.quest.area.crandor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.misthalin.MisthalinDoorLeaf
import org.rsmod.content.quest.area.misthalin.misthalinCrossDoorway
import org.rsmod.content.quest.area.misthalin.misthalinCrossingOpenTicks
import org.rsmod.content.quest.area.misthalin.misthalinCrossingRoute
import org.rsmod.content.quest.area.misthalin.misthalinCrossingTiles
import org.rsmod.content.quest.area.misthalin.openMisthalinLeaf
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal class DragonSlayerScenery(
    private val quest: Quest,
    private val cutscene: DragonSlayerCutscene,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) {
    private var Player.usedSilk by intVarBit("varbit.dragonslayer_used_silk")
    private var Player.usedLobsterPot by intVarBit("varbit.dragonslayer_used_lobster_pot")
    private var Player.usedUnfiredBowl by intVarBit("varbit.dragonslayer_used_bowl_unfired")
    private var Player.usedMindBomb by intVarBit("varbit.dragonslayer_used_wizards_mind_bomb")

    private var Player.hullOneThird by intVarBit("varbit.dragonslayer_ship_onethird_fixed")
    private var Player.hullTwoThirds by intVarBit("varbit.dragonslayer_ship_twothird_fixed")
    private var Player.hullFixed by intVarBit("varbit.dragonslayer_ship_fullyfixed")

    private var Player.foundSecretDoor by
        intVarBit("varbit.dragonslayer_crandor_found_secret_door")

    fun ScriptContext.register() {
        registerMazeDoors()
        registerMazeChest()
        registerDwarvenMine()
        registerShip()
        registerCrandor()
    }

    private fun ScriptContext.registerMazeDoors() {
        onOpLoc1(DragonSlayerRefs.MelzarDoor) { melzarDoor(it.loc) }
        onOpLocU(DragonSlayerRefs.MelzarDoor, DragonSlayerRefs.MelzarKey) { melzarDoor(it.loc) }

        colouredDoor(DragonSlayerRefs.RedDoor, DragonSlayerRefs.RedKey, DragonSlayerRefs.RedDoorOpen)
        colouredDoor(
            DragonSlayerRefs.OrangeDoor,
            DragonSlayerRefs.OrangeKey,
            DragonSlayerRefs.OrangeDoorOpen,
        )
        colouredDoor(
            DragonSlayerRefs.YellowDoor,
            DragonSlayerRefs.YellowKey,
            DragonSlayerRefs.YellowDoorOpen,
        )
        colouredDoor(
            DragonSlayerRefs.BlueDoor,
            DragonSlayerRefs.BlueKey,
            DragonSlayerRefs.BlueDoorOpen,
        )
        colouredDoor(
            DragonSlayerRefs.MagentaDoor,
            DragonSlayerRefs.MagentaKey,
            DragonSlayerRefs.MagentaDoorOpen,
        )
        colouredDoor(
            DragonSlayerRefs.GreenDoor,
            DragonSlayerRefs.GreenKey,
            DragonSlayerRefs.GreenDoorOpen,
        )

        onOpLoc1(DragonSlayerRefs.FunExit) {
            arriveDelay()
            swingDoor(it.loc, DragonSlayerRefs.FunExitOpen)
        }

        onOpLoc1(DragonSlayerRefs.FunLadderTop) { funLadder() }
    }

    private fun ScriptContext.colouredDoor(door: String, key: String, openLeaf: String) {
        onOpLoc1(door) { colouredDoorClicked(it.loc, key, openLeaf) }
        onOpLocU(door, key) { colouredDoorClicked(it.loc, key, openLeaf) }
    }

    private suspend fun ProtectedAccess.melzarDoor(door: BoundLocInfo) {
        arriveDelay()
        if (!inv.contains(DragonSlayerRefs.MelzarKey)) {
            mes("This door is securely locked.")
            soundSynth(DragonSlayerRefs.SynthLocked)
            return
        }
        mes("You use the key and the door opens.")
        swingDoor(door, DragonSlayerRefs.MelzarDoorOpen)
    }

    private suspend fun ProtectedAccess.colouredDoorClicked(
        door: BoundLocInfo,
        key: String,
        openLeaf: String,
    ) {
        arriveDelay()
        if (!inv.contains(key)) {
            mes("This door is securely locked.")
            soundSynth(DragonSlayerRefs.SynthLocked)
            return
        }
        val deleted = invDel(inv, key, 1)
        if (deleted.failure) {
            mes("This door is securely locked.")
            soundSynth(DragonSlayerRefs.SynthLocked)
            return
        }
        mes("The key disintegrates as it unlocks the door.")
        swingDoor(door, openLeaf)
    }

    // The open leaf still blocks walking; force the protected crossing through the doorway.
    private suspend fun ProtectedAccess.swingDoor(door: BoundLocInfo, openLeaf: String) {
        val route = misthalinCrossingRoute(door)
        val openTicks = misthalinCrossingOpenTicks(misthalinCrossingTiles(route))

        soundSynth(DragonSlayerRefs.SynthDoorOpen)
        openMisthalinLeaf(
            locRepo,
            MisthalinDoorLeaf(
                tile = door.coords,
                closed = RSCM.getReverseMapping(RSCMType.LOC, door.id),
                openLeaf = openLeaf,
                openAngle = door.turnAngle(rotations = 1),
                closedAngle = door.angle,
            ),
            openTicks,
        )
        misthalinCrossDoorway(route)
    }

    private suspend fun ProtectedAccess.funLadder() {
        arriveDelay()
        anim(DragonSlayerRefs.SeqClimbLadder)
        delay(1)
        mes("You climb down the ladder")
        telejump(DragonSlayerRefs.MazeBasement)
    }

    private fun ScriptContext.registerMazeChest() {
        onOpLoc1(DragonSlayerRefs.FunChestShut) { openFunChest(it.loc) }
        onOpLoc1(DragonSlayerRefs.FunChestOpen) { lootFunChest(it.loc) }
    }

    private suspend fun ProtectedAccess.openFunChest(chest: BoundLocInfo) {
        arriveDelay()
        mes("You open the chest.")
        delay(1)
        anim(DragonSlayerRefs.SeqOpenChest)
        soundSynth(DragonSlayerRefs.SynthChestOpen)
        locRepo.change(chest, DragonSlayerRefs.FunChestOpen, ChestOpenTicks)
    }

    private suspend fun ProtectedAccess.lootFunChest(chest: BoundLocInfo) {
        arriveDelay()
        takeMapPiece(chest, DragonSlayerRefs.MapPartMelzar, DragonSlayerRefs.FunChestShut)
    }

    private fun ScriptContext.registerDwarvenMine() {
        onOpLoc1(DragonSlayerRefs.MagicDoor) { magicDoor(it.loc) }
        onOpLocU(DragonSlayerRefs.MagicDoor, DragonSlayerRefs.Silk) {
            offerToDoor(it.loc, it.invSlot, Offer.Silk)
        }
        onOpLocU(DragonSlayerRefs.MagicDoor, DragonSlayerRefs.LobsterPot) {
            offerToDoor(it.loc, it.invSlot, Offer.LobsterPot)
        }
        onOpLocU(DragonSlayerRefs.MagicDoor, DragonSlayerRefs.UnfiredBowl) {
            offerToDoor(it.loc, it.invSlot, Offer.UnfiredBowl)
        }
        onOpLocU(DragonSlayerRefs.MagicDoor, DragonSlayerRefs.MindBomb) {
            offerToDoor(it.loc, it.invSlot, Offer.MindBomb)
        }

        onOpLoc1(DragonSlayerRefs.OracleChestShut) { openOracleChest(it.loc) }
        onOpLoc1(DragonSlayerRefs.OracleChestOpen) { lootOracleChest(it.loc) }
    }

    private enum class Offer(val obj: String, val message: String) {
        MindBomb(
            DragonSlayerRefs.MindBomb,
            "You pour the Wizard's Mind Bomb into the opening in the door.",
        ),

        Silk(DragonSlayerRefs.Silk, "You put the silk into the opening in the door."),

        LobsterPot(
            DragonSlayerRefs.LobsterPot,
            "You put the Lobster pot into the opening in the door.",
        ),

        UnfiredBowl(
            DragonSlayerRefs.UnfiredBowl,
            "You put the unfired bowl into the opening in the door.",
        ),
    }

    private suspend fun ProtectedAccess.magicDoor(door: BoundLocInfo) {
        arriveDelay()
        if (!riddleSolved()) {
            mes("You can't see any way to open the door.")
            return
        }
        openMagicDoor(door)
    }

    private suspend fun ProtectedAccess.offerToDoor(door: BoundLocInfo, invSlot: Int, offer: Offer) {
        arriveDelay()
        if (riddleSolved()) {
            openMagicDoor(door)
            return
        }
        if (alreadyOffered(offer)) {
            // TODO: Verify the refusal for duplicate door offerings.
            mes("You can't see any way to open the door.")
            return
        }

        val consumed =
            if (offer == Offer.MindBomb) {
                !invReplaceSlot(inv, invSlot, count = 1, replacement = beerGlass).failure
            } else {
                !invDel(inv, offer.obj, 1).failure
            }

        if (!consumed) {
            return
        }

        mes(offer.message)
        markOffered(offer)

        if (riddleSolved()) {
            delay(1)
            openMagicDoor(door)
        }
    }

    // Keep the closed leaf while it animates, then swap at T+6 through the centre of the footprint.
    private suspend fun ProtectedAccess.openMagicDoor(door: BoundLocInfo) {
        // The doorway: the centre tile of the 3x1 footprint, (3050, 9840). Derived from the loc,
        // never from the player.
        val doorway = door.adjustedCentre
        val level = door.coords.level
        // TODO: Verify crossing behavior when approaching the door end-on.
        val side = (player.coords.x - door.coords.x).sign

        if (side != 0 && player.coords.z != doorway.z) {
            forcedWalk(CoordGrid(player.coords.x, doorway.z, level), crossTiles = 1)
        }

        mes("The door opens...")
        worldRepo.locAnim(door, SeqMagicDoorOpen)
        worldRepo.soundArea(door.coords, SynthMagicDoor, radius = 7)
        delay(MagicDoorAnimTicks)

        locRepo.change(door, DragonSlayerRefs.MagicDoorRight, MagicDoorSideTicks)
        val farEnd = CoordGrid(door.coords.x, door.coords.z + door.adjustedLength - 1, level)
        locRepo.add(
            farEnd,
            DragonSlayerRefs.MagicDoorLeft,
            MagicDoorSideTicks,
            door.angle,
            door.shape,
        )
        locRepo.add(
            doorway,
            DragonSlayerRefs.MagicDoorFiller,
            MagicDoorSideTicks,
            LocAngle.South,
            LocShape.WallStraight,
        )
        locRepo.add(
            CoordGrid(doorway.x + 1, doorway.z, level),
            InvisWall,
            MagicDoorSealTicks,
            LocAngle.West,
            LocShape.WallL,
        )
        locRepo.add(
            CoordGrid(doorway.x - 1, doorway.z, level),
            InvisWall,
            MagicDoorSealTicks,
            LocAngle.East,
            LocShape.WallL,
        )

        if (side != 0) {
            val across = CoordGrid(door.coords.x - side, doorway.z, level)
            forcedWalk(listOf(doorway, across), crossTiles = 2)
            delay(1)
        } else {
            delay(3)
        }

        locRepo.change(door, DragonSlayerRefs.MagicDoorOpen, MagicDoorOpenTicks)
    }

    private fun ProtectedAccess.riddleSolved(): Boolean =
        player.usedSilk == 1 &&
            player.usedLobsterPot == 1 &&
            player.usedUnfiredBowl == 1 &&
            player.usedMindBomb == 1

    private fun ProtectedAccess.alreadyOffered(offer: Offer): Boolean =
        when (offer) {
            Offer.Silk -> player.usedSilk == 1
            Offer.LobsterPot -> player.usedLobsterPot == 1
            Offer.UnfiredBowl -> player.usedUnfiredBowl == 1
            Offer.MindBomb -> player.usedMindBomb == 1
        }

    private fun ProtectedAccess.markOffered(offer: Offer) {
        when (offer) {
            Offer.Silk -> player.usedSilk = 1
            Offer.LobsterPot -> player.usedLobsterPot = 1
            Offer.UnfiredBowl -> player.usedUnfiredBowl = 1
            Offer.MindBomb -> player.usedMindBomb = 1
        }
    }

    private val beerGlass by lazy {
        ServerCacheManager.getItem(DragonSlayerRefs.BeerGlass.asRSCM(RSCMType.OBJ))
            ?: error("Missing obj type: ${DragonSlayerRefs.BeerGlass}")
    }

    private suspend fun ProtectedAccess.openOracleChest(chest: BoundLocInfo) {
        arriveDelay()
        startDialogue {
            mesbox("As you open the chest, you notice an inscription on the lid:")
            mesbox(
                "Here I rest the map to my beloved home. To whoever finds it, I beg of you, let " +
                    "it be. I was honour-bound not to destroy the map piece, but I have used all " +
                    "my magical skill to keep it from being recovered."
            )
            mesbox(
                "This map leads to the lair of the beast that destroyed my home, devoured my " +
                    "family, and burned to a cinder all that I love. But revenge would not " +
                    "benefit me now, and to disturb this beast is to risk bringing its wrath " +
                    "down upon another land."
            )
            mesbox(
                "I cannot stop you from taking this map piece now, but think on this: if you can " +
                    "slay the Dragon of Crandor, you are a greater hero than my land ever " +
                    "produced. There is no shame in backing out now."
            )
        }
        anim(DragonSlayerRefs.SeqOpenChest)
        soundSynth(DragonSlayerRefs.SynthChestOpen)
        locRepo.change(chest, DragonSlayerRefs.OracleChestOpen, ChestOpenTicks)
    }

    private suspend fun ProtectedAccess.lootOracleChest(chest: BoundLocInfo) {
        arriveDelay()
        takeMapPiece(chest, DragonSlayerRefs.MapPartThalzar, DragonSlayerRefs.OracleChestShut)
    }

    private suspend fun ProtectedAccess.takeMapPiece(
        chest: BoundLocInfo,
        piece: String,
        shutType: String,
    ) {
        if (inv.contains(piece) || inv.contains(DragonSlayerRefs.DragonMap)) {
            mes("The chest is empty.")
            locRepo.change(chest, shutType, ChestOpenTicks)
            return
        }
        val added = invAdd(inv, piece, 1)
        if (added.failure) {
            mes("You don't have enough inventory space to take the map piece.")
            return
        }
        soundSynth(DragonSlayerRefs.SynthChestClose)
        locRepo.change(chest, shutType, ChestOpenTicks)
        startDialogue { objbox(piece, "You find a map piece in the chest.") }
    }

    private fun ScriptContext.registerShip() {
        onOpLoc1(DragonSlayerRefs.GangplankOn) { boardShip(it.loc) }
        onOpLoc1(DragonSlayerRefs.GangplankOff) { disembarkShip(it.loc) }
        onOpLoc1(DragonSlayerRefs.ShipLadderTop) { shipLadderDown(it.loc) }
        onOpLoc1(DragonSlayerRefs.ShipLadder) { shipLadderUp(it.loc) }
        onOpLoc1(DragonSlayerRefs.ShipHole) { repairHull(it.loc) }
    }

    private suspend fun ProtectedAccess.boardShip(gangplank: BoundLocInfo) {
        if (gangplank.column() != GangplankOnColumn) {
            return
        }
        arriveDelay()
        mes("You board the ship.")
        forcedWalk(gangplank.coords, crossTiles = 1)
        telejump(DragonSlayerRefs.ShipDeck)
    }

    private suspend fun ProtectedAccess.disembarkShip(gangplank: BoundLocInfo) {
        if (gangplank.column() != GangplankOffColumn) {
            return
        }
        arriveDelay()
        mes("You disembark the ship.")
        forcedWalk(gangplank.coords, crossTiles = 1)
        telejump(DragonSlayerRefs.ShipQuay)
    }

    private suspend fun ProtectedAccess.shipLadderDown(ladder: BoundLocInfo) {
        if (ladder.column() != ShipLadderTopColumn) {
            return
        }
        arriveDelay()
        anim(DragonSlayerRefs.SeqClimbLadder)
        delay(1)
        telejump(DragonSlayerRefs.ShipHold)
    }

    private suspend fun ProtectedAccess.shipLadderUp(ladder: BoundLocInfo) {
        if (ladder.column() != ShipLadderHoldColumn) {
            return
        }
        arriveDelay()
        anim(DragonSlayerRefs.SeqClimbLadder)
        delay(1)
        telejump(DragonSlayerRefs.ShipDeckFromHold)
    }

    // TODO: Add nail-bending chances and verify missing-hammer refusal.
    private suspend fun ProtectedAccess.repairHull(hole: BoundLocInfo) {
        if (hole.column() != ShipHoleColumn) {
            // TODO: Verify the refusal when repairing from outside the hull.
            arriveDelay()
            mes("You can't reach the hole from here. The hole is inside the ship's hold.")
            return
        }
        arriveDelay()

        if (player.hullFixed == 1) {
            mes("The hole is already patched.")
            return
        }
        if (!inv.contains(DragonSlayerRefs.Hammer)) {
            mes("You need a hammer to nail the planks in place.")
            return
        }
        if (!inv.contains(DragonSlayerRefs.WoodPlank)) {
            mes("You need a plank to patch this hole.")
            return
        }
        if (invTotal(inv, DragonSlayerRefs.Nails) < NailsPerPlank) {
            mes("You need $NailsPerPlank steel nails to fix a plank in place.")
            return
        }

        faceSquare(hole.coords)
        anim(DragonSlayerRefs.SeqNailPlank)
        soundSynth(DragonSlayerRefs.SynthNailPlank)
        delay(2)

        val plank = invDel(inv, DragonSlayerRefs.WoodPlank, 1)
        if (plank.failure) {
            return
        }
        val nails = invDel(inv, DragonSlayerRefs.Nails, NailsPerPlank)
        if (nails.failure) {
            invAdd(inv, DragonSlayerRefs.WoodPlank, 1)
            return
        }

        val message =
            when {
                player.hullOneThird == 0 -> {
                    player.hullOneThird = 1
                    "You nail a plank over the hole, but you still need more planks to close the " +
                        "hole completely."
                }
                player.hullTwoThirds == 0 -> {
                    player.hullTwoThirds = 1
                    "You nail a plank over the hole, but you still need one more plank to close " +
                        "the hole completely."
                }
                else -> {
                    player.hullFixed = 1
                    "You nail a final plank over the hole. You have successfully patched the hole " +
                        "in the ship."
                }
            }

        if (player.hullFixed == 1 && quest.getQuestStage(player) < DragonSlayerStage.ShipRepaired) {
            quest.setQuestStage(this, DragonSlayerStage.ShipRepaired)
        }

        startDialogue { mesbox(message) }
    }

    private fun ScriptContext.registerCrandor() {
        onOpLoc1(DragonSlayerRefs.RuinEntrance) {
            if (quest.getQuestStage(player) >= DragonSlayerStage.ElvargSlain) {
                enterHolePlain()
            } else {
                cutscene.crandorDescent(this)
            }
        }

        onOpLoc1(DragonSlayerRefs.ClimbingRope) { climbRope() }

        onOpLoc1(DragonSlayerRefs.StalagtiteJump) { climbLowWall(it.loc) }

        onOpLoc1(DragonSlayerRefs.SecretDoor) { secretDoor(it.loc) }

        onOpLoc1(DragonSlayerRefs.PrisonCellDoor) { lockedCellDoor() }
    }

    // TODO: Verify the post-quest descent animation.
    private suspend fun ProtectedAccess.enterHolePlain() {
        arriveDelay()
        anim(DragonSlayerRefs.SeqClimbLadder)
        delay(1)
        telejump(DragonSlayerRefs.CrandorDungeon)
    }

    // TODO: Verify the rope-climb animation and surface landing.
    private suspend fun ProtectedAccess.climbRope() {
        arriveDelay()
        anim(DragonSlayerRefs.SeqClimbLadder)
        delay(1)
        telejump(DragonSlayerRefs.CrandorSurface)
    }

    // TODO: Verify the locked-door message and sound.
    private suspend fun ProtectedAccess.lockedCellDoor() {
        arriveDelay()
        mes("The door is locked.")
        soundSynth(DragonSlayerRefs.SynthLocked)
    }

    private suspend fun ProtectedAccess.climbLowWall(wall: BoundLocInfo) {
        arriveDelay()
        val side = (player.coords.x - wall.coords.x).sign
        if (side == 0) {
            return
        }
        val dest = CoordGrid(wall.coords.x - side, wall.coords.z, wall.coords.level)
        val face = if (side < 0) ClimbFaceWest else ClimbFaceEast
        val start = player.coords
        spam("You climb over the low wall.")
        telejump(dest)
        exactMove(
            start = start,
            end = dest,
            delay1 = ClimbStartDelay,
            delay2 = ClimbEndDelay,
            dir = face,
        )
        anim(DragonSlayerRefs.SeqClimbWall, delay = ClimbStartDelay)
        soundSynth(DragonSlayerRefs.SynthClimbWall, delay = ClimbStartDelay)
    }

    private suspend fun ProtectedAccess.secretDoor(door: BoundLocInfo) {
        arriveDelay()
        player.foundSecretDoor = 1
        swingDoor(door, DragonSlayerRefs.SecretDoorOpen)
    }

    private companion object {
        private const val MagicDoorAnimTicks = 6

        private const val MagicDoorSideTicks = 4

        private const val MagicDoorSealTicks = 5

        private const val MagicDoorOpenTicks = 3

        private const val InvisWall = "loc.inviswall"

        private const val ChestOpenTicks = 100

        private const val SeqMagicDoorOpen = "seq.dragon_slayer_qip_magic_door_open"

        private const val SynthMagicDoor = "synth.DRAGONSLAYER_MAGICDOOR"

        private const val NailsPerPlank = 30

        private const val ClimbStartDelay = 30

        private const val ClimbEndDelay = 94

        private const val ClimbFaceWest = 1536

        private const val ClimbFaceEast = 512

        private fun column(x: Int, z: Int): CoordGrid = CoordGrid(x, z, 0)

        private fun BoundLocInfo.column(): CoordGrid = column(coords.x, coords.z)

        private val GangplankOnColumn = column(3047, 3205)

        private val GangplankOffColumn = column(3047, 3206)

        private val ShipLadderTopColumn = column(3049, 3208)

        private val ShipLadderHoldColumn = column(3049, 9640)

        private val ShipHoleColumn = column(3047, 9639)
    }
}
