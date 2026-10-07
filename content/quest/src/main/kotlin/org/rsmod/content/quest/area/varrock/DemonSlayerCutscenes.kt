package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.SceneSnapRate
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal class DemonSlayerCutscenes(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val musicPlayer: MusicPlayer,
    private val random: GameRandom,
) {

    private val sceneRegions = HashMap<Long, Region>()

    fun ScriptContext.register() {
        onPlayerLogin { discardLeakedRegion(player) }
    }

    private fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(sceneRegions::remove) ?: return
        regionRepo.unprotect(region)
    }

    suspend fun wallyFlashback(access: ProtectedAccess) {
        access.closeChat()

        val returnTo = access.player.coords

        val region = regionRepo.add(DemonSlayerCircleTemplate)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        access.player.loginExitCoord = returnTo
        access.player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        try {
            access.lockedScene(
                vantage = scene[WallyScene.Vantage],
                faceAt = scene[WallyScene.FaceNorth],
                camera = scene.map(WallyScene.Opening),
                returnTo = returnTo,
                underFade = {
                    access.transmog(DemonSlayerRefs.Wally)
                    access.rebuildAppearance()
                },
                teardown = {
                    access.resetTransmog()
                    access.resetAnim()
                },
            ) {
                wallyBody(scene)
            }
        } finally {
            access.player.loginExitCoord = null
            access.player.uuid?.let(sceneRegions::remove)

            region?.let(regionRepo::unprotect)
        }

        if (quest.isQuestNotStarted(access.player)) {
            quest.setQuestStage(access, DemonSlayerStage.ArisSpoken)
        }
    }

    private suspend fun ProtectedAccess.wallyBody(scene: SceneRegion) {
        val wallyTrack = MusicRow.getRow(DemonSlayerRefs.MusicWally)
        musicPlayer.unlock(player, wallyTrack)
        musicPlayer.play(player, wallyTrack)

        camShake(1, 0, 10, 10)
        camShake(3, 0, 90, 1)
        soundSynth(DemonSlayerRefs.SynthRumbling)

        startDialogue {
            chatNpcSpecific(
                ArisName,
                DemonSlayerRefs.Aris,
                neutral,
                "Wally managed to arrive at the stone circle just as Delrith was summoned by a " +
                    "cult of chaos druids...",
            )
        }

        telejump(scene[WallyScene.SouthEdge])
        chatNpcSpecificNp(WallyName, DemonSlayerRefs.Wally, angryMesAnim, "Die, foul demon!")
        camLookAtV3(
            scene[WallyScene.StrikeLookAt],
            height = 200,
            rate = StrikeRate,
            rate2 = StrikeRate2,
        )
        camLookAtV3(scene[WallyScene.StrikeLookAt], height = 100, rate = 1, rate2 = StrikeRate2)
        camShakeReset(3)
        soundSynth(DemonSlayerRefs.SynthRumbling)

        playerRun(scene[WallyScene.TableMark])
        delay(StrikeToSwordTicks - RunSuspendTicks)

        faceSquare(scene[WallyScene.StrikeFace])
        anim(SeqWallyStrike)
        soundSynth(DemonSlayerRefs.SynthWallySword, delay = SwordSynthDelay)
        delay(SwordToIncantationTicks)

        camMoveToV3(scene[WallyScene.PushIn], height = 100, rate = StrikeRate, rate2 = StrikeRate2)
        camShake(1, 0, 10, 5)
        camShake(3, 0, 2, 50)
        soundSynth(DemonSlayerRefs.SynthRumbling)

        startDialogue {
            chatNpcSpecific(
                WallyName,
                DemonSlayerRefs.Wally,
                quiz,
                "Now, what was that incantation again?",
            )
        }

        DemonSlayerIncantation.roll(player, random)
        startDialogue {
            chatNpcSpecific(
                WallyName,
                DemonSlayerRefs.Wally,
                angry,
                DemonSlayerIncantation.recite(player),
            )
        }

        closeChat()
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        delay(FadeToFinaleTicks)

        camShakeReset(1)
        camShakeReset(3)
        soundSynth(DemonSlayerRefs.SynthRumbling)
        val finale = scene.map(WallyScene.Finale)
        camMoveToV3(
            finale.eye,
            height = finale.eyeHeight,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camLookAtV3(
            finale.lookAt,
            height = finale.lookAtHeight,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        anim(DemonSlayerRefs.SeqReceivingSilverlight)
        spotanim(DemonSlayerRefs.SpotanimSwordGlint)
        faceSquare(scene[WallyScene.SouthEdge])
        closeFadeOverlayNow()

        soundSynth(DemonSlayerRefs.SynthEquipSilverlight)
        midiSong("midi.stop_music")
        midiJingle(DemonSlayerRefs.CompletionJingle)

        startDialogue {
            chatNpcSpecific(
                WallyName,
                DemonSlayerRefs.Wally,
                happy,
                "I am the greatest demon slayer EVER!",
            )
        }

        musicPlayer.resume(player)

        startDialogue {
            chatNpcSpecific(
                ArisName,
                DemonSlayerRefs.Aris,
                happy,
                "By reciting the correct magical incantation, and thrusting Silverlight into " +
                    "Delrith while he was newly summoned, Wally was able to imprison Delrith in " +
                    "the stone block in the centre of the circle.",
            )
        }
    }

    private val angryMesAnim by lazy {
        ServerCacheManager.getMesAnim("mesanim.angry".asRSCM(RSCMType.MESANIM))
            ?: error("mesanim.angry has no definition in the SERVER cache.")
    }

    private companion object {

        const val ArisName = "Aris"
        const val WallyName = "Wally"

        const val StrikeRate = 2

        const val StrikeRate2 = 10

        const val SeqWallyStrike = "seq.qip_ds_wally_cutscene"

        const val StrikeToSwordTicks = 4

        const val RunSuspendTicks = 1

        const val SwordToIncantationTicks = 4

        // Sound delay is in client cycles, not game ticks.
        const val SwordSynthDelay = 10

        const val FadeDuration = 50

        const val FadeToFinaleTicks = 4
    }
}

internal object WallyScene {
    val Vantage: CoordGrid = CoordGrid(3225, 3371, 0)

    val FaceNorth: CoordGrid = CoordGrid(3225, 3372, 0)

    val SouthEdge: CoordGrid = CoordGrid(3225, 3363, 0)

    val TableMark: CoordGrid = CoordGrid(3227, 3367, 0)

    val StrikeFace: CoordGrid = CoordGrid(3227, 3369, 0)

    val Opening: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3227, 3369, 0),
            eyeHeight = 300,
            lookAt = CoordGrid(3229, 3367, 0),
            lookAtHeight = 250,
        )

    val StrikeLookAt: CoordGrid = CoordGrid(3227, 3367, 0)

    val PushIn: CoordGrid = CoordGrid(3227, 3369, 0)

    val Finale: SceneCamera =
        SceneCamera(
            eye = CoordGrid(3225, 3363, 0),
            eyeHeight = 500,
            lookAt = CoordGrid(3227, 3367, 0),
            lookAtHeight = 200,
        )

    val Cameras: List<SceneCamera> =
        listOf(
            Opening,
            Finale,
            SceneCamera(PushIn, 100, StrikeLookAt, 100),
            SceneCamera(FaceNorth, 0, SouthEdge, 0),
            SceneCamera(TableMark, 0, StrikeFace, 0),
        )
}
