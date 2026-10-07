package org.rsmod.content.quest.area.falador

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

class TheKnightsSword
@Inject
constructor(
    private val locRepo: LocRepository,
    private val shieldOfArrav: org.rsmod.content.quest.area.varrock.ShieldOfArrav,
) :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards { xp("stat.smithing", TheKnightsSwordRefs.SmithingXpReward) },
        completionJingle = TheKnightsSwordRefs.CompletionJingle,
    ) {

    private val dialogue = TheKnightsSwordDialogue(quest, shieldOfArrav)
    private val scenery = TheKnightsSwordScenery(quest, locRepo)

    override fun ScriptContext.init() {
        check(quest.maxSteps == TheKnightsSwordStage.Complete) {
            "The Knight's Sword end state is ${quest.maxSteps} in the cache dbrow, but " +
                "TheKnightsSwordStage.Complete is ${TheKnightsSwordStage.Complete}."
        }

        with(dialogue) { register() }
        with(scenery) { register() }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Squire<col=000080> in the courtyard of the " +
            "<col=800000>White Knights' Castle<col=000080> in <col=800000>Falador<col=000080>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage >= TheKnightsSwordStage.Started) {
                strike(
                    "I told the Squire I would help him to replace the sword he had lost. It " +
                        "could only be made by an Imcando Dwarf."
                )
            }

            if (stage == TheKnightsSwordStage.Started) {
                line(
                    "I should ask <red>Reldo</red>, the palace librarian in <red>Varrock</red>, " +
                        "about the <red>Imcando Dwarves</red>."
                )
            }

            if (stage == TheKnightsSwordStage.ReldoAdvised) {
                line(
                    "I should take a <red>Redberry pie</red> to the cliffs on the " +
                        "<red>Asgarnian southern peninsula</red>."
                )
            }

            if (stage >= TheKnightsSwordStage.Stage3PieEaten) {
                strike(
                    "I found an Imcando Dwarf named Thurgo thanks to information provided by " +
                        "Reldo. He wasn't very talkative until I gave him a Redberry pie, which " +
                        "he gobbled up."
                )
            }

            if (stage == TheKnightsSwordStage.Stage3PieEaten) {
                line("I should ask <red>Thurgo</red> to make a replacement <red>sword</red>.")
            }

            if (stage == TheKnightsSwordStage.ThurgoWantsPicture) {
                line(
                    "I should find a <red>picture</red> of the sword to show to " +
                        "<red>Thurgo</red>. The <red>Squire</red> may know of one."
                )
            }

            if (stage == TheKnightsSwordStage.Stage5PortraitNamed) {
                line(
                    "I should take the <red>portrait</red> from the <red>cupboard</red> in " +
                        "<red>Sir Vyvin's room</red> without letting him catch me."
                )
            }

            if (stage >= TheKnightsSwordStage.ThurgoWantsMaterials) {
                strike(
                    "Thurgo needed a picture of the sword before he could start work on a " +
                        "replacement. I took him a portrait of it."
                )
            }

            if (stage == TheKnightsSwordStage.ThurgoWantsMaterials) {
                if (player.hasFinishedSword()) {
                    strike("Thurgo has now smithed me a replica of Sir Vyvin's sword.")
                    line("")
                    line("I should return it to the <red>Squire</red> for my <red>reward</red>.")
                } else {
                    line(
                        "I should take <red>two iron bars</red> and some <red>blurite ore</red> " +
                            "to <red>Thurgo</red>."
                    )
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I told the Squire I would help him to replace the sword he had lost. It could " +
                    "only be made by an Imcando Dwarf."
            )
            line(
                "I found an Imcando Dwarf named Thurgo thanks to information provided by Reldo. " +
                    "He wasn't very talkative until I gave him a Redberry pie, which he gobbled up."
            )
            line(
                "Thurgo needed a picture of the sword before he could start work on a " +
                    "replacement. I took him a portrait of it."
            )
            line("Thurgo has now smithed me a replica of Sir Vyvin's sword.")
            line("I returned the sword to the Squire and collected my reward.")
        }

    private fun ProtectedAccess.hasFinishedSword(): Boolean =
        invTotal(inv, TheKnightsSwordRefs.FaladianSword) > 0

    private companion object {
        private const val QuestKey = "quest_knightssword"

        private const val QuestVarp = "varp.squire"
    }
}
