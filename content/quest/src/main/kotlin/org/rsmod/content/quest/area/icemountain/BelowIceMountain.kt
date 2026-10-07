package org.rsmod.content.quest.area.icemountain

import jakarta.inject.Inject
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerEmote
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class BelowIceMountain
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val musicPlayer: MusicPlayer,
    private val random: GameRandom,
    private val playerHitModifier: PlayerHitModifier,
    private val npcHitModifier: NpcHitModifier,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val protectedAccess: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
    private val shieldOfArrav: org.rsmod.content.quest.area.varrock.ShieldOfArrav,
) :
    QuestScript(
        BelowIceMountainRefs.QuestKey,
        BelowIceMountainRefs.QuestVarp,
        rewards {
            item(BelowIceMountainRefs.Coins, CoinReward, label = "2,000 Coins")
            extra("Access to the Ruins of")
            extra("Camdozaal")
        },
        completedQuestItemDisplay = ItemRewardDisplay(BelowIceMountainRefs.SteakSandwich),
        questVarbit = BelowIceMountainRefs.StageVarbit,
        completionJingle = BelowIceMountainRefs.CompletionJingle,
    ) {

    private val cutscene =
        BelowIceMountainCutscene(
            quest,
            regionRepo,
            npcRepo,
            locRepo,
            worldRepo,
            musicPlayer,
            aiPlayerInteractions,
            collision,
        )

    private val dialogue = BelowIceMountainDialogue(quest, cutscene, objRepo, shieldOfArrav)

    private val travel = BelowIceMountainTravel(quest, cutscene)

    private val fight =
        BelowIceMountainFight(
            quest,
            cutscene,
            locRepo,
            npcRepo,
            worldRepo,
            random,
            playerHitModifier,
            npcHitModifier,
            protectedAccess,
        )

    override fun ScriptContext.init() {
        check(quest.maxSteps == BelowIceMountainStage.Complete) {
            "Below Ice Mountain end state is ${quest.maxSteps} in the cache dbrow, but " +
                "BelowIceMountainStage.Complete is ${BelowIceMountainStage.Complete}."
        }

        check(quest.requiredQuestPoints == RequiredQuestPoints) {
            "Below Ice Mountain requires ${quest.requiredQuestPoints} quest points in the cache " +
                "dbrow, but the wiki and the capture both say $RequiredQuestPoints."
        }

        assertBelowIceMountainSceneCoords()

        checkNotNull(musicRow(BelowIceMountainRefs.WorkoutMusic)) {
            "No music row named '${BelowIceMountainRefs.WorkoutMusic}'."
        }
        checkNotNull(musicRow(BelowIceMountainRefs.CamdozaalMusic)) {
            "No music row named '${BelowIceMountainRefs.CamdozaalMusic}'."
        }

        cutscene.dialogueDelegate = dialogue

        with(dialogue) { register() }
        with(fight) { register() }
        with(travel) { register() }

        registerFlexHook()

        // Login handlers run after scene construction; account-load transforms must handle instance recovery.
        onPlayerLogin { cutscene.releaseRuins(player) }

        onPlayerCoordsChanged { cutscene.reclaimAbandonedRuins(player) }
    }

    // Queue the emote hand-off; launching access inside the emote's existing protected block fails.
    private fun ScriptContext.registerFlexHook() {
        onPlayerEmote(BelowIceMountainRefs.FlexSeq) { player.weakQueue(FlexQueue, FlexReplyTicks) }
        onPlayerEmote(BelowIceMountainRefs.FlexLoopSeq) {
            player.weakQueue(FlexQueue, FlexReplyTicks)
        }

        onPlayerQueue(FlexQueue) {
            if (bimCheckal != BelowIceMountainTracks.CheckalAskedToFlex) {
                return@onPlayerQueue
            }
            startDialogue { with(dialogue) { checkalAnswersFlex() } }
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Willow<col=000080> on the path south of " +
            "<col=800000>Ice Mountain<col=000080>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage >= BelowIceMountainStage.AllRecruited) {
                strike(MetWillow)
                line(AllRecruitedBlock)
            } else {
                line(MetWillow)
            }

            burntofBlock(player)
            checkalBlock(player)
            marleyBlock(player)
        }

    private fun QuestJournalBuilder.burntofBlock(player: ProtectedAccess) {
        if (quest.getQuestStage(player.player) >= BelowIceMountainStage.AllRecruited) {
            return
        }
        line("")
        if (player.bimBurntof >= BelowIceMountainTracks.Recruited) {
            strike(BurntofRecruited)
        } else if (player.bimBurntof >= BelowIceMountainTracks.BurntofWantsDrink) {
            line(BurntofWantsBeer)
        } else {
            line(BurntofOutstanding)
        }
    }

    private fun QuestJournalBuilder.checkalBlock(player: ProtectedAccess) {
        if (quest.getQuestStage(player.player) >= BelowIceMountainStage.AllRecruited) {
            return
        }
        line("")
        if (player.bimCheckal >= BelowIceMountainTracks.Recruited) {
            strike(CheckalRecruited)
        } else {
            line(CheckalOutstanding)
        }
    }

    private fun QuestJournalBuilder.marleyBlock(player: ProtectedAccess) {
        if (quest.getQuestStage(player.player) >= BelowIceMountainStage.AllRecruited) {
            return
        }
        line("")
        if (player.bimMarley >= BelowIceMountainTracks.Recruited) {
            strike(MarleyRecruited)
        } else if (player.bimMarley >= BelowIceMountainTracks.MarleyWantsSandwich) {
            line(MarleyWantsSandwich)
        } else {
            line(MarleyOutstanding)
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(MetWillowPast)
            line(AllRecruitedPast)
            line(EntryGained)
            line(Betrayal)
            line(Aftermath)
        }

    private companion object {
        private const val CoinReward = 2_000

        private const val RequiredQuestPoints = 16

        private const val FlexQueue = "queue.bim_checkal_flex_reply"

        private const val FlexReplyTicks = 4

        private const val MetWillow =
            "I spoke to an archaeologist called Willow near Ice Mountain. She asked me to help " +
                "her find a way into some Ancient Ruins beneath the mountain. To do this, she " +
                "wants me to gather her old crew together."

        private const val BurntofOutstanding =
            "I need to recruit Burntof. I can find him in the Rising Sun Inn in Falador."

        private const val BurntofWantsBeer =
            "I need to recruit Burntof from the Rising Sun Inn in Falador. However, he wants me " +
                "to bring him a Beer before he speaks to me."

        private const val BurntofRecruited = "I have recruited Burntof."

        private const val CheckalOutstanding =
            "I need to recruit Checkal. I can find him in Barbarian Village."

        private const val CheckalRecruited = "I have recruited Checkal."

        private const val MarleyOutstanding =
            "I need to recruit Marley. Willow doesn't know where he is but suggested I ask " +
                "Charlie, who can be found near the Blue Moon Inn in Varrock."

        private const val MarleyWantsSandwich =
            "I need to recruit Marley from the Edgeville Ruins. However, he wants me to bring him " +
                "a Steak Sandwich before he agrees to help Willow. To make a Steak Sandwich, I'll " +
                "need a Knife, some Bread and some Cooked Meat."

        private const val MarleyRecruited = "I have recruited Marley."

        private const val AllRecruitedBlock =
            "I spoke with Burntof, Checkal and Marley. It took some convincing, but they've all " +
                "agreed to work with Willow again. I should meet up with them all at the entrance " +
                "to the Ancient Ruins on the west side of Ice Mountain."

        private const val MetWillowPast =
            "I spoke to an archaeologist called Willow near Ice Mountain. She asked me to help " +
                "her find a way into some Ancient Ruins beneath the mountain. To do this, she " +
                "wanted me to gather her old crew together."

        private const val AllRecruitedPast =
            "I spoke with Burntof, Checkal and Marley. It took some convincing, but they all " +
                "agreed to work with Willow again."

        private const val EntryGained =
            "With the help of her crew, Willow managed to gain entry into the Ancient Ruins."

        private const val Betrayal =
            "Inside the Ancient Ruins, Willow revealed herself to be a graverobber rather than an " +
                "archaeologist. She tried to kill me, but was interrupted by an Ancient Guardian " +
                "activating. Willow and her crew fled, leaving me with the Ancient Guardian."

        private const val Aftermath =
            "I managed to defeat the Ancient Guardian. With Willow gone, I claimed my reward from " +
                "a bag she left behind. Now that no one else is trying to kill me, I can explore " +
                "the Ancient Ruins at my leisure."
    }
}
