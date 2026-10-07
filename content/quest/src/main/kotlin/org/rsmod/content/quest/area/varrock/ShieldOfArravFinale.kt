package org.rsmod.content.quest.area.varrock

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.manager.Quest
import org.rsmod.plugin.scripts.ScriptContext

// Gate completion on quest state; another player can supply a certificate item.
internal class ShieldOfArravFinale(private val quest: Quest) {

    fun ScriptContext.register() {
        onOpNpc1(SoaRefs.Curator) { startDialogue(it.npc) { curator() } }
        onOpNpc1(SoaRefs.KingRoald) { startDialogue(it.npc) { kingRoald() } }

        onOpHeldU(SoaRefs.CertificateLeft, SoaRefs.CertificateRight) { combineCertificate() }

        onOpHeld1(SoaRefs.CertificateLeft) { inspectCertificateHalf() }
        onOpHeld1(SoaRefs.CertificateRight) { inspectCertificateHalf() }
        onOpHeld1(SoaRefs.Certificate) { inspectCertificate() }
        onOpHeld1(SoaRefs.ShieldHalfLeft) { inspectShieldHalf() }
        onOpHeld1(SoaRefs.ShieldHalfRight) { inspectShieldHalf() }
        onOpHeld4(SoaRefs.StoreKey) { inspectStoreKey() }
    }

    private suspend fun Dialogue.curator() {
        chatNpc(neutral, "Welcome to the museum of Varrock.")

        val carriesHalf =
            access.soaCarries(SoaRefs.ShieldHalfLeft) || access.soaCarries(SoaRefs.ShieldHalfRight)
        val inProgress = quest.isQuestInProgress(player)

        when {
            inProgress && (access.soaCarries(SoaRefs.Certificate) ||
                access.bank.contains(SoaRefs.Certificate)) ->
                chatNpc(neutral, "You already have a complete certificate. Take it to King Roald for your reward.")
            inProgress && carriesHalf && nextHalfToGive(access) != null -> curatorHandIn()
            inProgress && access.soaShieldGiven != 0 -> curatorMoreCertificates()
            else -> return
        }
    }

    private fun nextHalfToGive(access: ProtectedAccess): Pair<String, Int>? {
        val given = access.soaShieldGiven
        val hasLeft = access.soaCarries(SoaRefs.ShieldHalfLeft)
        val hasRight = access.soaCarries(SoaRefs.ShieldHalfRight)
        return when {
            hasRight && given and ShieldGivenRightBit == 0 ->
                SoaRefs.ShieldHalfRight to ShieldGivenRightBit
            hasLeft && given and ShieldGivenLeftBit == 0 ->
                SoaRefs.ShieldHalfLeft to ShieldGivenLeftBit
            else -> null
        }
    }

    private suspend fun Dialogue.curatorHandIn() {
        val (half, givenBit) = nextHalfToGive(access) ?: return
        val blackArmHalf = half == SoaRefs.ShieldHalfRight
        val ownCertificate = if (blackArmHalf) SoaRefs.CertificateRight else SoaRefs.CertificateLeft

        chatPlayer(quiz, "Hello there. I'm here about the Shield of Arrav.")
        chatNpc(
            shocked,
            "The Shield of Arrav? The Museum has been searching for that for years! The late " +
                "King Roald II offered a generous reward for its return!",
        )
        chatPlayer(happy, "Well, I'm here to claim it.")
        chatNpc(shocked, "You've found the shield? Let's have a look!")
        objbox(half, 400, "You show the shield half to the curator.")
        chatNpc(shocked, "This is incredible! But where's the other half?")

        if (blackArmHalf) {
            chatPlayer(
                neutral,
                "I obtained this half from the Black Arm Gang. I wonder if the Phoenix Gang " +
                    "might have the other half.",
            )
        } else {
            chatPlayer(
                neutral,
                "I obtained this half from the Phoenix Gang. I wonder if the Black Arm Gang " +
                    "might have the other half.",
            )
        }

        chatNpc(
            quiz,
            "That does sound plausible. We will have to hope that another adventurer can recover " +
                "that half.",
        )
        chatPlayer(quiz, "So will I be rewarded for recovering half of the shield?")
        chatNpc(
            neutral,
            "I'm afraid the reward is for the recovery of the full shield. However, what I'll do " +
                "is write you out two half- certificates. If another adventurer returns the " +
                "other shield half, I'll do the same for them.",
        )
        chatNpc(
            neutral,
            "The two of you can then swap one of the half- certificates to make a full one " +
                "each. Take that to the King and he will grant you your reward.",
        )

        if (access.invDel(access.inv, half, 1).failure) {
            return
        }
        if (access.invAdd(access.inv, ownCertificate, 2).failure) {
            access.invAdd(access.inv, half, 1)
            return
        }
        access.soaShieldGiven = access.soaShieldGiven or givenBit

        doubleobjbox(
            half,
            400,
            ownCertificate,
            400,
            "You give the shield half to the curator and he gives you two half-certificates.",
        )
    }

    private suspend fun Dialogue.curatorMoreCertificates() {
        val ownCertificate =
            if (access.soaShieldGiven and ShieldGivenRightBit != 0) SoaRefs.CertificateRight
            else SoaRefs.CertificateLeft

        chatPlayer(quiz, "Hello there. I'm here about the Shield of Arrav.")
        chatNpc(
            neutral,
            "I gave you two half-certificates for the half you returned. If another adventurer " +
                "returns the other shield half, I'll do the same for them.",
        )
        chatNpc(
            neutral,
            "The two of you can then swap one of the half-certificates to make a full one each. " +
                "Take that to the King and he will grant you your reward.",
        )
        chatPlayer(quiz, "Would it be okay to get more half-certificates?")
        chatNpc(
            neutral,
            "I suppose so. It's not like you can do anything with them without the other half.",
        )
        if (access.invAdd(access.inv, ownCertificate, 2).failure) {
            chatNpc(neutral, "You'll need to make some room for them first, though.")
            return
        }
        doubleobjbox(
            ownCertificate,
            400,
            ownCertificate,
            400,
            "The curator gives you two half-certificates.",
        )
    }

    private suspend fun ProtectedAccess.combineCertificate() {
        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, SoaRefs.CertificateLeft.asRSCM(), 1)
            delete(inventory, SoaRefs.CertificateRight.asRSCM(), 1)
            add(inventory, SoaRefs.Certificate.asRSCM(), 1)
        }
        if (transaction.failure) return
        soundSynth(SoaRefs.PaperTurnSound)
        objbox(
            SoaRefs.Certificate,
            400,
            "You combine the two halves to make a complete certificate.",
        )
    }

    private suspend fun ProtectedAccess.inspectCertificateHalf() {
        val hasBoth = soaCarries(SoaRefs.CertificateLeft) && soaCarries(SoaRefs.CertificateRight)
        if (hasBoth) {
            val combine =
                choice2("Yes.", true, "No.", false, title = "Combine the two half-certificates?")
            if (combine) {
                combineCertificate()
            }
            return
        }
        objbox(
            SoaRefs.CertificateLeft,
            400,
            "It's half of a certificate for claiming a reward from King Roald. If you're working " +
                "with a player who's restricted from trading, you can still give them this item " +
                "by <col=7f0000>using</col> it on them.",
        )
    }

    private fun ProtectedAccess.inspectCertificate() {
        ifOpenMain("interface.qip_soa_arrav_shield_interface")
        ifSetText(
            "component.qip_soa_arrav_shield_interface:qip_soa_arrav_certificate",
            "The bearer of this certificate has brought half of the legendary Shield of Arrav to " +
                "me, Haig Halen, Curator of the Varrock Museum. I have examined the shield and am " +
                "satisfied as to its authenticity. I recommend to His Majesty, King Roald III, " +
                "that the bearer is rewarded as per Proclimation 262 of the year 143 of the 5th " +
                "Age, by King Roald II.",
        )
    }

    private suspend fun ProtectedAccess.inspectShieldHalf() {
        objbox(
            SoaRefs.ShieldHalfRight,
            400,
            "It's half of the legendary Shield of Arrav. If you're working with a player who's " +
                "restricted from trading, you can still give them this item by " +
                "<col=7f0000>using</col> it on them.",
        )
    }

    private suspend fun ProtectedAccess.inspectStoreKey() {
        objbox(
            SoaRefs.StoreKey,
            400,
            "It's the key to the Phoenix Gang's weapons store. If you're working with a player " +
                "who's restricted from trading, you can still give them this item by " +
                "<col=7f0000>using</col> it on them.",
        )
    }

    private suspend fun Dialogue.kingRoald() {
        chatPlayer(happy, "Greetings, your majesty.")
        chatNpc(quiz, "Yes, citizen. Do you need something?")

        val ask = choice2("I'm here about the Shield of Arrav.", true, "Not really.", false)
        if (!ask) {
            chatPlayer(neutral, "Not really.")
            return
        }

        chatPlayer(happy, "I'm here about the Shield of Arrav.")

        val member =
            access.blackArmGang >= BlackArmState.Member || access.phoenixGang >= PhoenixState.Member

        when {
            !quest.isQuestCompleted(player) && member && access.soaCarries(SoaRefs.Certificate) ->
                completeQuest()
            access.soaCarries(SoaRefs.Certificate) -> {
                objbox(SoaRefs.Certificate, 400, "You show the certificate to the king.")
                chatNpc(neutral, "The name on this certificate isn't yours!")
            }
            access.soaCarries(SoaRefs.CertificateLeft) ||
                access.soaCarries(SoaRefs.CertificateRight) -> {
                objbox(SoaRefs.CertificateLeft, 400, "You show the certificate half to the king.")
                chatNpc(
                    neutral,
                    "I'm afraid that's only half the reward certificate. You'll have to get the " +
                        "other half and join them together if you want to claim the reward.",
                )
            }
            access.soaCarries(SoaRefs.ShieldHalfLeft) ||
                access.soaCarries(SoaRefs.ShieldHalfRight) -> {
                chatNpc(
                    neutral,
                    "The Shield of Arrav? Yes, I do recall my father put a reward out for that.",
                )
                chatPlayer(happy, "Well, I've managed to find half the shield!")
                chatNpc(
                    quiz,
                    "You have? Well, before we go any further, you'll need to get the " +
                        "authenticity of the shield verified by the curator at the museum.",
                )
                chatPlayer(neutral, "Okay. I'll head over there.")
            }
            else -> {
                chatNpc(
                    neutral,
                    "The Shield of Arrav? Yes, I do recall my father put a reward out for that.",
                )
                chatPlayer(neutral, "I'll see what I can do about finding it.")
            }
        }
    }

    private suspend fun Dialogue.completeQuest() {
        objbox(SoaRefs.Certificate, 400, "You show the certificate to the king.")
        chatNpc(
            happy,
            "My goodness! This is for the reward offered by my father many years ago! I never " +
                "thought I would live to see the day when someone came forward to claim it!",
        )
        chatPlayer(
            happy,
            "It was quite the undertaking. I wouldn't have been able to do it without help from " +
                "a fellow adventurer.",
        )
        chatNpc(
            happy,
            "Well, in that case, it's only right that you both be rewarded. I will grant half of " +
                "the reward to you and half to your companion. That means 600 coins for you. " +
                "Enjoy it! You've very much earned it!",
        )

        if (access.blackArmGang >= BlackArmState.Member) {
            access.blackArmGang = BlackArmState.Complete
        } else {
            access.phoenixGang = PhoenixState.Complete
        }

        val slot = access.inv.objs.indexOfFirst { it != null && it.id == CertificateId }
        if (slot >= 0) {
            if (access.invDel(access.inv, SoaRefs.Certificate, 1, slot = slot).failure) {
                return
            }
            access.invAdd(access.inv, SoaRefs.Coins, SoaRefs.CoinReward, slot = slot)
        }

        quest.completeQuest(access)
    }

    private companion object {
        private val CertificateId by lazy { SoaRefs.Certificate.asRSCM(RSCMType.OBJ) }
    }
}
