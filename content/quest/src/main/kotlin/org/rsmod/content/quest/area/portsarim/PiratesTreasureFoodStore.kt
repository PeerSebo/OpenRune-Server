package org.rsmod.content.quest.area.portsarim

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.Banana
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.CRATE_RUM
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.GroundApron
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.KaramjaRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.SearchRevealTicks
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.TakeFromContainerSeq
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.TakeSound
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.WYDIN_EMPLOYED
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.WhiteApron
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.crateHasRum
import org.rsmod.content.quest.area.portsarim.PiratesTreasureState.isWydinEmployee
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasureFoodStore
@Inject
constructor(
    private val shops: Shops,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.wydin") { talkToWydin(it.npc) }
        onOpNpc3("npc.wydin") { openShop(it.npc) }

        onOpLoc1(BackRoomDoor) { openBackRoomDoor(it.loc) }
        onOpLoc1(BackRoomCrate) { searchBackRoomCrate() }

        val groundApron =
            checkNotNull(ServerCacheManager.getItem(GroundApron.asRSCM(RSCMType.OBJ))) {
                "Pirate's Treasure ground apron does not resolve: $GroundApron"
            }
        onOpObj3(groundApron) { takeGroundApron(it.obj.coords) }
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, "Food Store", "inv.wydinstore")
    }

    private suspend fun ProtectedAccess.talkToWydin(npc: Npc) {
        startDialogue(npc) { wydinDialogue(npc) }
    }

    private suspend fun Dialogue.wydinDialogue(npc: Npc) {
        if (isWydinEmployee(player)) {
            employedDialogue(npc)
            return
        }

        chatNpc(happy, "Welcome to my food store! Would you like to buy anything?")

        when (
            choice4(
                "Yes please.",
                1,
                "No, thank you.",
                2,
                "What can you recommend?",
                3,
                "Can I get a job here?",
                4,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes please.")
                access.openShop(npc)
            }
            2 -> chatPlayer(neutral, "No, thank you.")
            3 -> {
                chatPlayer(quiz, "What can you recommend?")
                chatNpc(
                    happy,
                    "The bananas. They come in by the crate from Karamja, fresh as you like.",
                )
            }
            4 -> askForJob()
        }
    }

    private suspend fun Dialogue.askForJob(questionAsked: Boolean = false) {
        if (!questionAsked) {
            chatPlayer(quiz, "Can I get a job here?")
        }

        wydin(
            happy,
            "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your own white apron?",
        )

        if (!hasApron(player)) {
            chatPlayer(sad, "No, I haven't.")
            wydin(
                neutral,
                "Well, you can't work here unless you have a white apron. Health and safety " +
                    "regulations, you understand.",
            )
            chatPlayer(quiz, "Where can I get one of those?")
            wydin(
                happy,
                "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap there.",
            )
            wydin(
                happy,
                "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store " +
                    "somewhere. It's the little place just north of here.",
            )
            return
        }

        chatPlayer(happy, "Yes, I have one right here.")
        wydin(
            happy,
            "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, please.",
        )
        WYDIN_EMPLOYED.set(player, true)

        if (!isWearingApron(player)) {
            wydin(neutral, "You need to put your white apron on first though.")
        }
    }

    private suspend fun Dialogue.wydin(mesanim: MesAnimType, text: String) {
        chatNpcSpecific("Wydin", "npc.wydin", mesanim, text)
    }

    private suspend fun Dialogue.employedDialogue(npc: Npc) {
        chatNpc(quiz, "Is it nice and tidy round the back now?")

        when (
            choice4(
                "Yes, can I work out front now?",
                1,
                "Yes, are you going to pay me yet?",
                2,
                "No, it's a complete mess",
                3,
                "Can I buy something please?",
                4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Yes, can I work out front now?")
                chatNpc(short, "No, I'm the one who works here.")
            }
            2 -> {
                chatPlayer(quiz, "Yes, are you going to pay me yet?")
                chatNpc(shifty, "Umm... No, not yet.")
            }
            3 -> {
                chatPlayer(bored, "No, it's a complete mess.")
                chatNpc(laugh, "Ah well, it'll give you something to do, won't it.")
            }
            4 -> {
                chatPlayer(quiz, "Can I buy something please?")
                chatNpc(happy, "Yes, of course.")
                access.openShop(npc)
            }
        }
    }

    private suspend fun ProtectedAccess.openBackRoomDoor(door: BoundLocInfo) {
        arriveDelay()

        if (player.coords.x < door.coords.x) {
            crossDoorway(door)
            return
        }

        if (!isWydinEmployee(player)) {
            startDialogue {
                chatNpcSpecific(
                    "Wydin",
                    "npc.wydin",
                    neutral,
                    "Hey, you can't go in there. Only employees of the grocery store can go in.",
                )
                when (choice2("Well, can I get a job here?", 1, "Sorry, I didn't realise.", 2)) {
                    1 -> {
                        chatPlayer(quiz, "Well, can I get a job here?")
                        askForJob(questionAsked = true)
                    }
                    2 -> chatPlayer(neutral, "Sorry, I didn't realise.")
                }
            }

            if (!isWydinEmployee(player) || !isWearingApron(player)) {
                return
            }
        } else if (!isWearingApron(player)) {
            startDialogue {
                chatNpcSpecific(
                    "Wydin",
                    "npc.wydin",
                    neutral,
                    "Can you put your white apron on before going in there, please?",
                )
            }
            return
        }

        crossDoorway(door)
    }

    private suspend fun ProtectedAccess.crossDoorway(door: BoundLocInfo) {
        val route = crossingRoute(door)
        val tiles = crossingTiles(route)
        val openTicks = maxOf(MinOpenTicks, tiles + OpenTailTicks)

        locRepo.add(door.coords, InvisWall, openTicks, door.angle, door.shape)
        locRepo.add(
            door.acrossTile(),
            BackRoomDoorOpen,
            openTicks,
            door.turnAngle(rotations = 1),
            door.shape,
        )
        soundSynth(DoorOpenSound)

        val trimmed = route.dropWhile { it == player.coords }
        if (trimmed.isEmpty()) {
            return
        }
        forcedWalk(trimmed, crossTiles = crossingTiles(trimmed))
    }

    private fun ProtectedAccess.crossingRoute(door: BoundLocInfo): List<CoordGrid> {
        val here = player.coords
        val onTile = door.coords
        val across = door.acrossTile()
        val exit = if (here == across) onTile else across
        return if (here == onTile || exit == onTile) listOf(exit) else listOf(onTile, exit)
    }

    private fun ProtectedAccess.crossingTiles(route: List<CoordGrid>): Int {
        var tiles = 0
        var from = player.coords
        for (waypoint in route) {
            tiles += from.chebyshevDistance(waypoint)
            from = waypoint
        }
        return maxOf(1, tiles)
    }

    private fun BoundLocInfo.acrossTile(): CoordGrid {
        val (dx, dz) =
            when (angle) {
                LocAngle.West -> -1 to 0
                LocAngle.North -> 0 to 1
                LocAngle.East -> 1 to 0
                LocAngle.South -> 0 to -1
            }
        return CoordGrid(coords.x + dx, coords.z + dz, coords.level)
    }

    private suspend fun ProtectedAccess.searchBackRoomCrate() {
        arriveDelay()

        mes("There are a lot of bananas in the crate.")

        if (crateHasRum(player)) {
            delay(SearchRevealTicks)

            if (invAdd(inv, KaramjaRum, 1).failure) {
                return
            }

            CRATE_RUM.set(player, false)
            anim(TakeFromContainerSeq)
            soundSynth(TakeSound)
            mes("You find your bottle of rum in amongst the bananas.")
        }

        offerBanana()
    }

    private suspend fun ProtectedAccess.offerBanana() {
        startDialogue {
            val take = choice2("Yes.", true, "No.", false, title = "Do you want to take a banana?")
            if (!take) {
                return@startDialogue
            }
            if (access.invAdd(access.inv, Banana, 1).failure) {
                return@startDialogue
            }
            access.mes("You take a banana.")
            access.anim(TakeFromContainerSeq)
            access.soundSynth(TakeSound)
        }
    }

    private suspend fun ProtectedAccess.takeGroundApron(coords: CoordGrid) {
        arriveDelay()

        val ground = objRepo.findAll(coords).firstOrNull { it.type == groundApronId }
        if (ground == null) {
            return
        }

        if (invAdd(inv, WhiteApron, 1).failure) {
            return
        }

        objRepo.del(ground)

        anim(TakeFromContainerSeq)
        soundSynth(TakeSound)
        mes("You take an apron. It feels freshly starched and smells of laundry.")
    }

    private val groundApronId: Int by lazy { GroundApron.asRSCM(RSCMType.OBJ) }

    private fun hasApron(player: Player): Boolean =
        player.inv.count(WhiteApron) > 0 || isWearingApron(player)

    private fun isWearingApron(player: Player): Boolean =
        player.worn[Wearpos.Torso.slot]?.isType(WhiteApron) == true

    private companion object {
        private const val BackRoomDoor = "loc.wydindoor"
        private const val BackRoomDoorOpen = "loc.wydindooropen"
        private const val BackRoomCrate = "loc.grocerycrate"

        private const val DoorOpenSound = "synth.DOOR_OPEN"
        private const val InvisWall = "loc.inviswall"

        private const val MinOpenTicks = 4

        private const val OpenTailTicks = 3
    }
}
