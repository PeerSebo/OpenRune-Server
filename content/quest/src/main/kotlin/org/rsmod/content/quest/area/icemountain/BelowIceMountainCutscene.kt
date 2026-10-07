package org.rsmod.content.quest.area.icemountain

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.corsaircove.SceneFrame
import org.rsmod.content.quest.area.misthalin.SceneDriftRate
import org.rsmod.content.quest.area.misthalin.SceneFadeDuration
import org.rsmod.content.quest.area.misthalin.SceneFadeTicks
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.SceneSnapRate
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class BelowIceMountainCutscene(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val musicPlayer: MusicPlayer,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
) {
    private val ruinsRegions = HashMap<Long, List<Region?>>()

    private val ruinsEntered = HashSet<Long>()

    private fun markRuinsEntered(player: Player) {
        player.uuid?.let { ruinsEntered += it }
    }

    private val guardians = HashMap<Long, ActiveGuardian>()

    private class ActiveGuardian(val owner: Player, val npc: Npc)

    fun activeGuardian(player: Player): Npc? = player.uuid?.let { guardians[it]?.npc }

    fun guardianOwner(npc: Npc): Player? = guardians.values.firstOrNull { it.npc === npc }?.owner

    fun clearGuardian(npc: Npc) {
        guardians.entries.removeIf { it.value.npc === npc }
    }

    suspend fun workout(access: ProtectedAccess) {
        val region = regionRepo.add(BelowIceMountainRegions.Montage)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)
        val cast = mutableListOf<Npc>()

        try {
            access.lockedScene(
                vantage = scene[MontageCity.Vantage],
                faceAt = scene[MontageCity.VantageFacing],
                camera = scene.map(MontageCity.Opening.toCamera()),
                returnTo = MontageExit,
                underFade = {
                    access.hideWornAppearance()
                    cast += spawn(BelowIceMountainRefs.Hobo, scene[MontageCity.Hobo])
                    cast += spawn(BelowIceMountainRefs.Rat, scene[MontageCity.Rat])
                    cast +=
                        spawn(BelowIceMountainRefs.Aubury, scene[MontageCity.Aubury], wander = true)
                    cast += spawn(BelowIceMountainRefs.Man, scene[MontageCity.Man])
                },
                teardown = {
                    access.showWornAppearance()
                    cast.forEach(::despawn)
                },
            ) {
                unlockMusic(BelowIceMountainRefs.WorkoutMusic)

                val beats = Beats(access, MontageStart)
                cityWarmUp(scene, cast, beats)
                pressUps(scene, cast, beats, first = true)
                sparring(scene, cast, beats)
                roadWork(scene, cast, beats)
                pressUps(scene, cast, beats, first = false)
                cityFinish(scene, cast, beats)
                atlasFinale(scene, cast, beats)
            }
        } finally {
            region?.let(regionRepo::unprotect)
        }
    }

    private suspend fun ProtectedAccess.cityWarmUp(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
    ) {
        val hobo = cast[0]
        val rat = cast[1]
        val aubury = cast[2]
        val man = cast[3]

        beats.at(840)
        atlasSays("If you want to become a strongman, you need to get into shape!")

        beats.at(842)
        anim(RunOnSpotSeq)

        beats.at(846)
        anim(StarJumpSeq)

        beats.at(855)
        lookAt(scene, MontageCity.OpeningCreep, rate = 1)
        walkTo(scene[MontageCity.WarmUpWalk])

        beats.at(857)
        frame(scene, MontageCity.Street)
        mes(HoboExamine, ChatType.NpcExamine)

        beats.at(861)
        mes(CitizenExamine, ChatType.NpcExamine)
        atlasSays("It's not all about strength! You need to get some cardio in, too.")

        beats.at(866)
        val man2 = spawn(BelowIceMountainRefs.Man2, scene[MontageCity.Man2])
        cast += man2
        man2.walk(scene[MontageCity.Man2Dest])

        beats.at(869)
        runTo(scene[MontageCity.WarmUpRun])

        beats.at(872)
        dolly(scene, MontageCity.StreetDolly, rate = 3)

        beats.at(874)
        despawn(hobo)

        beats.at(875)
        val woman = spawn(BelowIceMountainRefs.Woman, scene[MontageCity.Woman])
        cast += woman

        beats.at(876)
        despawn(rat)
        despawn(aubury)

        beats.at(878)
        despawn(man)
        despawn(man2)

        beats.at(881)
        frame(scene, MontageCity.Square)

        beats.at(882)
        dollyBoth(scene, MontageCity.SquareDolly, rate = 3)
        runTo(scene[MontageCity.WarmUpRun2])

        beats.at(885)
        woman.faceSquare(scene[MontageCity.WomanFacing])
        woman.anim(ThrowSeq)
        woman.spotanim(ThrowSpotanim, height = ThrowSpotanimHeight)
        woman.say("Catch!")
        tomatoThrow(from = scene[MontageCity.Woman], to = scene[MontageCity.TomatoSplat])
        spotanimMap(
            worldRepo,
            TomatoSplatSpotanim,
            scene[MontageCity.TomatoSplat],
            delay = TomatoFlightDelay,
        )
        soundArea(
            worldRepo,
            scene[MontageCity.TomatoSplat],
            TomatoThrowSound,
            radius = TomatoSoundRadius,
        )
        soundArea(
            worldRepo,
            scene[MontageCity.TomatoSplat],
            TomatoSplatSound,
            delay = TomatoFlightDelay,
            radius = TomatoSoundRadius,
        )

        beats.at(886)
        cast += spawn(BelowIceMountainRefs.Man, scene[MontageCity.SquareMan])

        beats.at(888)
        cast += spawn(BelowIceMountainRefs.Baraek, scene[MontageCity.Baraek], wander = true)

        beats.at(889)
        mes(CitizenExamine, ChatType.NpcExamine)

        beats.at(891)
        fadeOut(FastFadeDuration)
    }

    private suspend fun ProtectedAccess.pressUps(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
        first: Boolean,
    ) {
        beats.at(if (first) 893 else 967)
        cast.forEach(::despawn)
        cast.clear()
        telejump(scene[MontageBarbarian.PushupMark])
        faceSquare(scene[MontageBarbarian.PushupFacing])
        val atlas = spawn(BelowIceMountainRefs.CutsceneAtlas, scene[MontageBarbarian.Atlas])
        atlas.facePathingEntitySquare(player)
        cast += atlas
        if (first) {
            val watcher =
                spawn(BelowIceMountainRefs.BarbarianFemale, scene[MontageBarbarian.Watcher])
            watcher.facePathingEntitySquare(player)
            cast += watcher
        } else {
            val types =
                listOf(
                    BelowIceMountainRefs.BarbarianMale,
                    BelowIceMountainRefs.BarbarianMale2,
                    BelowIceMountainRefs.BarbarianFemale,
                    BelowIceMountainRefs.BarbarianFemale2,
                )
            types.forEachIndexed { index, type ->
                val watcher = spawn(type, scene[MontageBarbarian.Watchers[index]])
                watcher.facePathingEntitySquare(player)
                cast += watcher
            }
        }

        beats.at(if (first) 894 else 968)
        frame(scene, MontageBarbarian.Counting)
        anim(BelowIceMountainRefs.PushupsSeq)
        fadeIn()
        if (!first) {
            atlasSays("You need to push yourself!")
        }

        val start = if (first) 896 else 971
        val counts = if (first) 1..9 else 91..100
        val emote = if (first) CheerSeq else YesSeq
        counts.forEachIndexed { index, count ->
            beats.at(start + index * CountGap)
            atlas.say("$count...")
            if (index % EmoteEveryNth == if (first) 2 else 0) {
                atlas.anim(emote)
            }
        }

        if (!first) {
            anim(BelowIceMountainRefs.PushupsStandSeq)
            beats.at(991)
            faceSquare(scene[MontageBarbarian.Atlas])
        }

        beats.at(if (first) 912 else 994)
        fadeOut(FastFadeDuration)
    }

    private suspend fun ProtectedAccess.sparring(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
    ) {
        beats.at(914)
        val atlas = cast.first()
        atlas.telejump(scene[MontageBarbarian.SparAtlas])
        telejump(scene[MontageBarbarian.SparPlayer])
        faceSquare(scene[MontageBarbarian.SparAtlas])
        atlas.facePathingEntitySquare(player)
        frame(scene, MontageBarbarian.Sparring)
        fadeIn()
        atlasSays("You need to be tough to be a strongman! Show me what you've got! Come on!")

        for (index in 0 until PunchCount) {
            beats.at(915 + index * PunchGap)
            atlas.anim(PunchSeq)
            anim(BlockSeq)
            soundSynth(PunchSound)
            if (index in HurtPunches) {
                say("Eugh...")
            }
        }

        beats.at(933)
        fadeOut(FastFadeDuration)
    }

    private suspend fun ProtectedAccess.roadWork(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
    ) {
        beats.at(935)
        cast.forEach(::despawn)
        cast.clear()
        camReset()
        telejump(scene[MontageBarbarian.RoadStart])
        frame(scene, MontageBarbarian.Running)
        fadeIn()

        beats.at(936)
        runTo(scene[MontageBarbarian.RoadLeg])

        beats.at(939)
        atlasSays("Every strongman was once a beginner who refused to give up.")

        beats.at(940)
        dolly(scene, MontageBarbarian.RunningDolly, rate = 3)
        lookAt(scene, MontageBarbarian.RunningDolly, rate = 2)

        for (tick in RunningChaseTicks) {
            beats.at(tick)
            camLookAtV3(player.coords, height = 0, rate = 1, rate2 = 1)
        }

        beats.at(954)
        runTo(scene[MontageBarbarian.RoadLeg2])
        dolly(scene, MontageBarbarian.RunningSettle, rate = 1)

        beats.at(957)
        dolly(scene, MontageBarbarian.RunningRise, rate = 3)
        lookAt(scene, MontageBarbarian.RunningRise, rate = 1)

        beats.at(960)
        lookAt(scene, MontageBarbarian.RunningEnd, rate = 1)

        beats.at(965)
        fadeOut()
    }

    private suspend fun ProtectedAccess.cityFinish(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
    ) {
        beats.at(996)
        cast.forEach(::despawn)
        cast.clear()
        telejump(scene[MontageCity.ClosingVantage])
        faceSquare(scene[MontageCity.ClosingFacing])
        val dog = spawn(BelowIceMountainRefs.StrayDog, scene[MontageCity.Dog])
        cast += dog

        beats.at(997)
        frame(scene, MontageCity.Closing)
        fadeIn()
        dollyBoth(scene, MontageCity.ClosingDolly, rate = 3)
        runTo(scene[MontageCity.ClosingRun])
        atlasSays("Always strive to be the best that you can be! That is the strongman way.")

        beats.at(1005)
        cast += spawn(BelowIceMountainRefs.Man, scene[MontageCity.ClosingMan])

        beats.at(1007)
        cast += spawn(BelowIceMountainRefs.Baraek, scene[MontageCity.ClosingBaraek], wander = true)

        beats.at(1008)
        despawn(dog)
        cast.remove(dog)
        dolly(scene, MontageCity.ClosingRise, rate = 3)
        lookAt(scene, MontageCity.ClosingRise, rate = 1)
        runTo(scene[MontageCity.ClosingRun2])

        beats.at(1015)
        frame(scene, MontageCity.Guards)

        beats.at(1019)
        cast += spawn(BelowIceMountainRefs.VarrockGuardF, scene[MontageCity.GuardF], wander = true)

        beats.at(1020)
        dolly(scene, MontageCity.GuardsCreep, rate = 2)
        lookAt(scene, MontageCity.GuardsCreep, rate = SceneSnapRate)

        beats.at(1021)
        cast +=
            spawn(
                BelowIceMountainRefs.VarrockGuard2,
                scene[MontageCity.GuardVariant],
                wander = true,
            )

        beats.at(1022)
        cast += spawn(BelowIceMountainRefs.VarrockGuard, scene[MontageCity.Guard], wander = true)

        beats.at(1025)
        dollyBoth(scene, MontageCity.GuardsRise, rate = 3)

        beats.at(1031)
        faceSquare(scene[MontageCity.ClosingStopFacing])

        beats.at(1034)
        anim(CheerSeq, delay = FinalCheerDelay)

        beats.at(1038)
        fadeOverlay(
            startColour = WhiteFade,
            startTransparency = 255,
            endColour = WhiteFade,
            endTransparency = 0,
            clientDuration = SceneFadeDuration,
        )
    }

    private suspend fun ProtectedAccess.atlasFinale(
        scene: SceneRegion,
        cast: MutableList<Npc>,
        beats: Beats,
    ) {
        beats.at(1040)
        cast.forEach(::despawn)
        cast.clear()
        telejump(scene[MontageBarbarian.PushupMark])
        val types =
            listOf(
                BelowIceMountainRefs.BarbarianMale,
                BelowIceMountainRefs.BarbarianMale2,
                BelowIceMountainRefs.BarbarianFemale,
                BelowIceMountainRefs.BarbarianFemale2,
            )
        types.forEachIndexed { index, type ->
            val watcher = spawn(type, scene[MontageBarbarian.Watchers[index]])
            watcher.facePathingEntitySquare(player)
            cast += watcher
        }

        beats.at(1041)
        val atlas = spawn(BelowIceMountainRefs.CutsceneAtlas, scene[MontageBarbarian.Atlas])
        atlas.facePathingEntitySquare(player)
        cast += atlas
        frame(scene, MontageBarbarian.Counting)
        fadeOverlay(
            startColour = WhiteFade,
            startTransparency = 0,
            endColour = WhiteFade,
            endTransparency = 255,
            clientDuration = SceneFadeDuration,
        )
        delay(SceneFadeTicks)
        closeFadeOverlayNow()

        bimCheckal = BelowIceMountainTracks.CheckalTrainingDone
        emoteFlexUnlocked = 1

        startDialogue {
            atlasLine(
                happy,
                "I'm very proud of you. You've shown grit, determination and an iron will. You " +
                    "are a true strongman at heart.",
            )
            chatPlayer(neutral, "That was exhausting... I've never worked so hard in my life!")
            atlasLine(
                happy,
                "And for that, I am proud. Now, show us what you're made of and flex those muscles!",
            )
        }

        faceSquare(scene[MontageBarbarian.FlexFacing])
        anim(BelowIceMountainRefs.FlexSeq)
        delay(FlexHoldTicks)

        startDialogue {
            mesbox("You have unlocked the <col=ef1020>flex</col> emote!")
            atlasLine(
                neutral,
                "My work is done. Return to Checkal and show him your new-found muscles.",
            )
        }
    }

    suspend fun rockPaperScissors(access: ProtectedAccess) {
        val region = regionRepo.add(BelowIceMountainRegions.RisingSun)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)
        val cast = mutableListOf<Npc>()

        try {
            access.lockedScene(
                vantage = scene[RockPaperScissorsScene.Vantage],
                faceAt = scene[RockPaperScissorsScene.Burntof],
                camera = scene.map(RockPaperScissorsScene.Camera.toCamera()),
                returnTo = RockPaperScissorsScene.Exit,
                underFade = {
                    cast +=
                        spawn(
                            BelowIceMountainRefs.CutsceneBurntof,
                            scene[RockPaperScissorsScene.Burntof],
                        )
                },
                teardown = { cast.forEach(::despawn) },
            ) {
                val burntof = cast.first()
                startDialogue {
                    burntofLine(
                        drunk,
                        "The rulesh are ash followsh. We both pick a shign to show and then shay " +
                            "'rock, paper, shcissors.' After schissors, we show our shigns.",
                    )
                    burntofLine(
                        drunk,
                        "Thish ish a besht of three game. Nowsh, pick what you are going to shhhow.",
                    )

                    val round1 = playRound(burntof, "Round 1: What sign will you show?")
                    val hisFirst = round1.beatenBy
                    mesbox("Burntof chose ${hisFirst.label}.")
                    burntofLine(drunk, "Looksh like you win the firsht round.")
                    chatPlayer(quiz, "But...? I put out ${round1.label}?")
                    burntofLine(
                        drunk,
                        "Yesh, and I put out *hic* ${round1.beats.label}... I think.",
                    )
                    chatPlayer(quiz, "Okay then... I guess the first round goes to me.")

                    val round2 = playRound(burntof, "Round 2: What sign will you show?")
                    val hisSecond = round2.beats
                    mesbox("Burntof chose ${hisSecond.label}.")
                    chatPlayer(happy, "I won! That's two rounds to me, so you have to help.")
                    burntofLine(
                        drunk,
                        "Hooold it right there *hic* bud, I thinksh I won that round...",
                    )
                    chatPlayer(quiz, "But ${round2.label} beats ${hisSecond.label}?")
                    burntofLine(
                        drunk,
                        "Yesh, that is correct, but you chose ${hisSecond.beats.label}. So I win.",
                    )
                    chatPlayer(angry, "Whatever, let's just play the last round.")

                    val round3 = playRound(burntof, "Round 3: What sign will you show?")
                    val hisThird = round3.beatenBy
                    mesbox("Burntof chose ${hisThird.label}.")
                    burntofLine(
                        drunk,
                        "Well, it looksh like I win, sho I don't have to help you out.",
                    )
                    mesbox("You quickly change your sign to ${hisThird.beatenBy.label}.")
                    chatPlayer(
                        shifty,
                        "Are you okay Burntof? I clearly beat you. Check my hand again.",
                    )

                    access.bimBurntof = BelowIceMountainTracks.Recruited
                    access.checkCrewRecruited()

                    burntofLine(
                        shocked,
                        "Merlinsh beard! You did. A deal'sh a deal, I'll help you out.",
                    )
                    burntofLine(
                        drunk,
                        " But I think I'll need one more drink before I hitsh the road.",
                    )
                    burntofLine(drunk, "Tina! Bring me another beer pleash.")

                    val tina =
                        spawn(BelowIceMountainRefs.CutsceneTina, scene[RockPaperScissorsScene.Tina])
                    cast += tina
                    tinaLine(happy, "Sure thing Burntof, I'll be right there!")

                    tina.walk(RockPaperScissorsScene.TinaServePath.map { scene[it] })
                    access.delay(TinaServeTicks)
                    tina.faceSquare(scene[RockPaperScissorsScene.Burntof])
                    burntof.faceSquare(scene[RockPaperScissorsScene.TinaServe])
                    tinaLine(happy, "Here you go.")
                    burntofLine(drunk, "Thanksh Tina.")
                    access.soundSynth(PourSound)
                }

                startDialogue { chatPlayer(happy, access.recruitSignOff(BurntofSignOff)) }
            }
        } finally {
            region?.let(regionRepo::unprotect)
        }
    }

    private suspend fun Dialogue.playRound(burntof: Npc, title: String): RpsSign {
        val sign =
            choice3(
                "Rock.",
                RpsSign.Rock,
                "Paper.",
                RpsSign.Paper,
                "Scissors.",
                RpsSign.Scissors,
                title = title,
            )
        access.anim(PlayerRpsSeq)
        access.say("Rock, paper, scissors!")
        burntof.anim(DwarfRpsSeq)
        burntof.say("Rock, paper, uhm... scissors!")
        access.delay(RpsThrowTicks)
        return sign
    }

    suspend fun enterRuins(access: ProtectedAccess) {
        releaseRuins(access.player)

        // Restore presentation before suspending for the next dialogue page.
        val entranceRegion = regionRepo.add(BelowIceMountainRegions.RuinsEntrance)
        entranceRegion?.let(regionRepo::protect)
        val interiorRegion = regionRepo.add(BelowIceMountainRegions.RuinsInterior)
        interiorRegion?.let(regionRepo::protect)
        // Nullable slots on purpose: allocation can fail, and [aftermath] reads the interior by
        // **index**. `listOfNotNull` would silently slide the interior into slot 0 when only the
        // entrance failed, and the aftermath would then play in the wrong copied block.
        holdRuins(access.player, listOf(entranceRegion, interiorRegion))

        val entrance = SceneRegion(entranceRegion)
        val interior = SceneRegion(interiorRegion)
        val cast = mutableListOf<Npc>()

        var completed = false
        try {
            access.lockedScene(
                vantage = entrance[ExcavationScene.Vantage],
                faceAt = entrance[ExcavationScene.Willow],
                camera = entrance.map(ExcavationScene.Opening.toCamera()),
                returnTo = null,
                underFade = {
                    markRuinsEntered(access.player)
                    cast +=
                        spawn(
                            BelowIceMountainRefs.CutsceneCheckal,
                            entrance[ExcavationScene.Checkal],
                            facing = Direction.East,
                        )
                    cast +=
                        spawn(
                            BelowIceMountainRefs.CutsceneBurntof,
                            entrance[ExcavationScene.Burntof],
                            facing = Direction.NorthEast,
                        )
                    cast +=
                        spawn(
                            BelowIceMountainRefs.CutsceneWillow,
                            entrance[ExcavationScene.Willow],
                            facing = Direction.West,
                        )
                    cast +=
                        spawn(
                            BelowIceMountainRefs.CutsceneMarley,
                            entrance[ExcavationScene.Marley],
                            facing = Direction.SouthEast,
                        )
                },
                teardown = { cast.forEach(::despawn) },
            ) {
                excavation(entrance, cast)
                corridor(interior, cast)
                betrayal(interior, cast)
                completed = true
            }

            guardianArrivalQuip(access)
        } finally {
            if (!completed) {
                releaseRuins(access.player)
            }
        }
    }

    private suspend fun guardianArrivalQuip(access: ProtectedAccess) {
        access.startDialogue {
            chatPlayer(
                neutral,
                "I can't believe they ran so quickly. I suppose I should take care of this " +
                    "thing.",
            )
        }
    }

    suspend fun resumeAtBetrayal(access: ProtectedAccess) {
        releaseRuins(access.player)

        val entranceRegion = regionRepo.add(BelowIceMountainRegions.RuinsEntrance)
        entranceRegion?.let(regionRepo::protect)
        val interiorRegion = regionRepo.add(BelowIceMountainRegions.RuinsInterior)
        interiorRegion?.let(regionRepo::protect)
        holdRuins(access.player, listOf(entranceRegion, interiorRegion))

        val interior = SceneRegion(interiorRegion)
        val cast = mutableListOf<Npc>()

        var completed = false
        try {
            access.lockedScene(
                vantage = interior[RuinsInteriorScene.Vantage],
                faceAt = interior[RuinsInteriorScene.Willow],
                camera = interior.map(RuinsInteriorScene.Opening.toCamera()),
                returnTo = null,
                teardown = { cast.forEach(::despawn) },
            ) {
                markRuinsEntered(access.player)
                betrayal(interior, cast)
                completed = true
            }

            guardianArrivalQuip(access)
        } finally {
            if (!completed) {
                releaseRuins(access.player)
            }
        }
    }

    suspend fun resumeFight(access: ProtectedAccess) {
        releaseRuins(access.player)

        val interiorRegion = regionRepo.add(BelowIceMountainRegions.RuinsInterior)
        interiorRegion?.let(regionRepo::protect)
        holdRuins(access.player, listOf(null, interiorRegion))

        val interior = SceneRegion(interiorRegion)

        with(access) {
            fadeOut()
            delay(SceneFadeTicks)
            telejump(interior[RuinsInteriorScene.Vantage])
            markRuinsEntered(player)
            armGuardianFight(player, interior)
            fadeIn()
        }
    }

    suspend fun resumeAftermath(access: ProtectedAccess) {
        releaseRuins(access.player)

        val interiorRegion = regionRepo.add(BelowIceMountainRegions.RuinsInterior)
        interiorRegion?.let(regionRepo::protect)
        holdRuins(access.player, listOf(null, interiorRegion))

        aftermath(access)
    }

    // Persist instance_exit_coord on entry so account loading can relocate before constructing the login rebuild.
    private fun holdRuins(player: Player, regions: List<Region?>) {
        player.uuid?.let { ruinsRegions[it] = regions }
        player.loginExitCoord = BelowIceMountainRefs.RuinsEntranceReturn
    }

    fun reclaimAbandonedRuins(player: Player) {
        val uuid = player.uuid ?: return
        if (ruinsRegions[uuid] == null) {
            return
        }
        if (uuid !in ruinsEntered) {
            return
        }
        Camera.camReset(player)
        releaseRuins(player)
        player.loginExitCoord = null
    }

    private suspend fun ProtectedAccess.excavation(entrance: SceneRegion, cast: MutableList<Npc>) {
        val checkal = cast[0]
        val burntof = cast[1]
        val willow = cast[2]
        val marley = cast[3]

        startDialogue {
            willowLine(
                neutral,
                "Right, that's everyone gathered. Thank you, ${player.displayName}, for all your " +
                    "help.",
            )
            chatPlayer(happy, "Oh it was no pro...")
            willowLine(
                neutral,
                "Okay everyone, we've wasted enough time. Let's crack this door open. Burntof, " +
                    "you're up!",
            )
            willowLine(neutral, "Try not to blow the entire mountain up this time...")
            burntofLine(drunk, "Yesh ma'am, let'sh get this rock out the way.")
        }

        burntof.walk(entrance[ExcavationScene.BurntofCharge])
        burntof.anim(DwarfBendSeq)
        delay(ChargeWalkTicks)
        burntof.anim(DwarfBendSeq)
        delay(ChargeBendTicks)

        fadeOut(FastFadeDuration)
        delay(SceneFadeTicks)

        checkal.telejump(entrance[ExcavationScene.CheckalCover])
        willow.telejump(entrance[ExcavationScene.WillowCover])
        marley.telejump(entrance[ExcavationScene.MarleyCover])
        val marker =
            spawn(BelowIceMountainRefs.InvisibleNpc, entrance[ExcavationScene.ChargeMarker])
        cast += marker
        frame(entrance, ExcavationScene.Cover)

        startDialogue {
            access.soundSynth(DynamitePrep1)
            burntofLine(drunk, "Jusht got to plashe thish here...")
            access.soundSynth(DynamitePrep2)
            burntofLine(drunk, "... little bit of dyn-*hic*-amite here...")
            access.soundSynth(DynamitePrep3)
            burntofLine(drunk, "... and there we go!")
            access.soundSynth(DynamiteFuse)
            burntofLine(drunk, "Now we light the fushe!")
        }

        fadeIn()
        soundSynth(DynamiteHiss)

        startDialogue {
            burntofLine(drunk, "What are you doin' standin' there?!")
            chatPlayer(quiz, "What do you mean?")
            burntofLine(drunk, "Everyone'sh ran for cover $ladOrLass. You'd best do the shame.")
            chatPlayer(quiz, "Wait, what?!")
            access.soundSynth(DynamiteTimer)
            marker.telejump(entrance[ExcavationScene.FuseShuffles[0]])
            burntofLine(drunk, "Clock'sh ticking $ladOrLass!")
        }

        dolly(entrance, ExcavationScene.CoverCreep, rate = 1)
        burntof.walk(entrance[ExcavationScene.BurntofRetreat])
        runTo(entrance[ExcavationScene.PlayerCover])
        marker.telejump(entrance[ExcavationScene.FuseShuffles[1]])
        delay(FuseShuffleLeadTicks)

        marker.telejump(entrance[ExcavationScene.FuseShuffles[2]])
        delay(BlastLeadTicks - FuseShuffleLeadTicks)

        addExplosion(entrance[ExcavationScene.Blast[1]])
        soundArea(
            worldRepo,
            entrance[ExcavationScene.BlastSound],
            BlastSound,
            delay = BlastSoundDelay,
            radius = BlastSoundRadius,
        )
        camShake(axis = 0, random = BlastShake, amplitude = 0, rate = 0)
        delay(2)

        addExplosion(entrance[ExcavationScene.Blast[2]])
        soundArea(
            worldRepo,
            entrance[ExcavationScene.BlastSoundP2],
            BlastSoundP2,
            delay = BlastSoundDelay,
            radius = BlastSoundRadius,
        )
        delay(2)

        ExcavationScene.Blast.forEach { addExplosion(entrance[it]) }
        delay(1)

        despawn(marker)
        cast.remove(marker)
        fadeOut()
        delay(SceneFadeTicks)

        telejump(entrance[ExcavationScene.PlayerRegroup])
        faceSquare(entrance[ExcavationScene.PlayerRegroupFacing])
        burntof.telejump(entrance[ExcavationScene.BurntofRegroup])
        burntof.faceSquare(entrance[ExcavationScene.PlayerRegroupFacing])
        willow.telejump(entrance[ExcavationScene.WillowRegroup])
        willow.faceSquare(entrance[ExcavationScene.CrewRegroupFacing])
        marley.telejump(entrance[ExcavationScene.MarleyRegroup])
        marley.faceSquare(entrance[ExcavationScene.CrewRegroupFacing])
        checkal.telejump(entrance[ExcavationScene.CheckalRegroup])
        checkal.faceSquare(entrance[ExcavationScene.BurntofRegroup])

        locRepo.findExact(entrance[ExcavationScene.Door], LocShape.CentrepieceStraight)?.let {
            locRepo.del(it, ScenePropTicks)
        }
        locRepo.add(
            entrance[ExcavationScene.Door],
            BelowIceMountainRefs.DoorCutscene,
            ScenePropTicks,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )
        val rubble =
            locRepo.add(
                entrance[ExcavationScene.Rubble],
                BelowIceMountainRefs.Rubble,
                ScenePropTicks,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
        camShakeResetAll()
        camReset()
        frame(entrance, ExcavationScene.Opening)
        fadeIn()

        startDialogue {
            marleyLine(angry, "Bloomin' 'eck, Burntof!")
            checkalLine(neutral, "That was a tad too explosive I think.")
            chatPlayer(shocked, "My ears are still ringing!")
            burntofLine(drunk, "Quit yer moanin', it did the job didnae it?")
            willowLine(neutral, "He's right, the rock's been broken up. Checkal, it's your turn.")
            checkalLine(neutral, "Of course $sirOrMadam, I will get to work.")
        }

        for (mark in ExcavationScene.Hauling) {
            checkal.walk(entrance[mark])
            delay(HaulWalkTicks)
            checkal.anim(HaulSeq)
            delay(HaulRestTicks)
        }

        fadeOut(FastFadeDuration)
        startDialogue { mesbox("Checkal sets to work clearing out the rubble...") }

        locRepo.del(rubble, ScenePropTicks)
        fadeIn()

        startDialogue {
            checkalLine(neutral, "The work is done, Willow. My apologies if it took too long.")
            willowLine(neutral, "Don't be sorry, Checkal. Gotta put those muscles to use somehow.")
            willowLine(neutral, "Alright Marley, in you go. Let us know if you find anything.")
            marleyLine(neutral, "Right, in I go. Wish me luck ${player.displayName}.")
            chatPlayer(neutral, "Try not to die...")
        }
    }

    private suspend fun ProtectedAccess.corridor(interior: SceneRegion, cast: MutableList<Npc>) {
        fadeOut()
        delay(SceneFadeTicks)

        cast.forEach(::despawn)
        cast.clear()
        telejump(interior[CorridorScene.Vantage])
        transmog(BelowIceMountainRefs.CutsceneMarley)
        rebuildAppearance()
        faceSquare(interior[CorridorScene.EntryFacing])
        val doorway =
            locRepo.add(
                interior[CorridorScene.Doorway],
                BelowIceMountainRefs.ExitCutscene,
                ScenePropTicks,
                LocAngle.East,
                LocShape.CentrepieceStraight,
            )
        val wires =
            CorridorScene.Tripwires.map {
                locRepo.add(
                    interior[it],
                    BelowIceMountainRefs.Tripwire,
                    ScenePropTicks,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
            }
        locRepo.findExact(interior[RuinsInteriorScene.Bag], LocShape.CentrepieceDiagonal)?.let {
            locRepo.del(it, ScenePropTicks)
        }
        frame(interior, CorridorScene.Opening)
        fadeIn()

        startDialogue {
            marleyLine(quiz, "Right, let's see what hidden surprises you have in store for me.")
        }
        walkTo(interior[CorridorScene.WalkMid])
        dollyBoth(interior, CorridorScene.Settle, rate = 1)

        startDialogue { marleyLine(quiz, "Hmm...") }

        anim(SearchSeq)
        soundSynth(DisarmTrap)
        startDialogue { marleyLine(neutral, "That would have been nasty.") }

        walkTo(interior[CorridorScene.WireTile])
        dollyBoth(interior, CorridorScene.Follow, rate = 1)
        startDialogue {
            marleyLine(laugh, "Is that it? I thought this place would be better protected.")
        }

        exactMove(
            start = interior[CorridorScene.WireTile],
            end = interior[CorridorScene.StepOver],
            delay1 = StepOverDelay1,
            delay2 = StepOverDelay2,
            dir = StepOverFaceNorth,
        )
        anim(StepOverSeq, delay = StepOverDelay1)
        delay(StepOverTicks)

        startDialogue {
            access.faceSquare(interior[CorridorScene.Tripwires[0]])
            access.anim(HaulSeq)
            access.soundSynth(DisarmFail1)
            marleyLine(quiz, "Now to disarm it...")
            access.anim(HaulSeq)
            access.soundSynth(DisarmFail2)
            marleyLine(quiz, "Maybe this will work?")
            access.anim(HaulSeq)
            access.soundSynth(DisarmFail3)
            access.soundSynth(DisarmTrap)
            marleyLine(quiz, "That got it!")
        }

        wires.forEach { locRepo.del(it, ScenePropTicks) }
        locRepo.del(doorway, ScenePropTicks)

        quest.setQuestStage(this, BelowIceMountainStage.TrapDisarmed)
        bimCheckal = BelowIceMountainTracks.InsideRuins
        bimBurntof = BelowIceMountainTracks.InsideRuins
        bimMarley = BelowIceMountainTracks.InsideRuins

        startDialogue { marleyLine(neutral, "Alright guys! It's safe to come in! I think...") }
    }

    private suspend fun ProtectedAccess.betrayal(interior: SceneRegion, cast: MutableList<Npc>) {
        fadeOut()
        delay(SceneFadeTicks)

        resetTransmog()
        rebuildAppearance()
        telejump(interior[RuinsInteriorScene.Vantage])

        RuinsInteriorScene.CutscenePillars.forEach { (coord, angle) ->
            locRepo.add(
                interior[coord],
                BelowIceMountainRefs.BossRockCutscene,
                ScenePropTicks,
                angle,
                LocShape.CentrepieceStraight,
            )
        }
        locRepo
            .findExact(interior[RuinsInteriorScene.DoorRight], LocShape.CentrepieceStraight)
            ?.let { locRepo.del(it, ScenePropTicks) }
        locRepo.add(
            interior[RuinsInteriorScene.DoorClosed],
            BelowIceMountainRefs.RuinsDoorClosed,
            ScenePropTicks,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        locRepo.add(
            interior[RuinsInteriorScene.Bag],
            BelowIceMountainRefs.WillowsBag,
            ScenePropTicks,
            LocAngle.South,
            LocShape.CentrepieceDiagonal,
        )

        cast +=
            spawn(
                BelowIceMountainRefs.CutsceneCheckal,
                interior[RuinsInteriorScene.Checkal],
                facing = Direction.West,
            )
        cast +=
            spawn(
                BelowIceMountainRefs.CutsceneMarley,
                interior[RuinsInteriorScene.Marley],
                facing = Direction.SouthEast,
            )
        cast +=
            spawn(
                BelowIceMountainRefs.CutsceneWillow,
                interior[RuinsInteriorScene.Willow],
                facing = Direction.South,
            )
        val burntof =
            spawn(
                BelowIceMountainRefs.CutsceneBurntof,
                interior[RuinsInteriorScene.Burntof],
                facing = Direction.SouthEast,
            )
        cast += burntof
        val dormant = spawn(DormantGuardianNpc, interior[RuinsInteriorScene.GuardianMark])
        cast += dormant

        frame(interior, RuinsInteriorScene.Opening)
        fadeIn()

        unlockMusic(BelowIceMountainRefs.CamdozaalMusic)
        dolly(interior, RuinsInteriorScene.Rise, rate = 2)
        lookAt(interior, RuinsInteriorScene.Rise, rate = 1)

        startDialogue {
            chatPlayer(shocked, "Wow, this place is amazing. I can't believe this was hidden away.")
            chatPlayer(quiz, "I wonder how long it's been left untouched for.")
            willowLine(angry, "You're impressed? There's nothing here but dust and fallen rocks.")
            burntof.say("Yaaa*hic*aawn")
            burntof.walk(interior[RuinsInteriorScene.BurntofWanders[0]])
            chatPlayer(
                happy,
                "You don't find it exciting? As an archaeologist, I thought you would be thrilled " +
                    "at this discovery?",
            )
            access.dolly(interior, RuinsInteriorScene.DriftEast, rate = 2)
            willowLine(
                angry,
                "Thrilled? What's there to be excited about? I don't see any gold, diamonds, rare " +
                    "gems or piles of lost treasure!",
            )
            burntof.say("Hmm...")
            burntof.walk(interior[RuinsInteriorScene.BurntofWanders[1]])
            chatPlayer(
                quiz,
                "It's not about the money though, right? We've potentially discovered the long " +
                    "lost ruins of an ancient civilisation!",
            )
            access.dolly(interior, RuinsInteriorScene.DriftBack, rate = 2)
            access.lookAt(interior, RuinsInteriorScene.DriftBack, rate = 1)
            willowLine(
                angry,
                "Of course it's about the money. It's always been about money, you fool.",
            )
            chatPlayer(neutral, "I don't understand...")
            willowLine(
                neutral,
                "We're not a team of archaeologist, you idiot. We're grave robbers looking for " +
                    "their next score.",
            )
            burntof.say("Theshe markings look familiar...")
            burntof.walk(interior[RuinsInteriorScene.BurntofWanders[2]])
            chatPlayer(
                neutral,
                "So this entire time, you've just been using me to help break into these ruins in " +
                    "hopes of finding someone else's treasures.",
            )
            willowLine(
                neutral,
                "Yep, and you were surprisingly easy to convince. I'll admit, you have been a " +
                    "tremendous help, ${player.displayName}.",
            )
            willowLine(
                neutral,
                "But your usefulness has come to an end I'm afraid. I can't simply let you leave here.",
            )
            chatPlayer(worried, "Why not?")
            willowLine(
                neutral,
                "I can't risk you running to the authorities and telling them about our little " +
                    "gang. We're wanted in quite a few large cities across Gielinor.",
            )
            burntof.say("I've sheen thish before...")
            burntof.walk(interior[RuinsInteriorScene.BurntofWanders[3]])
            willowLine(
                neutral,
                "I'm afraid this is the end of the road for you, ${player.displayName}. This ruin " +
                    "is about to become your tomb.",
            )

            access.soundSynth(LeverSound)
            burntofLine(drunk, "*hic* Woops...")
            willowLine(shocked, "What was that noise?")
            burntofLine(drunk, "Uh... it wasn't me!")
        }

        dollyBoth(interior, RuinsInteriorScene.Quake, rate = 5)
        soundSynth(DungeonRumble)
        RuinsInteriorScene.QuakeFalls.forEach { (coord, clientDelay) ->
            spotanimMap(worldRepo, RockfallSpotanim, interior[coord], delay = clientDelay)
        }
        camShake(axis = 0, random = QuakeShakeStrong, amplitude = 0, rate = 0)
        delay(QuakeBeat)
        camShake(axis = 0, random = QuakeShakeMid, amplitude = 0, rate = 0)
        delay(QuakeBeat)
        camShake(axis = 0, random = QuakeShakeSoft, amplitude = 0, rate = 0)
        delay(QuakeBeat)

        camShakeResetAll()
        camReset()
        frame(interior, RuinsInteriorScene.Quake)

        startDialogue {
            willowLine(worried, "Whats happening?!")
            willowLine(shocked, "It feels like the quaking has finally stopped...")
        }

        despawn(dormant)
        cast.remove(dormant)
        val waking =
            spawn(BelowIceMountainRefs.GuardianCutscene, interior[RuinsInteriorScene.GuardianMark])
        cast += waking
        waking.anim(BelowIceMountainRefs.GuardianWakeSeq)
        soundArea(
            worldRepo,
            interior[RuinsInteriorScene.GuardianMark],
            GuardianAwaken,
            radius = ArenaSoundRadius,
        )
        dolly(interior, RuinsInteriorScene.GuardianReveal, rate = 1)
        lookAt(interior, RuinsInteriorScene.GuardianReveal, rate = 1)
        camShake(axis = 0, random = QuakeShakeSoft, amplitude = 0, rate = 0)
        delay(GuardianWakeTicks)

        camShakeResetAll()
        camReset()
        frame(interior, RuinsInteriorScene.GuardianSettle)
        quest.setQuestStage(this, BelowIceMountainStage.GuardianAwake)
        bimCheckal = BelowIceMountainTracks.Fled
        bimBurntof = BelowIceMountainTracks.Fled
        bimMarley = BelowIceMountainTracks.Fled

        startDialogue {
            willowLine(worried, "Holy Zamorak! What is that thing?! Checkal, take it down!")
            checkalLine(worried, "I'm a muscle man, not a fighter.")
            marleyLine(worried, "I think we should get out of here, Willow!")
            willowLine(worried, "You're right, Marley. Burntof, we're leaving!")
        }

        fadeOut(FastFadeDuration)
        delay(SceneFadeTicks)
        cast.forEach(::despawn)
        cast.clear()

        armGuardianFight(player, interior)
    }

    private fun armGuardianFight(player: Player, interior: SceneRegion) {
        val guardian =
            spawn(
                BelowIceMountainRefs.Guardian,
                interior[RuinsInteriorScene.GuardianMark],
                FightLifespanTicks,
            )
        guardian.facePathingEntitySquare(player)
        player.uuid?.let { guardians[it] = ActiveGuardian(player, guardian) }
        guardian.apPlayer2(player, aiPlayerInteractions)
        RuinsInteriorScene.Pillars.forEach { (coord, angle) ->
            locRepo.add(
                interior[coord],
                BelowIceMountainRefs.BossRock,
                FightLifespanTicks,
                angle,
                LocShape.CentrepieceStraight,
            )
        }
    }

    suspend fun aftermath(access: ProtectedAccess) {
        val regions = access.player.uuid?.let(ruinsRegions::get)
        val interior = SceneRegion(regions?.getOrNull(InteriorSlot))
        val cast = mutableListOf<Npc>()

        try {
            access.lockedScene(
                vantage = interior[RuinsInteriorScene.BagVantage],
                faceAt = interior[RuinsInteriorScene.AftermathFacing],
                camera = interior.map(RuinsInteriorScene.Aftermath.toCamera()),
                returnTo = BelowIceMountainRefs.CamdozaalArrival,
                underFade = {
                    markRuinsEntered(access.player)
                    access.hideEntityOps()
                    locRepo.add(
                        interior[RuinsInteriorScene.Exit],
                        BelowIceMountainRefs.ExitCutscene,
                        ScenePropTicks,
                        LocAngle.East,
                        LocShape.CentrepieceStraight,
                    )
                },
                teardown = { cast.forEach(::despawn) },
            ) {
                startDialogue {
                    chatPlayer(
                        neutral,
                        "That monster showed up at the perfect time. I would have been toast if " +
                            "Willow and her gang had their chance to attack.",
                    )
                    chatPlayer(
                        neutral,
                        "Speaking of which, maybe they left something behind I can salvage some " +
                            "kind of reward from.",
                    )
                    access.anim(ThinkSeq)
                    chatPlayer(
                        quiz,
                        "Looks like Willow left a bag behind. Maybe there's something in it I could take.",
                    )
                }

                walkTo(interior[RuinsInteriorScene.BagWalk])
                dollyBoth(interior, RuinsInteriorScene.BagCreep, rate = 2)
                delay(BagWalkTicks)
                faceSquare(interior[RuinsInteriorScene.Bag])

                startDialogue {
                    access.anim(RummageSeq)
                    access.say("Hmm...")
                    mesbox("You rummage through Willow's bag...")
                    access.anim(HaulSeq)
                    mesbox("You find a pile of coins!")
                    chatPlayer(happy, "Those will do nicely!")
                }

                locRepo.add(
                    interior[RuinsInteriorScene.DoorRight],
                    BelowIceMountainRefs.RuinsDoorOpenRight,
                    ScenePropTicks,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
                locRepo
                    .findExact(
                        interior[RuinsInteriorScene.DoorClosed],
                        LocShape.CentrepieceStraight,
                    )
                    ?.let { locRepo.del(it, ScenePropTicks) }
                locRepo.add(
                    interior[RuinsInteriorScene.DoorLeft],
                    BelowIceMountainRefs.RuinsDoorOpenLeft,
                    ScenePropTicks,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
                soundSynth(GateOpen)
                soundSynth(DungeonDoorOpen)
                camShake(axis = 0, random = DoorShake, amplitude = 0, rate = 0)

                startDialogue {
                    chatPlayer(
                        worried,
                        "What was that noise? Please don't be another rock monster...",
                    )
                }

                camShakeResetAll()
                camReset()
                frame(interior, RuinsInteriorScene.Aftermath)

                val ramarno =
                    spawn(
                        BelowIceMountainRefs.RamarnoCutscene,
                        interior[RuinsInteriorScene.GuardianMark],
                    )
                cast += ramarno

                startDialogue { chatPlayer(shocked, "What the!") }
                dolly(interior, RuinsInteriorScene.DoorReveal, rate = 2)
                lookAt(interior, RuinsInteriorScene.DoorReveal, rate = 5)
                startDialogue {
                    chatNpcSpecific(
                        "Ramarno",
                        BelowIceMountainRefs.RamarnoCutscene,
                        shocked,
                        "Bless my beard...",
                    )
                }
            }
        } finally {
            releaseRuins(access.player)
            access.player.loginExitCoord = null
        }

        access.delay(CompletionLeadTicks)

        access.bimClaimedReward = 1
        quest.completeQuest(access)

        quest.awaitRewardScroll(access)

        val ramarno = findEntranceRamarno()
        if (ramarno != null) {
            access.faceEntitySquare(ramarno)
            access.npcPlayerFaceClose(ramarno)
        } else {
            access.faceSquare(BelowIceMountainRefs.RamarnoEntranceSpawn)
        }
        access.startDialogue { with(dialogueDelegate) { ramarnoFirstMeeting() } }
    }

    private fun findEntranceRamarno(): Npc? {
        val parentId = BelowIceMountainRefs.RamarnoEntrance.asRSCM(RSCMType.NPC)
        return npcRepo
            .findAll(ZoneKey.from(BelowIceMountainRefs.RamarnoEntranceSpawn), zoneRadius = 1)
            .firstOrNull { it.id == parentId }
    }

    internal lateinit var dialogueDelegate: BelowIceMountainDialogue

    // Release both protected regions on interrupted fights; otherwise neither can be reclaimed.
    fun releaseRuins(player: Player) {
        val uuid = player.uuid ?: return
        ruinsEntered.remove(uuid)
        ruinsRegions.remove(uuid)?.forEach { it?.let(regionRepo::unprotect) }
        guardians.remove(uuid)?.let {
            if (it.npc.isSlotAssigned) {
                npcRepo.del(it.npc, Int.MAX_VALUE)
            }
        }
    }

    private fun ProtectedAccess.frame(r: SceneRegion, frame: SceneFrame) {
        val mapped = r.map(frame.toCamera())
        camMoveToV3(
            mapped.eye,
            height = mapped.eyeHeight,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
        camLookAtV3(
            mapped.lookAt,
            height = mapped.lookAtHeight,
            rate = SceneSnapRate,
            rate2 = SceneSnapRate,
        )
    }

    private fun ProtectedAccess.dolly(
        r: SceneRegion,
        frame: SceneFrame,
        rate: Int = SceneDriftRate,
    ) {
        val mapped = r.map(frame.toCamera())
        camMoveToV3(mapped.eye, height = mapped.eyeHeight, rate = rate, rate2 = rate)
    }

    private fun ProtectedAccess.lookAt(r: SceneRegion, frame: SceneFrame, rate: Int) {
        val mapped = r.map(frame.toCamera())
        camLookAtV3(mapped.lookAt, height = mapped.lookAtHeight, rate = rate, rate2 = rate)
    }

    private fun ProtectedAccess.dollyBoth(r: SceneRegion, frame: SceneFrame, rate: Int) {
        dolly(r, frame, rate)
        lookAt(r, frame, rate)
    }

    private fun ProtectedAccess.fadeOut(duration: Int = SceneFadeDuration) {
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = duration,
        )
    }

    private suspend fun ProtectedAccess.fadeIn(duration: Int = SceneFadeDuration) {
        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = duration,
        )
        delay(SceneFadeTicks)
        closeFadeOverlayNow()
    }

    private fun addExplosion(coords: CoordGrid) {
        locRepo.add(
            coords,
            BelowIceMountainRefs.Explosion,
            ExplosionTicks,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
    }

    private fun ProtectedAccess.unlockMusic(displayName: String) {
        val row = musicRow(displayName) ?: return
        musicPlayer.unlockAndPlay(player, row)
    }

    private fun spawn(
        type: String,
        coords: CoordGrid,
        lifespan: Int = SceneLifespanTicks,
        wander: Boolean = false,
        facing: Direction? = null,
    ): Npc {
        val npc = Npc(type, coords)
        facing?.let { npc.respawnDir = it }
        npcRepo.add(npc, lifespan)
        if (!wander && type != BelowIceMountainRefs.Guardian) {
            npc.noneMode()
        }
        return npc
    }

    private fun despawn(npc: Npc) {
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun Npc.telejump(coords: CoordGrid) {
        PathingEntityCommon.telejump(this, collision, coords)
    }

    private fun ProtectedAccess.walkTo(dest: CoordGrid) {
        player.routeRequest = RouteRequestCoord(dest)
        player.tempMoveSpeed = MoveSpeed.Walk
    }

    private fun ProtectedAccess.runTo(dest: CoordGrid) {
        player.routeRequest = RouteRequestCoord(dest)
        player.tempMoveSpeed = MoveSpeed.Run
    }

    private fun tomatoThrow(from: CoordGrid, to: CoordGrid) {
        worldRepo.projAnim(
            ProjAnim(
                spotanim = TomatoTravelSpotanim.asRSCM(RSCMType.SPOTANIM),
                startHeight = TomatoStartHeight,
                endHeight = TomatoEndHeight,
                startTime = TomatoStartTime,
                endTime = TomatoFlightDelay,
                angle = TomatoAngle,
                progress = TomatoProgress,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = from,
                endCoord = to,
            )
        )
    }

    private suspend fun Dialogue.willowLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Willow", BelowIceMountainRefs.CutsceneWillow, mesanim, text)

    private suspend fun Dialogue.checkalLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Checkal", BelowIceMountainRefs.CutsceneCheckal, mesanim, text)

    private suspend fun Dialogue.marleyLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Marley", BelowIceMountainRefs.CutsceneMarley, mesanim, text)

    private suspend fun Dialogue.burntofLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Burntof", BelowIceMountainRefs.CutsceneBurntof, mesanim, text)

    private suspend fun Dialogue.tinaLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Tina", BelowIceMountainRefs.CutsceneTina, mesanim, text)

    private suspend fun Dialogue.atlasLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Atlas", BelowIceMountainRefs.CutsceneAtlas, mesanim, text)

    // These scene captions must not suspend; the beat clock replaces them automatically.
    private fun ProtectedAccess.atlasSays(text: String) {
        startDialogueNp { atlasLineNp(it, text) }
    }

    private fun ProtectedAccess.startDialogueNp(block: (Dialogue) -> Unit) {
        block(Dialogue(this, npc = null, faceFar = false))
    }

    private fun atlasLineNp(dialogue: Dialogue, text: String) {
        dialogue.chatNpcSpecificNp(
            "Atlas",
            BelowIceMountainRefs.CutsceneAtlas,
            dialogue.neutral,
            text,
        )
    }

    private companion object {
        private const val InteriorSlot = 1

        private const val MontageStart = 838

        private val MontageExit = CoordGrid(3076, 3437, 0)

        private const val HoboExamine = "Looks down on his luck."

        private const val CitizenExamine = "One of Gielinor's many citizens."

        private const val BurntofSignOff =
            "That was interesting, but that's Burntof recruited. On to the next person."

        private const val FastFadeDuration = 15

        private const val WhiteFade = 16_777_215

        private const val RunOnSpotSeq = "seq.emote_run_on_spot"
        private const val StarJumpSeq = "seq.emote_starjump_5"
        private const val CheerSeq = "seq.emote_cheer"
        private const val YesSeq = "seq.emote_yes"
        private const val ThrowSeq = "seq.human_throw"
        private const val ThrowSpotanim = "spotanim.rotten_tomato_launch"
        private const val ThrowSpotanimHeight = 96

        private val RunningChaseTicks = 941..952

        private const val TomatoTravelSpotanim = "spotanim.rotten_tomato_travel"
        private const val TomatoSplatSpotanim = "spotanim.rotten_splash"
        private const val TomatoStartHeight = 163
        private const val TomatoEndHeight = 0
        private const val TomatoStartTime = 28
        private const val TomatoAngle = 15
        private const val TomatoProgress = 32

        private const val TomatoFlightDelay = 81

        private const val TomatoThrowSound = "synth.THROWN"

        private const val TomatoSplatSound = "synth.GREASE_HIT"
        private const val TomatoSoundRadius = 5
        private const val PunchSeq = "seq.human_unarmedpunch"
        private const val BlockSeq = "seq.human_unarmedblock"
        private const val DwarfBendSeq = "seq.dwarf_bendover"
        private const val HaulSeq = "seq.human_pickupfloor"
        private const val SearchSeq = "seq.human_pickpocket"
        private const val StepOverSeq = "seq.regicide_stepover"
        private const val ThinkSeq = "seq.emote_think"
        private const val RummageSeq = "seq.generic_look_and_search"
        private const val PlayerRpsSeq = "seq.rps_human"
        private const val DwarfRpsSeq = "seq.dwarf_rps"
        private const val RockfallSpotanim = "spotanim.rockfall"

        private const val PunchSound = "synth.SFX_2H_SLASH"

        private const val PunchCount = 10
        private const val PunchGap = 2

        private val HurtPunches = setOf(1, 3, 5, 7)

        private const val CountGap = 2
        private const val EmoteEveryNth = 3

        private const val FinalCheerDelay = 2

        private const val FlexHoldTicks = 6

        private const val RpsThrowTicks = 5

        private const val PourSound = "synth.LIQUID"

        private const val TinaServeTicks = 8

        private const val ChargeWalkTicks = 3

        private const val ChargeBendTicks = 3

        private const val DynamitePrep1 = "synth.BIM_DYNAMITE_PREPARATION_01"
        private const val DynamitePrep2 = "synth.BIM_DYNAMITE_PREPARATION_02"
        private const val DynamitePrep3 = "synth.BIM_DYNAMITE_PREPARATION_03"
        private const val DynamiteFuse = "synth.BIM_DYNAMITE_LIGHTING_FUSE_02"
        private const val DynamiteHiss = "synth.BIM_DYNAMITE_HISS"
        private const val DynamiteTimer = "synth.BIM_DYNAMITE_TIMER"

        private const val BlastLeadTicks = 13

        private const val FuseShuffleLeadTicks = 9

        private const val BlastSound = "synth.BIM_WALL_DYNAMITE_EXPLOSIONS"

        private const val BlastSoundP2 = "synth.BIM_WALL_DYNAMITE_EXPLOSIONS_P2"
        private const val BlastSoundDelay = 5
        private const val BlastSoundRadius = 15

        private const val BlastShake = 75

        private const val ExplosionTicks = 4

        private const val HaulWalkTicks = 4

        private const val HaulRestTicks = 2

        private const val StepOverTicks = 3

        private const val StepOverDelay1 = 20

        private const val StepOverDelay2 = 50
        private const val StepOverFaceNorth = 1024

        private const val BagWalkTicks = 5

        private const val CompletionLeadTicks = 2

        private const val DisarmTrap = "synth.BIM_DISARM_TRAP"
        private const val DisarmFail1 = "synth.BIM_DISARM_TRAP_FAILURE_01"
        private const val DisarmFail2 = "synth.BIM_DISARM_TRAP_FAILURE_02"
        private const val DisarmFail3 = "synth.BIM_DISARM_TRAP_FAILURE_03"

        private const val LeverSound = "synth.LEVER"

        private const val DungeonRumble = "synth.BIM_DUNGEON_RUMBLE"

        private const val QuakeShakeStrong = 50

        private const val QuakeShakeMid = 20
        private const val QuakeShakeSoft = 5

        private const val QuakeBeat = 4

        private const val GuardianAwaken = "synth.BIM_ANCIENT_GUARDIAN_AWAKEN"

        private const val DormantGuardianNpc = "npc.bim_golem_boss_inactive_cutscene"

        private const val ArenaSoundRadius = 10

        private const val GuardianWakeTicks = 2

        private const val GateOpen = "synth.GATE_OPEN"

        private const val DungeonDoorOpen = "synth.BIM_DUNGEON_DOOR_OPEN"

        private const val DoorShake = 15

        private const val SceneLifespanTicks = 600

        private const val FightLifespanTicks = 6000

        private const val ScenePropTicks = 6000
    }
}

// Measure elapsed map ticks so delays inside helpers do not accumulate drift between beats.
private class Beats(private val access: ProtectedAccess, startTick: Int) {
    private val base = access.mapClock - startTick

    suspend fun at(target: Int) {
        val wait = base + target - access.mapClock
        if (wait > 0) {
            access.delay(wait)
        }
    }
}

private enum class RpsSign(val label: String) {
    Rock("rock"),
    Paper("paper"),
    Scissors("scissors");

    val beats: RpsSign
        get() =
            when (this) {
                Rock -> Scissors
                Paper -> Rock
                Scissors -> Paper
            }

    val beatenBy: RpsSign
        get() =
            when (this) {
                Rock -> Paper
                Paper -> Scissors
                Scissors -> Rock
            }
}

internal fun musicRow(displayName: String): MusicRow? =
    MusicRow.all().firstOrNull { it.displayname == displayName }

private fun Region.contains(coords: CoordGrid): Boolean =
    coords.x in southWest.x..northEast.x && coords.z in southWest.z..northEast.z
