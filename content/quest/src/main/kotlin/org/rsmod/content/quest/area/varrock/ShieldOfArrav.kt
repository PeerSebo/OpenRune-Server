package org.rsmod.content.quest.area.varrock

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class ShieldOfArrav
@Inject
constructor(
    locRepo: LocRepository,
    npcRepo: NpcRepository,
    objRepo: ObjRepository,
    aiPlayerInteractions: AiPlayerInteractions,
    takeValidator: PlayerObjTakeValidator,
) :
    QuestScript(
        SoaRefs.QuestKey,
        SoaRefs.QuestVarp,
        rewards { extra("600 Coins") },
        completedQuestItemDisplay = ItemRewardDisplay(SoaRefs.ScrollModel, y = 124),
        questVarbit = SoaRefs.StageVarbit,
        completionJingle = SoaRefs.CompletionJingle,
    ) {

    private val dialogue = ShieldOfArravDialogue(quest, objRepo)
    private val scenery = ShieldOfArravScenery(quest, dialogue, locRepo, npcRepo)
    private val finale = ShieldOfArravFinale(quest)
    private val weaponsmaster =
        ShieldOfArravWeaponsmaster(npcRepo, objRepo, aiPlayerInteractions, takeValidator)
    private val partnerSwap = ShieldOfArravPartnerSwap(objRepo)

    override fun ScriptContext.init() {
        check(quest.maxSteps == SoaStage.Complete) {
            "Shield of Arrav end state is ${quest.maxSteps} in the cache dbrow, but " +
                "SoaStage.Complete is ${SoaStage.Complete}."
        }

        with(dialogue) { register() }
        with(scenery) { register() }
        with(finale) { register() }
        with(weaponsmaster) { register() }
        with(partnerSwap) { register() }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Reldo</col> in the <col=800000>Varrock Palace Library</col>."

    fun reldoQuestOption(access: ProtectedAccess): String = dialogue.reldoQuestOption(access)

    suspend fun reldoQuestBranch(d: Dialogue): Unit = dialogue.reldoQuestBranch(d)

    fun charlieBlackArmAskVisible(access: ProtectedAccess): Boolean =
        dialogue.charlieBlackArmAskVisible(access)

    suspend fun charlieBlackArmAsk(d: Dialogue): Unit = dialogue.charlieBlackArmAsk(d)

    fun charliePhoenixAskVisible(access: ProtectedAccess): Boolean =
        dialogue.charliePhoenixAskVisible(access)

    suspend fun charliePhoenixAsk(d: Dialogue): Unit = dialogue.charliePhoenixAsk(d)

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val phoenix = player.phoenixGang
            val blackArm = player.blackArmGang

            if (phoenix >= PhoenixState.BookRead) {
                strike(
                    "I spoke to Reldo in the Varrock Palace Library. At his suggestion, I read a " +
                        "book called The Shield of Arrav. The book detailed the theft of the " +
                        "legendary Shield of Arrav and the reward on offer to anyone able to " +
                        "recover it."
                )
            }
            if (phoenix == PhoenixState.Accepted) {
                line(
                    "I should find and read a book called <red>The Shield of Arrav</red> in the " +
                        "<red>Varrock Palace Library</red>."
                )
            }
            if (phoenix == PhoenixState.BookRead) {
                line("I should speak to <red>Reldo</red> about what I read in the book.")
            }

            if (phoenix >= PhoenixState.GangsNamed) {
                strike(
                    "I spoke to Reldo about the missing Shield of Arrav. He gave me details on " +
                        "the Phoenix Gang and the Black Arm Gang, the two groups suspected of " +
                        "being in possession of the Shield."
                )
            }

            val onBlackArm = blackArm >= BlackArmState.CrossbowTask
            val onPhoenix = phoenix >= PhoenixState.JonnyTask

            if (phoenix == PhoenixState.GangsNamed && !onBlackArm && !onPhoenix) {
                if (player.soaCharlieMet == 0) {
                    line(
                        "According to <red>Reldo</red>, <red>Baraek</red> in <red>Varrock " +
                            "Square</red> can give me details on the <red>Phoenix Gang</red>, " +
                            "and <red>Charlie the Tramp</red> near the <red>Varrock South " +
                            "Gate</red> may know about the <red>Black Arm Gang</red>."
                    )
                }
            }

            if (player.soaCharlieMet == 1) {
                strike(
                    "Following advice from Reldo, I spoke to Charlie the Tramp near the Varrock " +
                        "South Gate about the Black Arm Gang. He directed me to speak to Katrine " +
                        "in the Gang Headquarters to the west of the Varrock South Gate."
                )
            }
            if (player.soaCharlieMet == 1 && blackArm == BlackArmState.GangsNamed) {
                line(
                    "I should speak to <red>Katrine</red> in the <red>Gang Headquarters</red> to " +
                        "the west of the <red>Varrock South Gate</red>."
                )
            }

            if (blackArm == BlackArmState.CrossbowTask) {
                line(
                    "I spoke to <red>Katrine</red> in the <red>Black Arm Gang Headquarters</red> " +
                        "to the west of the <red>Varrock South Gate</red>. She offered to let me " +
                        "join the <red>Gang</red> in return for stealing two <red>Phoenix " +
                        "Crossbows</red> from the <red>Phoenix Gang</red>. She suggested I speak " +
                        "to <red>Charlie the Tramp</red> about where to find these " +
                        "<red>Crossbows</red>."
                )
                line("")
                line(
                    "According to <red>Reldo</red>, <red>Baraek</red> in <red>Varrock " +
                        "Square</red> can give me details on the <red>Phoenix Gang</red>."
                )
            }

            if (blackArm >= BlackArmState.Member) {
                strike(
                    "I stole two Phoenix Crossbows from the Phoenix Gang's weapon stash and gave " +
                        "them to Katrine. In return, she allowed me to join the Black Arm Gang."
                )
            }

            if (phoenix >= PhoenixState.BaraekTold && onPhoenixTrack(player)) {
                strike(
                    "Following advice from Reldo, I spoke to Baraek in Varrock Square about the " +
                        "Phoenix Gang. He told me I'd find the Gang Headquarters in the VTAM " +
                        "Building south east of the Blue Moon Inn."
                )
            }
            if (phoenix >= PhoenixState.JonnyTask) {
                strike(
                    "I spoke to Straven in the Phoenix Gang Headquarters south east of the Blue " +
                        "Moon Inn. He offered to let me join the Gang in return for killing " +
                        "Jonny the Beard and recovering an Intelligence Report from him."
                )
            }
            if (phoenix == PhoenixState.JonnyTask) {
                line(
                    "I should confront <red>Jonny the Beard</red> in the <red>Blue Moon " +
                        "Inn</red> and take his <red>Intelligence Report</red> to " +
                        "<red>Straven</red>."
                )
            }
            if (phoenix >= PhoenixState.Member) {
                strike(
                    "I confronted and killed Jonny the Beard in the Blue Moon Inn and took an " +
                        "Intelligence Report from him. I gave this Report to Straven and he " +
                        "allowed me to join the Phoenix Gang."
                )
            }

            val member = blackArm >= BlackArmState.Member || phoenix >= PhoenixState.Member
            val gaveHalf = player.soaShieldGiven != 0

            if (member && !gaveHalf) {
                val hq =
                    if (blackArm >= BlackArmState.Member) "Black Arm Gang Headquarters"
                    else "Phoenix Gang Headquarters"
                val carriesHalf =
                    player.soaCarries(SoaRefs.ShieldHalfLeft) ||
                        player.soaCarries(SoaRefs.ShieldHalfRight)
                if (carriesHalf) {
                    line(
                        "I should take my <red>Shield Half</red> to the <red>Varrock " +
                            "Museum</red> so its authenticity can be verified."
                    )
                } else {
                    line(
                        "I should search the <red>$hq</red> for their half of the <red>Shield " +
                            "of Arrav</red>."
                    )
                }
            }

            if (gaveHalf) {
                strike(
                    "I took my Shield Half to the Varrock Museum and Curator Haig Halen " +
                        "confirmed it to be genuine. He then gave me two Half-Certificates."
                )
                line(
                    "I should swap one <red>Half-Certificate</red> with a <red>Fellow " +
                        "Adventurer</red> who recovered the other <red>Shield Half</red>, " +
                        "combine the halves, and take the <red>Full Certificate</red> to " +
                        "<red>King Roald</red> in <red>Varrock Palace</red>."
                )
            }
        }

    private fun onPhoenixTrack(player: ProtectedAccess): Boolean =
        player.blackArmGang < BlackArmState.CrossbowTask

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I spoke to Reldo in the Varrock Palace Library. At his suggestion, I read a " +
                    "book called The Shield of Arrav. The book detailed the theft of the " +
                    "legendary Shield of Arrav and the reward on offer to anyone able to recover " +
                    "it."
            )
            line(
                "I spoke to Reldo about the missing Shield of Arrav. He gave me details on the " +
                    "Phoenix Gang and the Black Arm Gang, the two groups suspected of being in " +
                    "possession of the Shield."
            )

            if (player.blackArmGang >= BlackArmState.Complete) {
                line(
                    "Following advice from Reldo, I spoke to Charlie the Tramp near the Varrock " +
                        "South Gate about the Black Arm Gang. He directed me to speak to Katrine " +
                        "in the Gang Headquarters to the west of the Varrock South Gate."
                )
                line(
                    "I spoke to Katrine in the Black Arm Gang Headquarters to the west of the " +
                        "Varrock South Gate. She offered to let me join the Gang in return for " +
                        "stealing two Phoenix Crossbows from the Phoenix Gang."
                )
                line(
                    "I stole two Phoenix Crossbows from the Phoenix Gang's weapon stash and " +
                        "gave them to Katrine. She then allowed me to join the Black Arm Gang."
                )
                line(
                    "I searched the Black Arm Gang Headquarters and found half of the Shield of " +
                        "Arrav. I took this Shield Half to the Varrock Museum and Curator Haig " +
                        "Halen confirmed it to be genuine. He then gave me two " +
                        "Half-Certificates. Meanwhile, a Fellow Adventurer was able to recover " +
                        "the other Shield Half and obtain two Half-Certificates for themselves. " +
                        "We swapped these Half-Certificates, allowing us to make a Full " +
                        "Certificate each."
                )
            } else {
                line(
                    "Following advice from Reldo, I spoke to Baraek in Varrock Square about the " +
                        "Phoenix Gang. He told me I'd find the Gang Headquarters in the VTAM " +
                        "Building south east of the Blue Moon Inn."
                )
                line(
                    "I spoke to Straven in the Phoenix Gang Headquarters south east of the Blue " +
                        "Moon Inn. He offered to let me join the Gang in return for killing " +
                        "Jonny the Beard and recovering an Intelligence Report from him."
                )
                line(
                    "I confronted and killed Jonny the Beard in the Blue Moon Inn and took an " +
                        "Intelligence Report from him. I gave this Report to Straven and he " +
                        "allowed me to join the Phoenix Gang."
                )
                line(
                    "I searched the Phoenix Gang Headquarters and found half of the Shield of " +
                        "Arrav. I took this Shield Half to the Varrock Museum and Curator Haig " +
                        "Halen confirmed it to be genuine. He then gave me two " +
                        "Half-Certificates. Meanwhile, a Fellow Adventurer was able to recover " +
                        "the other Shield Half and obtain two Half-Certificates for themselves. " +
                        "We swapped these Half-Certificates, allowing us to make a Full " +
                        "Certificate each."
                )
            }

            line(
                "I took my Certificate to King Roald in Varrock Palace and he rewarded me for " +
                    "helping to return the Shield of Arrav."
            )
        }
}
