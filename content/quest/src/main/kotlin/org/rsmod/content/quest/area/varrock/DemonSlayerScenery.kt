package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class DemonSlayerScenery(
    private val quest: Quest,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
) {
    private var Player.drainKey by intVarBit(DemonSlayerRefs.VarbitDrainKey)
    private var Player.silverlightCase by intVarBit(DemonSlayerRefs.VarbitSilverlightCase)

    fun ScriptContext.register() {

        // Bind both leaves: multiloc dispatch resolves the visible form before the parent.
        onOpLoc1(DemonSlayerRefs.DrainWithKey) { searchDrain(it.loc) }
        onOpLoc1(DemonSlayerRefs.DrainNoKey) { searchDrain(it.loc) }

        for (drain in
            listOf(
                DemonSlayerRefs.Drain,
                DemonSlayerRefs.DrainWithKey,
                DemonSlayerRefs.DrainNoKey,
            )) {
            onOpLocU(drain, DemonSlayerRefs.BucketWater) { pourWater(it.loc, it.invSlot) }
        }

        onOpLoc1(DemonSlayerRefs.SewerKey) { takeSewerKey(it.loc) }
    }

    private suspend fun ProtectedAccess.searchDrain(drain: BoundLocInfo) {
        arriveDelay()
        faceSquare(drain.coords)

        val keyStillThere =
            player.drainKey == DemonSlayerRefs.DrainKeyInDrain && quest.isQuestInProgress(player)
        if (!keyStillThere) {
            mes("Nothing interesting seems to have been dropped down here today.")
            return
        }

        startDialogue {
            chatPlayer(happy, "That must be the key Sir Prysin dropped.")
            chatPlayer(
                worried,
                "I don't seem to be able to reach it. I wonder if I can dislodge it somehow. " +
                    "That way it may go down into the sewers.",
            )
        }
    }

    private suspend fun ProtectedAccess.pourWater(drain: BoundLocInfo, slot: Int) {
        arriveDelay()
        faceSquare(drain.coords)

        mes("You pour the liquid down the drain.")
        invReplaceSlot(inv, slot, count = 1, replacement = bucketEmpty)

        val dislodges =
            player.drainKey == DemonSlayerRefs.DrainKeyInDrain && quest.isQuestInProgress(player)
        if (!dislodges) {
            return
        }

        player.drainKey = DemonSlayerRefs.DrainKeyInSewer
        soundSynth(DemonSlayerRefs.SynthDrain)
        soundSynth(DemonSlayerRefs.SynthKeyFall)

        startDialogue {
            chatPlayer(
                happy,
                "OK, I think I've washed the key down into the sewer. I'd better go down and get " +
                    "it!",
            )
        }
    }

    private suspend fun ProtectedAccess.takeSewerKey(key: BoundLocInfo) {
        arriveDelay()
        faceSquare(key.coords)

        if (player.drainKey != DemonSlayerRefs.DrainKeyInSewer) {
            return
        }

        player.drainKey = DemonSlayerRefs.DrainKeyRecovered
        invAddOrDrop(objRepo, DemonSlayerRefs.Key3)
        soundSynth(DemonSlayerRefs.SynthPick)
        startDialogue { objbox(DemonSlayerRefs.Key3, "You pick up an old rusty key.") }
    }

    suspend fun traibornWardrobe(access: ProtectedAccess) {
        val wardrobe =
            locRepo.add(
                DemonSlayerCoords.Wardrobe,
                DemonSlayerRefs.Wardrobe,
                WardrobeLifespanTicks,
                LocAngle.East,
                LocShape.CentrepieceStraight,
            )
        access.soundSynth(DemonSlayerRefs.SynthCupboardFall)
        access.delay(WardrobeToChestTicks)

        access.soundSynth(DemonSlayerRefs.SynthChestOpen)
        access.delay(ChestToKeyTicks)

        access.locAnim(worldRepo, wardrobe, DemonSlayerRefs.SeqWardrobeDisappear)
        access.soundSynth(DemonSlayerRefs.SynthCupboardDisappear)
    }

    suspend fun giveSilverlight(access: ProtectedAccess, prysin: Npc) {
        val post = prysin.coords
        val caseTile = DemonSlayerCoords.SilverlightCase
        val playerStand = caseTile.translateZ(1)
        val playerFaceTile = caseTile.translateZ(-1)

        try {
            access.closeChat()
            // Clear face-close mode before routing: it otherwise resets movement each tick.
            prysin.noneMode()
            prysin.walk(caseTile)
            access.delay(WalkToCaseTicks)

            if (prysin.coords != caseTile) {
                prysin.telejump(collision, caseTile)
            }
            prysin.lockFacing(caseTile.translateZ(-1))

            access.walk(playerStand)
            access.player.moveSpeed = MoveSpeed.Walk
            var faceCue = maxOf(1, access.coords.chebyshevDistance(playerStand))

            suspend fun delayCued(ticks: Int) {
                var remaining = ticks
                if (faceCue in 1..remaining) {
                    access.delay(faceCue)
                    remaining -= faceCue
                    faceCue = 0
                    access.faceSquare(playerFaceTile)
                } else if (faceCue > 0) {
                    faceCue -= remaining
                }
                if (remaining > 0) {
                    access.delay(remaining)
                }
            }

            delayCued(1)

            prysin.anim(DemonSlayerRefs.SeqPrysinOpenCase, delay = CaseOpenAnimDelay)
            access.soundSynth(DemonSlayerRefs.SynthCupboardOpen, delay = CaseOpenAnimDelay)
            delayCued(AnimToCaseAnimTicks)

            animateCase(access)
            access.soundSynth(DemonSlayerRefs.SynthCasketOpen)
            delayCued(CaseAnimToTakeTicks)

            access.player.silverlightCase = 1
            prysin.transmog(prysinWithSword, Int.MAX_VALUE)
            prysin.anim(DemonSlayerRefs.SeqPrysinTakeSword)
            delayCued(TakeToPresentTicks)

            prysin.anim(DemonSlayerRefs.SeqPrysinPresentStart)
            prysin.clearFacingLock()
            prysin.facePlayer(access.player)
            delayCued(1)

            prysin.anim(DemonSlayerRefs.SeqPrysinPresentMiddle)
            delayCued(1)

            prysin.resetTransmog()
            prysin.anim(DemonSlayerRefs.SeqPrysinPresentEnd)

            // Delete ingredients before adding; a net-negative recipe is valid with a full inventory.
            for (key in listOf(DemonSlayerRefs.Key1, DemonSlayerRefs.Key2, DemonSlayerRefs.Key3)) {
                access.invDel(access.inv, key)
            }
            access.invAddOrDrop(objRepo, DemonSlayerRefs.Silverlight)
            access.anim(DemonSlayerRefs.SeqReceivingSilverlight)
            access.spotanim(DemonSlayerRefs.SpotanimSwordGlint)
            access.soundSynth(DemonSlayerRefs.SynthEquipSilverlight)

            access.startDialogue(prysin) {
                objbox(DemonSlayerRefs.Silverlight, "Sir Prysin hands you a very shiny sword.")
                chatNpc(
                    neutral,
                    "That sword belonged to my great-grandfather. Make sure you treat it with " +
                        "respect!",
                )
                chatNpc(happy, "Now go kill that demon!")
            }
        } finally {
            prysin.clearFacingLock()
            prysin.resetTransmog()
            prysin.telejump(collision, post)
            prysin.defaultMode()
        }
    }

    // Find by shape: the registered loc stores the multiloc parent, not the rendered leaf ID.
    private fun animateCase(access: ProtectedAccess) {
        val case =
            locRepo.findExact(DemonSlayerCoords.SilverlightCase, LocShape.WallDecorStraightNoOffset)
                ?: return
        access.locAnim(worldRepo, case, DemonSlayerRefs.SeqCaseOpen)
    }

    private val bucketEmpty by lazy { itemType(DemonSlayerRefs.BucketEmpty) }

    private fun itemType(obj: String) =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: error("Missing obj type: $obj")

    private val prysinWithSword by lazy {
        ServerCacheManager.getNpc(DemonSlayerRefs.PrysinSilverlight.asRSCM(RSCMType.NPC))
            ?: error("Missing npc type: ${DemonSlayerRefs.PrysinSilverlight}")
    }

    private companion object {
        const val WardrobeLifespanTicks = 14

        const val WardrobeToChestTicks = 2
        const val ChestToKeyTicks = 2

        const val WalkToCaseTicks = 7
        const val CaseOpenAnimDelay = 19
        const val AnimToCaseAnimTicks = 5
        const val CaseAnimToTakeTicks = 3
        const val TakeToPresentTicks = 3
    }
}

internal fun ProtectedAccess.closeChat() {
    ifCloseSub("interface.chat_left")
    ifCloseSub("interface.chat_right")
}
