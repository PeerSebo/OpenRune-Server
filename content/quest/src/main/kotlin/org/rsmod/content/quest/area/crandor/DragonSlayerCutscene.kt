package org.rsmod.content.quest.area.crandor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.config.constants
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.icemountain.musicRow
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.SceneSnapRate
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.region.Region
import org.rsmod.plugin.scripts.ScriptContext

// Always restore HUD, camera and fov_clamp through non-suspending cancellation cleanup.
internal class DragonSlayerCutscene(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val musicPlayer: MusicPlayer,
) {
    private val sceneRegions = HashMap<Long, Region>()

    fun ScriptContext.register() {
        onPlayerLogin { discardLeakedRegion(player) }
    }

    private fun despawn(npc: Npc) {
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(sceneRegions::remove) ?: return
        regionRepo.unprotect(region)
    }

    suspend fun voyage(access: ProtectedAccess) {
        access.ifCloseSub("interface.chat_left")
        access.ifCloseSub("interface.chat_right")

        val region = regionRepo.add(DragonShipTemplate)
        region?.let(regionRepo::protect)
        val scene = ShipScene(region)

        access.player.loginExitCoord = DragonSlayerRefs.CrandorBeach
        access.player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        val ned =
            spawnShipActor(
                scene,
                DragonSlayerRefs.NedCutscene,
                VoyageTiles.Ned,
                faceTowards = VoyageTiles.NedFace,
            )
        val jenkins = spawnShipActor(scene, DragonSlayerRefs.JenkinsCutscene, VoyageTiles.Jenkins)

        try {
            access.lockedScene(
                vantage = scene[VoyageTiles.Player, DeckLevel],
                faceAt = scene[VoyageTiles.Ned, DeckLevel],
                camera = scene.camera(Opening),
                returnTo = DragonSlayerRefs.CrandorBeach,
                underFade = {
                    musicRow(DragonSlayerRefs.MusicCrestOfAWave)?.let {
                        musicPlayer.unlockAndPlay(access.player, it)
                    }
                    access.midiSong("midi.stop_music")
                    access.midiJingle(DragonSlayerRefs.VoyageJingle)
                },
                teardown = {
                    if (quest.getQuestStage(access.player) < DragonSlayerStage.Shipwrecked) {
                        quest.setQuestStage(access, DragonSlayerStage.Shipwrecked)
                    }
                    scatterBeachPlanks(access)
                    despawn(ned)
                    despawn(jenkins)
                },
            ) {
                voyageBody(scene, ned, jenkins)
            }
        } finally {
            access.player.loginExitCoord = null
            access.player.uuid?.let(sceneRegions::remove)
            region?.let(regionRepo::unprotect)
        }

        access.startDialogue {
            mesbox("You are knocked unconscious and later awake on an ash-strewn beach.")
        }

        musicRow(DragonSlayerRefs.MusicTheShadow)?.let {
            musicPlayer.unlockAndPlay(access.player, it)
        }
    }

    private suspend fun ProtectedAccess.voyageBody(scene: ShipScene, ned: Npc, jenkins: Npc) {
        camShake(1, 0, 10, 8)

        startDialogue {
            chatNedCutscene(
                happy,
                "Ah, it's good to feel the salt spray on my face once again!",
            )
        }

        camMoveToV3(
            scene[VoyageTiles.CloseUpEye, DeckLevel],
            height = 100,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        camLookAtV3(
            scene[VoyageTiles.NedWatch, DeckLevel],
            height = 25,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        startDialogue {
            chatNedCutscene(
                neutral,
                "And this is a mighty fine ship. She don't look much, but she handles like a dream.",
            )
        }

        camMoveToV3(
            scene[VoyageTiles.WideEye, DeckLevel],
            height = 300,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        camLookAtV3(
            scene[VoyageTiles.NedWatch, DeckLevel],
            height = 100,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        startDialogue { chatPlayer(quiz, "How much longer until we reach Crandor?") }

        musicRow(DragonSlayerRefs.MusicCrestOfAWave)?.let { musicPlayer.play(player, it) }

        startDialogue {
            chatNedCutscene(
                happy,
                "Not long now! According to the chart, we'd be able to see Crandor if it wasn't " +
                    "for those clouds on the horizon.",
            )
        }

        ifOpenFullOverlay(DragonSlayerRefs.OverlayClouds)
        soundSynth(DragonSlayerRefs.SynthThunder)
        musicRow(DragonSlayerRefs.MusicEyeOfTheStorm)?.let {
            musicPlayer.unlockAndPlay(player, it)
        }
        startDialogue { mesbox("Clouds surround the ship.") }

        ifCloseSub(DragonSlayerRefs.OverlayClouds)
        camShakeReset(1)
        camLookAtV3(
            scene[VoyageTiles.StormLookAt, DeckLevel],
            height = 300,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camMoveToV3(
            scene[VoyageTiles.StormEye, DeckLevel],
            height = 1500,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camMoveToV3(
            scene[VoyageTiles.StormDrift, DeckLevel],
            height = 1500,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        jenkins.walk(scene[VoyageTiles.JenkinsStormDest, DeckLevel])
        startDialogue {
            chatNpcSpecific(
                JenkinsName,
                DragonSlayerRefs.JenkinsCutscene,
                sad,
                "Looks like there's a storm coming up, cap'n. Soon we won't be able to see anything!",
            )
        }

        startDialogue {
            chatNedCutscene(sad, "Oh, well. The weather had been so good up until now.")
        }

        ifOpenFullOverlay(DragonSlayerRefs.OverlayElvarg)
        soundSynth(DragonSlayerRefs.SynthFlapWings)
        delay(1)
        ifCloseSub(DragonSlayerRefs.OverlayElvarg)

        startDialogue { chatPlayer(shocked, "Did you see that?") }
        startDialogue { chatNedCutscene(quiz, "See what?") }
        startDialogue { chatPlayer(neutral, "I thought I saw something up above us.") }

        camMoveToV3(
            scene[VoyageTiles.StrafeEye, DeckLevel],
            height = 300,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camLookAtV3(
            scene[VoyageTiles.StrafeLookAt, DeckLevel],
            height = 100,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camMoveToV3(
            scene[VoyageTiles.StrafeDrift, DeckLevel],
            height = 300,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        soundSynth(DragonSlayerRefs.SynthDragonBreath)
        soundSynth(DragonSlayerRefs.SynthBurningFire, delay = 55)
        soundSynth(DragonSlayerRefs.SynthBurningFire, delay = 85)
        soundSynth(DragonSlayerRefs.SynthBurningFire, delay = 115)
        sprayFire(scene, VoyageTiles.ForwardFireTrail, FirstBurstDelays)
        delay(1)

        faceSquare(scene[VoyageTiles.StrafeLookAt, DeckLevel])
        ned.faceSquare(scene[VoyageTiles.StrafeLookAt, DeckLevel])
        jenkins.faceSquare(scene[VoyageTiles.StrafeLookAt, DeckLevel])
        delay(1)

        ned.anim(DragonSlayerRefs.SeqPanic)
        jenkins.anim(DragonSlayerRefs.SeqPanicFlap)
        val forwardFires = igniteTrail(scene, VoyageTiles.ForwardFireTrail)

        startDialogue { chatNedCutscene(worried, "It's the dragon!") }

        ifOpenFullOverlay(DragonSlayerRefs.OverlayElvargFly)
        soundSynth(DragonSlayerRefs.SynthDragonScream)
        delay(3)

        ifCloseSub(DragonSlayerRefs.OverlayElvargFly)
        camMoveToV3(
            scene[VoyageTiles.AlightEye, DeckLevel],
            height = 300,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camLookAtV3(
            scene[VoyageTiles.AlightLookAt, DeckLevel],
            height = 100,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camMoveToV3(
            scene[VoyageTiles.AlightDrift, DeckLevel],
            height = 300,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        camShake(1, 0, 5, 8)
        for (delay in FireBurstDelays) {
            soundSynth(DragonSlayerRefs.SynthBurningFire, delay = delay)
        }
        sprayFire(scene, VoyageTiles.AftFireTrail, SecondBurstDelays)
        forwardFires[VoyageTiles.LargeFire]?.let { locRepo.del(it, Int.MAX_VALUE) }
        addFire(scene, VoyageTiles.LargeFire, DragonSlayerRefs.DeckFireLarge)
        delay(1)

        faceSquare(scene[VoyageTiles.AlightLookAt, DeckLevel])
        ned.faceSquare(scene[VoyageTiles.AlightLookAt, DeckLevel])
        jenkins.faceSquare(scene[VoyageTiles.AlightLookAt, DeckLevel])
        ned.anim(DragonSlayerRefs.SeqPanicFlap)
        jenkins.anim(DragonSlayerRefs.SeqPanic)
        addFire(scene, VoyageTiles.AftFireTrail[0], DragonSlayerRefs.DeckFireSmall)
        delay(1)

        jenkins.anim(DragonSlayerRefs.SeqJenkinsScared, delay = 6)
        addFire(scene, VoyageTiles.AftFireTrail[1], DragonSlayerRefs.DeckFireSmall)
        delay(1)

        addFire(scene, VoyageTiles.AftFireTrail[2], DragonSlayerRefs.DeckFireSmall)
        delay(1)

        jenkins.anim(DragonSlayerRefs.SeqHumanDeath)
        jenkins.showDeathHit()
        soundSynth(DragonSlayerRefs.SynthHumanHit)
        soundSynth(DragonSlayerRefs.SynthHumanDeath)
        addFire(scene, VoyageTiles.AftFireTrail[3], DragonSlayerRefs.DeckFireSmall)
        delay(1)

        addFire(scene, VoyageTiles.AftFireTrail[4], DragonSlayerRefs.DeckFireSmall)
        delay(1)

        despawn(jenkins)
        addFire(scene, VoyageTiles.SinkLargeFire, DragonSlayerRefs.DeckFireLarge)
        camMoveToV3(
            scene[VoyageTiles.SinkEye, DeckLevel],
            height = 600,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camLookAtV3(
            scene[VoyageTiles.NedWatch, DeckLevel],
            height = 100,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camMoveToV3(
            scene[VoyageTiles.SinkDrift, DeckLevel],
            height = 400,
            rate = SceneCreep,
            rate2 = SceneCreep,
        )
        camShake(1, 0, 10, 8)
        startDialogue { chatNedCutscene(worried, "We're going to sink!") }

        startDialogue { chatPlayer(shocked, "Look! Land ahead!") }

        camMoveToV3(
            scene[VoyageTiles.SinkDrift, DeckLevel],
            height = 400,
            rate = SceneNudge,
            rate2 = SceneNudge,
        )
        startDialogue { chatNedCutscene(worried, "We're going to crash!") }

        camShake(0, 20, 0, 0)
        soundSynth(DragonSlayerRefs.SynthCrashShip)
        startDialogue { mesbox("<col=800000>CRASH!") }
    }

    private fun sprayFire(scene: ShipScene, tiles: List<ShipTile>, delays: List<Int>) {
        for ((index, tile) in tiles.withIndex()) {
            worldRepo.spotanimMap(
                deckFireSpotanim,
                scene[tile, DeckLevel],
                delay = delays.getOrElse(index) { delays.last() },
            )
        }
    }

    private suspend fun ProtectedAccess.igniteTrail(
        scene: ShipScene,
        tiles: List<ShipTile>,
    ): Map<ShipTile, LocInfo> {
        val fires = HashMap<ShipTile, LocInfo>()
        for ((index, tile) in tiles.withIndex()) {
            if (index > 0) {
                delay(1)
            }
            fires[tile] = addFire(scene, tile, DragonSlayerRefs.DeckFireSmall)
        }
        return fires
    }

    private val deckFireSpotanim by lazy {
        SpotanimType(DragonSlayerRefs.SpotanimDeckFire.asRSCM(RSCMType.SPOTANIM))
    }

    // This is a scripted visual death; emit the hitmark without invoking combat damage or drops.
    private fun Npc.showDeathHit() {
        val hitmarkId = DragonSlayerRefs.JenkinsDeathHitmark.asRSCM(RSCMType.HITMARK)
        showHitmark(
            Hitmark.fromNoSource(
                self = hitmarkId,
                source = hitmarkId,
                public = hitmarkId,
                damage = JenkinsDeathDamage,
                delay = 0,
            )
        )
        val headbarId = DragonSlayerRefs.JenkinsDeathHeadbar.asRSCM(RSCMType.HEADBAR)
        showHeadbar(
            Headbar.fromNoSource(
                self = headbarId,
                public = headbarId,
                startFill = 0,
                endFill = 0,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun addFire(scene: ShipScene, tile: ShipTile, type: String): LocInfo =
        locRepo.add(
            scene[tile, DeckLevel],
            type,
            SceneActorTicks,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )

    private fun spawnShipActor(
        scene: ShipScene,
        type: String,
        tile: ShipTile,
        faceTowards: ShipTile? = null,
    ): Npc {
        val npc = Npc(type, scene[tile, DeckLevel])
        npcRepo.add(npc, SceneActorTicks)
        npc.noneMode()
        faceTowards?.let { npc.faceSquare(scene[it, DeckLevel]) }
        return npc
    }

    private fun scatterBeachPlanks(access: ProtectedAccess) {
        for (coords in DragonSlayerRefs.BeachPlanks) {
            objRepo.add(
                DragonSlayerRefs.WoodPlank,
                coords,
                constants.lootdrop_duration,
                receiver = access.player,
            )
        }
    }

    suspend fun crandorDescent(access: ProtectedAccess) {
        if (quest.getQuestStage(access.player) >= DragonSlayerStage.ElvargSlain) {
            return
        }
        val region = regionRepo.add(CrandorLairTemplate)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        access.player.loginExitCoord = DragonSlayerRefs.CrandorDungeon
        access.player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        var elvarg: Npc? = null

        try {
            access.lockedScene(
                vantage = scene[DescentScene.Vantage],
                faceAt = scene[DescentScene.ElvargMark],
                camera = scene.map(DescentScene.Opening),
                returnTo = DragonSlayerRefs.CrandorDungeon,
                underFade = {
                    val body = Npc(DragonSlayerRefs.ElvargCutscene, scene[DescentScene.ElvargMark])
                    npcRepo.add(body, SceneActorTicks)
                    body.noneMode()
                    body.faceSquare(scene[DescentScene.ElvargFaceTarget])
                    elvarg = body

                    musicRow(DragonSlayerRefs.MusicAttack2)?.let {
                        musicPlayer.unlockAndPlay(access.player, it)
                    }
                },
                teardown = {
                    elvarg?.let(this@DragonSlayerCutscene::despawn)
                    musicRow(DragonSlayerRefs.MusicDangerousRoad)?.let {
                        musicPlayer.unlockAndPlay(access.player, it)
                    }
                },
            ) {
                descentBody(scene, elvarg)
            }
        } finally {
            access.player.loginExitCoord = null
            access.player.uuid?.let(sceneRegions::remove)
            region?.let(regionRepo::unprotect)
        }
    }

    private suspend fun ProtectedAccess.descentBody(scene: SceneRegion, elvarg: Npc?) {
        delay(1)

        val push = scene.map(DescentScene.PushIn)
        camLookAtV3(push.lookAt, height = push.lookAtHeight, rate = SceneNudge, rate2 = SceneNudge)
        camMoveToV3(push.eye, height = push.eyeHeight, rate = SceneNudge, rate2 = SceneNudge)
        delay(DescentPushToStepsTicks)

        for (step in 0 until DescentFootfalls) {
            soundSynth(DragonSlayerRefs.SynthDragonWalk, delay = (step + 1) * DescentFootfallGap)
        }
        elvarg?.walk(scene[DescentScene.ElvargWalkDest])
        delay(DescentStepsToExitTicks)
    }

    private companion object {

        const val JenkinsName = "Jenkins"

        const val DeckLevel = 1

        // Camera rate is speed: higher is faster, and one is the slowest dolly.
        const val SceneCreep = 1

        const val SceneNudge = 2

        const val SceneActorTicks = 500

        const val JenkinsDeathDamage = 10

        val FirstBurstDelays = listOf(55, 85, 115)

        val SecondBurstDelays = listOf(55, 85, 115, 145, 175)

        val FireBurstDelays = listOf(55, 85, 115, 145, 175)

        const val DescentPushToStepsTicks = 2

        const val DescentStepsToExitTicks = 4

        const val DescentFootfalls = 7

        const val DescentFootfallGap = 25

        val Opening =
            ShipCamera(
                eye = VoyageTiles.OpeningEye,
                eyeHeight = 100,
                lookAt = VoyageTiles.OpeningLookAt,
                lookAtHeight = 25,
            )
    }
}

private suspend fun Dialogue.chatNedCutscene(mesanim: MesAnimType, text: String) {
    chatNpcSpecific(CaptainNedName, DragonSlayerRefs.NedCutscene, mesanim, text)
}

private const val CaptainNedName = "Captain Ned"
