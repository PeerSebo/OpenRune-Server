package org.rsmod.content.quest.area.varrock

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.ScriptContext

// Bind the spawned multinpc parent and its visible leaf.
internal class RomeoAndJulietDialogue(
    private val quest: Quest,
    private val cutscenes: RomeoAndJulietCutscenes,
) {

    fun ScriptContext.register() {
        onOpNpc1("npc.romeo") { startDialogue(it.npc) { romeo(it.npc) } }

        onOpNpc1("npc.juliet_multi_visible") { startDialogue(it.npc) { juliet() } }
        onOpNpc1("npc.juliet") { startDialogue(it.npc) { juliet() } }

        onOpNpc1("npc.phillipa") { startDialogue(it.npc) { phillipa() } }
        onOpNpc1("npc.draul_leptoc") { startDialogue(it.npc) { draul() } }

        onOpNpcU("npc.romeo", "obj.julietmessage") {
            startDialogue(it.npc) {
                chatNpc(
                    quiz,
                    "Why are you waving that around at me as if it's something important?",
                )
            }
        }
    }

    internal suspend fun ProtectedAccess.romeoApproaches(npc: Npc) {
        mes("Romeo approaches you...")
        startDialogue(npc) { romeoUnstarted() }
    }

    private suspend fun Dialogue.romeo(npc: Npc) {
        when (quest.getQuestStage(player)) {
            RomeoAndJulietStage.NotStarted -> romeoUnstarted()
            RomeoAndJulietStage.FindJuliet -> romeoAwaitingJuliet()
            RomeoAndJulietStage.DeliverMessage -> romeoMessage()
            RomeoAndJulietStage.FindFatherLawrence -> romeoAfterMessageOptions()
            RomeoAndJulietStage.FindApothecary -> romeoAfterLawrence()
            RomeoAndJulietStage.CadavaPotion -> romeoAwaitingPotion()
            RomeoAndJulietStage.TellRomeo -> romeoToTheCrypt(npc)
            else -> romeoAfterQuest(npc)
        }
    }

    private suspend fun Dialogue.romeoUnstarted() {
        val opening =
            when (access.random.of(maxExclusive = 6)) {
                0 -> "Blub! Blub...where is my Juliet? Have you seen her?"
                1 ->
                    "Looking for a blonde girl, goes by the name of Juliet..quite pretty..." +
                        "haven't seen her have you?"
                2 -> "Juliet, Juliet, wherefore art thou Juliet? Have you seen my Juliet?"
                3 -> "Oh woe is me that I cannot find my Juliet! You haven't seen Juliet have you?"
                4 ->
                    "Sadness surrounds me now that Juliet's father forbids us to meet. Have you " +
                        "seen my Juliet?"
                else ->
                    "What is to become of me and my darling Juliet, I cannot find her anywhere, " +
                        "have you seen her?"
            }
        chatNpc(sad, opening)

        when (
            choice3(
                "Yes, I have seen her actually!",
                1,
                "No sorry, I haven't seen her.",
                2,
                "Perhaps I could help to find her for you?",
                3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, I have seen her actually!")
                chatPlayer(shifty, "At least, I think it was her... Blonde? A bit stressed?")
                chatNpc(laugh, "Golly...yes, yes...you make her sound very interesting!")
                chatNpc(shifty, "And I'll bet she's a bit of a fox!")
                chatPlayer(shifty, "Well, I guess she could be considered attractive...")
                chatNpc(laugh, "I'll bet she is! <br>Wooooooooo!")
                chatNpc(
                    quiz,
                    "Sorry, all that jubilation has made me forget what we were talking about.",
                )
                chatPlayer(bored, "You were asking me about Juliet? You seemed to know her?")
                chatNpc(laugh, "Oh yes, Juliet!")
            }
            2 -> {
                chatPlayer(neutral, "No sorry, I haven't seen her.")
                chatNpc(sad, "Oh...well, that's a shame...I was rather hoping you had.")
                chatPlayer(
                    quiz,
                    "Why? <br>Is she a fugitive? Does she owe you some money or something?",
                )
                chatNpc(quiz, "Hmmm, she might do? Perhaps she does? How do you know?")
                chatPlayer(angry, "I don't know? I was asking 'YOU' how 'YOU' know Juliet!")
                chatNpc(
                    happy,
                    "Ahh, yes Juliet, she's my one true love. Well, one of my one true loves!",
                )
            }
            else -> {
                chatPlayer(quiz, "Perhaps I can help find her for you? What does she look like?")
                chatNpc(happy, "Oh would you? That would be great! She has this sort of hair...")
                chatPlayer(bored, "Hair...check..")
                chatNpc(happy, "...and she these...great lips...")
                chatPlayer(bored, "Lips...right.")
                chatNpc(happy, "Oh and she has these lovely shoulders as well..")
                chatPlayer(
                    bored,
                    "Shoulders...right, so she has hair, lips and shoulders...that should cut it " +
                        "down a bit.",
                )
                chatNpc(happy, "Oh yes, Juliet is very different...")
            }
        }

        chatNpc(
            happy,
            "The fox...could you tell her that she is the love of my long and that I life to be " +
                "with her?",
        )
        chatPlayer(
            quiz,
            "What? <br>Surely you mean that she is the love of your life and that you long to be " +
                "with her?",
        )
        chatNpc(
            happy,
            "Oh yeah...what you said...tell her that, it sounds much better! <br>Oh you're so " +
                "good at this!",
        )

        val start = choice2("Yes.", true, "No.", false, title = "Start the Romeo & Juliet quest?")
        if (!start) {
            chatPlayer(neutral, "Sorry Romeo, I've got better things to do right now. Maybe later?")
            chatNpc(
                happy,
                "Oh, okay, well, I guess my Juliet and I can spend some time apart. And as the " +
                    "old saying goes, 'Absinthe makes the heart glow longer'.",
            )
            chatPlayer(quiz, "Don't you mean that, 'Absence makes the...")
            chatPlayer(bored, "Actually forget it...")
            chatNpc(happy, "Okay!")
            return
        }

        chatPlayer(bored, "Yes, okay. I'll let her know.")
        chatNpc(happy, "Oh great! And tell her that I want to kiss her a give.")

        quest.setQuestStage(access, RomeoAndJulietStage.FindJuliet)

        chatPlayer(neutral, "You mean you want to give her a kiss!")
        chatNpc(quiz, "Oh you're good...you are good!")
        chatNpc(happy, "I see I've picked a true professional...!")
        romeoJulietOptions()
    }

    private suspend fun Dialogue.romeoAwaitingJuliet() {
        chatPlayer(happy, "Hello again, remember me?")
        chatNpc(quiz, "Of course, yes....how are.. you....ermmm...")
        chatPlayer(bored, "You haven't got a clue who I am do you?")
        chatNpc(
            happy,
            "Not a clue my friend, but you seem to have a friendly face...a little blood stained, " +
                "and perhaps in need of a wash, but friendly none the less.",
        )
        chatPlayer(angry, "You asked me to look for Juliet for you!")
        chatNpc(happy, "Ah yes, Juliet...my sweet darling...what news?")
        chatPlayer(neutral, "Nothing so far, but I need to ask a few questions?")
        romeoJulietOptions()
    }

    private suspend fun Dialogue.romeoJulietOptions() {
        when (
            choice3(
                "Where can I find Juliet?",
                1,
                "Is there anything else you can tell me about Juliet?",
                2,
                "Ok, thanks.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Where can I find Juliet?")
                chatNpc(quiz, "Why do you ask?")
                chatPlayer(neutral, "So that I can try and find her for you!")
                chatNpc(happy, "Ah yes....quite right. Hmmm, let me think now.")
                chatNpc(
                    happy,
                    "She may still be locked away at her Father's house on the sest vide of " +
                        "Warrock.",
                )
                chatNpc(
                    happy,
                    "Oh, I remember how she loved it when I would sing up to her balcony! She " +
                        "would reward me with her own personal items...",
                )
                chatPlayer(quiz, "What, she just gave you her stuff?")
                chatNpc(
                    laugh,
                    "Well, not exactly give...more like 'throw with considerable force'...she's " +
                        "always a kidder that Juliet!",
                )
                romeoJulietOptions()
            }
            2 -> {
                chatPlayer(quiz, "Is there anything else you can tell me about Juliet?")
                chatNpc(
                    happy,
                    "Oh, there is so much to tell...she is my true love, we intend to spend " +
                        "together forever...I can tell you so much about her..",
                )
                chatPlayer(happy, "Great!")
                chatNpc(quiz, "Ermmm.....")
                chatNpc(happy, "So much can I tell you...")
                chatPlayer(neutral, "Yes..")
                chatNpc(happy, "So much to tell...why, where do I start!")
                chatPlayer(happy, "Yes..yes! Please go on...don't let me interrupt...")
                chatNpc(quiz, "Ermmm.....")
                chatNpc(quiz, "...")
                chatPlayer(bored, "You can't remember can you?")
                chatNpc(sad, "Not a thing sorry....")
                romeoJulietOptions()
            }
            else -> chatPlayer(bored, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.romeoMessage() {
        if (!access.carryingObj("obj.julietmessage")) {
            chatPlayer(happy, "Romeo...great news...I've been in touch with Juliet!")
            chatNpc(
                happy,
                "Oh great! That is great news! Well done...well done... what a total success!",
            )
            chatPlayer(happy, "Yes, and she gave me a message to give you...")
            chatNpc(happy, "Ohhh great! A message....wow!")
            chatPlayer(happy, "Yes!")
            chatNpc(
                happy,
                "A message...oh, I can't wait to read what my dear Juliet has to say....",
            )
            chatPlayer(happy, "I know...it's exciting isn't it...?")
            chatNpc(happy, "Yes...yes...")
            chatNpc(quiz, "...")
            chatNpc(sad, "You've lost the message haven't you?")
            chatPlayer(bored, "Yep, haven't got a clue where it is.")
            return
        }

        chatPlayer(
            happy,
            "Romeo...great news...I've been in touch with Juliet!<br>She's written a message for " +
                "you...",
        )
        access.invDel(access.inv, "obj.julietmessage")
        objbox("obj.julietmessage", "You hand over Juliet's message to Romeo.")

        chatNpc(happy, "Oh, a message! A message! I've never had a message before...")
        chatPlayer(quiz, "Really?")
        chatNpc(happy, "No, no, not one!")
        chatNpc(quiz, "Oh, well, except for the occasional court summons.")
        chatNpc(
            happy,
            "But they're not really 'nice' messages. Not like this one!<br>I'm sure that this " +
                "message will be lovely.",
        )
        chatPlayer(quiz, "Well are you going to open it or not?")

        chatNpc(
            happy,
            "Oh yes, yes, of course! <br>'Dearest Romeo, I am very pleased that you sent " +
                "${player.displayName} to look for me and to tell me that you still hold " +
                "affliction...', Affliction! She thinks I'm diseased?",
        )
        chatPlayer(bored, "'Affection?'")
        chatNpc(
            happy,
            "Ahh yes...'still hold affection for me. I still feel great affection for you, but " +
                "unfortunately my Father opposes our marriage.'",
        )
        chatPlayer(sad, "Oh dear...that doesn't sound too good.")
        chatNpc(happy, "What? '...great affection for you. Father opposes our..")
        chatNpc(sad, "'...marriage and will...")
        chatNpc(shocked, "...will kill you if he sees you again!'")
        chatPlayer(shifty, "I have to be honest, it's not getting any better...")
        chatNpc(
            sad,
            "'Our only hope is that Father Lawrence, our long time confidant, can help us in some " +
                "way.'",
        )
        objbox("obj.julietmessage", "Romeo folds the message away.")

        chatNpc(sad, "Well, that's it then...we haven't got a chance...")
        chatPlayer(quiz, "What about Father Lawrence?")
        chatNpc(sad, "...our love is over...the great romance, the life of my love...")
        chatPlayer(quiz, "...or you could speak to Father Lawrence!")
        chatNpc(
            sad,
            "Oh, my aching, breaking, heart...how useless the situation is now...we have no one " +
                "to turn to...",
        )
        chatPlayer(angry, "FATHER LAWRENCE!")
        chatNpc(shocked, "Father Lawrence?")

        quest.setQuestStage(access, RomeoAndJulietStage.FindFatherLawrence)

        chatNpc(
            happy,
            "Oh yes, Father Lawrence...he's our long time confidant, he might have a solution! " +
                "Yes, yes, you have to go and talk to Lather Fawrence for us and ask him if he's " +
                "got any suggestions for our predicament?",
        )
        romeoWhereIsLawrence()
        romeoLawrenceOptions()
    }

    private suspend fun Dialogue.romeoAfterMessageOptions() {
        chatPlayer(happy, "Hey again Romeo!")
        romeoLawrenceOptions()
    }

    private suspend fun Dialogue.romeoWhereIsLawrence() {
        chatPlayer(quiz, "Where can I find Father Lawrence?")
        chatNpc(happy, "Lather Fawrence! Oh he's...")
        chatNpc(shifty, "You know he's not my 'real' Father don't you?")
        chatPlayer(bored, "I think I suspected that he wasn't.")
        chatNpc(
            happy,
            "Well anyway...he tells these song, loring bermons...and keeps these here Carrockian " +
                "vitizens snoring in his church to the East North.",
        )
    }

    private suspend fun Dialogue.romeoLawrenceOptions() {
        when (
            choice4(
                "How are you?",
                1,
                "Where can I find Father Lawrence?",
                2,
                "Have you heard anything from Juliet?",
                3,
                "Ok, thanks.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "How are you?")
                chatNpc(sad, "Not so good my friend...I miss Judi..., Junie..., Joopie...")
                chatPlayer(quiz, "Juliet?")
                chatNpc(sad, "Juliet! I miss Juliet, terribly!")
                chatPlayer(bored, "Hmmm, so I see!")
                romeoLawrenceOptions()
            }
            2 -> {
                romeoWhereIsLawrence()
                romeoLawrenceOptions()
            }
            3 -> {
                chatPlayer(quiz, "Have you heard anything from Juliet?")
                chatNpc(
                    sad,
                    "Sadly not my friend! And what's worse, her Father has threatened to kill me " +
                        "if he sees me. I mean, that seems a bit harsh!",
                )
                chatPlayer(
                    bored,
                    "Well, I shouldn't worry too much...you can always run away if you see him...",
                )
                chatNpc(
                    worried,
                    "I just wish I could remember what he looks like! I live in fear of every man " +
                        "I see!",
                )
                romeoLawrenceOptions()
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.romeoAfterLawrence() {
        chatPlayer(happy, "Hello again Romeo!")
        chatNpc(
            laugh,
            "Ooh did you manage to survive one of Lather Fawrences sermons? I bet not, you were " +
                "ages! I bet you snoozed on the welcome mat just as soon as you heard his voice!",
        )
        chatPlayer(angry, "Did not!")
        chatNpc(quiz, "Oh, come on, come on, what did he say?")
        romeoApothecaryOptions()
    }

    private suspend fun Dialogue.romeoApothecaryOptions() {
        when (
            choice3(
                "He wants me to go to the Apothecary!",
                1,
                "He seems keen for you to marry Juliet.",
                2,
                "Ok, thanks.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(happy, "He wants me to go to the Apothecary!")
                chatNpc(quiz, "The Apothecary?")
                chatNpc(quiz, "Oh...is he the one who mixes up all them magical potion- ey things?")
                chatPlayer(bored, "Yeah, I think so...but the word potion-ey doesn't exist.")
                chatNpc(happy, "Well, you just used it...so I guess it does exist!")
                chatPlayer(bored, "It doesn't matter...do you know where the Apothecary is?")
                chatNpc(quiz, "Why should I tell you?")
                chatPlayer(angry, "Because I'm doing you a favour!")
                chatNpc(
                    happy,
                    "Right, yes...of course! Well, I think the potion-ey place is Wouth Sest of " +
                        "here and near a sword shop.",
                )
                romeoApothecaryOptions()
            }
            2 -> {
                chatPlayer(neutral, "He seems very keen for you to marry Juliet.")
                chatNpc(happy, "Me too! I can't wait! Do you think it will be soon?")
                chatPlayer(
                    quiz,
                    "I don't know, but I'll do what I can. It just seems a bit odd that Father " +
                        "Lawrence is so keen for you to marry?",
                )
                chatNpc(
                    quiz,
                    "I can't imagine why he's so keen? Though, he was our messenger before you. " +
                        "Of course, he had a lot more hair back then...and I think we may have " +
                        "gone to school together...",
                )
                chatPlayer(quiz, "What, since you were kids?")
                chatNpc(
                    laugh,
                    "Yeah! Thinking about it now, we called him 'Diddy Dorrence' back then!",
                )
                chatPlayer(
                    quiz,
                    "So, maybe, he just wants a bit of peace? And when you two are married, he " +
                        "won't have to traipse around delivering your messages anymore?",
                )
                chatNpc(sad, "Yes, the years haven't been good to him.")
                romeoApothecaryOptions()
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.romeoAwaitingPotion() {
        if (!access.carryingObj("obj.cadava")) {
            chatPlayer(happy, "Hi Romeo!")
            chatNpc(quiz, "Oh hello, have you seen Lather Fawrence?")
            chatPlayer(
                neutral,
                "Yes, he's given me details of a potion which should help resolve this situation. " +
                    "The Apothecary is helping me prepare it.",
            )
            chatNpc(worried, "Ooooh, it all sounds horribly complicated?")
            chatNpc(happy, "All I can say is I'll be glad when Juliet's finally in that crypt!")
            chatPlayer(bored, "Spoken like a true lover!")
            return
        }

        objbox("obj.cadava", "Romeo spots the Cadava potion.")
        chatNpc(shifty, "Ooohh, that's the potion is it? Rather you than me!")
        chatPlayer(angry, "I'm not drinking it! It's for Juliet!")
        chatNpc(
            happy,
            "Well, I mean...it's probably delicious...and not dangerous at all. And you quite " +
                "often see harmless drinks in a glowing sort of pink colour.",
        )
        chatPlayer(
            shifty,
            "Hmm, I'm not convinced...maybe you ought to try some before I give it to Juliet?",
        )
        chatNpc(shocked, "Urgh! No way! <br>Not in a million years!")
        chatPlayer(quiz, "Ok...but you know what to do when I've given this to Juliet?")
        chatNpc(happy, "Oh, yeah...sure Lather Fawrence explained all that to me...")
    }

    private suspend fun Dialogue.romeoToTheCrypt(npc: Npc) {
        chatPlayer(
            happy,
            "Romeo, it's all set. Juliet has drunk the potion and has been taken down into the " +
                "Crypt...now you just need to pop along and collect her.",
        )
        chatNpc(happy, "Ah right, the potion! Great...")
        chatNpc(confused, "What potion would that be then?")
        chatPlayer(
            neutral,
            "The Cadava potion...you know, the one which will make her appear dead! She's in the " +
                "crypt, pop along and claim your true love.",
        )
        chatNpc(worried, "But I'm scared...will you come with me?")
        chatPlayer(
            angry,
            "Oh, ok...come on! I think I saw the entrance when I visited there last...",
        )

        cutscenes.cryptRescue(access)
    }

    private suspend fun Dialogue.romeoAfterQuest(npc: Npc) {
        chatNpc(sad, "I heard Juliet had died. Terrible business.")

        // Set the heart graphic before opening the dialogue page; chatNpc suspends.
        npc.spotanim(ROMEO_HEART_SPOTANIM)

        chatNpc(happy, "Her cousin and I are getting on well though. Thanks for your help.")
    }

    private suspend fun Dialogue.juliet() {
        when (quest.getQuestStage(player)) {
            RomeoAndJulietStage.NotStarted -> julietUnstarted()
            RomeoAndJulietStage.FindJuliet -> julietFirstMeeting()
            RomeoAndJulietStage.DeliverMessage -> julietAwaitingDelivery()
            RomeoAndJulietStage.FindFatherLawrence -> julietAfterMessage()
            RomeoAndJulietStage.FindApothecary -> julietAfterLawrence()
            RomeoAndJulietStage.CadavaPotion -> julietPotion()
            else -> julietAfterQuest()
        }
    }

    private suspend fun Dialogue.julietUnstarted() {
        chatPlayer(quiz, "Hello.")
        chatNpc(
            sad,
            "Oh...hello. Forgive me, I am not myself. My father keeps me here and I am parted " +
                "from the one I love.",
        )
        chatPlayer(bored, "That sounds like a job for somebody else.")
    }

    private suspend fun Dialogue.julietFirstMeeting() {
        chatPlayer(
            neutral,
            "Juliet, I come from Romeo. <br>He begs me to tell you that he cares still.",
        )
        chatNpc(
            happy,
            "Oh how my heart soars to hear this news! Please take this message to him with great " +
                "haste.",
        )
        chatPlayer(neutral, "Well, I hope it's good news...he was quite upset when I left him.")
        chatNpc(
            quiz,
            "He's quite often upset...the poor sensitive soul. But I don't think he's going to " +
                "take this news very well, however, all is not lost.",
        )
        chatNpc(
            happy,
            "Everything is explained in the letter, would you be so kind and deliver it to him " +
                "please?",
        )
        chatPlayer(happy, "Certainly, I'll do so straight away.")
        chatNpc(happy, "Many thanks! Oh, I'm so very grateful. You may be our only hope.")

        quest.setQuestStage(access, RomeoAndJulietStage.DeliverMessage)
        access.invAdd(access.inv, "obj.julietmessage", 1)
        objbox("obj.julietmessage", "Juliet gives you a message.")
    }

    private suspend fun Dialogue.julietAwaitingDelivery() {
        chatPlayer(happy, "Hello Juliet!")
        chatNpc(
            quiz,
            "Hello there...have you delivered the message to Romeo yet? What news do you have " +
                "from my loved one?",
        )

        if (access.carryingObj("obj.julietmessage")) {
            chatPlayer(sad, "Oh, sorry, I've not had chance to deliver it yet!")
            chatNpc(
                sad,
                "Oh, that's a shame. I've been waiting so patiently to hear some word " +
                    "from him.",
            )
            return
        }

        chatPlayer(shifty, "Hmmm, that's the thing about messages...they're so easy to misplace...")
        chatNpc(
            angry,
            "How could you lose that message? It was incredibly important...and it took me an age " +
                "to write! I used joined up writing and everything!",
        )
        chatNpc(neutral, "Please, take this new message to him, and please don't lose it.")
        access.invAdd(access.inv, "obj.julietmessage", 1)
        objbox("obj.julietmessage", "Juliet gives you another message.")
    }

    private suspend fun Dialogue.julietAfterMessage() {
        chatPlayer(
            neutral,
            "Hi Juliet, I have passed your message on to Romeo..he's scared half out of his wits " +
                "at the news that your father wants to kill him.",
        )
        chatNpc(
            worried,
            "Yes, unfortunately my father is quite the hunter, you may have seen some the animal " +
                "head trophies on the wall. And it would be so awful to see Romeo's head up there " +
                "with them!",
        )
        chatPlayer(neutral, "I know what you mean...")
        chatPlayer(laugh, "...his hair colour will clash terribly with the rest of the decoration.")
        chatNpc(angry, "That's not what I was suggesting at all...")
        chatPlayer(happy, "I know, I know...I was just kidding.")
        chatPlayer(
            happy,
            "Anyway, don't worry because I'm on the case. I'm going to get some help from Father " +
                "Lawrence.",
        )
        chatNpc(
            happy,
            "Oh yes, I'm sure that Father Lawrence will come up with a solution. I hope you find " +
                "him soon.",
        )
    }

    private suspend fun Dialogue.julietAfterLawrence() {
        chatPlayer(
            happy,
            "Hi Juliet, I've found Father Lawrence and he has a cunning plan. But for it to work, " +
                "I need to seek the Apothecary!",
        )
        chatNpc(
            happy,
            "Oh good! I knew Father Lawrence would come up with something. However, I don't know " +
                "where the apothecary is...I hope you find him soon. My father's temper gets no " +
                "better.",
        )
    }

    private suspend fun Dialogue.julietPotion() {
        if (!access.carryingObj("obj.cadava")) {
            chatPlayer(happy, "Hi Juliet!")
            chatNpc(
                happy,
                "Hi ${player.displayName}, how close am I to being with my true love Romeo?",
            )
            chatPlayer(sad, "Sorry, I still have to get a special potion for you.")
            chatNpc(
                happy,
                "Oh, I hope it isn't a love potion because you would be wasting your time. My " +
                    "love for Romeo grows stronger every minute...",
            )
            chatPlayer(bored, "That must be because you're not with him...")
            chatNpc(sad, "Oh no! I long to be close to my true love Romeo!")
            chatPlayer(
                happy,
                "Well, ok then...I'll set about getting this potion as quickly as I can!",
            )
            chatNpc(happy, "Fair luck to you, the end is close.")
            return
        }

        chatPlayer(
            happy,
            "Hi Juliet! I have an interesting proposition for you...suggested by Father Lawrence. " +
                "It may be the only way you'll be able to escape from this house and be with Romeo.",
        )
        chatNpc(shifty, "Go on....")
        chatPlayer(
            quiz,
            "I have a Cadava potion here, suggested by Father Lawrence. If you drink it, it will " +
                "make you appear dead!",
        )
        chatNpc(angry, "Yes...")
        chatPlayer(
            quiz,
            "And when you appear dead...your still and lifeless corpse will be removed to the " +
                "crypt!",
        )
        chatNpc(happy, "Oooooh, a cold dark creepy crypt...")
        chatNpc(angry, "...sounds just peachy!")
        chatPlayer(
            quiz,
            "Then...Romeo can steal into the crypt and rescue you just as you wake up!",
        )
        chatNpc(angry, "...and this is the great idea for getting me out of here?")
        chatPlayer(
            quiz,
            "To be fair, I can't take all the credit...in fact...it was all Father Lawrence's " +
                "suggestion...",
        )
        chatNpc(angry, "Ok...if this is the best we can do...hand over the potion!")

        access.invDel(access.inv, "obj.cadava")
        objbox("obj.cadava", "You pass the suspicious potion to Juliet.")

        chatNpc(angry, "Wonderful! I just hope Romeo can remember to get me from the crypt.")
        chatNpc(
            confused,
            " Please go to Romeo and make sure he understands. Although I love his gormless, " +
                "lovelorn soppy ways, he can be a bit dense sometimes and I don't want to wake up " +
                "in that crypt on my own.",
        )

        cutscenes.julietDrinks(access)
    }

    private suspend fun Dialogue.julietAfterQuest() {
        chatPlayer(quiz, "Hello Juliet. How was the crypt?")
        chatNpc(angry, "Cold. Dark. Creepy. And I woke up entirely alone in it, thank you.")
        chatPlayer(worried, "Romeo did come. He just...got distracted.")
        chatNpc(
            verymad,
            "Distracted! By my own cousin! I have had a great deal of time to think about Romeo, " +
                "and I have decided that I was very much better off asleep.",
        )
        chatPlayer(bored, "That's probably fair.")
    }

    private suspend fun Dialogue.phillipa() {
        val stage = quest.getQuestStage(player)
        when {
            stage >= RomeoAndJulietStage.TellRomeo -> {
                chatNpc(happy, "Oh, hello again! How was I - do you think I was convincing?")
                chatPlayer(happy, "Oh, yes, totally!")
                chatNpc(
                    happy,
                    "Ohhh good! I do hope Juliet will be pleased as well! That dashing young " +
                        "Romeo must be beside himself with excitement! Have you told him the good " +
                        "news yet?",
                )
                chatPlayer(happy, "Not yet, but soon!")
                chatNpc(
                    laugh,
                    "Good, good - you lucky thing, I can't wait to see the look on his face!",
                )
            }
            stage >= RomeoAndJulietStage.DeliverMessage -> {
                chatNpc(
                    happy,
                    "Oh, hello. Juliet has told me what you're doing for her and Romeo, and I " +
                        "have to say I'm very grateful to you. Juliet deserves a bit of happiness " +
                        "in her life.",
                )
                chatNpc(
                    laugh,
                    "And I'm sure Romeo is just the sort of jester to make her laugh out loud - " +
                        "hysterically you might say.",
                )
                chatNpc(
                    happy,
                    "He always brings a tear to my eyes - tears of happiness at his foolish " +
                        "antics!",
                )
                chatPlayer(happy, "Oh, thanks. I like to do my cupid bit.")
            }
            else -> {
                chatPlayer(happy, "Hello.")
                chatNpc(
                    happy,
                    "Hi, I'm Phillipa, Juliet's cousin. I like to keep an eye on her, make sure " +
                        "that dashing young Romeo doesn't just steal her away under our plain old " +
                        "noses!",
                )
                chatNpc(
                    laugh,
                    "He'd do it, you know - he's ever so dashing, and cavalier, in a wet blanket " +
                        "sort of way.",
                )
            }
        }
    }

    private suspend fun Dialogue.draul() {
        val stage = quest.getQuestStage(player)
        when {
            stage >= RomeoAndJulietStage.TellRomeo -> {
                chatNpc(
                    sad,
                    "My poor Juliet....she's dead...dead! I shouldn't have been so hard on her..." +
                        "she was my lovely daughter. Booo hoooo hooooo!",
                )
            }
            stage >= RomeoAndJulietStage.CadavaPotion -> draulSuspicious()
            stage >= RomeoAndJulietStage.FindFatherLawrence -> {
                chatNpc(
                    angry,
                    "Do you live here? If so, how's about a couple of hundred gold towards the " +
                        "rent eh? Pay your share I say...you don't want to be like that " +
                        "freeloading Romeo!",
                )
            }
            stage >= RomeoAndJulietStage.DeliverMessage -> draulNoticesMessage()
            else -> draulUnstarted()
        }
    }

    private suspend fun Dialogue.draulUnstarted() {
        chatNpc(angry, "What are you doing here? Snooping around...")
        when (
            choice4(
                "I've come to see Juliet on Romeo's behalf.",
                1,
                "I've just come to have a chat with Juliet.",
                2,
                "Oh...just looking around...",
                3,
                "Ok, thanks.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I've come to see Juliet on Romeo's behalf.")
                chatNpc(
                    verymad,
                    "What...what...Romeo! Why that good for nothing swine...he's always trying to " +
                        "get the affections of my daughter..that soppy, half brained nincompoop " +
                        "won't ever have the heart of my daughter.",
                )
                chatNpc(angry, "She deserves someone of character, wit and repose.")
                chatPlayer(quiz, "What's so wrong about Romeo?")
                chatNpc(
                    verymad,
                    "Wrong! What's wrong with him...have you actually talked to him? He's nothing " +
                        "but a dim witted upperclass twit, totally useless.",
                )
                chatNpc(
                    angry,
                    "If he threw a stone at the ground, he'd probably miss! He's totally " +
                        "invisible when it's raining because he's so wet!",
                )
                chatNpc(
                    angry,
                    "If you started with what's right with him, you'd have much less to consider!",
                )
                chatPlayer(
                    bored,
                    "Well, I admit, he's probably not the sharpest knife in the cutlery draw...",
                )
                chatNpc(
                    verymad,
                    "Sharp? I've seen keener wit in root vegetables. Anyway, stop changing the " +
                        "subject. Get out of here and don't think you can sneak up those stairs " +
                        "to see Juliet, because I'll catch you and then you'll be for it!",
                )
                chatPlayer(worried, "That seems a bit harsh....")
                chatNpc(verymad, "Harsh but fair I think you'll find...now get OUT!")
            }
            2 -> {
                chatPlayer(neutral, "I've just come to have a chat with Juliet.")
                chatNpc(
                    angry,
                    "What on earth about? I hope you're not in cahoots with that good for nothing " +
                        "Romeo!",
                )
                chatPlayer(shifty, "Err..no of course not....why would I be?")
                chatNpc(
                    angry,
                    "He's been trying to wooo my daughter for an age. Up until now she's had the " +
                        "good sense to just ignore him. I just don't know what's gotten into her " +
                        "recently so that she would give him the time of day.",
                )
                chatPlayer(
                    laugh,
                    "Well, love is mysterious! Perhaps one day someone may even learn to love you!",
                )
                chatNpc(
                    verymad,
                    "What! Someone may fall in love with me...what are you trying to insinuate?",
                )
                chatPlayer(shifty, "Err...Nothing....I guess I'd better be going now...")
            }
            3 -> {
                chatPlayer(neutral, "Oh...just looking around...")
                chatNpc(
                    verymad,
                    "Just looking around! This is MY house! You might have at least 'ASKED' to " +
                        "view my considerably well appointed abode...but no, you've just burst in " +
                        "with all the elegance of a Troll at a tea party.",
                )
                chatPlayer(
                    bored,
                    "I can see that you're busy ranting so I'll just nip off and investigate a bit.",
                )
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.draulNoticesMessage() {
        chatNpc(angry, "What are you doing in my house? Up to no good I shouldn't wonder!")
        chatPlayer(
            happy,
            "Just a small chore for Juliet, you do have a lovely daughter in her sir.",
        )
        chatNpc(happy, "Oh...why, thank you...I've always tried to my best...")
        chatNpc(
            angry,
            " ...Hang on! Enough of that smiley talk. I have a daughter and I know what she's " +
                "like. Don't even think of carrying on anything behind my back, I have the eyes " +
                "of a hawk, nothing gets past me!",
        )

        if (!access.carryingObj("obj.julietmessage")) {
            return
        }

        objbox("obj.julietmessage", "Sir Draul notices the message!")
        chatNpc(
            angry,
            "Hey! What's that in your hands...looks like a message to me...with Juliet's barely " +
                "legible scrawl on it...",
        )
        chatPlayer(shifty, "Yes, yes, that's probably why I can't read it!")
        chatPlayer(
            shifty,
            "Sorry, I mean, that's right sir. I'm just popping to the shops to get some groceries " +
                "for Juliet.",
        )
        chatPlayer(shifty, "Right, have to be off now...thanks...")
        chatNpc(angry, "Groceries!")
        chatNpc(
            verymad,
            "Groceries!...at a time like this, does that girl know what she's putting me through!",
        )
    }

    private suspend fun Dialogue.draulSuspicious() {
        chatNpc(angry, "Hey, what are you doing here?")
        chatPlayer(
            shifty,
            "Nothing much sir...I promise...I'm just an innocent friend of Juliet's...doing her a " +
                "few favours...as a friend.",
        )
        chatNpc(
            angry,
            "Well, just make sure there's no funny business going on that's all I can say. Do you " +
                "know why?",
        )
        chatPlayer(bored, "I think I can guess sir...")
        chatNpc(
            angry,
            "There's no need to guess! You can see it in my hawk like eyes, in my cat like ears " +
                "and my dog like nose...",
        )
        chatPlayer(quiz, "Are you saying that you look like an animal sir?")
        chatNpc(
            verymad,
            "NO! Not look like an animal! I have the keen 'SENSES' of an animal and I don't miss " +
                "a thing. Don't even think about trying anything!",
        )

        if (!access.carryingObj("obj.cadava")) {
            return
        }

        objbox("obj.cadava", "Draul notices the potion!")
        chatNpc(angry, "Hey! What's that in your hands? Looks like some sort of potion to me!")
        chatPlayer(
            shifty,
            "Err...no! Nope! Not a potion! Some medicine...I have a terrible cough ...cough..." +
                "cough....see?",
        )
        chatPlayer(
            shifty,
            "Except, I can't drink too much or else it makes me feel 'really' tired. One sip of " +
                "this and the lights go out...I mean, I'm asleep in a minute.",
        )
        chatNpc(shocked, "Sleep!")
        chatNpc(
            verymad,
            "Sleep...! How can you even think of such a thing at a time like this? And I hope you " +
                "have no intentions of sleeping in my house!",
        )
        chatNpc(angry, "You'll get a bill for rent if I catch you dozing off around here!")
    }

    private companion object {
        const val ROMEO_HEART_SPOTANIM = "spotanim.trollromance_heart"
    }
}
