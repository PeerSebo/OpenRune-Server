package org.rsmod.content.quest.area.varrock.gertrudescat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.other.pets.cats.CatColour
import org.rsmod.content.other.pets.cats.CatPet
import org.rsmod.content.other.pets.cats.CatPetManager
import org.rsmod.content.other.pets.cats.CatStage
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class GertrudesCatQuest @Inject constructor(private val cats: CatPetManager) : QuestScript(
    "quest_gertrudescat",
    "varp.fluffs",
    rewards {
        xp("stat.cooking", 1525.0)
        item(CHOCOLATE_CAKE)
        item(STEW)
        extra("A kitten to raise")
    },
    ItemRewardDisplay(KITTEN_DISPLAY),
    completionJingle = 154,
) {
    val kittenCrate = quest.int(
        "KITTEN_CRATE", "varp.gertrudes_cat_kitten_crate", "varbit.gertrudes_cat_kitten_crate_migrated",
        default = -1,
    )

    val foundFluffs = quest.flag(
        "FOUND_FLUFFS", "varbit.gertrudes_cat_found_fluffs", "varbit.gertrudes_cat_found_fluffs_migrated",
    )

    val toldSardines = quest.flag(
        "TOLD_SARDINES", "varbit.gertrudes_cat_told_sardines", "varbit.gertrudes_cat_told_sardines_migrated",
    )

    override fun ScriptContext.init() {}

    override fun subTitle(): String =
        "talking to <col=800000>Gertrude</col> in her house <col=800000>west of Varrock</col>, " +
            "south of the Cooks' Guild."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Gertrude</red>, who lives west of Varrock, has lost her cat <red>Fluffs</red>. " +
                    "She asked me to find her while she looks after her sons.",
            ) {}

            objective(
                "Gertrude's sons, <red>Shilop</red> and <red>Wilough</red>, saw Fluffs last. They " +
                    "should be in Varrock's market place.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) < STAGE_GAVE_MILK }
                stageAtLeast(
                    STAGE_PAID_KIDS,
                    "For 100 coins the boys told me Fluffs followed them to their secret play " +
                        "area: the old <red>lumber yard</red> north-east of Varrock, past the Jolly " +
                        "Boar Inn. I'll need to climb the broken fence to get in.",
                )
            }

            objective(
                "Fluffs is up the ladder in the lumber yard, but she hisses at me and won't " +
                    "leave. Maybe she is thirsty. A <red>bucket of milk</red> might help.",
            ) {
                visibleWhen {
                    val stage = quest.getQuestStage(access.player)
                    stage >= STAGE_PAID_KIDS && stage < STAGE_GAVE_MILK && foundFluffs.get(access.player)
                }
            }

            objective(
                "Fluffs drank the milk but still won't leave. Gertrude says she loves " +
                    "<red>doogle sardines</red>: a raw sardine rubbed with doogle leaves, which grow " +
                    "in the woods behind Gertrude's house.",
            ) {
                visibleWhen {
                    val stage = quest.getQuestStage(access.player)
                    stage == STAGE_GAVE_MILK
                }
                custom(
                    !toldSardines.get(access.player),
                    "Fluffs drank the milk but still won't leave. Maybe she is hungry. Gertrude " +
                        "might know what she likes to eat.",
                )
            }

            objective(
                "Fluffs ate the sardine, but she still seems afraid to leave. I can hear " +
                    "<red>kittens mewing</red> from the crates around the lumber yard. I should " +
                    "search them.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_GAVE_SARDINE }
                hasItem("gertrudekittens", "I found Fluffs' kitten in one of the crates. I should take it up to her.")
            }

            objective(
                "Fluffs ran off home with her kitten. I should tell <red>Gertrude</red> the good news.",
            ) {
                visibleWhen { quest.getQuestStage(access.player) == STAGE_KITTEN_RETURNED }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Gertrude had lost her cat Fluffs. Her sons told me, for a price, that Fluffs had " +
                    "followed them to the old lumber yard north-east of Varrock.",
            )
            line(
                "Fluffs wouldn't leave until I gave her milk and a doogle sardine, and even then " +
                    "she stayed until I found her kitten in one of the crates and brought it to her.",
            )
            line(
                "Gertrude was so grateful that she gave me one of her kittens to raise, along " +
                    "with some food. She will sell me another kitten if I ever need one.",
            )
        }

    fun randomKitten(): CatPet = CatPet(CatStage.KITTEN, KITTEN_COLOURS.random())

    fun ProtectedAccess.hasAnyCat(): Boolean {
        if (cats.hasFollower(player)) {
            return true
        }
        return CatPet.all.any { inv.count(it.obj) > 0 || bank.count(it.obj) > 0 }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_PAID_KIDS = 2
        const val STAGE_GAVE_MILK = 3
        const val STAGE_GAVE_SARDINE = 4
        const val STAGE_KITTEN_RETURNED = 5
        const val STAGE_COMPLETE = 6

        const val KITTEN_DISPLAY = "obj.kittenobject"
        const val CHOCOLATE_CAKE = "obj.chocolate_cake"
        const val STEW = "obj.stew"
        const val FLUFFS_KITTEN = "obj.gertrudekittens"
        const val SEASONED_SARDINE = "obj.seasoned_sardine"
        const val RAW_SARDINE = "obj.raw_sardine"
        const val DOOGLE_LEAVES = "obj.doogleleaves"
        const val BUCKET_OF_MILK = "obj.bucket_milk"
        const val BUCKET_EMPTY = "obj.bucket_empty"

        const val KIDS_FEE = 100
        const val KITTEN_PRICE = 100

        val KITTEN_COLOURS = CatColour.entries.filter { it != CatColour.HELL }
    }
}
