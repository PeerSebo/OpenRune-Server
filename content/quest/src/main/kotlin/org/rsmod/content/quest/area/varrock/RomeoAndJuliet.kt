package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestProgressState
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class RomeoAndJuliet
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val playerRepo: PlayerRepository,
    private val protectedAccess: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) :
    QuestScript(
        "quest_romeoandjuliet",
        "varp.rjquest",
        rewards {},
        ItemRewardDisplay("obj.cadava"),
    ) {

    private val cutscenes = RomeoAndJulietCutscenes(quest, regionRepo, npcRepo, locRepo, collision)
    private val dialogue = RomeoAndJulietDialogue(quest, cutscenes)
    private val potion = RomeoAndJulietPotion(quest)
    private val approach = RomeoApproach(quest, random, dialogue, protectedAccess)

    override fun ScriptContext.init() {
        check(quest.maxSteps == RomeoAndJulietStage.Complete) {
            "Romeo & Juliet end state is ${quest.maxSteps} in the cache, but " +
                "RomeoAndJulietStage.Complete is ${RomeoAndJulietStage.Complete}."
        }

        assertRomeoAndJulietSceneCoords()

        checkNotNull(ServerCacheManager.getMesAnim("mesanim.sleep".asRSCM(RSCMType.MESANIM))) {
            "mesanim.sleep has no definition in the SERVER cache. It is declared in " +
                ".data/gamevals/mesanim.rscm and .data/raw-cache/server/mesanim.toml but the " +
                "cache has not been rebuilt since - run `./gradlew :or-cache:freshCache`."
        }

        with(dialogue) { register() }
        with(potion) { register() }
        with(approach) { register(playerRepo) }
        with(cutscenes) { register() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Romeo</col> in <col=800000>Varrock Square</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage >= RomeoAndJulietStage.FindJuliet) {
                strike(
                    "I have agreed to find Juliet for Romeo and tell her how he feels. For some " +
                        "reason he can't just do this himself."
                )
            }
            if (stage == RomeoAndJulietStage.FindJuliet) {
                line(
                    "I should go and speak to <red>Juliet</red>. I can find her <red>west</red> " +
                        "of <red>Varrock</red>."
                )
            }

            if (stage >= RomeoAndJulietStage.DeliverMessage) {
                strike(
                    "I found Juliet on the Western edge of Varrock, and told her about Romeo. " +
                        "She gave me a message to take back."
                )
            }
            if (stage == RomeoAndJulietStage.DeliverMessage) {
                line("I should take this <red>message</red> back to <red>Romeo</red>.")
            }

            if (stage >= RomeoAndJulietStage.FindFatherLawrence) {
                strike(
                    "I delivered the message to Romeo, and he was sad to hear that Juliet's " +
                        "father opposed their marriage. However, he said that Father Lawrence " +
                        "might be able to overcome this."
                )
            }
            if (stage == RomeoAndJulietStage.FindFatherLawrence) {
                line(
                    "I should find <red>Father Lawrence</red> and see how we can help. I can " +
                        "find him in his <red>church</red> in the <red>north-east</red> of " +
                        "<red>Varrock</red>."
                )
            }

            if (stage >= RomeoAndJulietStage.FindApothecary) {
                strike(
                    "I found Father Lawrence and he suggested the use of a potion to fool " +
                        "Juliet's father that she is dead so that Romeo and Juliet can be " +
                        "together in peace."
                )
            }
            if (stage == RomeoAndJulietStage.FindApothecary) {
                line(
                    "I should find the <red>Apothecary</red> in <red>south-west Varrock</red>, " +
                        "west of the sword shop."
                )
            }

            if (stage >= RomeoAndJulietStage.CadavaPotion) {
                strike(
                    "I went to the Apothecary regarding making this cadava potion, and he told " +
                        "me to bring him some cadava berries."
                )
            }
            if (stage == RomeoAndJulietStage.CadavaPotion) {
                line("I should take this <red>cadava potion</red> to <red>Juliet</red>.")
            }

            if (stage >= RomeoAndJulietStage.TellRomeo) {
                strike(
                    "Juliet drank the potion and her father had her body carried down to the " +
                        "crypt."
                )
            }
            if (
                stage == RomeoAndJulietStage.TellRomeo &&
                    quest.questState(player.player) != QuestProgressState.FINISHED
            ) {
                line(
                    "I should tell <red>Romeo</red> that Juliet is waiting for him in the " +
                        "<red>crypt</red>."
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I agreed to find Juliet for Romeo and tell her how he feels. For some reason he " +
                    "couldn't just do this himself."
            )
            line(
                "I found Juliet on the Western edge of Varrock and carried her message back to " +
                    "Romeo, who was sad to hear that her father opposed their marriage."
            )
            line(
                "Father Lawrence suggested a cadava potion to make Juliet appear dead, and the " +
                    "Apothecary brewed one for me once I brought him some cadava berries."
            )
            line(
                "Juliet drank the potion and was carried down to the crypt. I took Romeo to " +
                    "collect her - where he promptly forgot all about her and fell for her cousin " +
                    "Phillipa instead."
            )
        }
}

internal var ProtectedAccess.julietVisibility by intVarBit("varbit.romjul_juliet_visible")

internal fun ProtectedAccess.carryingObj(obj: String): Boolean = invTotal(inv, obj) > 0
