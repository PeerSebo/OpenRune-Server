package org.rsmod.content.quest.area.blackknights

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal object SabotageScene {
    val Cauldron: CoordGrid = CoordGrid(3031, 3507)

    val ProjectileStart: CoordGrid = CoordGrid(3032, 3507)

    val CatTile: CoordGrid = CoordGrid(3027, 3507)

    val CaptainTile: CoordGrid = CoordGrid(3028, 3509)

    val WitchSpawn: CoordGrid = CoordGrid(3030, 3509)

    val WitchForward: CoordGrid = CoordGrid(3030, 3508)

    val WitchAtCauldron: CoordGrid = CoordGrid(3030, 3507)

    val Vantage: CoordGrid = CoordGrid(3017, 3497)

    val Camera: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3035, 3499),
            eyeHeight = 1500,
            lookAt = CoordGrid(3032, 3505),
            lookAtHeight = 300,
        )
}

// Set the cauldron varbit inside the scene; advance the quest after teardown.
internal class BlackKnightsFortressCutscene(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) {

    private val sceneRegions = HashMap<Long, Region>()

    fun ScriptContext.register() {
        onPlayerLogin { discardLeakedRegion(player) }
    }

    private fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(sceneRegions::remove) ?: return
        regionRepo.unprotect(region)
    }

    suspend fun sabotage(access: ProtectedAccess) {
        lateinit var witch: Npc
        lateinit var captain: Npc

        val cast = mutableListOf<Npc>()

        val returnTo = access.coords

        access.closeChat()

        access.scene(
            vantage = SabotageScene.Vantage,
            faceAt = SabotageScene.Cauldron,
            camera = SabotageScene.Camera,
            returnTo = returnTo,
            underFade = { r ->
                val cat = spawn(BlackKnightsFortressState.BlackCat, r[SabotageScene.CatTile])
                captain = spawn(BlackKnightsFortressState.Captain, r[SabotageScene.CaptainTile])
                witch = spawn(BlackKnightsFortressState.Witch, r[SabotageScene.WitchSpawn])
                cast += listOf(cat, captain, witch)

                captain.faceSquare(r[SabotageScene.Cauldron])
                witch.faceSquare(r[SabotageScene.Cauldron])
                cat.faceSquare(r[SabotageScene.Cauldron])

                player.midiSong(BlackKnightsFortressState.StopMusic)
                midiJingle(BlackKnightsFortressState.CutsceneJingle)
            },
            teardown = { cast.forEach(::despawn) },
        ) { r ->
            startDialogue {
                delay(WitchStepTicks)
                witch.walk(r[SabotageScene.WitchForward])
                delay(WitchFirstLineTicks)

                witchLine(neutral, "Where has Greldo got to with that magic cabbage!")

                witch.walk(r[SabotageScene.WitchSpawn])
                delay(NoiseTicks)

                access.soundSynth(BlackKnightsFortressState.NoiseSound)
                delay(CaptainNoticesTicks)

                captainLine(quiz, "What's that noise?")
                witchLine(
                    neutral,
                    "Hopefully Greldo with the cabbage... yes, look here it<br>co....NOOOOOoooo!",
                )

                delay(CabbageFallsTicks)
                access.cabbageFalls(r)
                delay(PotionRuinedTicks)

                access.vars[BlackKnightsFortressState.CauldronVarbit] = 1

                player.midiSong(BlackKnightsFortressState.CutsceneMusic)
                witchLine(shocked, "My potion!")

                witch.anim(BlackKnightsFortressState.CryAnim)
                witch.walk(r[SabotageScene.WitchAtCauldron])
                delay(CaptainDespairTicks)

                captainLine(worried, "Oh boy, this doesn't look good!")

                captain.anim(BlackKnightsFortressState.PanicAnim)
                catLine(neutral, CatSay)
                delay(CatSoundTicks)

                worldRepo.soundArea(
                    r[SabotageScene.CatTile],
                    BlackKnightsFortressState.CatSound,
                    radius = CatSoundRadius,
                )
                delay(HoldTicks)
            }
        }

        quest.setQuestStage(access, BlackKnightsFortressState.Sabotaged)
        access.startDialogue {
            chatPlayer(
                happy,
                "Looks like my work here is done. Seems like that's<br>successfully sabotaged " +
                    "their little secret weapon plan.",
            )
        }
    }

    private fun ProtectedAccess.cabbageFalls(r: SceneRegion) {
        spotanimMap(
            worldRepo,
            BlackKnightsFortressState.CauldronSpotanim,
            r[SabotageScene.Cauldron],
            height = 0,
            delay = CauldronAnimDelay,
        )
        worldRepo.projAnim(
            ProjAnim(
                spotanim = BlackKnightsFortressState.CabbageProjectile.asRSCM(RSCMType.SPOTANIM),
                startHeight = FallStartHeight,
                endHeight = FallEndHeight,
                startTime = FallStartTime,
                endTime = FallEndTime,
                angle = FallAngle,
                progress = FallProgress,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = r[SabotageScene.ProjectileStart],
                endCoord = r[SabotageScene.Cauldron],
            )
        )
        soundSynth(BlackKnightsFortressState.SplashSound)
    }

    private suspend fun ProtectedAccess.scene(
        vantage: CoordGrid,
        faceAt: CoordGrid,
        camera: SceneCamera,
        returnTo: CoordGrid,
        underFade: suspend ProtectedAccess.(SceneRegion) -> Unit = {},
        teardown: () -> Unit = {},
        body: suspend ProtectedAccess.(SceneRegion) -> Unit,
    ) {
        val access = this
        val template: RegionTemplate = BlackKnightsFortressRegions.WitchRoom
        val region = regionRepo.add(template)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        player.loginExitCoord = returnTo
        player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        try {
            lockedScene(
                vantage = scene[vantage],
                faceAt = scene[faceAt],
                camera = scene.map(camera),
                returnTo = returnTo,
                underFade = { access.underFade(scene) },
                teardown = teardown,
            ) {
                body(scene)
            }
        } finally {
            player.loginExitCoord = null
            player.uuid?.let(sceneRegions::remove)

            region?.let(regionRepo::unprotect)
        }
    }

    private fun spawn(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npcRepo.add(npc, SceneLifespan)
        npc.noneMode()
        return npc
    }

    private fun despawn(npc: Npc) {
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.closeChat() {
        ifCloseSub("interface.chat_left")
        ifCloseSub("interface.chat_right")
    }

    private suspend fun Dialogue.witchLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.WitchTitle,
            BlackKnightsFortressState.Witch,
            mesanim,
            text,
        )

    private suspend fun Dialogue.captainLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.CaptainTitle,
            BlackKnightsFortressState.Captain,
            mesanim,
            text,
        )

    private suspend fun Dialogue.catLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific(
            BlackKnightsFortressState.BlackCatTitle,
            BlackKnightsFortressState.BlackCat,
            mesanim,
            text,
        )

    private companion object {

        const val SceneLifespan = 3000

        const val CatSay = "Meow!"

        const val WitchStepTicks = 5
        const val WitchFirstLineTicks = 5
        const val NoiseTicks = 2
        const val CaptainNoticesTicks = 3
        const val CabbageFallsTicks = 2
        const val PotionRuinedTicks = 5
        const val CaptainDespairTicks = 1

        const val CatSoundTicks = 1

        const val HoldTicks = 3

        const val CatSoundRadius = 5

        const val CauldronAnimDelay = 100

        const val FallStartHeight = 500
        const val FallEndHeight = 50
        const val FallStartTime = 0
        const val FallEndTime = 100
        const val FallAngle = 192
        const val FallProgress = 128
    }
}
