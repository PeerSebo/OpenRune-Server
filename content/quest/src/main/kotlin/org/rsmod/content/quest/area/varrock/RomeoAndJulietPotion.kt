package org.rsmod.content.quest.area.varrock

import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.manager.Quest
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

// Inventory operation numbers include the implicit Use slot; cache indices and wire ops differ.
internal class RomeoAndJulietPotion(private val quest: Quest) {

    fun ScriptContext.register() {
        onOpNpc1("npc.father_lawrence") { startDialogue(it.npc) { fatherLawrence() } }

        onOpNpc1("npc.apothecary") { startDialogue(it.npc) { apothecary() } }
        onOpNpc3("npc.apothecary") { startDialogue(it.npc) { openPotionService() } }

        onOpHeld1("obj.cadava") { objbox("obj.cadava", LookAtText) }
        onOpHeld2("obj.cadava") { drinkPotion() }

        for (recipe in ApothecaryRecipes.all) {
            onIfModalButton(recipe.component) { brewPotion(recipe) }
        }
    }

    private suspend fun Dialogue.fatherLawrence() {
        when {
            quest.getQuestStage(player) < RomeoAndJulietStage.FindFatherLawrence -> {
                chatNpc(angry, "Can't you see that I'm in the middle of a sermon?!")
            }
            quest.getQuestStage(player) == RomeoAndJulietStage.FindFatherLawrence -> theSermon()
            quest.getQuestStage(player) == RomeoAndJulietStage.FindApothecary ->
                chatNpc(
                    neutral,
                    "Ah, have you found the Apothecary yet? Remember, Cadava potion, for Juliet.",
                )
            quest.getQuestStage(player) == RomeoAndJulietStage.CadavaPotion -> lawrenceProgress()
            else ->
                chatNpc(happy, "May Saradomin watch over that young couple. And over my sermon.")
        }
    }

    private suspend fun Dialogue.theSermon() {
        chatNpc(happy, "''...and let Saradomin light the way for you... ''<br>Urgh!")
        chatNpc(angry, "Can't you see that I'm in the middle of a sermon?!")
        chatPlayer(angry, "But Romeo sent me!")
        chatNpc(angry, "But I'm busy delivering a sermon to my congregation!")

        access.camMoveToV3(
            CongregationCameraEye,
            height = CongregationEyeHeight,
            rate = CameraSnapRate,
            rate2 = CameraSnapRate,
        )
        access.camLookAtV3(
            CongregationCameraLookAt,
            height = CongregationLookAtHeight,
            rate = CameraSnapRate,
            rate2 = CameraSnapRate,
        )

        chatNpcSpecific(
            title = "Congregation",
            type = "npc.romeo_juliet_pew_sleeping_man",
            mesanim = mesanim("mesanim.sleep"),
            text = "Zzzzzzzzz",
        )

        access.say("Yes, well, it certainly seems")
        access.delay(1)
        access.say("like you have a captive")
        access.delay(1)
        access.say("audience!")
        chatPlayer(bored, "Yes, well, it certainly seems like you have a captive<br>audience!")

        access.camReset()
        chatNpc(
            confused,
            "Ok, okay... what do you want so I can get rid of you and continue with my sermon?",
        )
        chatPlayer(neutral, "Romeo sent me. He says you may be able to help.")
        chatNpc(confused, "Ah Romeo, yes. A fine lad, but a little bit confused.")
        chatPlayer(
            neutral,
            "Yes, very confused.... Anyway, Romeo wishes to be married to Juliet! She must be " +
                "rescued from her father's control!",
        )
        chatNpc(happy, "I agree, and I think I have an idea! A potion to make her appear dead...")
        chatPlayer(shifty, "Dead! Sounds a bit creepy to me... but please, continue.")
        chatNpc(
            shifty,
            "The potion will only make Juliet 'appear' dead... then she'll be taken to the crypt...",
        )
        chatPlayer(shifty, "Crypt! Again... very creepy! You must have some strange hobbies.")
        chatNpc(
            shifty,
            "Then Romeo can collect her from the crypt! Go to the Apothecary, tell him I sent you " +
                "and that you'll need a 'Cadava' potion.",
        )

        quest.setQuestStage(access, RomeoAndJulietStage.FindApothecary)

        chatPlayer(
            shifty,
            "Apart from the strong overtones of death, this is turning out to be a real love story.",
        )
    }

    private suspend fun Dialogue.lawrenceProgress() {
        chatNpc(quiz, "Did you find the Apothecary?")

        if (access.carryingObj("obj.cadava")) {
            chatPlayer(happy, "I've got the cadava potion.")
            chatNpc(
                happy,
                "Good! Good work! Ok, take it to Juliet, she's expecting you. I'll talk to Romeo " +
                    "and make sure he knows what to do.",
            )
            return
        }

        chatPlayer(neutral, "Yes I did. He's told me I must find some Cadava berries.")
        chatNpc(neutral, "Well, good luck with that...they're quite tricky to find.")
        chatPlayer(quiz, "Any clues where I can start to look?")
        chatNpc(
            neutral,
            "I heard some kids saying they saw some the other day. They were visiting the mining " +
                "place to the south east of Varrock.",
        )
        chatPlayer(neutral, "Ok, that's as good a place to start looking as any.")
    }

    private suspend fun Dialogue.apothecary() {
        chatNpc(neutral, "I am the Apothecary. I brew potions.<br>Do you need anything specific?")
        apothecaryOptions()
    }

    private suspend fun Dialogue.apothecaryOptions() {
        val makePotions =
            choice2("Can you make potions for me?", true, "Talk about something else.", false)

        if (makePotions) {
            chatPlayer(quiz, "Can you make potions for me?")
            openPotionService()
            return
        }

        when (
            choice4(
                "Talk about Romeo & Juliet.",
                1,
                "Do you know a potion to make hair fall out?",
                2,
                "Have you got any good potions to give away?",
                3,
                "No thanks.",
                4,
            )
        ) {
            1 -> apothecaryQuestTalk()
            2 -> {
                chatPlayer(happy, "Do you know a potion to make hair fall out?")
                chatNpc(
                    happy,
                    "I do indeed. I gave it to my mother.<br>That's why I now live alone.",
                )
                chatNpc(neutral, "But can I do something for you?")
                apothecaryOptions()
            }
            3 -> {
                chatPlayer(happy, "Have you got any good potions to give away?")
                chatNpc(sad, "Sorry, charity is not my strong point.<br>Do you need anything else?")
                apothecaryOptions()
            }
            else -> {}
        }
    }

    private suspend fun Dialogue.apothecaryQuestTalk() {
        val stage = quest.getQuestStage(player)
        val hasBerries = access.carryingObj("obj.cadavaberries")

        when {
            stage < RomeoAndJulietStage.FindApothecary -> {
                chatPlayer(quiz, "Can you tell me about Romeo and Juliet?")
                chatNpc(bored, "Two young fools in love. Half of Varrock could tell you that much.")
            }

            stage == RomeoAndJulietStage.FindApothecary -> {
                chatPlayer(
                    neutral,
                    "Apothecary, Father Lawrence sent me.<br>I need a Cadava potion to help Romeo " +
                        "and Juliet.",
                )
                chatNpc(neutral, "Cadava potion. It's pretty nasty. And hard to make.")
                chatNpc(neutral, "Wing of rat, tail of frog.<br>Ear of snake and horn of dog.")
                chatNpc(neutral, "I have all that, but I need some Cadava berries.")

                quest.setQuestStage(access, RomeoAndJulietStage.CadavaPotion)

                chatNpc(
                    neutral,
                    "You will have to find them while I get the rest ready. Bring them here when " +
                        "you have them. But be careful.<br>They are nasty.",
                )

                if (hasBerries) {
                    chatPlayer(happy, "Conveniently, I have some here.")
                    handOverBerries()
                } else {
                    berryQuestions()
                }
            }

            access.carryingObj("obj.cadava") -> {
                chatPlayer(neutral, "Thank you for the Cadava potion.")
                chatNpc(happy, "You're welcome. I hope it helps that young couple find happiness.")
            }

            stage > RomeoAndJulietStage.CadavaPotion -> {
                chatPlayer(neutral, "I gave Juliet the Cadava potion.")
                chatNpc(happy, "Good, good. I hope it helps that young couple find happiness.")
            }

            hasBerries -> {
                chatNpc(happy, "Well done. You have the berries.")
                handOverBerries()
            }

            else -> {
                chatNpc(
                    neutral,
                    "Keep searching for those Cadavaberries. They're needed for the " + "potion.",
                )
            }
        }
    }

    private suspend fun Dialogue.handOverBerries() {
        if (access.invDel(access.inv, "obj.cadavaberries").failure) {
            return
        }

        // Grant the result on the ingredient-removal tick, before any protected-access suspension.
        if (access.invAdd(access.inv, "obj.cadava", 1).failure) {
            access.invAdd(access.inv, "obj.cadavaberries", 1)
            return
        }

        objbox(
            "obj.cadavaberries",
            "You hand over the berries, which the Apothecary shakes up in a vial of strange liquid.",
        )
        chatNpc(happy, "Phew! Here is what you need.")
        objbox("obj.cadava", "The Apothecary gives you a Cadava potion.")
    }

    private suspend fun Dialogue.berryQuestions() {
        when (
            choice4(
                "What do these berries look like?",
                1,
                "Where can I get these berries?",
                2,
                "How are these berries dangerous?",
                3,
                "Ok, thanks.",
                4,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What do these berries look like?")
                chatNpc(
                    neutral,
                    "They're a nice bright pink colour, with green leaves, stalks are a pale " +
                        "brown colour...you can't miss them. They look pretty tasty, but I " +
                        "wouldn't advise eating them.",
                )
                berryQuestions()
            }
            2 -> {
                chatPlayer(quiz, "Where can I get these berries?")
                chatNpc(
                    neutral,
                    "I think I saw some recently in the local vicinity. Oh yes, that's right! " +
                        "There was a little bit of commotion when some kids visited the south " +
                        "eastern Varrock mining pit. The trip was disrupted because some kids",
                )
                chatNpc(
                    neutral,
                    "had found some cadavaberry bushes and they started picking them. They " +
                        "wrapped that trip up pretty quickly!",
                )
                berryQuestions()
            }
            3 -> {
                chatPlayer(quiz, "How are these berries dangerous?")
                chatNpc(
                    neutral,
                    "Hmm, they're generally okay, but if handled improperly they can be a bit " +
                        "nasty. And I certainly wouldn't advise drinking the potion!",
                )
                berryQuestions()
            }
            else -> chatPlayer(neutral, "Ok, thanks.")
        }
    }

    private suspend fun Dialogue.openPotionService() {
        access.ifOpenMainModal("interface.apothecary_potions")
        access.player.populateApothecaryPotions()
    }

    private suspend fun ProtectedAccess.brewPotion(recipe: ApothecaryRecipe) {
        if (player.reportMissingApothecaryIngredients(recipe)) return

        val brewed = player.invTransaction(inv) {
            brewApothecaryPotion(select(inv), recipe)
        }
        if (brewed.failure) return
        objbox(recipe.product, "The Apothecary mixes the ingredients into a ${recipe.label.lowercase()}.")
    }

    private suspend fun ProtectedAccess.drinkPotion() {
        if (!inApothecarysShop()) {
            objbox(
                "obj.cadava",
                "You dare not drink this outside of the apothecary's establishment. At least " +
                    "there the apothecary may be able to help you if you need assistance.",
            )
            return
        }

        val drink =
            choice2(
                "Yes, I'll drink it all down.",
                true,
                "No, I'm having second thoughts now.",
                false,
                title = "Are you sure you wish to drink this Cadava Potion?",
            )

        if (!drink) {
            mes("You decide against drinking the potion.")
            return
        }

        objbox(
            "obj.cadava",
            "In defiance of Father Lawrence's warnings, you drink the entire contents of the " +
                "potion.",
        )

        invDel(inv, "obj.cadava")
        anim("seq.human_eat")
        soundSynth("synth.LIQUID")
        delay(BlackoutSwayDelay)

        camShake(axis = 3, random = 0, amplitude = SwayAmplitude, rate = SwayRate)
        delay(BlackoutCollapseDelay)

        anim("seq.human_death")
        say("Urk!")
        startDialogue { chatPlayer(shocked, "Urk!") }

        mes("Oh dear...you are nearly dead.")
        fadeOverlay(
            startColour = 0,
            startTransparency = 255,
            endColour = 0,
            endTransparency = 0,
            clientDuration = FadeDuration,
        )
        delay(BlackoutHoldTicks)

        resetAnim()
        camShakeReset(axis = 3)

        fadeOverlay(
            startColour = 0,
            startTransparency = 0,
            endColour = 0,
            endTransparency = 255,
            clientDuration = FadeDuration,
        )
        delay(FadeTicks)
        closeFadeOverlayNow()

        mesbox("Some time later.....")
        startDialogue {
            chatNpcSpecific(
                title = "Apothecary",
                type = "npc.apothecary",
                mesanim = confused,
                text =
                    "Blimey... you've been out for hours! Are you ok?<br>People were tripping " +
                        "over you as they walked into the shop.",
            )
        }
    }

    private fun ProtectedAccess.inApothecarysShop(): Boolean {
        val coords = player.coords
        return coords.level == ShopSouthWest.level &&
            coords.x in ShopSouthWest.x..ShopNorthEast.x &&
            coords.z in ShopSouthWest.z..ShopNorthEast.z
    }

    private companion object {
        const val LookAtText =
            "This looks very colourful, but you remember what Father Lawrence told you about it, " +
                "probably best not to drink it."

        val ShopSouthWest: CoordGrid = CoordGrid(3192, 3402, 0)
        val ShopNorthEast: CoordGrid = CoordGrid(3198, 3409, 0)

        val CongregationCameraEye: CoordGrid = CoordGrid(3254, 3486, 0)
        val CongregationCameraLookAt: CoordGrid = CoordGrid(3256, 3476, 0)
        const val CongregationEyeHeight = 325
        const val CongregationLookAtHeight = 25

        const val CameraSnapRate = 100

        const val BlackoutSwayDelay = 5
        const val BlackoutCollapseDelay = 4
        const val BlackoutHoldTicks = 4
        const val FadeTicks = 2

        const val FadeDuration = 50

        const val SwayAmplitude = 20
        const val SwayRate = 2
    }
}
