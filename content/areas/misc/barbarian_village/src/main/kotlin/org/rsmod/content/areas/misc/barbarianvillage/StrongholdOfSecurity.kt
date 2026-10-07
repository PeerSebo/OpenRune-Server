package org.rsmod.content.areas.misc.barbarianvillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.misc.barbarianvillage.configs.StrongholdRefs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillMultiSelection
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StrongholdOfSecurity @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    private var ProtectedAccess.flapUnlocked by boolVarBit(StrongholdRefs.FlapVarbit)
    private var ProtectedAccess.slapHeadUnlocked by boolVarBit(StrongholdRefs.SlapHeadVarbit)
    private var ProtectedAccess.ideaUnlocked by boolVarBit(StrongholdRefs.IdeaVarbit)
    private var ProtectedAccess.stampUnlocked by boolVarBit(StrongholdRefs.StampVarbit)

    override fun ScriptContext.startup() {
        onOpLoc1(StrongholdRefs.Entrance) { enterStronghold() }
        onOpLoc1(StrongholdRefs.ExplorerBag) { searchBag() }
        onOpLoc1(StrongholdRefs.CreditsPlaque) { readPlaque() }

        onOpLoc1(StrongholdRefs.WarLadderDown) { climb(StrongholdRefs.FamineStart, DownMessage) }
        onOpLoc1(StrongholdRefs.FamineLadderDown) {
            climb(StrongholdRefs.PestilenceStart, DownMessage)
        }
        onOpLoc1(StrongholdRefs.PestilenceLadderDown) {
            climb(StrongholdRefs.DeathStart, DownMessage)
        }

        onOpLoc1(StrongholdRefs.FamineLadderUp) { climb(StrongholdRefs.WarStart, UpMessage) }
        onOpLoc1(StrongholdRefs.PestilenceLadderUp) { climb(StrongholdRefs.FamineStart, UpMessage) }
        onOpLoc1(StrongholdRefs.DeathLadderUp) { climb(StrongholdRefs.PestilenceStart, UpMessage) }

        onOpLoc1(StrongholdRefs.WarLadderUp) { climb(StrongholdRefs.SurfaceExit, TwistingMessage) }

        onOpLoc1(StrongholdRefs.WarChain) { climb(StrongholdRefs.WarStart, null) }
        onOpLoc1(StrongholdRefs.FamineRope) { climb(StrongholdRefs.FamineStart, null) }
        onOpLoc1(StrongholdRefs.PestilenceRope) { climb(StrongholdRefs.PestilenceStart, null) }
        onOpLoc1(StrongholdRefs.DeathRope) { deathRope(it.loc) }

        onOpLoc1(StrongholdRefs.WarPortal) {
            portal(WarTreasureRoom, flapUnlocked, WarCombatRequirement)
        }
        onOpLoc1(StrongholdRefs.FaminePortal) {
            portal(FamineTreasureRoom, slapHeadUnlocked, FamineCombatRequirement)
        }
        onOpLoc1(StrongholdRefs.PestilencePortal) {
            portal(PestilenceTreasureRoom, ideaUnlocked, PestilenceCombatRequirement)
        }
        onOpLoc1(StrongholdRefs.DeathPortal) {
            portal(DeathTreasureRoom, stampUnlocked, DeathCombatRequirement)
        }

        onOpLoc1(StrongholdRefs.WarChest) { claimWarChest() }
        onOpLoc1(StrongholdRefs.FamineSack) { claimFamineSack() }
        onOpLoc1(StrongholdRefs.PestilenceChest) { claimPestilenceChest() }
        onOpLoc1(StrongholdRefs.DeathCradle) { cradleOfLife() }
    }

    private suspend fun ProtectedAccess.enterStronghold() {
        arriveDelay()
        telejump(StrongholdRefs.FirstLevelEntry)
        mesbox(
            "You squeeze through the hole and find a ladder a few feet down leading into the " +
                "Stronghold of Security."
        )
    }

    private suspend fun ProtectedAccess.searchBag() {
        arriveDelay()
        soundSynth(StrongholdRefs.BagSound)
        invAddOrDrop(objRepo, StrongholdRefs.NotesBook)
        mesbox("You rummage around in the dead explorer's bag.....")
        mesbox("You find a book of hand written notes.")
    }

    private suspend fun ProtectedAccess.readPlaque() {
        arriveDelay()
        ifSetText("component.messagescroll2:ms1", "")
        ifSetText("component.messagescroll2:ms2", "<col=ffffff>Thanks to the following who made an")
        ifSetText("component.messagescroll2:ms3", "<col=ffffff>offering:")
        ifSetText("component.messagescroll2:ms4", "")
        ifSetText("component.messagescroll2:ms5", "")
        ifSetText("component.messagescroll2:ms6", "<col=ffffff>TwoTwentyTwo, OblongNoodle,")
        ifSetText("component.messagescroll2:ms7", "<col=ffffff>Big Sage, A Classy Boy,")
        ifSetText("component.messagescroll2:ms8", "<col=ffffff>Trick Deck, Superfire444,")
        ifSetText("component.messagescroll2:ms9", "<col=ffffff>Rainwaker, DemiGoo,")
        ifSetText("component.messagescroll2:ms10", "<col=ffffff>Yak in a hat, No1pk,")
        ifSetText("component.messagescroll2:ms11", "<col=ffffff>& Sauron</col>")
        ifSetText("component.messagescroll2:ms12", "")
        ifOpenMain(StrongholdRefs.CreditsInterface)
    }

    private suspend fun ProtectedAccess.climb(destination: CoordGrid, message: String?) {
        arriveDelay()
        anim(StrongholdRefs.ClimbSeq)
        delay(1)
        telejump(destination)
        message?.let { mes(it, ChatType.Spam) }
    }

    private suspend fun ProtectedAccess.deathRope(loc: BoundLocInfo) {
        if (loc.coords != StrongholdRefs.DeathExitRope) {
            climb(StrongholdRefs.DeathStart, null)
            return
        }

        arriveDelay()
        anim(StrongholdRefs.ClimbSeq)
        delay(1)
        mes("You shin up the rope, squeeze through a passage then climb a ladder.", ChatType.Spam)
        delay(1)
        telejump(StrongholdRefs.SurfaceExit)
        mes(TwistingMessage, ChatType.Spam)
    }

    private suspend fun ProtectedAccess.portal(
        destination: CoordGrid,
        rewardClaimed: Boolean,
        combatRequirement: Int,
    ) {
        arriveDelay()

        if (!rewardClaimed && player.combatLevel < combatRequirement) {
            mes(
                "You are not of sufficient experience to take the shortcut through this level.",
                ChatType.GameMessage,
            )
            return
        }

        telejump(destination)
        mes("You enter the portal to be whisked through to the treasure room.", ChatType.Spam)
    }

    private suspend fun ProtectedAccess.claimWarChest() {
        claimReward(
            alreadyClaimed = flapUnlocked,
            flavour = "The box hinges creak and appear to be forming audible words....",
            emote = "Flap",
            coins = 2_000,
            jingle = StrongholdRefs.WarJingle,
        ) {
            flapUnlocked = true
        }
    }

    private suspend fun ProtectedAccess.claimFamineSack() {
        claimReward(
            alreadyClaimed = slapHeadUnlocked,
            flavour = "The grain shifts in the sack, sighing audible words....",
            emote = "Slap Head",
            coins = 3_000,
            jingle = StrongholdRefs.FamineJingle,
        ) {
            slapHeadUnlocked = true
        }
    }

    private suspend fun ProtectedAccess.claimPestilenceChest() {
        claimReward(
            alreadyClaimed = ideaUnlocked,
            flavour = "The box hinges creak and appear to be forming audible words....",
            emote = "Idea",
            coins = 5_000,
            jingle = StrongholdRefs.PestilenceJingle,
            restoreStats = true,
        ) {
            ideaUnlocked = true
        }
    }

    private suspend fun ProtectedAccess.claimReward(
        alreadyClaimed: Boolean,
        flavour: String,
        emote: String,
        coins: Int,
        jingle: Int,
        restoreStats: Boolean = false,
        unlock: ProtectedAccess.() -> Unit,
    ) {
        arriveDelay()

        if (alreadyClaimed) {
            mesbox("You have already claimed your reward from this level.")
            return
        }

        soundSynth(StrongholdRefs.CreakSound)
        mesbox(flavour)

        unlock()

        if (restoreStats) {
            mes("You feel refreshed and renewed.", ChatType.GameMessage)
            statRestoreAll(allStats())
        }

        invAddOrDrop(objRepo, StrongholdRefs.Coins, coins)
        midiJingle(jingle)

        mesbox(
            "...congratulations adventurer, you have been deemed worthy of this reward. You have " +
                "also unlocked the $emote emote!"
        )
    }

    private suspend fun ProtectedAccess.cradleOfLife() {
        arriveDelay()
        soundSynth(StrongholdRefs.ChoirSound)

        if (stampUnlocked) {
            reclaimBoots()
            return
        }

        mesbox(
            "As your hand touches the cradle, you hear a voice in your head of a million dead " +
                "adventurers..."
        )
        mesbox(
            "... welcome adventurer... you have learnt the value of securing your account, and " +
                "may claim your prize..."
        )
        doubleobjbox(
            StrongholdRefs.FancyBoots,
            StrongholdRefs.FightingBoots,
            "You may claim these boots. They protect your feet equally, but they look very " +
                "different.",
        )
        doubleobjbox(
            StrongholdRefs.FightingBoots,
            StrongholdRefs.FancierBoots,
            "You can return here for more boots at any time, or to get replacements if you lose " +
                "them.",
        )

        val chosen = chooseBoots() ?: return
        stampUnlocked = true
        invAddOrDrop(objRepo, chosen.entry.internal)
        midiJingle(StrongholdRefs.DeathJingle)
        objbox(
            chosen.entry.internal,
            "You claim your prize: ${chosen.entry.item.name} You have unlocked the 'Stamp Foot' " +
                "emote.",
        )
    }

    private suspend fun ProtectedAccess.reclaimBoots() {
        val accepted =
            choice2(
                "Accept",
                true,
                "Decline",
                false,
                title = "You hear the voices offering you more boots!",
            )
        if (!accepted) {
            return
        }

        val chosen = chooseBoots() ?: return
        invAddOrDrop(objRepo, chosen.entry.internal)
        objbox(chosen.entry.internal, "You retrieve your prize: ${chosen.entry.item.name}")
    }

    // The boots menu must preserve native skillmulti selection counts and preview ordering.
    private suspend fun ProtectedAccess.chooseBoots(): SkillMultiSelection? {
        var chosen: SkillMultiSelection? = null
        val config =
            SkillMultiConfig(
                actionType = SkillingActionType.TAKE_ALT_1,
                verb = "claim",
                titleOverride = "Select the boots you want.",
                trailingArg = 1,
                maxCountProvider = { inv, _ -> inv.freeSpace().coerceAtLeast(1) },
                entries =
                    listOf(
                        SkillMultiEntry(StrongholdRefs.FancyBoots),
                        SkillMultiEntry(StrongholdRefs.FightingBoots),
                        SkillMultiEntry(StrongholdRefs.FancierBoots),
                    ),
            )
        openSkillMulti(config) { chosen = it }
        return chosen
    }

    private fun allStats(): List<String> =
        ServerCacheManager.getStats().values.map {
            RSCM.getReverseMapping(RSCMType.STAT, it.id)
        }

    private companion object {
        private const val DownMessage = "You climb down the ladder to the next level."

        private const val UpMessage = "You climb up the ladder to the level above."

        private const val TwistingMessage =
            "You climb up the ladder which seems to twist and wind in all directions."

        private val WarTreasureRoom = CoordGrid(1914, 5222, 0)

        private val FamineTreasureRoom = CoordGrid(2021, 5223, 0)

        private val PestilenceTreasureRoom = CoordGrid(2147, 5284, 0)

        private val DeathTreasureRoom = CoordGrid(2340, 5219, 0)

        private const val WarCombatRequirement = 26

        private const val FamineCombatRequirement = 51
        private const val PestilenceCombatRequirement = 76

        private const val DeathCombatRequirement = Int.MAX_VALUE
    }
}
