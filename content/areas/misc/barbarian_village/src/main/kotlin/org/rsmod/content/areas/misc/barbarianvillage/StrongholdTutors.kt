package org.rsmod.content.areas.misc.barbarianvillage

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.misc.barbarianvillage.configs.StrongholdRefs
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StrongholdTutors : PluginScript() {
    private var ProtectedAccess.brotherFound by intVarBit(StrongholdRefs.BrotherFoundVarbit)

    override fun ScriptContext.startup() {
        onOpNpc1(StrongholdRefs.Litara) { startLitara(it.npc) }
        onOpNpc1(StrongholdRefs.Solztun) { startSolztun(it.npc) }
    }

    private suspend fun ProtectedAccess.startLitara(npc: Npc) {
        if (brotherFound != BrotherFoundInStronghold) {
            startDialogue(npc) { litaraFirstMeeting() }
            return
        }
        var newsBroken = false
        startDialogue(npc) { litaraBrotherNews { newsBroken = true } }
        if (newsBroken) {
            brotherFound = BrotherToldLitara
        }
    }

    private suspend fun Dialogue.litaraFirstMeeting() {
        chatNpc(happy, "Hello there. You look lost, are you okay?")

        val asking =
            choice2(
                "I'm looking for a stronghold or something...",
                true,
                "I'm fine, just passing through.",
                false,
            )
        if (!asking) {
            chatPlayer(neutral, "I'm fine, just passing through.")
            return
        }

        chatPlayer(confused, "I'm looking for a stronghold or something...")
        chatNpc(happy, "Ahh... the Stronghold of Security. It's down there.")
        mesbox("Litara points to the hole in the ground that looks like you could squeeze through.")
        chatPlayer(worried, "Looks kind of... deep and dark.")
        chatNpc(sad, "Yeah... tell that to my brother, he still hasn't come back.")
        chatPlayer(quiz, "Your brother?")
        chatNpc(
            neutral,
            "He's an explorer too. When the miner fell down that hole he'd made and came back " +
                "babbling about doors, questions and treasure, my brother went to explore. No-one " +
                "has seen him since.",
        )
        chatPlayer(sad, "Oh... that's not good.")
        chatNpc(
            neutral,
            "Lots of people have been down there, but none of them have seen him, Let me know if " +
                "you do, will you?",
        )
        chatPlayer(neutral, "I'll certainly keep my eyes open.")
    }

    private suspend fun Dialogue.litaraBrotherNews(onNewsBroken: () -> Unit) {
        chatNpc(happy, "Hello there. You look lost, are you okay?")
        chatPlayer(
            neutral,
            "I travelled to the bottom of the stronghold, and I emerge victorious!",
        )
        chatNpc(happy, "Well done! You didn't happen to find my brother?")
        chatPlayer(sad, "About that...")
        chatNpc(sad, "I sense bad news. It's okay, he's been gone so long, I believe I've moved on.")
        onNewsBroken()
        chatPlayer(neutral, "Well, at least he died doing what he loved.")
        chatNpc(
            sad,
            "True, I will raise a drink for him tonight, he was a good man, even if he was a " +
                "pacifist.",
        )
        chatPlayer(neutral, "As will I. See you around, Litara.")
        chatNpc(sad, "Goodbye, ${player.displayName}.")
    }

    private suspend fun ProtectedAccess.startSolztun(npc: Npc) {
        var metHim = false
        startDialogue(npc) { solztun { metHim = true } }
        if (metHim && brotherFound < BrotherFoundInStronghold) {
            brotherFound = BrotherFoundInStronghold
        }
    }

    private suspend fun Dialogue.solztun(onIdentified: () -> Unit) {
        chatNpc(short, "<col=000080>Can you hear me?")
        chatPlayer(
            confused,
            "... I can, but I'm not wearing an amulet of ghostspeak... A-am I dead?",
        )
        chatNpc(
            short,
            "<col=000080>I'm no ghost! I'm a spirit, I'm injecting my thoughts into your head.",
        )
        chatPlayer(worried, "Oh, that's... interesting. Also rather disturbing...")
        chatNpc(short, "<col=000080>Try not to think about it.")
        solztunMenu(onIdentified)
    }

    private suspend fun Dialogue.solztunMenu(onIdentified: () -> Unit) {
        while (true) {
            when (choice3("Who are you?", 1, "Why are you here?", 2, "Bye!", 3)) {
                1 -> solztunWhoAreYou(onIdentified)
                2 -> solztunWhyAreYouHere()
                else -> {
                    chatPlayer(neutral, "Bye!")
                    chatNpc(short, "<col=000080>Stay safe, adventurer.")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.solztunWhoAreYou(onIdentified: () -> Unit) {
        chatPlayer(quiz, "Who are you?")
        chatNpc(short, "<col=000080>I am Solztun, the greatest barbarian explorer!")
        chatPlayer(quiz, "It's very nice to meet you, Solztun! I'm ${player.displayName}.")
        chatNpc(short, "<col=000080>I know.")
        chatPlayer(quiz, "... so, you were exploring this place?")
        chatNpc(
            short,
            "<col=000080>Yes, my sister told me not to go, she said it was dangerous and that " +
                "I'm no fighter. Unfortunately... she was correct.",
        )
        onIdentified()
        chatPlayer(
            quiz,
            "Oh! Your sister is Litara, the woman out on the surface? How come she was correct?",
        )
        chatNpc(
            short,
            "<col=000080>I died... Of course, I tried escaping, long before I'd made it to the " +
                "treasure, but I was shot with arrows just as I approached the exit.",
        )
        chatPlayer(sad, "Nobody came to help?!")
        chatNpc(
            short,
            "<col=000080>Oh no, they had no idea I was hurt, I guess they assumed I'd found the " +
                "treasure and made a new life for myself.",
        )
        chatPlayer(quiz, "Were you not a fan of the barbarian life style?")
        chatNpc(
            short,
            "<col=000080>It was all death and pillaging, I prefer the finer things in life, like " +
                "exploration and discovery!",
        )
        chatPlayer(happy, "Ah, I too enjoy those.")
    }

    private suspend fun Dialogue.solztunWhyAreYouHere() {
        chatPlayer(quiz, "Why are you here?")
        chatNpc(short, "<col=000080>I came for treasure!")
        chatPlayer(quiz, "Oh right, you were looking for what's in that cradle?")
        chatNpc(short, "<col=000080>Yes, but alas, I'm dead.")
        chatPlayer(shifty, "I guess I could show you... for a price.")
        chatNpc(
            short,
            "<col=000080>Hmm... I don't have much to offer, but if you happen upon a skull " +
                "sceptre, I could make it stronger.",
        )
        chatPlayer(quiz, "Stronger you say... In what way?")
        chatNpc(
            short,
            "<col=000080>Currently the sceptre is fragile: it breaks once all charges have been " +
                "used. Show me the treasure and I could imbue it so it can be recharged with any " +
                "sceptre piece.",
        )
        chatPlayer(happy, "You, my dead friend, have a deal!")
    }

    private companion object {
        private const val BrotherFoundInStronghold = 1
        private const val BrotherToldLitara = 2
    }
}
