package org.rsmod.content.quest.area.blackknights

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.misthalin.MisthalinDoorLeaf
import org.rsmod.content.quest.area.misthalin.misthalinCrossDoorway
import org.rsmod.content.quest.area.misthalin.misthalinCrossingOpenTicks
import org.rsmod.content.quest.area.misthalin.misthalinCrossingRoute
import org.rsmod.content.quest.area.misthalin.misthalinCrossingTiles
import org.rsmod.content.quest.area.misthalin.openMisthalinLeaf
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class BlackKnightsFortress
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) :
    QuestScript(
        BlackKnightsFortressState.QuestKey,
        BlackKnightsFortressState.QuestVarp,
        rewards {
            item(
                BlackKnightsFortressState.Coins,
                BlackKnightsFortressState.RewardCoins,
                label = "2,500 Coins",
            )
        },
        ItemRewardDisplay(BlackKnightsFortressState.RewardScrollObj),
        completionJingle = BlackKnightsFortressState.CompletionJingle,
    ) {

    private val cutscene = BlackKnightsFortressCutscene(quest, regionRepo, npcRepo, worldRepo)
    private val dialogue = BlackKnightsFortressDialogue(quest)

    private val meetingWarned = quest.boolean("meeting_warned", "varp.black_knights_meeting_warned", "varp.black_knights_meeting_warned_migrated")

    private val grillPoseAlternate = quest.temporaryBoolean("grill_pose_alternate", "varp.black_knights_grill_pose_alternate")

    override fun ScriptContext.init() {
        check(quest.maxSteps == BlackKnightsFortressState.Complete) {
            "Black Knights' Fortress end state is ${quest.maxSteps} in the cache dbrow, but " +
                "BlackKnightsFortressState.Complete is ${BlackKnightsFortressState.Complete}."
        }

        assertBlackKnightsFortressSceneCoords()

        with(dialogue) { register() }
        with(cutscene) { register() }

        onOpHeld1(BlackKnightsFortressState.Dossier) { readDossier() }

        onOpLoc1(BlackKnightsFortressState.EntranceDoor) { entranceDoor(it.loc) }
        onOpLoc1(BlackKnightsFortressState.MeetingDoor) { meetingDoor(it.loc) }
        onOpLoc1(BlackKnightsFortressState.InnerDoor) {
            crossFortressDoor(
                it.loc,
                closed = BlackKnightsFortressState.InnerDoor,
                openForm = BlackKnightsFortressState.OpenDoor,
                sound = BlackKnightsFortressState.DoorSound,
            )
        }

        onOpLoc1(BlackKnightsFortressState.SecretWall) { pushSecretWall(it.loc) }

        onOpLoc1(BlackKnightsFortressState.Grill) { listenAtGrill(it.loc) }

        onPlayerSoftTimer(BlackKnightsFortressState.GrillPoseTimer) { holdGrillPose(player) }

        onOpLoc1(BlackKnightsFortressState.Boxes) { searchBoxes() }

        onOpLocU(BlackKnightsFortressState.CauldronHole) { useOnHole(it.loc, it.objType) }

        for (door in BlackKnightsFortressState.FrontDoors) {
            onOpLoc1(door) { mes(FrontDoorRefusal) }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Sir Amik Varze</col> on the top floor of " +
            "<col=800000>Falador Castle</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage <= BlackKnightsFortressState.Started) {
                line(StartedLine)
                line("")
                line(DisguiseHint)
                return@questJournal
            }

            strike(StartedStruck)

            if (stage == BlackKnightsFortressState.OverheardMeeting) {
                line(OverheardLine)
                return@questJournal
            }

            strike(OverheardStruck)
            line(SabotagedLine)
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Sir Amik Varze asked me to investigate the Black Knights' Fortress. I sneaked " +
                    "inside disguised as a Guard."
            )
            line(
                "I eavesdropped on a Witch and the Black Knight Captain and discovered that their " +
                    "invincibility potion could be destroyed with a normal cabbage."
            )
            line(
                "I dropped one through the hole above their cauldron, and Sir Amik paid me for " +
                    "putting an end to the Black Knights' scheming."
            )
        }

    private suspend fun ProtectedAccess.entranceDoor(door: BoundLocInfo) {
        if (!isDisguised()) {
            startDialogue {
                with(dialogue) { uniformRefusal(BlackKnightsFortressState.EntranceGuard) }
            }
            return
        }
        crossFortressDoor(
            door,
            closed = BlackKnightsFortressState.EntranceDoor,
            openForm = BlackKnightsFortressState.OpenDoor,
            sound = BlackKnightsFortressState.DoorSound,
        )
    }

    private suspend fun ProtectedAccess.meetingDoor(door: BoundLocInfo) {
        if (!meetingWarned.get(player)) {
            var insisted = false
            startDialogue { insisted = with(dialogue) { meetingWarning() } }
            if (!insisted) {
                return
            }
            meetingWarned.set(player, true)
        }
        crossFortressDoor(
            door,
            closed = BlackKnightsFortressState.MeetingDoor,
            openForm = BlackKnightsFortressState.OpenDoor,
            sound = BlackKnightsFortressState.DoorSound,
        )
    }

    private suspend fun ProtectedAccess.pushSecretWall(wall: BoundLocInfo) {
        mes(SecretPassage)
        crossFortressDoor(
            wall,
            closed = BlackKnightsFortressState.SecretWall,
            openForm = BlackKnightsFortressState.OpenSecretWall,
            sound = BlackKnightsFortressState.WallSound,
        )
    }

    private suspend fun ProtectedAccess.crossFortressDoor(
        door: BoundLocInfo,
        closed: String,
        openForm: String,
        sound: String,
    ) {
        val route = misthalinCrossingRoute(door)
        val openTicks = misthalinCrossingOpenTicks(misthalinCrossingTiles(route))

        openMisthalinLeaf(
            locRepo,
            MisthalinDoorLeaf(
                tile = door.coords,
                closed = closed,
                openLeaf = openForm,
                openAngle = door.turnAngle(rotations = 1),
                closedAngle = door.angle,
            ),
            openTicks,
        )
        soundSynth(sound)
        misthalinCrossDoorway(route)
    }

    private suspend fun ProtectedAccess.listenAtGrill(grill: BoundLocInfo) {
        if (quest.getQuestStage(player) >= BlackKnightsFortressState.Sabotaged) {
            mes("You can hear the witch wailing about her ruined potion.")
            return
        }

        faceSquare(grillFacing(grill))
        delay(BlackKnightsFortressState.ListenFaceToBendTicks)
        anim(BlackKnightsFortressState.BendToListenAnim)
        delay(BlackKnightsFortressState.ListenBendToIdleTicks)

        anim(BlackKnightsFortressState.ListenIdleAnim)

        grillPoseAlternate.set(player, true)
        softTimer(
            BlackKnightsFortressState.GrillPoseTimer,
            BlackKnightsFortressState.GrillPoseTicks,
        )

        var stoodUp = false
        try {
            startDialogue { with(dialogue) { overheardMeeting() } }
            clearSoftTimer(BlackKnightsFortressState.GrillPoseTimer)
            resetAnim()
            anim(BlackKnightsFortressState.StandAfterListenAnim)
            stoodUp = true
        } finally {
            if (!stoodUp) {
                clearSoftTimer(BlackKnightsFortressState.GrillPoseTimer)
                resetAnim()
            }
        }
    }

    // Alternate poses because reply mode 2 does not restart the current sequence's frame pointer.
    private fun holdGrillPose(player: Player) {
        val alternate = grillPoseAlternate.get(player)
        grillPoseAlternate.set(player, !alternate)

        val pose =
            if (alternate) {
                BlackKnightsFortressState.ListenIdleHoldAnim
            } else {
                BlackKnightsFortressState.ListenIdleAnim
            }
        player.anim(pose)
    }

    private fun ProtectedAccess.searchBoxes() {
        mes(BoxesEmpty)
    }

    private fun grillFacing(grill: BoundLocInfo): CoordGrid {
        val (dx, dz) =
            when (grill.angle.id) {
                0 -> -1 to 0
                1 -> 0 to 1
                2 -> 1 to 0
                else -> 0 to -1
            }
        return CoordGrid(grill.coords.x + dx, grill.coords.z + dz, grill.coords.level)
    }

    private suspend fun ProtectedAccess.useOnHole(hole: BoundLocInfo, objType: ItemServerType) {
        val stage = quest.getQuestStage(player)

        if (objType.isType(BlackKnightsFortressState.MagicCabbage)) {
            startDialogue { with(dialogue) { wrongCabbage() } }
            return
        }

        val sabotagePossible =
            stage == BlackKnightsFortressState.OverheardMeeting &&
                objType.isType(BlackKnightsFortressState.Cabbage)

        if (!sabotagePossible) {
            startDialogue { chatPlayer(confused, "Why exactly would I want to do that?") }
            return
        }

        throwCabbage(hole)
        cutscene.sabotage(this)
    }

    private suspend fun ProtectedAccess.throwCabbage(hole: BoundLocInfo) {
        if (invDel(inv, BlackKnightsFortressState.Cabbage, 1).failure) {
            return
        }

        anim(BlackKnightsFortressState.ThrowCabbageAnim)
        spotanim(BlackKnightsFortressState.ThrowCabbageSpotanim, height = ThrowSpotanimHeight)

        worldRepo.projAnim(
            ProjAnim(
                spotanim = BlackKnightsFortressState.CabbageProjectile.asRSCM(RSCMType.SPOTANIM),
                startHeight = ThrowStartHeight,
                endHeight = ThrowEndHeight,
                startTime = ThrowStartTime,
                endTime = ThrowEndTime,
                angle = ThrowAngle,
                progress = ThrowProgress,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = coords,
                endCoord = hole.coords,
            )
        )
        soundSynth(BlackKnightsFortressState.ThrowSound)
    }

    private suspend fun ProtectedAccess.readDossier() {
        try {
            anim(BlackKnightsFortressState.ReadDossierAnim)

            for (line in DossierLines) {
                mesboxNp(line)
                delay(BlackKnightsFortressState.DossierStepTicks)
            }

            selfDestructDossier()
        } finally {
            ifCloseSub("interface.messagebox")
        }
    }

    private fun ProtectedAccess.selfDestructDossier() {
        if (invDel(inv, BlackKnightsFortressState.Dossier, 1).failure) {
            return
        }

        spotanim(BlackKnightsFortressState.ExplosionSpotanim, delay = 0, height = 0)
        anim(BlackKnightsFortressState.ExplodeDossierAnim)
        soundSynth(
            BlackKnightsFortressState.ExplosionSound,
            loops = 1,
            delay = BlackKnightsFortressState.ExplosionSoundDelay,
        )
    }

    private companion object {
        private const val FrontDoorRefusal = "You can't open this door."

        private const val SecretPassage = "You push against the wall. You find a secret passage."

        private const val BoxesEmpty = "There is nothing interesting in these boxes."

        private val DossierLines =
            listOf(
                "Infiltrate fortress...<br>sabotage secret weapon...<br>self destruct in 3...",
                "Infiltrate fortress...<br>sabotage secret weapon...<br>self destruct in 3...",
                "Infiltrate fortress...<br>sabotage secret weapon...<br>self destruct in 2...",
                "Infiltrate fortress...<br>sabotage secret weapon...<br>self destruct in 1...",
            )

        private const val StartedLine =
            "Sir Amik Varze asked me to investigate the Black Knights' Fortress and find out what " +
                "their secret weapon is."

        private const val DisguiseHint =
            "I should sneak inside disguised as a Guard. An <red>iron chainbody</red> and a " +
                "<red>bronze med helm</red> ought to do it."

        private const val StartedStruck =
            "Sir Amik Varze asked me to investigate the Black Knights' Fortress. I sneaked inside " +
                "disguised as a Guard."

        private const val OverheardLine =
            "I eavesdropped on a Witch and the Black Knight Captain and discovered that their " +
                "<red>invincibility potion</red> can be destroyed with a normal <red>cabbage</red>."

        private const val OverheardStruck =
            "I eavesdropped on a Witch and the Black Knight Captain and discovered that their " +
                "invincibility potion could be destroyed with a normal cabbage."

        private const val SabotagedLine =
            "Now that I have sabotaged the witch's potion, I can claim my <red>reward</red> from " +
                "<red>Sir Amik Varze</red> in <red>Falador Castle</red>."

        private const val ThrowSpotanimHeight = 96
        private const val ThrowStartHeight = 130
        private const val ThrowEndHeight = 0
        private const val ThrowStartTime = 96
        private const val ThrowEndTime = 196
        private const val ThrowAngle = 55
        private const val ThrowProgress = 60
    }
}
