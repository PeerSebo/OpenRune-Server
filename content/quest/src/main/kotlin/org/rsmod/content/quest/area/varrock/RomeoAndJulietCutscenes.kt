package org.rsmod.content.quest.area.varrock

import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneCreepRate
import org.rsmod.content.quest.area.misthalin.SceneDriftRate
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.SceneSnapRate
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class RomeoAndJulietCutscenes(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
) {

    // Release held regions on login too; a removed player may never run the scene finally block.
    private val sceneRegions = HashMap<Long, Region>()

    fun ScriptContext.register() {
        onPlayerLogin { discardLeakedRegion(player) }
    }

    private fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(sceneRegions::remove) ?: return
        regionRepo.unprotect(region)
    }

    suspend fun julietDrinks(access: ProtectedAccess) {
        lateinit var juliet: Npc
        lateinit var phillipa: Npc

        access.closeChat()

        access.scene(
            template = RomeoAndJulietRegions.JulietHouse,
            vantage = DeathScene.Vantage,
            faceAt = DeathScene.JulietTile,
            camera = DeathScene.OpeningCamera,
            returnTo = DeathScene.Vantage,
            underFade = { r ->
                juliet = spawn("npc.juliet", r[DeathScene.JulietTile])
                phillipa = spawn("npc.phillipa", r[DeathScene.PhillipaTile])
                juliet.faceSquare(r[DeathScene.Vantage])
                phillipa.faceSquare(r[DeathScene.Vantage])
            },
        ) { r ->
            midiSong("midi.stop_music")
            midiJingle(DeathJingle)

            startDialogue {
                julietLine(happy, "Oh, here's Phillipa, my cousin...she's in on the plot too!")
                julietLine(shifty, "She's going to make it seem even more convincing!")

                access.faceSquare(r[DeathScene.PhillipaTile])
                phillipaLine(happy, "Yes, I'm quite the actress! Good luck dear cousin!")

                access.faceSquare(r[DeathScene.JulietTile])
                julietLine(happy, "Right...bottoms up!")

                access.closeChat()
                juliet.spotanim("spotanim.human_drink_from_vial_cadava_spotanim")
                juliet.anim("seq.human_drink_from_vial_cadava")
                access.soundSynth("synth.LIQUID")
                access.delay(DrinkToUrkTicks)

                julietLine(shocked, "Urk!")
                juliet.facePathingEntitySquare(access.player)

                access.closeChat()
                access.delay(1)
                juliet.anim("seq.human_death")
                access.soundSynth("synth.FEMALE_DEATH")
                access.delay(DeathToPhillipaTicks)

                access.faceSquare(r[DeathScene.PhillipaTile])
                phillipaLine(happy, "Oh no...Juliet has...died!")
                chatPlayer(
                    neutral,
                    "You might be more believable if you're not smiling when you say it...",
                )
                phillipaLine(shocked, "Oh yeah...you might be right...ok, let's try again.")
                phillipaLine(confused, "Oh no...Juliet has...died?")
                chatPlayer(
                    quiz,
                    "Perhaps a bit louder, like you're upset...that your cousin has died!",
                )
                phillipaLine(
                    shifty,
                    "Right...yes...Ok, ok, I think I'm getting my motivation now. Ok, let's try " +
                        "this again!",
                )
                phillipaLine(
                    sad,
                    "OH NO...JULIET HAS...DIED?....<br>Oooooohhhhhh....(sob), (sob).Juliet...my " +
                        "poor dead cousin!",
                )

                val draul = spawn("npc.draul_leptoc", r[DeathScene.DraulSpawn])
                openDraulsDoor(r)
                access.faceSquare(r[DeathScene.DraulSpawn])
                access.frame(r, DeathScene.DraulCamera, SceneCreepRate)
                draulLine(angry, "What's all that screaming?")

                access.closeChat()
                draul.walk(r[DeathScene.DraulStand])
                access.delay(DraulStepTicks)
                draul.faceSquare(r[DeathScene.PhillipaTile])
                access.faceSquare(r[DeathScene.DraulStand])

                draulLine(sad, "Oh no! My poor daughter...what has become of you?")

                access.faceSquare(r[DeathScene.PhillipaTile])
                phillipaLine(
                    sad,
                    "Poor Juliet...make preparations for her body to be placed in the Crypt...",
                )

                access.julietVisibility = JulietVisibility.InCrypt
                quest.setQuestStage(access, RomeoAndJulietStage.TellRomeo)
            }
        }
    }

    private fun openDraulsDoor(r: SceneRegion) {
        val closed = locRepo.findExact(r[DeathScene.DoorClosed], LocShape.WallStraight) ?: return
        locRepo.del(closed, SceneLifespan)
        locRepo.add(
            r[DeathScene.DoorOpen],
            "loc.fai_varrock_castle_door_open",
            SceneLifespan,
            LocAngle.East,
            LocShape.WallStraight,
        )
    }

    suspend fun cryptRescue(access: ProtectedAccess) {
        access.closeChat()
        access.soundSynth("synth.TREK_WALKAWAY")

        // Return to the original conversation tile; the crypt camera vantage is unreachable on the normal map.
        val returnTo = access.player.coords

        lateinit var romeo: Npc
        lateinit var phillipa: Npc

        access.scene(
            template = RomeoAndJulietRegions.Crypt,
            vantage = CryptScene.Vantage,
            faceAt = CryptScene.RomeoSpawn,
            camera = CryptScene.EntryCamera,
            returnTo = returnTo,
            underFade = { r ->
                romeo = spawn("npc.romeo", r[CryptScene.RomeoSpawn])
                romeo.faceSquare(r[CryptScene.Vantage])

                midiSong("midi.stop_music")
                midiJingle(CryptJingle)

                startDialogue {
                    romeoLine(worried, "This is pretty scary...")
                    chatPlayer(angry, "Oh, be quiet...")
                }
                closeChat()
            },
        ) { r ->
            startDialogue {
                access.soundSynth("synth.TREK_WALKTOWARD")
                access.frame(r, CryptScene.Cameras[0], SceneSnapRate)

                chatPlayer(happy, "We're here. Look, Juliet is over there!")

                access.closeChat()
                access.frame(r, CryptScene.Cameras[1], SceneDriftRate)
                access.delay(PanTicks)
                access.frame(r, CryptScene.Cameras[2], SceneCreepRate)
                access.delay(PanTicks)
                access.frame(r, CryptScene.Cameras[3], SceneCreepRate)
                access.delay(PanHoldTicks)
                access.frame(r, CryptScene.Cameras[0], SceneSnapRate)

                chatPlayer(happy, "You go over to her...and I'll go and wait over here...")
                romeoLine(worried, "Ohhh, ok then...")

                access.closeChat()
                romeo.telejump(collision, r[CryptScene.RomeoWalkStart])
                access.frame(r, CryptScene.Cameras[4], SceneSnapRate)
                access.delay(1)

                for (leg in CryptScene.RomeoWalkLegs) {
                    romeo.walk(r[leg.tile])
                    access.delay(leg.steps)

                    if (leg.tile != CryptScene.PhillipaCue) {
                        if (leg.pause > 0) {
                            access.delay(leg.pause)
                        }
                        continue
                    }

                    phillipa = spawn("npc.phillipa", r[CryptScene.PhillipaSpawn])
                    access.delay(leg.pause)
                    phillipa.walk(CryptScene.PhillipaWalkLegs.map { tile -> r[tile] })
                }

                romeo.faceSquare(r[CryptScene.CoffinTile])
                access.faceSquare(r[CryptScene.RomeoWalkEnd])
                access.delay(1)

                romeoLine(worried, "Hey...Juliet...")
                romeoLine(neutral, "Juliet....?")

                phillipa.faceSquare(r[CryptScene.RomeoWalkEnd])
                romeoLine(sad, "Oh dear...you seem to be dead.")

                access.faceSquare(r[CryptScene.PhillipaWalkEnd])
                phillipaLine(happy, "Hi Romeo...I'm Phillipa!")

                access.faceSquare(r[CryptScene.RomeoWalkEnd])
                romeo.faceSquare(r[CryptScene.PhillipaWalkEnd])
                romeoLine(happy, "Wow! You're a fox!")

                access.faceSquare(r[CryptScene.PhillipaWalkEnd])
                phillipaLine(happy, "It's a shame about Juliet...but perhaps we can meet up later?")

                access.faceSquare(r[CryptScene.RomeoWalkEnd])
                romeoLine(happy, "Who's Juliet?")
            }
        }

        access.julietVisibility = JulietVisibility.Returned
        quest.completeQuest(access)
        access.midiJingle(CompletionJingle)
    }

    private suspend fun ProtectedAccess.scene(
        template: RegionTemplate,
        vantage: CoordGrid,
        faceAt: CoordGrid,
        camera: SceneCamera,
        returnTo: CoordGrid,
        underFade: suspend ProtectedAccess.(SceneRegion) -> Unit = {},
        body: suspend ProtectedAccess.(SceneRegion) -> Unit,
    ) {
        val access = this
        val region = regionRepo.add(template)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        // Persist the exit on entry; login rebuilds the scene before login scripts can relocate the player.
        player.loginExitCoord = returnTo
        player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        try {
            lockedScene(
                vantage = scene[vantage],
                faceAt = scene[faceAt],
                camera = scene.map(camera),
                returnTo = returnTo,
                underFade = { access.underFade(scene) },
            ) {
                body(scene)
            }
        } finally {
            player.loginExitCoord = null
            player.uuid?.let(sceneRegions::remove)

            // Unprotect instead of deleting; the registry must wait until no player occupies the region.
            region?.let(regionRepo::unprotect)
        }
    }

    private fun ProtectedAccess.frame(r: SceneRegion, camera: SceneCamera, rate: Int) {
        val mapped = r.map(camera)
        camMoveToV3(mapped.eye, height = mapped.eyeHeight, rate = rate, rate2 = rate)
        camLookAtV3(mapped.lookAt, height = mapped.lookAtHeight, rate = rate, rate2 = rate)
    }

    private fun ProtectedAccess.closeChat() {
        ifCloseSub("interface.chat_left")
        ifCloseSub("interface.chat_right")
    }

    private fun spawn(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npcRepo.add(npc, SceneLifespan)
        npc.noneMode()
        return npc
    }

    private suspend fun Dialogue.julietLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Juliet", "npc.juliet", mesanim, text)

    private suspend fun Dialogue.phillipaLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Phillipa", "npc.phillipa", mesanim, text)

    private suspend fun Dialogue.draulLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Draul Leptoc", "npc.draul_leptoc", mesanim, text)

    private suspend fun Dialogue.romeoLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Romeo", "npc.romeo", mesanim, text)

    private companion object {

        const val SceneLifespan = 3000

        const val DeathJingle = 120
        const val CryptJingle = 168

        const val CompletionJingle = 154

        const val DrinkToUrkTicks = 3
        const val DeathToPhillipaTicks = 4
        const val DraulStepTicks = 2
        const val PanTicks = 4
        const val PanHoldTicks = 6
    }
}

internal object DeathScene {
    val Vantage: CoordGrid = CoordGrid(3157, 3425, 1)

    val JulietTile: CoordGrid = CoordGrid(3157, 3426, 1)
    val PhillipaTile: CoordGrid = CoordGrid(3156, 3426, 1)

    val DraulSpawn: CoordGrid = CoordGrid(3158, 3427, 1)
    val DraulStand: CoordGrid = CoordGrid(3158, 3426, 1)

    val DoorClosed: CoordGrid = CoordGrid(3158, 3426, 1)
    val DoorOpen: CoordGrid = CoordGrid(3158, 3427, 1)

    val OpeningCamera: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3153, 3422, 1),
            eyeHeight = 375,
            lookAt = CoordGrid(3156, 3425, 1),
            lookAtHeight = 175,
        )

    val DraulCamera: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3156, 3421, 1),
            eyeHeight = 425,
            lookAt = CoordGrid(3157, 3426, 1),
            lookAtHeight = 125,
        )
}

internal data class CryptWalkLeg(val tile: CoordGrid, val steps: Int, val pause: Int)

internal object CryptScene {
    val Vantage: CoordGrid = CoordGrid(2333, 4646, 0)

    val RomeoSpawn: CoordGrid = CoordGrid(2333, 4645, 0)
    val RomeoWalkStart: CoordGrid = CoordGrid(2330, 4645, 0)
    val RomeoWalkEnd: CoordGrid = CoordGrid(2323, 4643, 0)

    val CoffinTile: CoordGrid = CoordGrid(2322, 4642, 0)

    val PhillipaSpawn: CoordGrid = CoordGrid(2331, 4645, 0)
    val PhillipaWalkEnd: CoordGrid = CoordGrid(2325, 4644, 0)

    val PhillipaWalkLegs: List<CoordGrid> =
        listOf(CoordGrid(2327, 4645, 0), CoordGrid(2325, 4644, 0))

    val RomeoWalkLegs: List<CryptWalkLeg> =
        listOf(
            CryptWalkLeg(CoordGrid(2329, 4645, 0), steps = 1, pause = 2),
            CryptWalkLeg(CoordGrid(2327, 4645, 0), steps = 2, pause = 1),
            CryptWalkLeg(CoordGrid(2325, 4644, 0), steps = 2, pause = 1),
            CryptWalkLeg(CoordGrid(2323, 4643, 0), steps = 2, pause = 0),
        )

    val PhillipaCue: CoordGrid = CoordGrid(2325, 4644, 0)

    val EntryCamera: SceneCamera =
        SceneCamera(
            eye = CoordGrid(2330, 4641, 0),
            eyeHeight = 400,
            lookAt = CoordGrid(2342, 4655, 0),
            lookAtHeight = 25,
        )

    val Cameras: List<SceneCamera> =
        listOf(
            SceneCamera(CoordGrid(2330, 4641, 0), 400, CoordGrid(2342, 4655, 0), 25),
            SceneCamera(CoordGrid(2334, 4647, 0), 375, CoordGrid(2322, 4639, 0), 250),
            SceneCamera(CoordGrid(2330, 4647, 0), 375, CoordGrid(2320, 4639, 0), 250),
            SceneCamera(CoordGrid(2324, 4644, 0), 250, CoordGrid(2320, 4639, 0), 200),
            SceneCamera(CoordGrid(2322, 4639, 0), 300, CoordGrid(2328, 4654, 0), 25),
        )
}
