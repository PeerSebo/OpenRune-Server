package org.rsmod.content.quest.area.alkharid

import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player

internal object PrinceAliRescueState {
    const val QuestKey: String = "quest_princealirescue"

    const val QuestVarp: String = "varp.princequest"

    // The stage ladder is not contiguous; use named setQuestStage values.

    const val NotStarted: Int = 0

    const val TalkToOsman: Int = 10

    const val Gathering: Int = 20

    const val DealWithGuard: Int = 30

    const val GuardHadOneBeer: Int = 31

    const val GuardDrunk: Int = 40

    const val KeliTiedUp: Int = 50

    const val PrinceFreed: Int = 100

    const val Complete: Int = 110

    const val SoftClay: String = "obj.softclay"
    const val KeyPrint: String = "obj.keyprint"
    const val PrincesKey: String = "obj.princeskey"
    const val BronzeBar: String = "obj.bronze_bar"

    const val BallOfWool: String = "obj.ball_of_wool"
    const val PlainWig: String = "obj.plainwig"
    const val BlondWig: String = "obj.blondwig"
    const val YellowDye: String = "obj.yellowdye"
    const val PinkSkirt: String = "obj.pink_skirt"
    const val SkinPaste: String = "obj.skinpaste"

    const val Ashes: String = "obj.ashes"
    const val PotOfFlour: String = "obj.pot_flour"
    const val Redberries: String = "obj.redberries"

    const val Beer: String = "obj.beer"
    const val Rope: String = "obj.rope"
    const val Coins: String = "obj.coins"

    val WaterContainers: List<String> = listOf("obj.bucket_water", "obj.jug_water")

    val EmptiedWaterContainer: Map<String, String> =
        mapOf("obj.bucket_water" to "obj.bucket_empty", "obj.jug_water" to "obj.jug_empty")

    const val WigWoolCost: Int = 3

    const val BeersToDrunk: Int = 3

    const val RecommendedCombatLevel: Int = 10

    const val KeyCraftingXp: Double = 30.0

    fun quest(): Quest =
        checkNotNull(Quest.get(QuestKey)) {
            "Prince Ali Rescue is not registered; is PrinceAliRescue in the plugin list?"
        }

    fun stage(player: Player): Int = quest().getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= TalkToOsman

    fun isComplete(player: Player): Boolean = stage(player) >= Complete
}
