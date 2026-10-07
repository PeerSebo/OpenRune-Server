package org.rsmod.content.quest.area.lumbridge.lostcity

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class LostCityQuest : QuestScript(
    "quest_lostcity",
    "varp.zanaris",
    rewards { extra("Access to Zanaris") },
    ItemRewardDisplay(DRAMEN_STAFF),
    completionJingle = 154,
) {
    val thankedWarrior = quest.boolean("THANKED_WARRIOR", "varp.lost_city_thanked_warrior", "varp.lost_city_thanked_warrior_migrated")

    val spiritDefeated = quest.temporaryBoolean("SPIRIT_DEFEATED", "varp.lost_city_spirit_defeated")

    val searchedByMonks = quest.temporaryBoolean("SEARCHED_BY_MONKS", "varp.lost_city_searched_by_monks")

    private val staffIds = FAIRY_STAFFS.map { it.asRSCM(RSCMType.OBJ) }.toSet()

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to the <col=800000>Warrior</col> at the adventurers' camp on the north-west " +
            "edge of <col=800000>Lumbridge Swamp</col>, south of Draynor Village."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "A group of adventurers have made camp in <red>Lumbridge Swamp</red>. The " +
                    "<red>Warrior</red> let slip that they are searching for Zanaris, a " +
                    "hidden city, and that a <red>leprechaun</red> hiding in one of the trees " +
                    "near the camp knows the way there.",
            ) {}

            objective(
                "I should look for the tree the leprechaun is hiding in and <red>chop</red> it " +
                    "to get him to come out.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_STARTED }
            }

            objective(
                "<red>Shamus</red> the leprechaun told me the doorway of the <red>shed</red> " +
                    "in the middle of the swamp is a portal to Zanaris, but only if I am " +
                    "carrying a <red>Dramen staff</red>.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) >= STAGE_MET_SHAMUS }
            }

            objective(
                "Dramen staffs are crafted from branches of the <red>Dramen tree</red>, which " +
                    "grows in a cave on the island of <red>Entrana</red>. The monks run a ship " +
                    "there from <red>Port Sarim</red>, but they will not let me bring any " +
                    "weapons or armour.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_MET_SHAMUS }
            }

            objective(
                "I defeated the <red>Tree spirit</red> guarding the Dramen tree. I can now cut " +
                    "a <red>Dramen branch</red> from it and use a <red>knife</red> on the " +
                    "branch to carve a staff.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_SPIRIT_DEFEATED }
                hasItem("dramen_branch", "I have a Dramen branch.").strike()
                hasItem("dramen_staff", "I have made a Dramen staff.").strike()
            }

            objective(
                "I should wield the <red>Dramen staff</red> and enter the <red>shed</red> in " +
                    "the middle of Lumbridge Swamp.",
            ) {
                visibleWhen {
                    quest.getQuestStage(access.player) == STAGE_SPIRIT_DEFEATED &&
                        (access.inv.contains(DRAMEN_STAFF) || wieldsDramenStaff(access.player))
                }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "A group of adventurers camped in Lumbridge Swamp were searching for the lost " +
                    "city of Zanaris. Their Warrior let slip that a leprechaun hiding in a " +
                    "nearby tree knew the way.",
            )
            line(
                "Shamus the leprechaun told me the shed in the swamp is a portal to Zanaris " +
                    "for anyone carrying a Dramen staff. I sailed to Entrana, fought the " +
                    "Tree spirit guarding the Dramen tree in the caves beneath the island, " +
                    "cut a branch and carved it into a staff.",
            )
            line("With the staff in hand I stepped through the shed door into Zanaris.")
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun wieldsDramenStaff(player: Player): Boolean {
        val weapon = player.righthand ?: return false
        return weapon.id in staffIds
    }

    fun complete(access: ProtectedAccess) {
        val remaining = quest.maxSteps - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_MET_SHAMUS = 2
        const val STAGE_SPIRIT_DEFEATED = 3

        const val CRAFTING_REQ = 31
        const val WOODCUTTING_REQ = 36
        const val RECOMMENDED_COMBAT = 45

        const val DRAMEN_BRANCH = "obj.dramen_branch"
        const val DRAMEN_STAFF = "obj.dramen_staff"

        val FAIRY_STAFFS = listOf(DRAMEN_STAFF, "obj.lunar_moonclan_liminal_staff")
    }
}
