package org.rsmod.content.quest.area.varrock

import org.rsmod.api.npc.owner.hasSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

class ARuffSituation :
    QuestScript(
        "quest_ruffsituation",
        "varp.dogquest_main",
        rewards {},
        questVarbit = "varbit.dogquest",
    ) {
    override fun ScriptContext.init() {
        onOpNpc1("npc.dogq_talia") { talkToTalia(it.npc) }
        onOpNpc1("npc.dogq_varrock_stray_dog_multi_quest") { meetStray(it.npc) }
        onOpNpc1("npc.dogq_varrock_stray_dog_multi_prequest") { meetStray(it.npc) }
        onOpNpc1("npc.dogq_dog_stray") { meetStray(it.npc) }
        for (box in listOf("loc.dogq_den_box_multi", "loc.dogq_den_box")) {
            onOpLoc1(box) { inspectDen(BOX_BIT, "crate", "You notice some chew marks on the corner.") }
        }
        for (scraps in listOf("loc.dogq_den_scraps_multi", "loc.dogq_den_scraps")) {
            onOpLoc1(scraps) {
                inspectDen(SCRAPS_BIT, "scraps on the floor", "They've been ripped up by something with small teeth.")
            }
        }
        for (bed in listOf("loc.dogq_den_bed_multi", "loc.dogq_den_bed")) {
            onOpLoc1(bed) { inspectDen(BED_BIT, "bed", "You notice a large indentation and three smaller ones.") }
        }
        for (goblin in GOBLINS) {
            onOpNpc1(goblin) { talkToGoblin(it.npc) }
        }
        onOpHeldU(FUR, GRAIN) { makeStuffedDog() }
        onOpNpc1("npc.dogq_puppy_three_multi") { rescueThirdPuppy(it.npc) }
        onOpNpc1("npc.dogq_puppy_three") { rescueThirdPuppy(it.npc) }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Talia</col> at the dog shelter near Edgeville."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            when (quest.getQuestStage(player.player)) {
                0 -> line("I should speak to Talia at the dog shelter near Edgeville.")
                QUEST_ACCEPTED -> line("I should ask Talia where I might find a stray dog.")
                STARTED -> line("Talia asked me to find stray dogs. Gertrude in western Varrock may know where to look.")
                GERTRUDE_CLUE -> line("Gertrude told me to look for a stray near Varrock's western entrance.")
                FOUND_STRAY -> line("The stray led me to a den. I should inspect it for clues.")
                IN_DEN -> line("The stray led me to a den. I should inspect it for clues.")
                in FOUND_CLUES until FIRST_PUPPY ->
                    line("The stray's trail leads towards the road east of Goblin Village.")
                in FIRST_PUPPY until NEED_STUFFED ->
                    line("I rescued one puppy. I should question the goblins near Goblin Village.")
                in NEED_STUFFED until SECOND_PUPPY ->
                    line("I should make a stuffed dog from fur and grain for the goblins.")
                in SECOND_PUPPY until THIRD_PUPPY ->
                    line("I rescued two puppies. I should search the road north of Goblin Village for the third.")
                in THIRD_PUPPY until quest.maxSteps -> line("I should take the three puppies to Talia's shelter.")
                else -> line("I should find the stray dog's missing puppies and bring them to the shelter.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I reunited a stray dog with her puppies and helped Talia's shelter.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    suspend fun Dialogue.gertrudeClue() {
        chatPlayer(quiz, "Hello there, Gertrude.")
        chatNpc(happy, "Hello, adventurer! How can I help you today?")
        chatPlayer(quiz, "Talia sent me to you. She said you helped with their dog shelter?")
        chatNpc(neutral, "They barely have any dogs in for adoption? Oh dear.")
        chatNpc(neutral, "I often see stray dogs near the western entrance to Varrock.")
        chatPlayer(happy, "I'll start my search there, then. You've been a great help!")
        quest.setQuestStage(access, GERTRUDE_CLUE)
    }

    private suspend fun ProtectedAccess.talkToTalia(npc: Npc) {
        startDialogue(npc) {
            when {
                quest.isQuestCompleted(player) -> {
                    chatNpc(happy, "Thank you once again, adventurer. You've given us hope.")
                    chatNpc(neutral, "Chase keeps a log of the stray dogs people have reported.")
                }
                quest.isQuestInProgress(player) -> {
                    if (quest.getQuestStage(player) >= THIRD_PUPPY) {
                        reunitePuppies()
                        return@startDialogue
                    }
                    chatNpc(neutral, "Have you found any strays yet?")
                    chatNpc(neutral, "Gertrude lives near the western entrance to Varrock. Try asking her.")
                    if (quest.getQuestStage(player) == QUEST_ACCEPTED) {
                        quest.setQuestStage(access, STARTED)
                    }
                }
                else -> introduction()
            }
        }
    }

    private suspend fun ProtectedAccess.meetStray(npc: Npc) {
        if (stage(player) != GERTRUDE_CLUE) {
            return
        }
        startDialogue(npc) {
            chatNpc(neutral, "Woof woof!")
            chatPlayer(quiz, "Something has her upset. Maybe she wants me to follow her.")
            chatNpc(happy, "Arf arf!")
        }
        quest.setQuestStage(this, FOUND_STRAY)
    }

    private fun ProtectedAccess.inspectDen(bit: String, objectName: String, clue: String) {
        if (stage(player) < FOUND_STRAY || stage(player) > FOUND_CLUES) {
            mes("You find nothing useful right now.")
            return
        }
        if (stage(player) == FOUND_STRAY) {
            quest.setQuestStage(this, IN_DEN)
        }
        mes("You inspect the $objectName...")
        mes(clue)
        VarPlayerIntMapSetter.set(player, bit, 1)
        if (stage(player) == IN_DEN && DEN_BITS.all { player.vars[it] != 0 }) {
            quest.setQuestStage(this, FOUND_CLUES)
            mes("The stray dog's puppies have gone missing. I should follow her scent trail.")
        }
    }

    private suspend fun ProtectedAccess.talkToGoblin(npc: Npc) {
        if (npc.hasSpawnOwner && !npc.isSpawnOwnedBy(player)) {
            mes("That goblin is occupied with another adventurer.")
            return
        }
        when (stage(player)) {
            FOUND_CLUES -> {
                if (inv.isFull()) {
                    mes("You need a free inventory space to rescue the puppy.")
                    return
                }
                startDialogue(npc) {
                    chatNpc(worried, "Uh oh! Big dog!")
                    chatPlayer(angry, "Hang on, get back here! That puppy needs help.")
                    chatPlayer(happy, "Come with us, little guy. We'll keep you safe.")
                }
                invAdd(inv, FIRST_PUPPY_OBJ)
                quest.setQuestStage(this, FIRST_PUPPY)
            }
            FIRST_PUPPY -> {
                startDialogue(npc) {
                    chatPlayer(angry, "Why did you steal these puppies? You need to give them back.")
                    chatNpc(neutral, "We want big, strong guard dog. But dog need food and walks?")
                    chatPlayer(neutral, "Maybe I could make you one that needs none of that.")
                    chatNpc(happy, "Human find goblins dog that no need extra stuff, then goblins give puppy.")
                    chatPlayer(quiz, "Fur, grain, a needle and thread... I think I can make something.")
                }
                quest.setQuestStage(this, NEED_STUFFED)
            }
            NEED_STUFFED -> mes("The goblins are waiting for a stuffed dog.")
            STUFFED_CRAFTED -> {
                if (invTotal(inv, STUFFED_DOG) == 0) {
                    mes("I need to bring the goblins the stuffed dog.")
                    return
                }
                startDialogue(npc) {
                    chatPlayer(happy, "Here is a guard dog that needs no food or walks.")
                    chatNpc(happy, "That's big dog... Village never attacked again!")
                    chatNpc(neutral, "Goblins accept trade. No takesies backsies.")
                }
                invDel(inv, STUFFED_DOG)
                if (invTotal(inv, SECOND_PUPPY_OBJ) == 0) {
                    invAdd(inv, SECOND_PUPPY_OBJ)
                }
                quest.setQuestStage(this, SECOND_PUPPY)
            }
        }
    }

    private fun ProtectedAccess.makeStuffedDog() {
        if (stage(player) != NEED_STUFFED) {
            return
        }
        if (invTotal(inv, NEEDLE) == 0 || invTotal(inv, THREAD) == 0) {
            mes("You need a needle and thread to sew a stuffed dog.")
            return
        }
        if (invTotal(inv, FUR) == 0 || invTotal(inv, GRAIN) == 0) {
            return
        }
        invDel(inv, FUR)
        invDel(inv, GRAIN)
        invDel(inv, THREAD)
        invAdd(inv, STUFFED_DOG)
        anim("seq.human_clay_stitching")
        soundSynth("synth.STITCHING")
        mes("You create a stuffed dog. It will probably convince the goblins.")
        quest.setQuestStage(this, STUFFED_CRAFTED)
    }

    private suspend fun ProtectedAccess.rescueThirdPuppy(npc: Npc) {
        if (!npc.isSpawnOwnedBy(player)) {
            mes("That isn't your puppy to rescue.")
            return
        }
        if (stage(player) != OUTLAWS_DEFEATED) {
            mes("The puppy isn't safe to take yet.")
            return
        }
        if (inv.isFull()) {
            mes("You need a free inventory space to rescue the puppy.")
            return
        }
        startDialogue(npc) {
            chatPlayer(happy, "Are you okay, little guy? Let's get you somewhere safe.")
            chatNpc(happy, "Arf!")
        }
        invAdd(inv, THIRD_PUPPY_OBJ)
        quest.setQuestStage(this, THIRD_PUPPY)
    }

    private suspend fun Dialogue.reunitePuppies() {
        if (PUPPY_OBJS.any { access.invTotal(access.inv, it) == 0 }) {
            chatNpc(neutral, "Please bring all three puppies back to the shelter.")
            return
        }
        chatPlayer(happy, "Talia, Chase! I found the stray and rescued all three of her puppies.")
        chatNpc(happy, "Oh my goodness! This is exactly what we need to keep the shelter going!")
        chatNpc(neutral, "Are you sure they want to stay with us?")
        chatNpcSpecific("Stray dog", STRAY_HEAD, happy, "Arf!")
        chatPlayer(happy, "I'm pretty sure that means yes at this point.")
        chatNpc(happy, "Wonderful, you will all be happy and safe here!")
        chatNpcSpecific("Stray dog", STRAY_HEAD, sad, "Whine.")
        chatPlayer(neutral, "I don't think their mother wants to stay. She's quite stubborn.")
        chatNpcSpecific("Guard", GUARD_HEAD, neutral, "I'm one of the Varrock guards. We've grown fond of her.")
        chatNpcSpecific("Guard", GUARD_HEAD, happy, "We'd be delighted to bring her into our ranks.")
        chatNpc(happy, "Thank you! We'll get you set up with some things for her, free of charge!")
        chatNpcSpecific("Chase", CHASE_HEAD, quiz, "Free?")
        chatNpc(happy, "Thank you, adventurer, this is just what the shelter needed!")
        chatNpc(neutral, "If you would ever like to adopt one of these puppies yourself, just talk to Chase.")
        chatNpcSpecific("Chase", CHASE_HEAD, neutral, "For a small fee.")
        PUPPY_OBJS.forEach { access.invDel(access.inv, it) }
        quest.completeQuest(access)
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(quiz, "Hello, what is this place?")
        chatNpc(happy, "Welcome to our dog shelter! I'm Talia and I run it alongside my husband, Chase.")
        chatPlayer(quiz, "You don't seem to have many dogs. Where are the rest?")
        chatNpc(sad, "I wish I could tell you they'd all been adopted... but I'd be lying to myself.")
        chatNpc(neutral, "We need more strays to rescue. If people know they can find a companion here, they'll come.")
        chatNpc(sad, "It can take one of us hours to coax a puppy out of hiding, and the shelter doesn't run itself.")
        chatNpc(neutral, "What we really need is someone worldly who can bring strays back for us.")
        val help = choice2("I'll help you find some strays.", true, "Not right now.", false)
        if (!help) {
            return
        }
        quest.setQuestStage(access, QUEST_ACCEPTED)
        chatNpc(happy, "Wonderful! Gertrude was a great help with the cats. She may know where to begin.")
        chatNpc(neutral, "You'll find her house near the western entrance to Varrock.")
        quest.setQuestStage(access, STARTED)
    }

    companion object {
        const val QUEST_ACCEPTED = 5
        const val STARTED = 10
        const val GERTRUDE_CLUE = 15
        const val FOUND_STRAY = 20
        const val IN_DEN = 25
        const val FOUND_CLUES = 30
        const val FIRST_PUPPY = 50
        const val NEED_STUFFED = 55
        const val STUFFED_CRAFTED = 60
        const val SECOND_PUPPY = 80
        const val OUTLAWS_DEFEATED = 90
        const val THIRD_PUPPY = 110
        const val FIRST_PUPPY_OBJ = "obj.dogq_puppy_one_object"
        const val SECOND_PUPPY_OBJ = "obj.dogq_puppy_two_object"
        const val THIRD_PUPPY_OBJ = "obj.dogq_puppy_three_object"
        private val PUPPY_OBJS = listOf(FIRST_PUPPY_OBJ, SECOND_PUPPY_OBJ, THIRD_PUPPY_OBJ)
        private val GOBLINS = listOf(
            "npc.dogq_goblin_picklenose_multi", "npc.dogq_goblin_toetaller_multi",
            "npc.dogq_goblin_picklenose", "npc.dogq_goblin_toetaller",
        )
        private const val FUR = "obj.werewolve_fur"
        private const val GRAIN = "obj.grain"
        private const val NEEDLE = "obj.needle"
        private const val THREAD = "obj.thread"
        private const val STUFFED_DOG = "obj.dogq_stuffed_dog"
        private const val BOX_BIT = "varbit.dogq_stray_den_1"
        private const val SCRAPS_BIT = "varbit.dogq_stray_den_2"
        private const val BED_BIT = "varbit.dogq_stray_den_3"
        private const val STRAY_HEAD = "npc.dogq_dog_stray"
        private const val GUARD_HEAD = "npc.dogq_varrock_guard"
        private const val CHASE_HEAD = "npc.dogq_chase_post"
        private val DEN_BITS = listOf(BOX_BIT, SCRAPS_BIT, BED_BIT)
    }
}
