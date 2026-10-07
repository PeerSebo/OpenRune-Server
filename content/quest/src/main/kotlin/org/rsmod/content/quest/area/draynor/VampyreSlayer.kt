package org.rsmod.content.quest.area.draynor

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

class VampyreSlayer :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards {
            xp("stat.attack", AttackXpReward)
        },
        ItemRewardDisplay(Stake),
        completionJingle = CompletionJingle,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == CompleteStage) {
            "Vampyre Slayer end state is ${quest.maxSteps} in the cache dbrow, but " +
                "CompleteStage is $CompleteStage."
        }

        onOpNpc1("npc.morgan") { talkToMorgan(it.npc) }
        onOpNpc1("npc.dr_harlow") { talkToHarlow(it.npc) }

        onOpNpcU("npc.dr_harlow", Beer) { useBeerOnHarlow(it.npc) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Morgan</col> in <col=800000>Draynor Village</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage > StartedStage) {
                strike(
                    "I spoke to Morgan at his home in Draynor Village. He told me about Count " +
                        "Draynor, a vampyre living in Draynor Manor north of the village. At his " +
                        "request, I agreed to try and kill Count Draynor. Morgan suggested I " +
                        "begin by seeking out Dr Harlow, a retired vampyre hunter, at the Blue " +
                        "Moon Inn in Varrock."
                )
            } else {
                line(
                    "I spoke to <red>Morgan</red> at his home in <red>Draynor Village</red>. He " +
                        "told me about <red>Count Draynor</red>, a vampyre living in <red>Draynor " +
                        "Manor</red> north of the village. At his request, I agreed to try and " +
                        "kill <red>Count Draynor</red>. <red>Morgan</red> suggested I begin by " +
                        "seeking out <red>Dr Harlow</red>, a retired vampyre hunter, at the " +
                        "<red>Blue Moon Inn</red> in <red>Varrock</red>."
                )
                return@questJournal
            }

            if (!player.carryingObj(Stake)) {
                line(
                    "I travelled to the <red>Blue Moon Inn</red> in <red>Varrock</red> and found " +
                        "<red>Dr Harlow</red>. However, he doesn't seem willing to talk to me " +
                        "unless I buy him a <red>Beer</red> first."
                )
                return@questJournal
            }

            line(
                "I travelled to the <red>Blue Moon Inn</red> in <red>Varrock</red> and found " +
                    "<red>Dr Harlow</red>. He told me that vampyres are hard to kill due to their " +
                    "ability to regenerate. However, he gave me a <red>Stake</red> and told me I " +
                    "could use it to prevent the regeneration. I'll now need to head to " +
                    "<red>Draynor Manor</red>, find <red>Count Draynor</red> and kill him. I " +
                    "should make sure to take the <red>Stake</red> with me, along with a " +
                    "<red>Hammer</red>. Most <red>General Stores</red> should stock a " +
                    "<red>Hammer</red>. <red>Dr Harlow</red> also suggested I carry some " +
                    "<red>Garlic</red> on me. <red>Morgan</red> might have some."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I spoke to Morgan at his home in Draynor Village. He told me about Count " +
                    "Draynor, a vampyre living in Draynor Manor north of the village. At his " +
                    "request, I agreed to try and kill Count Draynor. Morgan suggested I begin by " +
                    "seeking out Dr Harlow, a retired vampyre hunter, at the Blue Moon Inn in " +
                    "Varrock."
            )
            line(
                "I travelled to the Blue Moon Inn in Varrock and found Dr Harlow. He told me that " +
                    "vampyres are hard to kill due to their ability to regenerate. However, he " +
                    "gave me a Stake and told me I could use it to prevent the regeneration."
            )
            line(
                "I entered Draynor Manor and found Count Draynor in the basement. However, I was " +
                    "able to use the Stake that Dr Harlow gave me to kill him for good."
            )
        }

    private suspend fun ProtectedAccess.talkToMorgan(npc: Npc) {
        startDialogue(npc) { morganDialogue() }
    }

    private suspend fun Dialogue.morganDialogue() {
        when {
            quest.isQuestCompleted(player) -> {
                chatNpc(happy, "You did it! Draynor Village is free of that monster at last.")
            }
            quest.isQuestInProgress(player) -> morganAgain()
            else -> morganOpener()
        }
    }

    private suspend fun Dialogue.morganOpener() {
        chatNpc(shocked, "Could it be? A bold adventurer! Please, you must help us!")
        chatPlayer(quiz, "What is it? What's the problem?")
        chatNpc(
            shocked,
            "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his manor " +
                "to the north. Someone finally needs to put a stop to him once and for all!",
        )

        if (player.combatLevel < RecommendedCombatLevel) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombatLevel."
            )
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start the Vampyre Slayer quest?")

        if (!accept) {
            chatPlayer(
                worried,
                "An evil vampyre? I don't think this is the job for me. It sounds far too scary!",
            )
            chatNpc(
                sad,
                "I understand... Hopefully someone else will come along to finally save us from " +
                    "this evil.",
            )
            return
        }

        chatPlayer(neutral, "Sounds like a job for me. Where should I start?")
        chatNpc(
            happy,
            "Oh, thank goodness! I've been hoping this day would come for a long time, so I've " +
                "made sure to do my research. I've heard of a retired vampyre hunter called Dr " +
                "Harlow who lives in Varrock.",
        )

        quest.setQuestStage(access, StartedStage)

        chatNpc(
            happy,
            "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old " +
                "soak these days, so he spends most of his time in the Blue Moon Inn.",
        )

        morganTailMenu(firstTime = true)
    }

    private suspend fun Dialogue.morganAgain() {
        chatNpc(quiz, "Adventurer! Has Count Draynor been dealt with yet?")

        if (access.carryingObj(Stake) && !access.carryingObj(Garlic)) {
            chatPlayer(
                neutral,
                "I'm still working on it, but I could use some garlic to help me out.",
            )
            chatNpc(
                happy,
                "Ah, yes! I heard vampyres didn't like garlic. I keep some in the cupboard " +
                    "upstairs, just in case Count Draynor comes and tries anything!",
            )
        } else {
            chatPlayer(neutral, "I'm still working on it.")
            chatNpc(
                worried,
                "Please hurry! I'm sure Dr Harlow in Varrock's Blue Moon Inn can help.",
            )
        }

        morganTailMenu(firstTime = false)
    }

    private suspend fun Dialogue.morganTailMenu(firstTime: Boolean) {
        val label =
            if (firstTime) {
                "What else can you tell me about this vampyre?"
            } else {
                "What else can you tell me about Count Draynor?"
            }

        val tail = choice2(label, true, "Alright, I'll see about paying him a visit.", false)

        if (!tail) {
            chatPlayer(neutral, "Alright, I'll see about paying him a visit.")
            chatNpc(happy, "Thank you, brave adventurer.")
            return
        }

        chatPlayer(quiz, label)
        chatNpc(happy, "All sorts of things! I've studied the legends extensively!")
        chatPlayer(confused, "Wait... legends? What do you mean legends?")
        chatNpc(
            happy,
            "The legends of Count Draynor! It's said that long ago, he travelled here from dark " +
                "lands in the east. He built Draynor Manor, and from there he began his campaign " +
                "of darkness against our peaceful lands!",
        )
        chatPlayer(
            quiz,
            "Long ago? If this has been going on for so long, why has no one tried to stop him?",
        )
        chatNpc(
            neutral,
            "Many have tried. All have failed. Vampyres are not killed easily. Only those with " +
                "the right skills and equipment have any hope of slaying one.",
        )
        chatPlayer(
            quiz,
            "Like this Dr Harlow? Home come he's never tried to kill this Count Draynor?",
        )
        chatNpc(confused, "I'm... not sure. Perhaps he was always too busy killing other vampyres?")
        chatPlayer(
            quiz,
            "Hmm... Something seems amiss here. Are you sure you're telling me everything?",
        )
        chatNpc(
            worried,
            "Well... it is true that some claim Count Draynor hasn't left his manor for " +
                "generations now.",
        )
        chatPlayer(quiz, "So he's not actually attacked the village?")
        chatNpc(worried, "Er... no. At least, not for a long time! He used to though!")
        chatPlayer(quiz, "When?")
        chatNpc(worried, "Well... not since I've moved here at least.")
        chatPlayer(
            quiz,
            "So if there's no actual danger, why are you so keen for someone to kill this vampyre?",
        )
        chatNpc(
            worried,
            "Because what if he does start attacking again? Are we just meant to live our lives " +
                "constantly fearing the future?",
        )
        chatPlayer(neutral, "It doesn't really sound like anyone is living in fear apart from you.")
        chatNpc(
            angry,
            "More fool them! Count Draynor has cast a shadow over Draynor Village for " +
                "generations. They even named the place after him for Saradomin's sake!",
        )
        chatNpc(
            angry,
            "Even if the people here don't realise it, we will never be at peace until we know " +
                "he's dead!",
        )
        chatPlayer(neutral, "If you're sure... I guess I'll go and see this Dr Harlow.")
        chatNpc(happy, "Thank you, brave adventurer.")
    }

    private suspend fun ProtectedAccess.talkToHarlow(npc: Npc) {
        startDialogue(npc) { harlowDialogue() }
    }

    private suspend fun Dialogue.harlowDialogue() {
        val stage = quest.getQuestStage(player)

        when {
            stage >= CompleteStage -> {
                chatNpc(drunk, "Yoush got him! Have a drink on me. Well... buy yourshelf one.")
            }
            stage < StartedStage -> {
                chatNpc(drunk, "Buy me a drink pleassh...")
            }
            access.carryingObj(Stake) -> harlowAfterStake()
            stage >= MetHarlowStage -> harlowWantsBeer()
            else -> harlowFirstMeeting()
        }
    }

    private suspend fun Dialogue.harlowFirstMeeting() {
        chatNpc(drunk, "Buy me a drink pleassh...")

        val help =
            choice2(
                "I need your help dealing with a vampyre.",
                true,
                "I think you've had enough.",
                false,
            )

        if (!help) {
            chatPlayer(neutral, "I think you've had enough.")
            return
        }

        chatPlayer(neutral, "I need your help dealing with a vampyre.")
        chatNpc(drunk, "A vampyre you shhay...?")
        chatPlayer(neutral, "Not just any vampyre. Count Draynor.")
        chatNpc(drunk, "Draynor? Well, buy me a beer firsht...")
        chatPlayer(confused, "Are you sure you've not had enough?")

        quest.setQuestStage(access, MetHarlowStage)

        chatNpc(drunk, "Huh? No, I don't think ssho. Now, buy ush a beer.")
    }

    private suspend fun Dialogue.harlowWantsBeer() {
        chatNpc(drunk, "Have yoush got me a beer?")

        if (!access.carryingObj(Beer)) {
            chatPlayer(neutral, "I'll go and get you one.")
            chatNpc(drunk, "Cheersh, matey...")
            return
        }

        val give = choice2("Yes, here you go.", true, "No. I think you've had enough.", false)

        if (!give) {
            chatPlayer(neutral, "No. I think you've had enough.")
            chatNpc(drunk, "No help for yoush then.")
            return
        }

        chatPlayer(neutral, "Yes, here you go.")
        handOverBeer()
    }

    private suspend fun ProtectedAccess.useBeerOnHarlow(npc: Npc) {
        val stage = quest.getQuestStage(player)
        if (stage < MetHarlowStage || carryingObj(Stake)) {
            talkToHarlow(npc)
            return
        }
        startDialogue(npc) { handOverBeer() }
    }

    private suspend fun Dialogue.handOverBeer() {
        if (access.invDel(access.inv, Beer, 1).failure) {
            return
        }

        // Add the replacement item before suspending, on the same tick as the consumed item is removed.
        if (access.invAdd(access.inv, Stake, 1).failure) {
            access.invAdd(access.inv, Beer, 1)
            return
        }

        mesbox("You give a beer to Dr Harlow.")
        chatNpc(drunk, "Cheersh, matey...")
        chatPlayer(neutral, "Now, about Count Draynor...")
        chatNpc(
            drunk,
            "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh " +
                "and then, some adventurer... theysh go and try to kill him, but none of 'em ever " +
                "comesh back.",
        )
        chatNpc(
            drunk,
            "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... Theysh " +
                "hard to kill. Some... maybe imposhible.",
        )
        chatPlayer(quiz, "So how do I make sure I'm prepared?")
        chatNpc(
            drunk,
            "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the " +
                "easiest ish a stake. Here, You can havesh thish one.",
        )

        objbox(Stake, "Dr Harlow hands you a stake.")

        harlowInstructions()
        harlowTailMenu()
    }

    private suspend fun Dialogue.harlowInstructions() {
        chatNpc(
            drunk,
            "Takesh that to Draynor Manor. Find the vampyre inshide and show him what for! When " +
                "hesh weak, use a hammer to drive the stake in! Mosht general stores have them.",
        )
        chatNpc(
            drunk,
            "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.",
        )
        chatPlayer(quiz, "Garlic? Hmm... I'll see if Morgan knows where I can get some.")
    }

    private suspend fun Dialogue.harlowAfterStake() {
        chatNpc(drunk, "Yoush back! Killed that vampyre yet?")
        harlowTailMenu()
    }

    private suspend fun Dialogue.harlowTailMenu() {
        while (true) {
            val option =
                choice4(
                    "What do I need to do again?",
                    1,
                    "You said no one's ever seen Count Draynor?",
                    2,
                    "So you were once a proper vampyre hunter?",
                    3,
                    "I'd best get going.",
                    4,
                )

            when (option) {
                1 -> {
                    chatPlayer(quiz, "What do I need to do again?")
                    chatNpc(
                        drunk,
                        "Go to Draynor Manor. Find the vampyre inshide and show him what for! " +
                            "When hesh weak, use a hammer to drive that stake I gave you in! " +
                            "Mosht general stores have them.",
                    )
                    chatNpc(
                        drunk,
                        "Oh, and yoush should take some garlic with you as well. Vampyres don't " +
                            "likesh garlic.",
                    )
                    chatPlayer(
                        quiz,
                        "Garlic? Hmm... I'll see if Morgan knows where I can get some.",
                    )
                }
                2 -> {
                    chatPlayer(quiz, "You said no one's ever seen Count Draynor?")
                    chatNpc(drunk, "No, butsh every now and then someonesh tried to kill him.")
                    chatPlayer(
                        quiz,
                        "But they've never come back? Meaning someone or something killed them " +
                            "most likely. Presumably Count Draynor?",
                    )
                    chatNpc(drunk, "Yesh, I'd say so.")
                    chatPlayer(quiz, "So why does he never leave his manor?")
                    chatNpc(drunk, "Who knowsh. Most likely he's grown too weak to do ssho.")
                    chatPlayer(quiz, "Weak? So he shouldn't be too hard to kill then?")
                    chatNpc(
                        drunk,
                        "Even a weak vampyre ish very dangerous. Just go ashk those other " +
                            "adventurers thatsh never came back.",
                    )
                }
                3 -> {
                    chatPlayer(quiz, "So you were once a proper vampyre hunter?")
                    chatNpc(
                        drunk,
                        "Yesh! I travelled their homeland to the easht. Morytania! I killed lotsh " +
                            "of vampyres over there!",
                    )
                    chatPlayer(quiz, "What was the most dangerous vampyre you killed?")
                    chatNpc(
                        drunk,
                        "Dangerous? Oh, well... Ish never killed one of the really dangerous ones.",
                    )
                    chatPlayer(quiz, "Why not?")
                    chatNpc(drunk, "Can't. Theysh too strong. Nothing cansh hurt them.")
                    chatPlayer(worried, "Oh... But what if Count Draynor's like that?")
                    chatNpc(
                        drunk,
                        "Yoush don't get many vampyres around here. Pretty much none, actshually. " +
                            "There's speshial magic that stops them coming over here. Any that are " +
                            "here... theysh weak... and trapped.",
                    )
                    chatPlayer(quiz, "So how come you've never tried to kill him yourself?")
                    chatNpc(
                        drunk,
                        "Why bother? Hesh not killed anyone in a long time, so no one really " +
                            "caresh about him that much, apart from the odd adventurer thatsh " +
                            "looking to prove themshelves.",
                    )
                    chatPlayer(neutral, "I see. Fair enough.")
                }
                else -> {
                    chatPlayer(neutral, "I'd best get going.")
                    chatNpc(drunk, "Good luck, matey...")
                    return
                }
            }
        }
    }

    internal companion object {
        internal const val QuestKey = "quest_vampyreslayer"

        internal const val QuestVarp = "varp.vampire"

        internal const val StartedStage = 1

        internal const val MetHarlowStage = 2

        internal const val CompleteStage = 3

        internal const val Stake = "obj.stake"
        internal const val Garlic = "obj.garlic"
        internal const val Hammer = "obj.hammer"
        internal const val Beer = "obj.beer"

        private const val RecommendedCombatLevel = 20

        private const val AttackXpReward = 4825.0

        private const val CompletionJingle = 154
    }
}

private fun ProtectedAccess.carryingObj(obj: String): Boolean = invTotal(inv, obj) > 0
