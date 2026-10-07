package org.rsmod.content.interfaces.makeover.npcs

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccessLauncher
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.interfaces.makeover.MakeoverPage
import org.rsmod.content.interfaces.makeover.openMakeover
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MakeoverMage
@Inject
constructor(private val random: GameRandom, private val npcAccess: StandardNpcAccessLauncher) :
    PluginScript() {
    private val logger = InlineLogger()

    // Resolve gamevals during startup, after their tables are loaded.
    private val flipTo = HashMap<Int, NpcServerType>()

    override fun ScriptContext.startup() {
        val male = ServerCacheManager.getNpc(MakeoverMageMale.asRSCM(RSCMType.NPC))
        val female = ServerCacheManager.getNpc(MakeoverMageFemale.asRSCM(RSCMType.NPC))
        if (male == null || female == null) {
            logger.error {
                "Makeover Mage cannot alternate gender: missing npc type " +
                    "(male=$male, female=$female). Both $MakeoverMageMale and " +
                    "$MakeoverMageFemale must exist in the cache."
            }
        } else {
            flipTo[male.id] = female
            flipTo[female.id] = male
        }

        for (mage in MakeoverMageIds) {
            onOpNpc1(mage) { op -> startDialogue(op.npc) { talkTo() } }
            onOpNpc4(mage) { op -> startDialogue(op.npc) { makeoverMenu() } }
            // Both ids, because `AiTimerProcessor.publishEvent` keys the lookup on `visType.id` —
            // once he has flipped, the handler bound to the *other* id is the one that fires.
            onAiTimer(mage) { npc.ambientGenderSwap() }
        }
    }

    private fun Npc.ambientGenderSwap() {
        // Reschedule before early returns so the alternating timer cannot retire.
        aiTimer(random.of(TransformMinTicks, TransformMaxTicks))

        if (isBusy) {
            return
        }

        val next = flipTo[visType.id] ?: return

        npcAccess.launch(this) {
            spotanim(
                TransformSpotanim,
                delay = TransformSpotanimDelay,
                height = TransformSpotanimHeight,
            )
            anim(TransformSeq)
            delay(2)
            say(TransformSay)
            changeType(next, duration = Int.MAX_VALUE)
        }
    }

    private suspend fun Dialogue.talkTo() {
        chatNpc(
            happy,
            "Hello there! I am known as the Makeover Mage! I have spent many years researching " +
                "magics that can change your physical appearance!",
        )
        chatNpc(
            quiz,
            "I can alter your physical form with my magic, there's no fee. Would you like me to " +
                "perform my magics upon you?",
        )
        mainMenu(justAsked = null)
    }

    private suspend fun Dialogue.mainMenu(justAsked: Branch?) {
        val choice =
            when (justAsked) {
                Branch.TellMore ->
                    choice3(
                        AcceptLabel,
                        Branch.Accept,
                        AmuletLabel,
                        Branch.Amulet,
                        DeclineLabel,
                        Branch.Decline,
                    )
                Branch.Amulet ->
                    choice3(
                        TellMoreLabel,
                        Branch.TellMore,
                        AcceptLabel,
                        Branch.Accept,
                        DeclineLabel,
                        Branch.Decline,
                    )
                else ->
                    choice4(
                        TellMoreLabel,
                        Branch.TellMore,
                        AcceptLabel,
                        Branch.Accept,
                        AmuletLabel,
                        Branch.Amulet,
                        DeclineLabel,
                        Branch.Decline,
                    )
            }

        when (choice) {
            Branch.TellMore -> tellMeMore()
            Branch.Amulet -> amulet()
            Branch.Accept -> accept()
            Branch.Decline -> decline()
        }
    }

    private suspend fun Dialogue.tellMeMore() {
        chatPlayer(quiz, TellMoreLabel)
        chatNpc(
            happy,
            "Why, of course! Basically, and I will try and explain this so that you will " +
                "understand it correctly,",
        )
        chatNpc(
            happy,
            "I use my secret magical technique to melt your body down into a puddle of its elements.",
        )
        chatNpc(
            happy,
            "When I have broken down all trace of your body, I then rebuild it into the form I am " +
                "thinking of!",
        )
        chatNpc(confused, "Or, you know, somewhere vaguely close enough anyway.")
        chatPlayer(quiz, "Uh... that doesn't sound particularly safe to me...")
        chatNpc(
            happy,
            "It's as safe as houses! Why, I have only had thirty-six major accidents this month!",
        )
        chatNpc(quiz, "So what do you say? Feel like a change? There's no fee.")
        mainMenu(justAsked = Branch.TellMore)
    }

    private suspend fun Dialogue.amulet() {
        chatPlayer(happy, AmuletLabel)
        chatNpc(
            neutral,
            "No problem, but please remember that the amulet I will sell you is only a copy of my " +
                "own. It contains no magical powers, and as such will only cost you 100 coins.",
        )

        val buy = choice2(AmuletBuyLabel, true, AmuletRefuseLabel, false)
        if (buy) {
            chatPlayer(happy, AmuletBuyLabel)
            buyAmulet()
        } else {
            chatPlayer(neutral, AmuletRefuseLabel)
        }

        chatNpc(
            happy,
            "Anyway, would you like me to alter your physical form? For you, I'll do it for free!",
        )
        mainMenu(justAsked = Branch.Amulet)
    }

    private suspend fun Dialogue.buyAmulet() {
        if (access.invTotal(access.inv, Coins) < AmuletPrice) {
            chatPlayer(sad, "Oh dear, I don't have enough coins.")
            return
        }
        if (access.invDel(access.inv, Coins, AmuletPrice).failure) {
            return
        }
        if (access.invAdd(access.inv, Amulet, 1).failure) {
            access.invAdd(access.inv, Coins, AmuletPrice)
            return
        }
        objbox(Amulet, "You receive an amulet in exchange for 100 coins.")
    }

    private suspend fun Dialogue.accept() {
        chatPlayer(happy, AcceptLabel)
        chatNpc(
            happy,
            "Good choice, good choice. You wouldn't want to carry on looking like that, I'm sure!",
        )
        access.openMakeover(MakeoverPage.Body)
    }

    private suspend fun Dialogue.decline() {
        chatPlayer(angry, DeclineLabel)
        chatNpc(sad, "Ehhh... suit yourself.")
    }

    private suspend fun Dialogue.makeoverMenu() {
        val choice =
            choice4(
                "Change my body type and skin colour.",
                MakeoverPage.Body,
                "Change my hair and facial hair.",
                MakeoverPage.Hair,
                "Change my clothes.",
                MakeoverPage.Top,
                "Cancel.",
                null,
                title = "A makeover is free!",
            )
        choice?.let { access.openMakeover(it) }
    }

    private enum class Branch {
        TellMore,
        Accept,
        Amulet,
        Decline,
    }

    private companion object {
        const val MakeoverMageMale = "npc.makeover_mage"
        const val MakeoverMageFemale = "npc.makeover_mage_female"

        val MakeoverMageIds = listOf(MakeoverMageMale, MakeoverMageFemale)

        const val TransformSpotanim = "spotanim.curse_impact"
        const val TransformSpotanimDelay = 15
        const val TransformSpotanimHeight = 124
        const val TransformSeq = "seq.human_castentangle_staff"
        const val TransformSay = "Ooh!"

        const val TransformMinTicks = 200
        const val TransformMaxTicks = 400

        const val Coins = "obj.coins"
        const val Amulet = "obj.postie_pete_yin_yang"
        const val AmuletPrice = 100

        const val TellMoreLabel = "Tell me more about this 'makeover'."
        const val AcceptLabel = "Sure, I'll have a makeover."
        const val AmuletLabel = "Cool amulet! Can I have one?"
        const val DeclineLabel = "No thanks."
        const val AmuletBuyLabel = "Sure, here you go."
        const val AmuletRefuseLabel = "No way! That's far too expensive."
    }
}
