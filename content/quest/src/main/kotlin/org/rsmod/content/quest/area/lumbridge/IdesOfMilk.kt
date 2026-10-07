package org.rsmod.content.quest.area.lumbridge

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class IdesOfMilk
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val alignment: TextAlignment,
    private val instantHitProcessor: InstantPlayerHitProcessor,
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
    private val objRepo: ObjRepository,
) :
    QuestScript(
        IdesOfMilkRefs.QuestKey,
        "varp.cowquest_main",
        rewards {
            extra("Access to Brutus' Field")
            extra("Gillie's Cowbell Supply")
        },
        completedQuestItemDisplay = ItemRewardDisplay(IdesOfMilkRefs.Sample2),
        questVarbit = IdesOfMilkRefs.VarbitStage,
        completionJingle = IdesOfMilkRefs.CompletionJingle,
    ) {

    internal val dialogue =
        IdesOfMilkDialogue(
            quest,
            npcRepo,
            random,
            alignment,
            instantHitProcessor,
            teleportValidator,
            areaChecker,
            objRepo,
        )

    override fun ScriptContext.init() {
        check(quest.maxSteps == IdesOfMilkStage.Complete) {
            "The Ides of Milk end state is ${quest.maxSteps} in the cache, but " +
                "IdesOfMilkStage.Complete is ${IdesOfMilkStage.Complete}."
        }

        assertIdesOfMilkArenaCoords()

        with(dialogue) { register() }
    }

    suspend fun dukeIdesBranch(dialogue: Dialogue): Boolean =
        with(this.dialogue) { dialogue.dukeBranch() }

    override fun subTitle(): String =
        "talking to <col=800000>Cassius</col> by the <col=800000>Lumbridge pond</col>, south of " +
            "the windmill."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            strike(
                "I have met an enigmatic man by the name of Cassius lurking around Farmer Fred's " +
                    "farm in Lumbridge."
            )
            strike(
                "Cassius has offered me the chance to share the profits of his upcoming milk " +
                    "business, in exchange for a bit of help setting it up."
            )

            if (stage < IdesOfMilkStage.BookSnatched) {
                line(
                    "I need to go and speak to <red>Gillie and Seth Groats</red> to research " +
                        "their <red>animal husbandry</red> techniques. Apparently <red>Cassius" +
                        "</red> already has an inside man in the Groats operation, <red>Brutus" +
                        "</red>."
                )
                return@questJournal
            }
            strike(
                "Having spoken to Seth and Gillie Groats, I found that they raise their animals " +
                    "following a set of principles that Seth keeps in a book. I took that book " +
                    "to Cassius as it may have been useful to his budding milk business."
            )

            if (stage < IdesOfMilkStage.SampleOneDrunk) {
                line(
                    "<red>Cassius</red> has handed me a <red>milk sample</red> to try. I should " +
                        "<red>drink</red> it."
                )
                return@questJournal
            }
            strike(
                "Having returned Seth's book to Cassius, he then offered me a sample of the milk " +
                    "we will be selling. It wasn't the most pleasant thing to drink, but I " +
                    "managed to finish it."
            )

            if (stage < IdesOfMilkStage.DukeConsulted) {
                line(
                    "Cassius needs a <red>business permit</red>. I should take the <red>milk " +
                        "sample</red> he gave me to the <red>Duke of Lumbridge</red> and " +
                        "convince him to let us begin trading."
                )
                return@questJournal
            }
            strike(
                "The next step in getting our business set up was to get a business permit. To " +
                    "that end, Cassius sent me to the Duke of Lumbridge with another milk " +
                    "sample, aiming to convince him to let us begin trading."
            )

            if (stage < IdesOfMilkStage.GillieJudging) {
                line(
                    "The <red>Duke</red> couldn't grant us a permit without the approval of the " +
                        "Groats family. I should speak to <red>Gillie Groats</red> about it."
                )
                return@questJournal
            }
            strike(
                "I spoke to the Duke of Lumbridge about getting a business permit for Cassius' " +
                    "future milk empire, but unfortunately the Duke couldn't grant us one " +
                    "without us speaking to the Groats family first."
            )

            if (stage < IdesOfMilkStage.SampleTwoDrunk) {
                line(
                    "<red>Gillie</red> has asked me to <red>drink the milk sample</red> so that " +
                        "she can get a sense of the quality of the milk."
                )
                return@questJournal
            }
            strike(
                "I spoke to Gillie at the Duke of Lumbridge's request, and she asked me to drink " +
                    "the milk sample so that she could get a sense of the quality of the milk. " +
                    "It wasn't pleasant, but seemed to bring her around to my cause."
            )

            if (stage < IdesOfMilkStage.BullSlain) {
                line(
                    "<red>Gillie</red> does need another favour. There is a rather " +
                        "<red>aggressive bull</red> that she has penned in her field that she " +
                        "has asked me to <red>deal with</red>."
                )
                return@questJournal
            }
            strike(
                "Gillie sent me to deal with the aggressive bull that she had fenced in her " +
                    "field. It was a tough fight but I brought the beast down. Unfortunately it " +
                    "turned out the the bull was Brutus, who I believed to be Cassius business " +
                    "partner."
            )

            if (stage < IdesOfMilkStage.ToldGillie) {
                line(
                    "I should let <red>Gillie</red> know that the bull has been <red>dealt " +
                        "with</red>."
                )
                return@questJournal
            }
            line(
                "<red>Gillie</red> has offered to grant me the <red>business permit</red>. " +
                    "Unfortunately it's no longer needed. I should return to <red>Cassius</red> " +
                    "to apologise for killing <red>Brutus</red>."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I have met an enigmatic man by the name of Cassius lurking around Farmer Fred's " +
                    "farm in Lumbridge."
            )
            line(
                "Cassius has offered me the chance to share the profits of his upcoming milk " +
                    "business, in exchange for a bit of help setting it up."
            )
            line(
                "Having spoken to Seth and Gillie Groats, I found that they raise their animals " +
                    "following a set of principles that Seth keeps in a book. I took that book " +
                    "to Cassius as it may have been useful to his budding milk business."
            )
            line(
                "Having returned Seth's book to Cassius, he then offered me a sample of the milk " +
                    "we will be selling. It wasn't the most pleasant thing to drink, but I " +
                    "managed to finish it."
            )
            line(
                "The next step in getting our business set up was to get a business permit. To " +
                    "that end, Cassius sent me to the Duke of Lumbridge with another milk " +
                    "sample, aiming to convince him to let us begin trading."
            )
            line(
                "I spoke to the Duke of Lumbridge about getting a business permit for Cassius' " +
                    "future milk empire, but unfortunately the Duke couldn't grant us one " +
                    "without us speaking to the Groats family first."
            )
            line(
                "I spoke to Gillie at the Duke of Lumbridge's request, and she asked me to drink " +
                    "the milk sample so that she could get a sense of the quality of the milk. " +
                    "It wasn't pleasant, but seemed to bring her around to my cause."
            )
            line(
                "Gillie sent me to deal with the aggressive bull that she had fenced in her " +
                    "field. It was a tough fight but I brought the beast down. Unfortunately it " +
                    "turned out the the bull was Brutus, who I believed to be Cassius business " +
                    "partner."
            )
            line(
                "I have apologised to Cassius for killing Brutus, but unfortunately this may be " +
                    "a deathblow to his potential milk empire. It's unlikely he's going to want " +
                    "to see my face again."
            )
        }
}
