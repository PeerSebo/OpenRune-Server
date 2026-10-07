package org.rsmod.content.skills.farming.configs

import org.rsmod.api.farming.FarmingCompost

object FarmingMessages {
    fun levelRequirement(level: Int, action: String): String =
        "You need a Farming level of $level to $action."

    fun plantAction(cropName: String): String = "plant $cropName"

    const val NEED_RAKE: String = "You need a rake to clear this patch."
    const val NEED_DIBBER: String = "You need a seed dibber to plant seeds."
    const val NEED_SPADE: String = "You need a spade to do that."
    const val NEED_SECATEURS: String = "You need secateurs to prune this."
    const val NEED_PLANT_CURE: String = "You need plant cure to treat this."

    const val RAKE_WEEDS: String = "You rake the weeds out of the patch."
    const val RAKE_ALREADY_CLEAR: String = "This patch is already clear."

    const val PLANTED: String = "You plant the seeds in the patch."
    const val PATCH_OCCUPIED: String = "This patch already has something growing in it."
    const val PATCH_NOT_RAKED: String = "This patch needs to be raked first."
    const val WRONG_PATCH: String = "You can't plant that here."

    fun notEnoughSeeds(count: Int): String = "You need $count seeds to plant this."

    const val COMPOST_APPLIED: String = "You treat the patch with compost."
    const val COMPOST_ALREADY_BETTER: String = "This patch has already been treated."

    const val COMPOST_NOTHING_TO_TREAT: String = "There is no point treating this patch."

    const val BIN_CLOSED: String = "You close the compost bin."
    const val BIN_CONTENTS_ROTTING: String = "The contents have begun to rot."
    const val BIN_TOMATOES_ROTTING: String = "The tomatoes have begun to rot."
    const val BIN_NOT_ROTTED: String = "The vegetation hasn't finished rotting yet."
    const val BIN_TOMATOES_NOT_ROTTED: String = "The tomatoes haven't finished rotting yet."
    const val BIN_OPENED: String = "You open the compost bin."
    const val BIN_NOTHING_TO_TAKE: String = "There's no compost to take at the moment."
    const val BIN_NEED_BUCKET: String = "You need a suitable bucket to do that."
    const val BIN_MUST_BE_EMPTIED: String =
        "The compost bin must be emptied before you can put new items in it."
    const val BIN_EMPTIED: String = "The compost bin is now empty."
    const val BIN_APPLY_POTION: String = "You apply the potion to the compost in the compost bin."
    const val BIN_COMPOST_TO_SUPERCOMPOST: String = "The compost transforms into supercompost."
    const val BIN_SUPERCOMPOST_TO_ULTRACOMPOST: String =
        "The supercompost transforms into ultracompost."
    const val BIN_WRONG_TIER_FOR_POTION: String =
        "You can only apply supercompost potion to a bin containing normal compost."
    const val BIN_WRONG_TIER_FOR_ASH: String =
        "You can only apply volcanic ash to a bin containing super compost."
    const val BIN_NO_SPACE_ROTTEN_TOMATO: String =
        "You don't have space to hold a rotten tomato, squishy though it may be."

    fun binNeedsAsh(amount: Int): String = "You need $amount volcanic ash to do that."

    const val WATERED: String = "You water the crop."
    const val WATER_NOT_NEEDED: String = "This patch doesn't need watering."
    const val WATERING_CAN_EMPTY: String = "Your watering can is empty."

    const val CURED: String = "You treat the crop and it recovers."
    const val PRUNED: String = "You prune away the diseased growth."
    const val PRUNE_FAILED: String = "You fail to prune the diseased growth."
    const val NOTHING_TO_CURE: String = "There is nothing wrong with this patch."

    const val CLEARED_DEAD: String = "You dig up the dead plant."
    const val CLEARED_STUMP: String = "You dig up the stump."

    const val NOT_READY: String = "The crop isn't ready to harvest yet."
    const val NOTHING_LEFT: String = "There is nothing left to pick."
    const val NEEDS_CHECKING: String = "You should check the health of this plant first."

    fun harvested(produceName: String): String {
        val name = produceName.lowercase()
        val produce = when {
            name.startsWith("grimy ") -> "some herbs"
            name in COUNTABLE_PRODUCE -> {
                val article = if (name.first() in "aeiou") "an" else "a"
                "$article $name"
            }
            else -> "some $name"
        }
        return "You pick $produce."
    }

    private val COUNTABLE_PRODUCE = setOf(
        "potato", "onion", "cabbage", "tomato", "strawberry", "watermelon",
        "limpwurt root", "woad leaf", "curry leaf", "potato cactus", "coconut", "pineapple", "papaya fruit",
        "orange", "banana", "apple", "cooking apple", "calquat fruit", "white tree fruit",
        "cactus spine", "mushroom",
    )

    const val CHECK_HEALTH: String =
        "You examine the plant for signs of disease and find that it is in perfect health."
    const val ALREADY_CHECKED: String = "You have already checked this plant."

    const val NEED_AXE: String = "You need an axe to chop down this tree."

    const val CHOPPED_TREE: String =
        "You chop down the tree, leaving a stump behind. The logs crumble away."

    const val CHOPPED_FRUIT_TREE: String =
        "You chop down the fruit tree, leaving a stump behind. The logs crumble away."

    const val NEED_TROWEL: String = "You need a gardening trowel to fill the plant pot."
    const val POT_FILLED: String = "You fill the plant pot with soil."
    const val POT_NOT_EMPTY: String = "That plant pot already has something in it."
    const val SEED_POTTED: String = "You plant the seed in the plant pot."
    const val SEEDLING_WATERED: String = "You water the seedling."
    const val SEEDLING_ALREADY_WATERED: String = "This seedling has already been watered."
    const val SEEDLING_GROWN: String = "Your seedlings have grown into saplings."

    const val SCARECROW_PLACED: String = "You place the scarecrow in the flower patch."
    const val SCARECROW_REMOVED: String = "You remove the scarecrow."

    const val LEPRECHAUN_CHECK_STORAGE: String = "I would like to check my Farming supplies."
    const val LEPRECHAUN_NEVERMIND: String = "Never mind."

    const val LEPRECHAUN_NOTHING_STORED: String = "You have nothing of that type stored."
    const val LEPRECHAUN_STORAGE_FULL: String = "Your storage is full of that item."
    const val LEPRECHAUN_INV_FULL: String = "You don't have enough inventory space."
    const val LEPRECHAUN_NOTHING_TO_STORE: String = "You don't have any of that to store."

    fun leprechaunPartialWithdraw(amount: Int): String =
        "You don't have enough inventory space for all of them. You withdraw $amount."

    const val LEPRECHAUN_ONE_CAN_ONLY: String = "I can only store one watering can at a time."
    const val LEPRECHAUN_CANNOT_STORE_THAT: String = "I can't store that for you."

    const val LEPRECHAUN_DEPOSITED_ALL: String = "You deposit your farming tools."
    const val LEPRECHAUN_NOTHING_TO_DEPOSIT: String = "You have no farming tools to deposit."

    const val LEPRECHAUN_EXCHANGED: String = "You exchange your farming produce for notes."
    const val LEPRECHAUN_NOTHING_TO_EXCHANGE: String =
        "You don't have any farming produce I can note for you."

    fun gardenerOffer(itemName: String, count: Int): String =
        "I'll look after this patch of crops for $count $itemName. Do you want to pay?"

    const val GARDENER_NOTHING_PLANTED: String =
        "There's nothing planted in that patch for me to protect."

    const val GARDENER_CANNOT_PROTECT: String = "I'm afraid I can't protect that for you."

    const val GARDENER_ALREADY_PAID: String = "I'm already looking after this patch for you."

    const val GARDENER_UNNECESSARY: String =
        "There's already a healthy flower watching over this patch - you don't need to pay me."

    fun gardenerNotEnoughPayment(count: Int, itemName: String): String =
        "I'll need $count $itemName to look after this patch."

    const val GARDENER_PAYMENT: String = "You hand over the payment."

    const val GARDENER_PAID: String = "I'll keep an eye on it for you."

    const val GARDENER_DECLINED: String = "Never mind."

    const val GARDENER_CHECK_HEALTH: String = "You should check the health of your tree first."

    const val GARDENER_REMOVE_OFFER: String =
        "I can remove your tree and its roots for 200 coins. Would you like me to do that?"

    const val GARDENER_REMOVE_NO_COINS: String =
        "You don't have enough coins. I need 200 coins to remove your tree."

    const val GARDENER_CANNOT_REMOVE: String = "I'm afraid I can't remove that tree for you."

    const val GARDENER_REMOVED_TREE: String =
        "You pay the gardener 200 coins to remove the tree."

    const val INSPECT_WEEDS: String = "This patch is overgrown with weeds and needs raking."

    const val INSPECT_EMPTY: String = "This patch has been cleared and is ready for planting."

    const val INSPECT_STUMP: String = "All that remains here is a stump."

    const val INSPECT_SCARECROW: String = "A scarecrow stands here, warding off crows."

    fun inspectDead(cropName: String): String =
        "This $cropName has died. It needs to be cleared away."

    fun inspectDiseased(cropName: String): String =
        "This $cropName is diseased and needs treatment."

    fun inspectGrowing(cropName: String, stage: Int, grownStage: Int, compost: FarmingCompost): String =
        "This $cropName is growing well. It is at stage $stage of $grownStage${compostNote(compost)}."

    fun inspectGrown(cropName: String, compost: FarmingCompost): String =
        "This $cropName is fully grown and ready to harvest${compostNote(compost)}."

    fun inspectGrownWithProduce(cropName: String, produceCount: Int, capacity: Int): String =
        "This $cropName is fully grown, holding $produceCount out of a possible $capacity."

    private fun compostNote(compost: FarmingCompost): String =
        when (compost) {
            FarmingCompost.NONE -> ""
            FarmingCompost.COMPOST -> ", and has been treated with compost"
            FarmingCompost.SUPERCOMPOST -> ", and has been treated with supercompost"
            FarmingCompost.ULTRACOMPOST -> ", and has been treated with ultracompost"
        }
}
