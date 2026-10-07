package org.rsmod.content.quest.area.goblinvillage

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

// The stage shares a varp with other flags; write through its varbit.
class GoblinDiplomacy
@Inject
constructor(
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) :
    QuestScript(
        GoblinDiplomacyState.QuestKey,
        GoblinDiplomacyState.QuestVarp,
        rewards {
            xp("stat.crafting", GoblinDiplomacyState.CraftingXp)
            item(GoblinDiplomacyState.GoldBar, label = "A Gold Bar")
        },
        ItemRewardDisplay(GoblinDiplomacyState.BrownMail),
        questVarbit = GoblinDiplomacyState.StageVarbit,
        completionJingle = GoblinDiplomacyState.CompletionJingle,
    ) {

    private val cutscene = GoblinDiplomacyCutscene(quest, regionRepo, npcRepo, locRepo, collision)
    private val dialogue = GoblinDiplomacyDialogue(quest, random, cutscene)

    override fun ScriptContext.init() {
        check(quest.maxSteps == GoblinDiplomacyState.Complete) {
            "Goblin Diplomacy end state is ${quest.maxSteps} in the cache dbrow, but " +
                "GoblinDiplomacyState.Complete is ${GoblinDiplomacyState.Complete}."
        }

        assertGoblinDiplomacySceneCoords()

        with(dialogue) { register() }
        with(cutscene) { register() }

        for (crate in GoblinMailCrate.entries) {
            onOpLoc1(crate.loc) { searchCrate(crate) }
        }

        onOpHeldU(GoblinDiplomacyState.BlueDye, GoblinDiplomacyState.BrownMail) {
            dyeMail(GoblinDiplomacyState.BlueDye, GoblinDiplomacyState.BlueMail, "blue")
        }
        onOpHeldU(GoblinDiplomacyState.OrangeDye, GoblinDiplomacyState.BrownMail) {
            dyeMail(GoblinDiplomacyState.OrangeDye, GoblinDiplomacyState.OrangeMail, "orange")
        }

        for (mail in Mails) {
            onOpHeld2(mail) { mes(TooSmall) }
        }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Goblin Generals</col> in <col=800000>Goblin Village</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val stage = quest.getQuestStage(player.player)

            if (stage <= GoblinDiplomacyState.WantOrange) {
                line(StartedPresent)
            } else {
                strike(StartedPast)
            }

            if (stage == GoblinDiplomacyState.WantBlue) {
                line(OrangeRejected)
            } else if (stage > GoblinDiplomacyState.WantBlue) {
                strike(OrangeRejected)
            }

            if (stage >= GoblinDiplomacyState.WantBrown) {
                line(BlueRejected)
            }

            line("")
            line(player.closingHint(stage))
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(StartedPast)
            line(OrangeRejected)
            line(BlueRejected)
            line(
                "I gave General Wartface and General Bentnoze some of the original Brown Goblin " +
                    "Mail, and it turned out to be their favourite. They're clearly not the " +
                    "smartest, but that's goblins for you."
            )
        }

    private fun ProtectedAccess.closingHint(stage: Int): String =
        when (stage) {
            GoblinDiplomacyState.WantOrange -> orangeHint()
            GoblinDiplomacyState.WantBlue -> blueHint()
            GoblinDiplomacyState.WantBrown ->
                if (carryingObj(GoblinDiplomacyState.BrownMail)) {
                    "I should give them the Brown Goblin Mail that I have."
                } else {
                    "I should be able to find some Brown Goblin Mail in crates around Goblin Village."
                }
            else -> "Perhaps they'll have an idea on how I can get some."
        }

    private fun ProtectedAccess.orangeHint(): String {
        val hasMail = carryingObj(GoblinDiplomacyState.BrownMail)
        val findMail = "I should be able to find some Goblin Mail in crates around Goblin Village."
        val aggie =
            "Aggie in Draynor Village can make some Dye for me to recolour Goblin Mail with."
        val witch =
            "Apparently there's a Witch in Draynor Village who makes Dye that can be used to " +
                "recolour Goblin Mail."

        return when {
            carryingObj(GoblinDiplomacyState.OrangeMail) ->
                "I should give them the Orange Goblin Mail that I have."
            gobdipMetAggie != 0 && hasMail -> aggie
            gobdipMetAggie != 0 -> "$aggie $findMail"
            gobdipKnowsArmour != 0 && gobdipKnowsDye != 0 && hasMail -> witch
            gobdipKnowsArmour != 0 && gobdipKnowsDye != 0 -> "$witch $findMail"
            gobdipKnowsArmour != 0 ->
                "I should be able to find some Goblin Mail in crates around Goblin Village, " +
                    "though I'll need to find a way to change the colour. General Wartface and " +
                    "General Bentnoze might have an idea on how to do that."
            else -> "Perhaps they'll have an idea on how I can get some."
        }
    }

    private fun ProtectedAccess.blueHint(): String {
        val hasMail = carryingObj(GoblinDiplomacyState.BrownMail)
        val aggie =
            "Aggie in Draynor Village can make some Dye for me to recolour Goblin Mail with."

        return when {
            carryingObj(GoblinDiplomacyState.BlueMail) ->
                "I should give them the Blue Goblin Mail that I have."
            hasMail && carryingObj(GoblinDiplomacyState.BlueDye) ->
                "I should use my Blue Dye to recolour some Goblin Mail and then give it to them."
            hasMail -> aggie
            else ->
                "$aggie I should be able to find some Goblin Mail in crates around Goblin Village."
        }
    }

    private suspend fun ProtectedAccess.searchCrate(crate: GoblinMailCrate) {
        if (vars[crate.varbit] != 0) {
            mes(EmptyCrate)
            return
        }

        // Deliver the item before setting the crate flag so a full inventory cannot consume the reward.
        if (invAdd(inv, GoblinDiplomacyState.BrownMail, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }

        vars[crate.varbit] = 1
        objbox(GoblinDiplomacyState.BrownMail, "You find some goblin mail in the crate.")
    }

    // Commit both ingredient deletes and the result before announcing a successful dye.
    private suspend fun ProtectedAccess.dyeMail(dye: String, result: String, colour: String) {
        if (invDel(inv, GoblinDiplomacyState.BrownMail, 1).failure) {
            return
        }
        if (invDel(inv, dye, 1).failure) {
            invAdd(inv, GoblinDiplomacyState.BrownMail, 1)
            return
        }
        if (invAdd(inv, result, 1).failure) {
            invAdd(inv, GoblinDiplomacyState.BrownMail, 1)
            invAdd(inv, dye, 1)
            return
        }

        mes("You dye the goblin mail $colour.")
        soundSynth(GoblinDiplomacyState.DyeSound)
    }

    private companion object {
        private const val TooSmall = "That armour is too small for a human."

        private const val EmptyCrate = "You search the crate, but find nothing of interest."

        private val Mails =
            listOf(
                GoblinDiplomacyState.BrownMail,
                GoblinDiplomacyState.OrangeMail,
                GoblinDiplomacyState.BlueMail,
            )

        private const val StartedPresent =
            "I spoke to General Wartface and General Bentnoze in Goblin Village. They've been " +
                "trying to pick a new colour of armour for the goblins in the village, but have " +
                "been unable to agree on a colour. I offered to help, so they asked me to bring " +
                "them some Orange Goblin Mail to try out."

        private const val StartedPast =
            "I spoke to General Wartface and General Bentnoze in Goblin Village. They were trying " +
                "to pick a new colour of armour for the goblins in the village, but were unable " +
                "to agree on a colour. I offered to help, so they asked me to bring them some " +
                "Orange Goblin Mail to try out."

        private const val OrangeRejected =
            "I gave some Orange Goblin Mail to General Wartface and General Bentnoze, but they " +
                "didn't like it. They asked me to bring them some Blue Goblin Mail to try out " +
                "instead."

        private const val BlueRejected =
            "I gave some Blue Goblin Mail to General Wartface and General Bentnoze, but they " +
                "didn't like it. They asked me to bring them some of their original Brown Goblin " +
                "Mail to try out instead."
    }
}
