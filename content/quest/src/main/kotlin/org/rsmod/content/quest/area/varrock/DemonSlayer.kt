package org.rsmod.content.quest.area.varrock

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class DemonSlayer
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val musicPlayer: MusicPlayer,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val alignment: TextAlignment,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) :
    QuestScript(
        DemonSlayerRefs.QuestKey,
        "varp.demonstart",
        rewards {
            extra("Silverlight")
        },
        completedQuestItemDisplay = null,
        questVarbit = DemonSlayerRefs.VarbitStage,
        completionJingle = DemonSlayerRefs.CompletionJingle,
    ) {

    private val cutscenes = DemonSlayerCutscenes(quest, regionRepo, musicPlayer, random)

    private val scenery = DemonSlayerScenery(quest, locRepo, objRepo, worldRepo, collision)

    private val dialogue =
        DemonSlayerDialogue(quest, locRepo, worldRepo, objRepo, random, cutscenes, scenery)

    private val fight =
        DemonSlayerFight(
            quest,
            regionRepo,
            npcRepo,
            locRepo,
            worldRepo,
            protectedAccess,
            musicPlayer,
            aiPlayerInteractions,
            alignment,
            collision,
        )

    override fun ScriptContext.init() {
        check(quest.maxSteps == DemonSlayerStage.Complete) {
            "Demon Slayer end state is ${quest.maxSteps} in the cache, but " +
                "DemonSlayerStage.Complete is ${DemonSlayerStage.Complete}."
        }

        assertDemonSlayerSceneCoords()

        with(dialogue) { register() }
        with(scenery) { register() }
        with(fight) { register() }
        with(cutscenes) { register() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Gypsy Aris</col> in her tent in <col=800000>Varrock Square</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            strike(
                "I spoke to Aris in Varrock Square who saw my future. Unfortunately it involved " +
                    "killing a demon who nearly destroyed Varrock over 150 years ago."
            )

            if (player.silverlightCaseOpened) {
                strike("I reclaimed the magical sword Silverlight from Sir Prysin.")
                line("")
                line(
                    "Now I should go to the stone circle south of the city and destroy " +
                        "<red>Delrith</red> using <red>Silverlight</red>!"
                )
                return@questJournal
            }

            line("")
            line("To defeat the <red>demon</red> I need the magical sword <red>Silverlight</red>.")

            if (stage < DemonSlayerStage.KeysNamed) {
                line("I should ask <red>Sir Prysin</red> in <red>Varrock Palace</red> where it is.")
                return@questJournal
            }

            line(
                "<red>Sir Prysin</red> needs <red>3 keys</red> before he can give me " +
                    "<red>Silverlight</red>."
            )
            line("")

            if (player.carryingObj(DemonSlayerRefs.Key3)) {
                strike("I have the 1st Key with me.")
            } else if (
                player.vars[DemonSlayerRefs.VarbitDrainKey] == DemonSlayerRefs.DrainKeyInSewer
            ) {
                line(
                    "The <red>1st Key</red> was dropped down the <red>palace kitchen drains.</red> " +
                        "I have washed the key down the drain into the sewer. I should go down " +
                        "there to fetch it. The <red>sewer entrance</red> should be somewhere just " +
                        "<red>outside</red> the <red>palace courtyard</red> to <red>the east.</red>"
                )
            } else {
                line(
                    "The <red>1st Key</red> was dropped down the <red>palace kitchen drains.</red> " +
                        "Maybe some water can dislodge it."
                )
            }

            if (player.carryingObj(DemonSlayerRefs.Key2)) {
                strike("I have the 2nd Key with me.")
            } else {
                line("The <red>2nd Key</red> is with Captain Rovin in Varrock Palace.")
            }

            if (player.carryingObj(DemonSlayerRefs.Key1)) {
                strike("I have the 3rd Key with me.")
            } else {
                line(
                    "The <red>3rd Key</red> is with Wizard Traiborn at the Wizards' Tower, south " +
                        "of Draynor Village."
                )
                val owed = dialogue.bonesOwed(player.player)
                if (owed > 0) {
                    line("<red>Traiborn</red> needs <red>$owed</red> more <red>bones</red>.")
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I spoke to Aris in Varrock Square who saw my future. Unfortunately it involved " +
                    "killing a demon who nearly destroyed Varrock over 150 years ago."
            )
            line(
                "Sir Prysin kept Silverlight locked in a case that needed three keys: one from " +
                    "Captain Rovin, one from Wizard Traiborn in return for twenty-five sets of " +
                    "bones, and one he had dropped down the palace kitchen drain, which I washed " +
                    "into the sewers and fished out of the mud."
            )
            line(
                "With Silverlight in hand I walked into the stone circle south of Varrock, watched " +
                    "Denath summon Delrith, and banished the demon back into the dark dimension " +
                    "from which he came."
            )
        }
}
