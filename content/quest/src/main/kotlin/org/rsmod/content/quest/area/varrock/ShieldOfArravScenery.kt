package org.rsmod.content.quest.area.varrock

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.misthalin.MisthalinDoorLeaf
import org.rsmod.content.quest.area.misthalin.misthalinCrossDoorway
import org.rsmod.content.quest.area.misthalin.misthalinCrossingOpenTicks
import org.rsmod.content.quest.area.misthalin.misthalinCrossingRoute
import org.rsmod.content.quest.area.misthalin.misthalinCrossingTiles
import org.rsmod.content.quest.area.misthalin.openMisthalinLeaf
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

internal class ShieldOfArravScenery(
    private val quest: Quest,
    private val dialogue: ShieldOfArravDialogue,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
) {

    fun ScriptContext.register() {
        onOpLoc1(SoaRefs.QuestBookcase) { searchBookcase(it.loc) }
        onOpHeld1(SoaRefs.Book) { readBook() }

        onOpLoc1(SoaRefs.VtamSign) { readVtamSign() }

        onOpLoc1(SoaRefs.StashDoor) { stashDoor(it.loc, usedKey = false) }
        onOpLocU(SoaRefs.StashDoor, SoaRefs.StoreKey) { stashDoor(it.loc, usedKey = true) }

        onOpLoc1(SoaRefs.BlackArmDoor) { blackArmDoor(it.loc) }
        onOpLoc1(SoaRefs.PhoenixInnerDoor) { phoenixInnerDoor(it.loc) }

        onOpLoc1(SoaRefs.CupboardShut) { searchCupboard(it.loc) }
        onOpLoc1(SoaRefs.PhoenixChestShut) { openPhoenixChest(it.loc, it.type) }

        onOpLoc1(SoaRefs.PhoenixExitLadder) { climbOutOfHideout() }
    }

    private suspend fun ProtectedAccess.searchBookcase(loc: BoundLocInfo) {
        arriveDelay()
        if (loc.coords != SoaRefs.BookcaseTile) {
            mes("You don't find anything interesting.")
            return
        }

        val give =
            quest.isQuestInProgress(player) &&
                phoenixGang >= PhoenixState.Accepted &&
                !soaCarries(SoaRefs.Book)
        if (!give) {
            mes("You don't find anything interesting.")
            return
        }

        if (invAdd(inv, SoaRefs.Book, 1).failure) {
            mes("You find the book, but you don't have enough room to take it.")
            return
        }

        soundSynth(SoaRefs.PickupSound)
        objbox(
            SoaRefs.Book,
            400,
            "You search the bookcase and find a book titled The Shield of Arrav.",
        )
        startDialogue { chatPlayer(happy, "Aha! Exactly what I was looking for.") }
    }

    private suspend fun ProtectedAccess.readBook() {
        if (phoenixGang == PhoenixState.Accepted) {
            phoenixGang = PhoenixState.BookRead
        }

        var spread = 0
        while (true) {
            ifOpenMain("interface.book")
            ifSetText("component.book:title", "The Shield of Arrav")
            ifSetEvents("component.book:close_button", -1..-1, IfEvent.Op1)
            renderBookSpread(spread)
            val input = pauseButton()
            spread =
                when {
                    input.isComponentType("component.book:page_right_button") && spread == 0 -> 1
                    input.isComponentType("component.book:page_left_button") && spread == 1 -> 0
                    else -> return
                }
        }
    }

    private fun ProtectedAccess.renderBookSpread(spread: Int) {
        val left = if (spread == 0) BookSpread1Left else BookSpread2Left
        val right = if (spread == 0) BookSpread1Right else BookSpread2Right

        ifSetText("component.book:page_left_number", "${spread * 2 + 1}")
        ifSetText("component.book:page_right_number", "${spread * 2 + 2}")
        for (i in 1..BookLinesPerPage) {
            ifSetText("component.book:page_left_text_$i", left.getOrElse(i - 1) { "" })
            ifSetText("component.book:page_right_text_$i", right.getOrElse(i - 1) { "" })
            ifSetHide("component.book:page_left_model_$i", hide = true)
            ifSetHide("component.book:page_right_model_$i", hide = true)
        }

        ifSetHide("component.book:page_left_button", hide = spread == 0)
        ifSetHide("component.book:page_right_button", hide = spread == 1)
        ifSetEvents("component.book:page_left_button", -1..-1, IfEvent.PauseButton)
        ifSetEvents("component.book:page_right_button", -1..-1, IfEvent.PauseButton)
    }

    private fun ProtectedAccess.readVtamSign() {
        ifOpenMain("interface.qip_soa_vtam_interface")
    }

    private suspend fun ProtectedAccess.stashDoor(loc: BoundLocInfo, usedKey: Boolean) {
        arriveDelay()
        if (!soaCarries(SoaRefs.StoreKey)) {
            soundSynth(SoaRefs.LockedSound)
            mesbox("The door is securely locked.")
            return
        }

        if (usedKey) {
            mes("You unlock the door using the key you're carrying.")
        }
        crossQuestDoor(loc, StashDoorLeaf)
    }

    private suspend fun ProtectedAccess.blackArmDoor(loc: BoundLocInfo) {
        arriveDelay()

        val inside = player.coords.z >= loc.coords.z && player.coords.level == loc.coords.level
        val member = blackArmGang >= BlackArmState.Member
        if (!member && !inside) {
            if (phoenixGang >= PhoenixState.Member) {
                mesbox("The door is securely locked.")
            } else {
                mesbox("This door seems to be locked from the inside.")
            }
            return
        }
        crossQuestDoor(loc, BlackArmDoorLeaf)
    }

    private suspend fun ProtectedAccess.phoenixInnerDoor(loc: BoundLocInfo) {
        arriveDelay()
        if (phoenixGang < PhoenixState.Member || blackArmGang >= BlackArmState.Member) {
            val straven = findStraven()
            if (straven != null) {
                startDialogue(straven) { dialogue.stravenOutsiderApproach(this) }
            } else {
                mesbox("The door is securely locked.")
            }
            return
        }
        crossQuestDoor(loc, PhoenixInnerDoorLeaf)
    }

    private fun findStraven(): Npc? {
        val stravenId = SoaRefs.Straven.asRSCM(RSCMType.NPC)
        return npcRepo.findAll(ZoneKey.from(StravenSpawn), zoneRadius = 1).firstOrNull {
            it.id == stravenId
        }
    }

    private suspend fun ProtectedAccess.crossQuestDoor(loc: BoundLocInfo, leaf: MisthalinDoorLeaf) {
        soundSynth(SoaRefs.NiceDoorOpenSound)
        val route = misthalinCrossingRoute(loc)
        val openTicks = misthalinCrossingOpenTicks(misthalinCrossingTiles(route))
        openMisthalinLeaf(locRepo, leaf, openTicks)
        misthalinCrossDoorway(route)
    }

    private suspend fun ProtectedAccess.searchCupboard(loc: BoundLocInfo) {
        arriveDelay()
        soundSynth(SoaRefs.CupboardOpenSound, delay = 10)
        delay(1)

        locRepo.del(loc, CupboardSwapTicks)
        locRepo.add(loc.coords, SoaRefs.CupboardOpen, CupboardSwapTicks, loc.angle, loc.shape)
        soundSynth(SoaRefs.CupboardCloseSound, delay = 45)

        val give =
            blackArmGang >= BlackArmState.Member &&
                !quest.isQuestCompleted(player) &&
                !soaCarries(SoaRefs.ShieldHalfRight)
        if (!give) {
            mesbox("You open the cupboard, but don't find anything of interest inside.")
            return
        }

        if (invAdd(inv, SoaRefs.ShieldHalfRight, 1).failure) {
            mesbox(
                "You open the cupboard and find half of a shield inside, but you don't have " +
                    "enough room to take it."
            )
            return
        }

        objbox(
            SoaRefs.ShieldHalfRight,
            400,
            "You open the cupboard and find half of a shield inside.",
        )
        startDialogue {
            chatPlayer(
                quiz,
                "This must be the Shield of Arrav! Well... half of it at least. I wonder where " +
                    "the other half is...",
            )
        }
    }

    private suspend fun ProtectedAccess.openPhoenixChest(
        loc: BoundLocInfo,
        type: dev.openrune.types.ObjectServerType,
    ) {
        arriveDelay()
        mesbox("You search the chest.")

        locRepo.del(loc, CupboardSwapTicks)
        locRepo.add(loc.coords, SoaRefs.PhoenixChestOpen, CupboardSwapTicks, loc.angle, loc.shape)
        type.paramOrNull(params.opensound)?.let { soundSynth(it) }

        val give =
            phoenixGang >= PhoenixState.Member &&
                !quest.isQuestCompleted(player) &&
                !soaCarries(SoaRefs.ShieldHalfLeft)
        if (!give) {
            mesbox("You open the chest, but don't find anything of interest inside.")
            return
        }

        if (invAdd(inv, SoaRefs.ShieldHalfLeft, 1).failure) {
            mesbox(
                "You open the chest and find half of a shield inside, but you don't have enough " +
                    "room to take it."
            )
            return
        }

        objbox(SoaRefs.ShieldHalfLeft, 400, "You open the chest and find half of a shield inside.")
    }

    private suspend fun ProtectedAccess.climbOutOfHideout() {
        arriveDelay()
        anim(SoaRefs.ClimbUpSeq)
        delay(1)
        telejump(player.coords.translateZ(-UndergroundZOffset))
    }

    private companion object {
        private const val CupboardSwapTicks = 2

        private const val UndergroundZOffset = 6400

        private val StravenSpawn = CoordGrid(3246, 9780, 0)

        private val PhoenixInnerDoorLeaf =
            MisthalinDoorLeaf(
                tile = CoordGrid(3247, 9779, 0),
                closed = SoaRefs.PhoenixInnerDoor,
                openLeaf = SoaRefs.StashDoorOpenLeaf,
                openAngle = LocAngle.East,
                closedAngle = LocAngle.North,
            )

        private val StashDoorLeaf =
            MisthalinDoorLeaf(
                tile = CoordGrid(3251, 3386, 0),
                closed = SoaRefs.StashDoor,
                openLeaf = SoaRefs.StashDoorOpenLeaf,
                openAngle = LocAngle.West,
                closedAngle = LocAngle.South,
            )

        private val BlackArmDoorLeaf =
            MisthalinDoorLeaf(
                tile = CoordGrid(3185, 3388, 0),
                closed = SoaRefs.BlackArmDoor,
                openLeaf = SoaRefs.BlackArmDoorOpenLeaf,
                openAngle = LocAngle.West,
                closedAngle = LocAngle.South,
            )

        private const val BookLinesPerPage = 15

        private val BookSpread1Left =
            listOf(
                "The Shield of Arrav",
                "",
                "by A. R. Wright",
                "",
                "",
                "Arrav is probably the best",
                "known hero of the 4th Age.",
                "Many legends are told of his",
                "heroics, most notably his role",
                "in the defence of Avarrocka",
                "against the zombie hordes of",
                "the Mahjarrat Zemouregal.",
                "",
                "One surviving artefact from",
                "the 4th Age is a fabulous",
            )

        private val BookSpread1Right =
            listOf(
                "shield. This shield once",
                "belonged to Arrav and is now",
                "indeed known as the Shield of",
                "Arrav. For over 150 years, it",
                "was the prize piece in the",
                "Varrock Museum.",
                "",
                "However, in the year 143 of",
                "the 5th Age, a group of",
                "thieves known as the Phoenix",
                "Gang broke into the museum",
                "and stole the shield in a",
                "daring raid. As a result,",
                "Misthalin's ruler at the time,",
                "King Roald II, put a 1,200",
            )

        private val BookSpread2Left =
            listOf(
                "gold bounty (a massive sum",
                "of money in those days) on",
                "the return of the shield,",
                "hoping that one of the",
                "culprits would betray their",
                "fellows out of greed. Alas, this",
                "tactic did not work.",
                "",
                "Many years after the",
                "original theft of the shield, a",
                "rift resulted in a splinter",
                "group of the Phoenix Gang",
                "forming their own criminal",
                "organisation, the Black Arm",
                "Gang.",
            )

        private val BookSpread2Right =
            listOf(
                "Reports on which of the two",
                "gangs ended up in possession",
                "of the Shield of Arrav were",
                "inconsistent, so the location of",
                "the shield is now sadly",
                "unknown. However, King",
                "Roald III has recently",
                "declared that the reward for",
                "its return still stands.",
            )
    }
}
