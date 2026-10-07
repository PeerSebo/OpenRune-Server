package org.rsmod.content.quest.area.alkharid

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.minigames.sorceressgarden.npcs.sqirkJuiceTopic
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.BronzeBar
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Coins
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Complete
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.Gathering
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.GuardDrunk
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeliTiedUp
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.KeyPrint
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PrinceFreed
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.PrincesKey
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.QuestKey
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.QuestVarp
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.RecommendedCombatLevel
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.SkinPaste
import org.rsmod.content.quest.area.alkharid.PrinceAliRescueState.TalkToOsman
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

class PrinceAliRescue :
    QuestScript(
        QuestKey,
        QuestVarp,
        rewards {
            item(Coins, RewardCoins)
            extra("Free use of the")
            extra("Al Kharid Toll Gate")
        },
        ItemRewardDisplay(SkinPaste, x = 75),
        completionJingle = CompletionJingle,
    ) {

    override fun ScriptContext.init() {
        check(quest.maxSteps == Complete) {
            "Prince Ali Rescue end state changed: dbrow says ${quest.maxSteps}, script says $Complete"
        }

        onOpNpc1("npc.hassan") { talkToHassan(it.npc) }

        onOpNpc1("npc.osman") { talkToOsman(it.npc) }
        onOpNpc1("npc.contact_osman_multi") { talkToOsman(it.npc) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Chancellor Hassan</col> in the " +
            "<col=800000>Al Kharid Palace</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage > TalkToOsman) {
                strike(
                    "I spoke to Hassan, the Chancellor to the Emir of Al Kharid, in the Al Kharid " +
                        "Palace. He asked for my help with an urgent matter, and directed me to " +
                        "speak to Osman, Al Kharid's Spymaster."
                )
            } else {
                line(
                    "I spoke to <red>Chancellor Hassan</red>, the <red>Chancellor</red> to the " +
                        "<red>Emir of Al Kharid</red>, in the <red>Al Kharid Palace</red>. He " +
                        "asked for my help with an urgent matter, and directed me to speak to " +
                        "<red>Osman</red>, <red>Al Kharid's Spymaster</red>, just outside the " +
                        "<red>Palace</red>."
                )
                return@questJournal
            }

            if (stage > Gathering) {
                strike(
                    "I spoke to Osman outside the Al Kharid Palace. He informed me that Prince " +
                        "Ali, the Emir's heir, was captured by a group of Bandits and taken to an " +
                        "Abandoned Jail east of Draynor Village. Osman asked for my help in " +
                        "rescuing Prince Ali, and suggested I speak with Leela in Draynor Village."
                )
            } else {
                line(
                    "I spoke to <red>Osman</red> outside the <red>Al Kharid Palace</red>. He " +
                        "informed me that <red>Prince Ali</red>, the <red>Emir's</red> heir, was " +
                        "captured by a group of <red>Bandits</red> and taken to an <red>Abandoned " +
                        "Jail</red> east of <red>Draynor Village</red>. <red>Osman</red> asked " +
                        "for my help in rescuing <red>Prince Ali</red>, and suggested I speak " +
                        "with <red>Leela</red>, who I can find spying on the <red>Jail</red>."
                )
                return@questJournal
            }

            strike(
                "With help from Osman and Leela, I created a disguise to make Prince Ali look " +
                    "like Lady Keli, the leader of the Bandits. I also made a copy of the key to " +
                    "his cell."
            )

            if (stage < GuardDrunk) {
                line(
                    "I need to stop <red>Prince Ali's Personal Guard</red> from being a problem. " +
                        "<red>Leela</red> says he is talkative, so he must have a weakness."
                )
                return@questJournal
            }

            strike(
                "To stop Prince Ali's Personal Guard from being a problem, I gave him some Beer " +
                    "to get him drunk."
            )

            if (stage < KeliTiedUp) {
                line(
                    "I still need to get <red>Lady Keli</red> out of the way before I can free " +
                        "<red>Prince Ali</red>."
                )
                return@questJournal
            }

            strike("To get Lady Keli out of the way, I tied her up and put her in a Cupboard.")

            if (stage < PrinceFreed) {
                line(
                    "I should now unlock <red>Prince Ali's</red> cell and give him the " +
                        "<red>disguise</red>."
                )
                return@questJournal
            }

            line(
                "With <red>Lady Keli</red> dealt with, I was able to free <red>Prince Ali</red> " +
                    "and get him to safety. I should now return to <red>Chancellor Hassan</red> " +
                    "in the <red>Al Kharid Palace</red>."
            )
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I spoke to Hassan, the Chancellor to the Emir of Al Kharid, in the Al Kharid " +
                    "Palace. He asked for my help with an urgent matter, and directed me to speak " +
                    "to Osman, Al Kharid's Spymaster."
            )
            line(
                "I spoke to Osman outside the Al Kharid Palace. He informed me that Prince Ali, " +
                    "the Emir's heir, was captured by a group of Bandits and taken to an " +
                    "Abandoned Jail east of Draynor Village. Osman asked for my help in rescuing " +
                    "Prince Ali, and suggested I speak with Leela in Draynor Village."
            )
            line(
                "With help from Osman and Leela, I created a disguise to make Prince Ali look " +
                    "like Lady Keli, the leader of the Bandits. I also made a copy of the key to " +
                    "his cell."
            )
            line(
                "To stop Prince Ali's Personal Guard from being a problem, I gave him some Beer " +
                    "to get him drunk."
            )
            line("To get Lady Keli out of the way, I tied her up and put her in a Cupboard.")
            line(
                "With Lady Keli dealt with, I was able to free Prince Ali and get him to safety. " +
                    "I returned to Al Kharid, where Hassan rewarded me for my work."
            )
        }

    private suspend fun ProtectedAccess.talkToHassan(npc: Npc) {
        startDialogue(npc) { hassanDialogue() }
    }

    private suspend fun Dialogue.hassanDialogue() {
        when {
            quest.isQuestCompleted(player) -> {
                chatNpc(
                    happy,
                    "Thank you for being a friend to Al Kharid. You are always welcome here.",
                )
            }
            quest.getQuestStage(player) >= PrinceFreed -> handOutReward()
            quest.getQuestStage(player) >= Gathering -> {
                chatNpc(
                    neutral,
                    "Hello again. I hear you have agreed to help rescue Prince Ali. On behalf of " +
                        "the Emir, I will have a reward ready for you upon your success.",
                )
            }
            quest.getQuestStage(player) >= TalkToOsman -> {
                chatNpc(
                    neutral,
                    "Hello again. Have you spoken to Osman yet? He should be just outside the " +
                        "palace.",
                )
                chatPlayer(neutral, "Not yet. I'll go and see him.")
            }
            else -> hassanOpener()
        }
    }

    private suspend fun Dialogue.hassanOpener() {
        chatNpc(neutral, "Greetings! I am Hassan, Chancellor to the Emir of Al Kharid.")

        val option =
            choice4(
                "Is there anything I can help you with?",
                1,
                "It's just too hot here. How can you stand it?",
                2,
                "Do you mind if I just kill your warriors?",
                3,
                "I'd better be off.",
                4,
            )

        when (option) {
            1 -> offerQuest()
            2 -> {
                chatPlayer(neutral, "It's just too hot here. How can you stand it?")
                chatNpc(
                    happy,
                    "You grow used to the desert sun, my friend. Al Kharid has stood in it for " +
                        "a very long time.",
                )
            }
            3 -> {
                chatPlayer(quiz, "Do you mind if I just kill your warriors?")
                chatNpc(angry, "I most certainly do! They are the Emir's own guard.")
            }
            4 -> {
                chatPlayer(neutral, "I'd better be off.")
                chatNpc(neutral, "Farewell, traveller.")
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(happy, "Is there anything I can help you with?")
        chatNpc(
            confused,
            "Well... we do currently have a very urgent issue we need to resolve, and I suppose " +
                "you look like someone who knows how to get a job done. Are you definitely " +
                "interested in helping?",
        )

        if (player.combatLevel < RecommendedCombatLevel) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RecommendedCombatLevel."
            )
        }

        val accept =
            choice2("Yes.", true, "No.", false, title = "Start the Prince Ali Rescue quest?")

        if (!accept) {
            chatPlayer(neutral, "Actually, I've changed my mind.")
            chatNpc(neutral, "I see. Well you know where to find me if you do wish to help us.")
            return
        }

        chatPlayer(happy, "Of course.")

        quest.setQuestStage(access, TalkToOsman)

        chatNpc(
            neutral,
            "You'll find our Spymaster, Osman, just outside the palace. Go to him and tell him I " +
                "sent you. He will fill you in on the details of our problem.",
        )
        chatPlayer(happy, "Alright, I'll get to it.")
    }

    private suspend fun Dialogue.handOutReward() {
        chatNpc(
            happy,
            "Prince Ali is home safe. You have the eternal gratitude of the Emir for rescuing his " +
                "son. Please, take this payment as a thank you.",
        )
        quest.completeQuest(access)
    }

    private suspend fun ProtectedAccess.talkToOsman(npc: Npc) {
        startDialogue(npc) { osmanDialogue() }
    }

    private suspend fun Dialogue.osmanDialogue() {
        when {
            quest.isQuestCompleted(player) -> {
                chatNpc(
                    shifty,
                    "Prince Ali is safe once more. Chancellor Hassan has your payment waiting for " +
                        "you.",
                )
                sqirkJuiceTopic()
            }
            quest.getQuestStage(player) >= Gathering -> osmanAgain()
            quest.getQuestStage(player) >= TalkToOsman -> osmanBriefing()
            else -> osmanPreQuest()
        }
    }

    private suspend fun Dialogue.osmanPreQuest() {
        chatNpc(shifty, "Hello. I am Osman. What can I assist you with?")

        val nosy =
            choice2(
                "You don't seem very tough. Who are you?",
                false,
                "Nothing. I'm just being nosy.",
                true,
            )

        if (nosy) {
            chatPlayer(shifty, "Nothing. I'm just being nosy.")
            chatNpc(shifty, "That bothers me not. The secrets of Al Kharid protect themselves.")
            return
        }

        chatPlayer(neutral, "You don't seem very tough. Who are you?")
        chatNpc(shifty, "I work for Al Kharid's Emir. That is all you need to know.")
    }

    private suspend fun Dialogue.osmanBriefing() {
        chatPlayer(
            neutral,
            "Osman? I was told by the Chancellor to come and speak to you. Apparently you have an " +
                "issue that I can help with.",
        )
        chatNpc(
            shifty,
            "That would be an apt description. However, I find you to be an interesting choice by " +
                "the Chancellor. Why has he chosen to trust you with this task over one of our own?",
        )
        chatPlayer(confused, "Er... I just asked if he needed help and he said yes.")
        chatNpc(
            shifty,
            "That man is far too trusting at a time when we must take extra care. However, if he " +
                "has made his decision, I will not question it. Still, you should know that I " +
                "will be keeping a close eye on you.",
        )
        chatPlayer(quiz, "Fair enough. So what is this issue?")
        chatNpc(
            shifty,
            "Prince Ali, heir to the Emir of Al Kharid, has been taken. My spies have already " +
                "discovered where he is being held, but we need someone to make the rescue.",
        )
        chatPlayer(happy, "Well I'm sure I can manage that. How do I go about rescuing him?")
        chatNpc(
            shifty,
            "The Prince has been taken by a group of bandits led by the self-proclaimed 'Lady' " +
                "Keli. They are holding him in the abandoned jail just east of Draynor Village.",
        )
        chatNpc(
            shifty,
            "According to our information, Keli is the only one able to freely move around the " +
                "area. For you to get the Prince out, you will need to disguise him as her. She " +
                "will of course need dealing with first.",
        )
        chatPlayer(quiz, "Why can't I just go in and kill her and her bandits?")
        chatNpc(
            shifty,
            "And endanger the life of the Prince in the process? No. There will be no unnecessary " +
                "risks. We need to do this with as little bloodshed as possible.",
        )
        chatPlayer(quiz, "You make a fair point. Do you know what Keli looks like?")
        chatNpc(
            shifty,
            "She has blonde hair and wears pink clothes. My daughter, Leela, one of my spies, is " +
                "currently in Draynor Village keeping an eye on the jail. I'm sure she can help " +
                "you with the specifics.",
        )
        chatNpc(
            shifty,
            "Before you go, there is one more thing. You'll need a key to get the Prince out of " +
                "his cell. Keli has the only one. You could steal it, but that seems like a risk " +
                "we should avoid.",
        )
        chatPlayer(
            confused,
            "But if she has the only copy, and I can't just steal it, how do I get the key?",
        )
        chatNpc(
            shifty,
            "If you bring me an imprint of the key along with a bronze bar, I can show you how to " +
                "make a copy. You should be able to make an imprint of the key using some soft clay.",
        )
        chatNpc(
            shifty,
            "Of course, you'll need to find a way to get Keli to show you the key without causing " +
                "suspicion. I'm sure Leela can help you with that.",
        )

        quest.setQuestStage(access, Gathering)

        chatPlayer(
            happy,
            "Sounds like I should head on over to Draynor Village and see Leela then.",
        )
        chatNpc(quiz, "Indeed. Do you have any further questions before you go?")

        questionLoop()
    }

    private suspend fun Dialogue.questionLoop() {
        var askedWhy = false
        var askedWhere = false

        while (true) {
            val option =
                when {
                    askedWhy && askedWhere -> Leave
                    askedWhy ->
                        choice2(
                            WhereLabel,
                            Where,
                            "No. I think I know everything I need to.",
                            Leave,
                        )
                    askedWhere ->
                        choice2(WhyLabel, Why, "No. I think I know everything I need to.", Leave)
                    else ->
                        choice3(
                            WhyLabel,
                            Why,
                            WhereLabel,
                            Where,
                            "No. I think I know everything I need to.",
                            Leave,
                        )
                }

            when (option) {
                Why -> {
                    askedWhy = true
                    chatPlayer(quiz, WhyLabel)
                    chatNpc(shifty, "No, but we have our theories.")
                    chatPlayer(quiz, "Care to share them?")
                    chatNpc(
                        shifty,
                        "No. You have not yet proven yourself enough to be trusted with that " +
                            "information.",
                    )
                    if (askedWhere) {
                        break
                    }
                    chatNpc(quiz, "Now, do you have any further questions?")
                }
                Where -> {
                    askedWhere = true
                    chatPlayer(quiz, WhereLabel)
                    chatNpc(
                        shifty,
                        "She will be somewhere near the abandoned jail that the bandits are using. " +
                            "It's just east of the village.",
                    )
                    if (askedWhy) {
                        break
                    }
                    chatNpc(quiz, "Do you have any further questions?")
                }
                else -> {
                    chatPlayer(neutral, "No. I think I know everything I need to.")
                    break
                }
            }
        }

        chatNpc(shifty, "Then you should get going.")
    }

    private suspend fun Dialogue.osmanAgain() {
        chatNpc(shifty, "You again. How are things going in Draynor?")

        val hasImprint = access.carrying(KeyPrint)
        val hasKey = access.carrying(PrincesKey)

        when {
            hasKey -> {
                chatPlayer(happy, "I have a copy of the key.")
                chatNpc(shifty, "Good. Then the cell is no longer a problem. Speak to Leela.")
            }
            hasImprint && access.carrying(BronzeBar) -> {
                chatPlayer(neutral, "I have an imprint of the key, and a bronze bar.")
                chatNpc(
                    shifty,
                    "Then you have everything you need. Any furnace will cast it for you - you do " +
                        "not need me for that.",
                )
            }
            hasImprint -> {
                chatPlayer(neutral, "I have an imprint of the key.")
                chatNpc(shifty, "Good. Bring a bronze bar to a furnace and cast yourself a copy.")
            }
            else -> {
                chatPlayer(neutral, "I'm still working on gathering all the items I need.")
                chatNpc(shifty, "Well if you need help, just talk to Leela.")
            }
        }

        questionLoop()
    }

    private companion object {
        private const val RewardCoins = 700

        private const val CompletionJingle = 153

        private const val Why = 1
        private const val Where = 2
        private const val Leave = 3

        private const val WhyLabel = "Do you know why they've taken the Prince?"
        private const val WhereLabel = "Where abouts in Draynor is Leela?"
    }
}

internal fun ProtectedAccess.carrying(obj: String): Boolean = invTotal(inv, obj) > 0

internal fun ProtectedAccess.carriedCount(obj: String): Int = invTotal(inv, obj)
