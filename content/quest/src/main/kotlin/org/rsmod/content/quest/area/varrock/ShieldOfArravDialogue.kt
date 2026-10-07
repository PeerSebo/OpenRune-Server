package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.ScriptContext

internal class ShieldOfArravDialogue(private val quest: Quest, private val objRepo: ObjRepository) {

    fun ScriptContext.register() {
        onOpNpc1(SoaRefs.Baraek) { startDialogue(it.npc) { baraek() } }
        onOpNpc1(SoaRefs.Katrine) { startDialogue(it.npc) { katrine() } }
        onOpNpc1(SoaRefs.Straven) { startDialogue(it.npc) { straven() } }
    }

    fun reldoQuestOption(access: ProtectedAccess): String {
        val phoenix = access.phoenixGang
        return when {
            quest.isQuestNotStarted(access.player) -> "I'm in search of a quest."
            quest.isQuestCompleted(access.player) -> "I'm in search of a quest."
            phoenix == PhoenixState.Accepted && !access.soaCarries(SoaRefs.Book) ->
                "About that book... Where is it again?"
            phoenix == PhoenixState.Accepted -> "I found that book."
            phoenix == PhoenixState.BookRead ->
                "Okay, I've read that book about the Shield of Arrav."
            carriesShieldHalf(access) -> "I've found half of the Shield of Arrav!"
            else -> "I'm in search of a quest."
        }
    }

    suspend fun reldoQuestBranch(dialogue: Dialogue): Unit =
        with(dialogue) {
            val phoenix = access.phoenixGang
            when {
                quest.isQuestNotStarted(player) -> reldoOffer()
                quest.isQuestCompleted(player) -> reldoNoQuest()
                phoenix == PhoenixState.Accepted && !access.soaCarries(SoaRefs.Book) ->
                    reldoWhereIsTheBook()
                phoenix == PhoenixState.Accepted -> reldoFoundTheBook()
                phoenix == PhoenixState.BookRead -> reldoNamesTheGangs()
                carriesShieldHalf(access) -> reldoShieldHalf()
                else -> reldoNoQuest()
            }
        }

    private fun carriesShieldHalf(access: ProtectedAccess): Boolean =
        access.soaCarries(SoaRefs.ShieldHalfLeft) || access.soaCarries(SoaRefs.ShieldHalfRight)

    private suspend fun Dialogue.reldoOffer() {
        chatPlayer(neutral, "I'm in search of a quest.")
        chatNpc(neutral, "Hmm... I don't believe there are any here.")
        chatNpc(confused, "Let me think actually...")
        chatNpc(happy, "Ah, yes. I think I have something, if you're definitely interested?")

        if (player.combatLevel < SoaRefs.RecommendedCombat) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of ${SoaRefs.RecommendedCombat}."
            )
        }

        val accept = choice2("Yes.", true, "No.", false, title = "Start the Shield of Arrav quest?")
        if (!accept) {
            chatPlayer(neutral, "Actually, I don't think I am. Goodbye.")
            return
        }

        chatPlayer(neutral, "Of course.")
        chatNpc(
            neutral,
            "Well, if you look around for book called The Shield of Arrav, you'll find a quest in " +
                "there.",
        )

        access.phoenixGang = PhoenixState.Accepted
        quest.setQuestStage(access, SoaStage.Started)

        chatNpc(
            confused,
            "I'm not sure where the book is mind you... but I'm sure it's around here somewhere.",
        )
        chatPlayer(happy, "Great! Thank you.")
    }

    private suspend fun Dialogue.reldoNoQuest() {
        chatPlayer(neutral, "I'm in search of a quest.")
        chatNpc(neutral, "Hmm... I don't believe there are any here.")
    }

    private suspend fun Dialogue.reldoWhereIsTheBook() {
        chatPlayer(quiz, "About that book... Where is it again?")
        chatNpc(confused, "I'm not sure exactly... but I'm sure it's around here somewhere.")
    }

    private suspend fun Dialogue.reldoFoundTheBook() {
        chatPlayer(neutral, "I found that book.")
        chatNpc(neutral, "Very good. I suggest you give it a read.")
    }

    private suspend fun Dialogue.reldoNamesTheGangs() {
        chatPlayer(quiz, "Okay, I've read that book about the Shield of Arrav.")
        chatNpc(quiz, "Then perhaps you now have your quest?")
        chatPlayer(
            quiz,
            "I think I do. Do you know where I can find the Phoenix Gang or the Black Arm Gang?",
        )
        chatNpc(
            neutral,
            "No, I don't. However, I hear Baraek, the fur trader in the market, has had " +
                "connections with the Phoenix Gang.",
        )
        chatPlayer(quiz, "And the Black Arm Gang?")

        access.blackArmGang = BlackArmState.GangsNamed
        access.phoenixGang = PhoenixState.GangsNamed

        chatNpc(
            neutral,
            "There are rumours that they are based in the south west corner of the city. Charlie " +
                "the Tramp might be able to tell you more. He knows that part of Varrock well.",
        )
        chatPlayer(happy, "Thanks! I'll get to it!")
        chatNpc(neutral, "Good luck.")
    }

    private suspend fun Dialogue.reldoShieldHalf() {
        chatPlayer(happy, "I've found half of the Shield of Arrav!")
        chatNpc(quiz, "Half?")
        chatPlayer(neutral, "Yes. It seems it was split in two.")
        chatNpc(quiz, "I see... Where did you find this half?")

        if (access.blackArmGang >= BlackArmState.Member) {
            chatPlayer(
                neutral,
                "The Black Arm Gang had it. I managed to infiltrate their gang and steal it from " +
                    "them.",
            )
            chatNpc(
                neutral,
                "A split gang and a split shield. My guess would be that the other gang has the " +
                    "other half.",
            )
            chatPlayer(
                neutral,
                "Well, I did have a member of the Phoenix Gang help me... Maybe they can find the " +
                    "other half.",
            )
        } else {
            chatPlayer(
                neutral,
                "The Phoenix Gang had it. I managed to infiltrate their gang and steal it from " +
                    "them.",
            )
            chatNpc(
                neutral,
                "A split gang and a split shield. My guess would be that the other gang has the " +
                    "other half.",
            )
            chatPlayer(
                neutral,
                "Well, I don't think I'll have much luck infiltrating the Black Arm Gang now that " +
                    "I've joined their rivals, but maybe someone else will be able to.",
            )
        }

        chatNpc(
            neutral,
            "Let's hope so. In the meantime, you should probably head over to the museum so they " +
                "can verify the authenticity of this half.",
        )
    }

    fun charlieBlackArmAskVisible(access: ProtectedAccess): Boolean =
        quest.isQuestInProgress(access.player) &&
            access.phoenixGang >= PhoenixState.GangsNamed &&
            access.blackArmGang < BlackArmState.CrossbowTask

    fun charliePhoenixAskVisible(access: ProtectedAccess): Boolean =
        quest.isQuestInProgress(access.player) && access.blackArmGang >= BlackArmState.CrossbowTask

    suspend fun charlieBlackArmAsk(dialogue: Dialogue): Unit =
        with(dialogue) {
            chatPlayer(quiz, "I'm looking for the Black Arm Gang.")
            if (access.soaCharlieMet == 0) {
                chatNpc(
                    shifty,
                    "Funny you should mention that... Rumour has it that they have their " +
                        "headquarters right down this alleyway. Though if I were you, I'd be " +
                        "careful about messing with them...",
                )
            } else {
                chatNpc(
                    shifty,
                    "Like I told you, the rumour is that they have their headquarters right down " +
                        "this alleyway. Though if I were you, I'd be careful about messing with " +
                        "them...",
                )
            }
            chatPlayer(quiz, "Do you think they would let me join?")

            if (access.phoenixGang >= PhoenixState.Member) {
                chatNpc(
                    neutral,
                    "No. You're a collaborator with the Phoenix Gang. There's no way they'll let " +
                        "you join.",
                )
                val how =
                    choice2(
                        "How did you know I was in the Phoenix Gang?",
                        true,
                        "Any ideas how I could get in there then?",
                        false,
                    )
                if (how) {
                    chatPlayer(quiz, "How did you know I was in the Phoenix Gang?")
                    chatNpc(
                        neutral,
                        "I've been known to have dealings with the Phoenix Gang. I wouldn't be " +
                            "very good at my job if I didn't know their members.",
                    )
                    chatPlayer(quiz, "I see. So, any ideas how I could get in there?")
                } else {
                    chatPlayer(quiz, "Any ideas how I could get in there then?")
                }
                chatNpc(
                    neutral,
                    "Your best bet would probably be to find someone who isn't a member of the " +
                        "Phoenix Gang and get them to infiltrate the ranks of the Black Arm Gang " +
                        "for you.",
                )
                chatPlayer(quiz, "Someone? Like who?")
                chatNpc(
                    neutral,
                    "There's plenty of other adventurers about besides yourself. I'm sure if you " +
                        "asked one of them nicely they would help you.",
                )
                chatPlayer(neutral, "I see...")
                return
            }

            access.soaCharlieMet = 1
            chatNpc(
                neutral,
                "You never know. You'll find a lady down there called Katrine. Speak to her. But " +
                    "whatever you do, don't upset her.",
            )
        }

    suspend fun charliePhoenixAsk(dialogue: Dialogue): Unit =
        with(dialogue) {
            chatPlayer(quiz, "Any idea where the Phoenix Gang are based?")

            if (access.soaCharlieHint == 1) {
                chatNpc(
                    neutral,
                    "Well, given you paid up, I guess I can remind you. The Phoenix Gang can be " +
                        "found east of here. Take the southern alley past the Blue Moon Inn and " +
                        "look for a building owned by the VTAM corporation.",
                )
                charlieStashDirectionsTail()
                return
            }

            if (access.soaCharliePaid == 1) {
                chatNpc(neutral, "If you pay me five more coins, I'll tell you.")
                charlieFiveMoreCoins()
                return
            }

            chatNpc(
                neutral,
                "Sure, I know everything there is to know about this city. I'd be happy to tell " +
                    "you for ten coins.",
            )

            val pick =
                choice4(
                    "Okay, that sounds fair.",
                    1,
                    "Ten coins? That's too much! I'll give you five.",
                    2,
                    "You thieving gutter-scum! You'd better watch your back!",
                    3,
                    "Never mind. I'll find it myself.",
                    4,
                )
            when (pick) {
                1 -> {
                    chatPlayer(neutral, "Okay, that sounds fair.")
                    if (access.invDel(access.inv, SoaRefs.Coins, 10).failure) {
                        chatPlayer(sad, "Actually... I don't seem to have ten coins on me.")
                        return
                    }
                    access.soaCharliePaid = 1
                    mesbox("You give ten coins to Charlie.")
                    charlieStashDirections()
                }
                2 -> {
                    chatPlayer(neutral, "Ten coins? That's too much! I'll give you five.")
                    if (access.invDel(access.inv, SoaRefs.Coins, 5).failure) {
                        chatPlayer(sad, "Actually... I don't seem to have five coins on me.")
                        return
                    }
                    access.soaCharliePaid = 1
                    chatNpc(neutral, "I guess that'll have to do then.")
                    mesbox("You give five coins to Charlie.")
                    chatNpc(neutral, "Great, thanks.")
                    chatPlayer(quiz, "So where can I find the Phoenix Gang then?")
                    chatNpc(neutral, "If you pay me five more coins, I'll tell you.")
                    charlieFiveMoreCoins()
                }
                3 -> {
                    chatPlayer(angry, "You thieving gutter-scum! You'd better watch your back!")
                    chatNpc(neutral, "Yeah, sure, whatever. You don't frighten me, pal.")
                }
                4 -> {
                    chatPlayer(neutral, "Never mind. I'll find it myself.")
                    chatNpc(neutral, "Well, you know where to find me if you get lost!")
                }
            }
        }

    private suspend fun Dialogue.charlieFiveMoreCoins() {
        val pay =
            choice2(
                "That's not fair, but I guess I don't have a choice.",
                true,
                "You thieving gutter-scum! You'd better watch your back!",
                false,
            )
        if (!pay) {
            chatPlayer(angry, "You thieving gutter-scum! You'd better watch your back!")
            chatNpc(neutral, "Yeah, sure, whatever. You don't frighten me, pal.")
            return
        }
        chatPlayer(neutral, "That's not fair, but I guess I don't have a choice.")
        if (access.invDel(access.inv, SoaRefs.Coins, 5).failure) {
            chatPlayer(sad, "Actually... I don't seem to have five coins on me.")
            return
        }
        mesbox("You give five coins to Charlie.")
        charlieStashDirections()
    }

    private suspend fun Dialogue.charlieStashDirections() {
        chatNpc(
            neutral,
            "Perfect! Now listen closely. The Phoenix Gang can be found east of here. Take the " +
                "southern alley past the Blue Moon Inn and look for a building owned by the VTAM " +
                "corporation.",
        )
        charlieStashDirectionsTail()
    }

    private suspend fun Dialogue.charlieStashDirectionsTail() {
        access.soaCharlieHint = 1
        access.soaPhoenixSource = 1
        chatNpc(
            neutral,
            "Their main hideout is in that building, with their weapon stash being two buildings " +
                "east of it. Good luck getting into either without being a member of their gang " +
                "though.",
        )
        chatPlayer(quiz, "Without being a member of their gang? That might be problematic.")
        chatNpc(
            neutral,
            "Well, your best bet would probably be to find someone who isn't a member of the " +
                "Black Arm Gang and get them to infiltrate the ranks of the Phoenix Gang for you.",
        )
        chatPlayer(quiz, "Someone? Like who?")
        chatNpc(
            neutral,
            "There's plenty of other adventurers about besides yourself. I'm sure if you asked " +
                "one of them nicely they would help you.",
        )
        chatPlayer(neutral, "I see...")
    }

    private suspend fun Dialogue.baraek() {
        val phoenixAsk =
            quest.isQuestInProgress(player) &&
                access.phoenixGang in PhoenixState.GangsNamed until PhoenixState.Member

        val pick =
            if (phoenixAsk) {
                choice3(
                    "Can you sell me some furs?",
                    1,
                    "Can you tell me where I can find the Phoenix Gang?",
                    2,
                    "Hello. I am in search of a quest.",
                    3,
                )
            } else {
                choice2("Can you sell me some furs?", 1, "Hello. I am in search of a quest.", 3)
            }

        when (pick) {
            1 -> baraekSellsFurs()
            2 -> baraekPhoenixGang()
            3 -> {
                chatPlayer(quiz, "Hello! I am in search of a quest.")
                chatNpc(neutral, "Sorry kiddo, I'm a fur trader not a damsel in distress.")
            }
        }
    }

    private suspend fun Dialogue.baraekSellsFurs() {
        chatPlayer(quiz, "Can you sell me some furs?")
        chatNpc(neutral, "Yeah, sure. They're 20 gold coins each.")
        val buy =
            choice2("Yeah, okay, here you go.", true, "20 gold coins? That's an outrage!", false)
        if (!buy) {
            chatPlayer(angry, "20 gold coins? That's an outrage!")
            chatNpc(
                neutral,
                "Well, I can't go any cheaper than that mate. I have a family to feed.",
            )
            return
        }
        chatPlayer(neutral, "Yeah, OK, here you go.")
        if (access.invTotal(access.inv, SoaRefs.Coins) >= 20) {
            baraekFurSale(price = 20)
            return
        }
        chatPlayer(sad, "Oh dear, I don't have enough money!")
        chatNpc(neutral, "Well, my best price is 18 coins.")
        val pay = choice2("OK, here you go.", true, "No thanks, I'll leave it.", false)
        if (!pay) {
            chatPlayer(neutral, "No thanks, I'll leave it.")
            chatNpc(neutral, "It's your loss mate.")
            return
        }
        chatPlayer(neutral, "OK, here you go.")
        if (access.invTotal(access.inv, SoaRefs.Coins) >= 18) {
            baraekFurSale(price = 18)
            return
        }
        chatPlayer(sad, "Oh dear, I don't have that either.")
        chatNpc(neutral, "Well, I can't go any cheaper than that mate. I've got a family to feed.")
        chatPlayer(neutral, "Oh well, never mind.")
    }

    private suspend fun Dialogue.baraekFurSale(price: Int) {
        if (access.invDel(access.inv, SoaRefs.Coins, price).failure) {
            return
        }
        access.invAddOrDrop(objRepo, BearFur)
        objbox(BearFur, "Baraek sells you a fur.")
    }

    private suspend fun Dialogue.baraekPhoenixGang() {
        chatPlayer(quiz, "Can you tell me where I can find the Phoenix Gang?")
        chatNpc(neutral, "Shh, shh, shh! Not so loud! You don't want to get me in trouble, do you?")
        chatPlayer(quiz, "So I take it that you do know where they are.")
        chatNpc(
            neutral,
            "I may do... but I don't want to get into trouble for revealing their hideout. Of " +
                "course, if I was, say 20 coins richer, I may happen to be more inclined to take " +
                "that sort of risk...",
        )

        val pick =
            choice3(
                "Okay. Have 20 gold coins.",
                1,
                "No, I don't like things like bribery.",
                2,
                "Yes. I'd like to be 20 coins richer too.",
                3,
            )
        when (pick) {
            1 -> {
                chatPlayer(neutral, "Okay. Have 20 gold coins.")
                if (access.invDel(access.inv, SoaRefs.Coins, 20).failure) {
                    chatPlayer(sad, "Uh... oops. I don't actually have 20 coins. Silly me.")
                    return
                }
                if (access.phoenixGang == PhoenixState.GangsNamed) {
                    access.phoenixGang = PhoenixState.BaraekTold
                }
                chatNpc(
                    neutral,
                    "Okay, to get to the gang hideout, head to the southern gates of the city and " +
                        "then head down the alleyway past the Blue Moon Inn.",
                )
                chatNpc(
                    neutral,
                    "Down there you'll find a building owned by the VTAM corporation. That's " +
                        "where you'll find the Phoenix Gang. Just be careful. They ain't the " +
                        "types to be messed about.",
                )
                chatPlayer(happy, "Thanks!")
            }
            2 -> {
                chatPlayer(neutral, "No, I don't do things like bribery.")
                chatNpc(
                    neutral,
                    "If you wanna deal with the Phoenix Gang, they're involved in much worse than " +
                        "a bit of bribery.",
                )
            }
            3 -> chatPlayer(neutral, "Yes, I'd like to be 20 coins richer too.")
        }
    }

    private suspend fun Dialogue.katrine() {
        when {
            access.phoenixGang >= PhoenixState.Member ->
                chatNpc(
                    angry,
                    "You've got some guts coming here, Phoenix scum! Get lost, or I'll make sure " +
                        "you haven't got those guts anymore!",
                )
            access.blackArmGang >= BlackArmState.Member -> katrineMember()
            access.blackArmGang == BlackArmState.CrossbowTask -> katrineCrossbowReminder()
            else -> katrineIntro()
        }
    }

    private suspend fun Dialogue.katrineIntro() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(neutral, "It's a private business. Can I help you at all?")

        val knowsGangs = access.blackArmGang >= BlackArmState.GangsNamed
        val pick =
            if (knowsGangs) {
                choice3(
                    "I've heard you're the Black Arm Gang.",
                    1,
                    "What sort of business?",
                    2,
                    "I'm looking for fame and riches.",
                    3,
                )
            } else {
                choice2("What sort of business?", 2, "I'm looking for fame and riches.", 3)
            }

        when (pick) {
            1 -> katrineBlackArmAccusation()
            2 -> {
                chatPlayer(quiz, "What sort of business?")
                chatNpc(
                    neutral,
                    "A small, family business. We give financial advice to other companies. Now, " +
                        "if you'll excuse me.",
                )
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for fame and riches.")
                chatNpc(
                    neutral,
                    "And you expect to find that up the back streets of Varrock? Keep dreaming.",
                )
            }
        }
    }

    private suspend fun Dialogue.katrineBlackArmAccusation() {
        chatPlayer(quiz, "I've heard you're the Black Arm Gang.")
        chatNpc(angry, "Who told you that?")

        val source =
            choice3(
                "I'd rather not reveal my sources.",
                1,
                "It was Charlie, the tramp outside.",
                2,
                "Everyone knows. It's no great secret.",
                3,
            )
        when (source) {
            1 -> {
                chatPlayer(neutral, "I'd rather not reveal my sources.")
                chatNpc(
                    quiz,
                    "Hmm... I suppose I can understand that. So, say we were the Black Arm Gang, " +
                        "what would you want with us?",
                )
            }
            2 -> {
                chatPlayer(neutral, "It was Charlie, the tramp outside.")
                chatNpc(
                    neutral,
                    "Is that guy still out there? He's getting to be a nuisance. Might be time " +
                        "for him to have a little accident...",
                )
                chatNpc(
                    quiz,
                    "Anyway, say we were the Black Arm Gang, what would you want with us?",
                )
            }
            3 -> {
                chatPlayer(neutral, "Everyone knows. It's no great secret.")
                chatNpc(shocked, "What?! You must be joking!")
                chatPlayer(
                    neutral,
                    "Oh no, not at all... It's so obvious! Even the town guard have caught on...",
                )
                chatNpc(
                    quiz,
                    "I see... Well... say we were the Black Arm Gang, what would you want with us?",
                )
            }
        }

        val want =
            choice3(
                "I want to become a member of your gang.",
                1,
                "I want some hints on becoming a thief.",
                2,
                "I'm looking for the door out of here.",
                3,
            )
        when (want) {
            1 -> {
                chatPlayer(neutral, "I want to become a member of your gang.")
                katrineRecruitment()
            }
            2 -> {
                chatPlayer(neutral, "I want some hints on becoming a thief.")
                chatNpc(
                    quiz,
                    "Do you now? And what makes you think I'll provide anything like that?",
                )
                chatPlayer(neutral, "Well, how about I become a member of your gang in return?")
                katrineRecruitment()
            }
            3 -> {
                chatPlayer(neutral, "I'm looking for the door out of here.")
                chatNpc(neutral, "Try... the one you just came through?")
            }
        }
    }

    private suspend fun Dialogue.katrineRecruitment() {
        chatNpc(
            confused,
            "How unusual... Okay, let's say we are who you believe we are. Do you think we'd be " +
                "recruiting people who just waltz in here saying 'hello, I'd like to play'?",
        )
        chatPlayer(quiz, "I don't know. What would be the alternative?")
        chatNpc(
            confused,
            "Watching local thugs and thieves in action, perhaps? How do I know I can just trust " +
                "someone who's wandered in here.",
        )

        val tryMe =
            choice2(
                "Well, you can give me a try can't you?",
                true,
                "Well, people tell me I have an honest face.",
                false,
            )
        if (tryMe) {
            chatPlayer(neutral, "Well, you can give me a try can't you?")
            chatNpc(neutral, "I'm not so sure...")
        } else {
            chatPlayer(neutral, "Well, people tell me I have an honest face.")
            chatNpc(
                neutral,
                "Someone honest wanting to join a gang of thieves? Excuse me if I remain " +
                    "unconvinced...",
            )
        }

        chatNpc(
            neutral,
            "Thinking about it... I may have a solution actually. You may have heard of the " +
                "Phoenix Gang. I hear they have a very nice weapons stash not too far from here.",
        )
        chatNpc(
            neutral,
            "We're fresh out of crossbows, but apparently they have plenty. If someone were to " +
                "acquire a couple for us, it would be very much appreciated.",
        )
        chatPlayer(quiz, "Sounds simple enough. Any particular reason you need them?")
        chatNpc(
            shifty,
            "Let's just say there's a local merchant who is refusing to pay some very reasonable " +
                "'keep-your-life-pleasant' insurance rates.",
        )
        chatPlayer(quiz, "So you're planning to murder him?")
        chatNpc(
            shifty,
            "Him? No. However, I hear that the local law might soon be finding a crossbow " +
                "belonging to the Phoenix Gang in his home...",
        )
        chatNpc(
            shifty,
            "I also hear that a similar type of crossbow might soon be used in a murder on the " +
                "other side of the city...",
        )
        chatNpc(
            shifty,
            "Now, are you going to keep asking questions, or are you going to go get those " +
                "crossbows?",
        )

        val accept =
            choice2(
                "Okay, no problem.",
                true,
                "Sounds a little tricky. Got anything easier?",
                false,
            )
        if (accept) {
            chatPlayer(happy, "Okay, no problem.")
            katrineTaskAccepted()
            return
        }

        chatPlayer(quiz, "Sounds a little tricky. Got anything easier?")
        chatNpc(
            neutral,
            "If you're not up for a little bit of danger, I don't think you've got anything to " +
                "offer us.",
        )
        val retry = choice2("Okay, I'll do it.", true, "It's a no from me then.", false)
        if (retry) {
            chatPlayer(neutral, "Okay, I'll do it.")
            katrineTaskAccepted()
        } else {
            chatPlayer(neutral, "It's a no from me then.")
            chatNpc(angry, "Then get lost!")
        }
    }

    private suspend fun Dialogue.katrineTaskAccepted() {
        access.blackArmGang = BlackArmState.CrossbowTask
        chatNpc(
            neutral,
            "Great! Apparently the Phoenix Gang's weapons stash is somewhere to the east. Ask " +
                "that tramp outside. I'm sure he'll know more.",
        )
    }

    private suspend fun Dialogue.katrineCrossbowReminder() {
        chatNpc(quiz, "Have you got those crossbows for me yet?")

        val crossbows = access.invTotal(access.inv, SoaRefs.Crossbow)
        when {
            crossbows >= 2 -> {
                chatPlayer(happy, "Yes, I have.")

                if (access.invDel(access.inv, SoaRefs.Crossbow, 2).failure) {
                    return
                }
                access.blackArmGang = BlackArmState.Member
                access.weaponsmasterDead = 0
                objbox(SoaRefs.Crossbow, 400, "You give the crossbows to Katrine.")

                chatNpc(
                    neutral,
                    "It seems you're more skilled than I originally gave you credit for. Well " +
                        "done. Consider yourself a member of the Black Arm Gang. I'd suggest you " +
                        "now go and familiarise yourself with the rest of the ganghouse.",
                )
                chatPlayer(happy, "Will do! Thanks!")
            }
            crossbows == 1 -> {
                chatPlayer(neutral, "I have one...")
                chatNpc(neutral, "Well, I need two. Come back when you have them.")
            }
            else -> {
                chatPlayer(
                    neutral,
                    "No, I haven't found them yet. Do you know where I should be looking.",
                )
                chatNpc(
                    neutral,
                    "Apparently the Phoenix Gang's weapons stash is somewhere to the east. Ask " +
                        "that tramp outside. I'm sure he'll know more.",
                )
            }
        }
    }

    private suspend fun Dialogue.katrineMember() {
        chatPlayer(neutral, "Hey.")
        chatNpc(neutral, "Hey.")

        val pick =
            choice3(
                "So I'm a part of the gang now?",
                1,
                "Who are all those people in there?",
                2,
                "Teach me to be a top class criminal!",
                3,
            )
        when (pick) {
            1 -> {
                chatPlayer(quiz, "So I'm part of the gang now?")
                if (quest.isQuestCompleted(player)) {
                    chatNpc(
                        neutral,
                        "You are, but don't make me regret it. In case you didn't hear, someone " +
                            "recently stole something very important from us. We need to be more " +
                            "careful about who we let in.",
                    )
                    chatPlayer(neutral, "Imagine doing something like that!")
                } else {
                    chatNpc(
                        neutral,
                        "You are. Feel free to familiarise yourself with the rest of the " +
                            "ganghouse.",
                    )
                    chatPlayer(happy, "Will do! Thanks!")
                }
            }
            2 -> {
                chatPlayer(quiz, "Who are all those people in there?")
                chatNpc(neutral, "They're just various rogues and thieves.")
                chatPlayer(neutral, "They don't say a lot...")
                chatNpc(neutral, "Nope.")
            }
            3 -> {
                chatPlayer(neutral, "Teach me to be a top class criminal.")
                chatNpc(neutral, "Teach yourself.")
            }
        }
    }

    private suspend fun Dialogue.straven() {
        when {
            access.blackArmGang >= BlackArmState.Member ->
                chatNpc(angry, "Stay away from here! Black Arm dog!")
            access.phoenixGang >= PhoenixState.Member -> stravenMember()
            access.phoenixGang == PhoenixState.JonnyTask -> stravenMissionCheck()
            else -> stravenOutsider()
        }
    }

    suspend fun stravenOutsiderApproach(dialogue: Dialogue): Unit =
        with(dialogue) {
            when {
                access.blackArmGang >= BlackArmState.Member ->
                    chatNpc(angry, "Stay away from here! Black Arm dog!")
                access.phoenixGang == PhoenixState.JonnyTask -> stravenMissionCheck()
                else -> stravenOutsiderRefusal()
            }
        }

    private suspend fun Dialogue.stravenOutsider() {
        chatPlayer(quiz, "What's through that door?")
        stravenOutsiderRefusal()
    }

    private suspend fun Dialogue.stravenOutsiderRefusal() {
        chatNpc(
            neutral,
            "You can't go in there. Only authorised personnel of the VTAM Corporation are " +
                "allowed beyond this point.",
        )

        val recruitable =
            quest.isQuestInProgress(player) &&
                access.phoenixGang in PhoenixState.GangsNamed until PhoenixState.JonnyTask &&
                access.blackArmGang < BlackArmState.CrossbowTask

        val pick =
            if (recruitable) {
                choice3(
                    "I know who you are!",
                    1,
                    "How do I get a job with the VTAM corporation?",
                    2,
                    "Fair enough.",
                    3,
                )
            } else {
                choice2("How do I get a job with the VTAM corporation?", 2, "Fair enough.", 3)
            }

        when (pick) {
            1 -> stravenRecruitment()
            2 -> {
                chatPlayer(quiz, "How do I get a job with the VTAM corporation?")
                chatNpc(
                    neutral,
                    "Get a copy of the Varrock Herald. If we have any positions right now, " +
                        "they'll be advertised in there.",
                )
            }
            3 -> chatPlayer(quiz, "Fair enough.")
        }
    }

    private suspend fun Dialogue.stravenRecruitment() {
        chatPlayer(neutral, "I know who you are!")
        chatNpc(quiz, "Really? Well? Who are we then?")
        chatPlayer(
            neutral,
            "This is the headquarters of one of the most dangerous crime syndicates this city " +
                "has ever seen! The Phoenix Gang!",
        )
        chatNpc(
            neutral,
            "No, this is a legitimate business run by legitimate individuals. However... " +
                "supposing we were this crime gang, what would you want with us?",
        )

        val offer =
            choice2(
                "I'd like to offer you my services.",
                true,
                "I want nothing. I was just making sure you were them.",
                false,
            )
        if (!offer) {
            chatPlayer(neutral, "I want nothing. I was just making sure you were them.")
            chatNpc(neutral, "Well then get lost and stop wasting my time.")
            return
        }

        chatPlayer(neutral, "I'd like to offer you my services.")
        chatNpc(
            neutral,
            "You mean you'd like to join the Phoenix Gang? Well, obviously I can't speak for " +
                "them, but the Phoenix Gang doesn't let people join just like that. First, you'd " +
                "need to prove your loyalty.",
        )
        chatPlayer(quiz, "How would I go about doing that?")
        chatNpc(
            neutral,
            "Obviously, I would have no idea. Although, having said that, a rival gang of ours, " +
                "er, theirs, called the Black Arm Gang is supposedly meeting a contact from Port " +
                "Sarim today.",
        )
        chatNpc(
            neutral,
            "Apparently the meeting is going to take place in the Blue Moon Inn, and supposedly " +
                "the contact is none other than the notorious mass murderer turned pirate " +
                "captain, Jonny the Beard.",
        )

        access.phoenixGang = PhoenixState.JonnyTask

        chatNpc(
            neutral,
            "Now, obviously I know nothing about the dealings of the Phoenix Gang, but I bet if " +
                "somebody were to arrange an accident for Jonny and bring back his intelligence " +
                "report, the gang would be very pleased.",
        )
        chatPlayer(
            neutral,
            "I see... I'm sure I can help sort something out for them. I'll get right on it.",
        )
    }

    private suspend fun Dialogue.stravenMissionCheck() {
        chatNpc(quiz, "How's your little mission going?")

        if (!access.soaCarries(SoaRefs.IntelReport)) {
            if (access.bank.contains(SoaRefs.IntelReport)) {
                chatPlayer(neutral, "I have the intelligence report!")
                chatNpc(neutral, "Let's see it then.")
                chatPlayer(neutral, "Let me go get it from the bank.")
                chatNpc(neutral, "I'll wait here.")
                return
            }
            chatPlayer(sad, "I don't have it yet...")
            chatNpc(
                neutral,
                "Jonny the Beard is the person who has it. I imagine he will be in the Blue Moon " +
                    "Inn... Not that I'd know for sure, with me definitely not being a member of " +
                    "the Phoenix Gang and all.",
            )
            return
        }

        chatPlayer(happy, "I have the intelligence report!")
        chatNpc(neutral, "Let's see it then.")

        // Commit the item hand-in and stage together before suspending for the object dialogue.
        if (access.invDel(access.inv, SoaRefs.IntelReport, 1).failure) {
            return
        }

        access.phoenixGang = PhoenixState.Member

        objbox(SoaRefs.IntelReport, "You show the report to Straven.")

        chatNpc(
            neutral,
            "Very good... Very good... Okay, I'm satisfied. Welcome to the Phoenix Gang! I'm " +
                "Straven, one of the gang leaders.",
        )
        chatPlayer(happy, "Nice to meet you. I'm ${player.displayName}.")
        chatNpc(
            neutral,
            "As a member of our gang, you now have access to our hideout and our weapons supply " +
                "depot. The hideout is just through this door and the depot is two doors down " +
                "from here. You'll need this key to get in.",
        )

        access.invAddOrDrop(objRepo, SoaRefs.StoreKey)
        objbox(SoaRefs.StoreKey, "Straven hands you a key.")

        chatNpc(neutral, "Once again, welcome to the Phoenix Gang!")
    }

    private suspend fun Dialogue.stravenMember() {
        val keyLost =
            !access.soaCarries(SoaRefs.StoreKey) && !access.bank.contains(SoaRefs.StoreKey)

        chatNpc(neutral, "Greetings, fellow gang member.")

        if (keyLost) {
            chatPlayer(sad, "I'm afraid I lost that key you gave me...")
            chatNpc(
                neutral,
                "You really need to be more careful. We don't want that key falling into the " +
                    "wrong hands. Have this spare, but don't lose it this time!",
            )
            access.invAddOrDrop(objRepo, SoaRefs.StoreKey)
            objbox(SoaRefs.StoreKey, "Straven hands you a key.")
            return
        }

        val pick =
            choice4(
                "I've heard you've got some cool treasures in this place.",
                1,
                "Any suggestions for where I can go thieving?",
                2,
                "Where's the Black Arm Gang hideout?",
                3,
                "I'd better get going.",
                4,
            )
        when (pick) {
            1 -> stravenTreasures()
            2 -> {
                chatPlayer(quiz, "Any suggestions for where I can go thieving?")
                chatNpc(
                    neutral,
                    "You could always try the marketplace over in Ardougne. Lots of opportunity " +
                        "there!",
                )
            }
            3 -> {
                chatPlayer(quiz, "Where's the Black Arm Gang hideout?")
                chatNpc(
                    neutral,
                    "Interested in spying on the enemy, are you? It will be tricky. Their " +
                        "security is pretty good. Not as good as ours obviously, but still good.",
                )
                access.soaStravenTrampChat = 1
                chatNpc(
                    neutral,
                    "Still, if you really want to go there, it's down an alleyway near the " +
                        "southern gate. One of our contacts, a tramp going by the name of " +
                        "Charlie, keeps an eye on it. He may be able to help you.",
                )
                chatPlayer(happy, "I see. Thanks for the help!")
            }
            4 -> chatPlayer(neutral, "I'd better get going.")
        }
    }

    private suspend fun Dialogue.stravenTreasures() {
        chatPlayer(quiz, "I've heard you've got some cool treasures in this place.")
        chatNpc(
            neutral,
            "Oh yeah, we've all stolen some stuff in our time. Our candlesticks down here, for " +
                "example, were quite a challenge to get out of the palace.",
        )
        chatPlayer(quiz, "And the Shield of Arrav?")

        if (quest.isQuestCompleted(player)) {
            chatNpc(neutral, "Once one of our most prized possessions.")
            chatPlayer(quiz, "Once?")
            access.soaStravenShieldChat = 1
            chatNpc(
                neutral,
                "It seems the Black Arm Gang weren't content with having one half. They recently " +
                    "stole the other half of the shield from us as well!",
            )
            chatPlayer(
                neutral,
                "Oh no! I'll be sure to keep an eye out for any of their members sneaking around.",
            )
            return
        }

        chatNpc(neutral, "Woah... that's a blast from the past! We stole that years and years ago!")
        chatNpc(
            neutral,
            "Truth be told, we don't even have the whole shield anymore. A few years ago there " +
                "was a big fight within the gang and the shield got broken in half as a result.",
        )
        chatNpc(
            neutral,
            "After the fight, some gang members split off and formed their own group. They took " +
                "one half of the shield with them.",
        )
        chatPlayer(quiz, "This other group? The Black Arm Gang?")
        chatNpc(neutral, "Indeed.")
    }

    private companion object {
        private const val BearFur = "obj.fur"
    }
}
