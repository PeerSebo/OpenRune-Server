package org.rsmod.content.skills.fishing.scripts

import dev.openrune.types.ItemServerType
import dev.openrune.types.enums.enum
import jakarta.inject.Inject
import kotlin.math.floor
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishingClueBottleScript
@Inject
constructor(private val random: GameRandom, private val objRepo: ObjRepository) : PluginScript() {

    private val baseChance: Map<String, Int> by lazy {
        enum<ItemServerType, Int>("heron_pet_chance")
            .backing
            .mapNotNull { (fish, base) -> base?.let { fish.internalName to it } }
            .toMap()
    }

    override fun ScriptContext.startup() {
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            if (product.isBonus || product.skill != "stat.fishing") {
                return@onEvent
            }
            rollClueBottle(product)
        }
    }

    private fun SkillingActionCompleteEvent.rollClueBottle(product: SkillingActionContext.Product) {
        val base = baseChance[product.item] ?: return
        val fishing = player.fishingLvl
        for ((bottle, tier) in CLUE_TIERS) {
            if (bottle in player.inv) {
                continue
            }
            val chance = floor(base / (100.0 + fishing) * tier).toInt()
            if (chance <= 0 || random.of(chance) != 0) {
                continue
            }
            if (player.invAddOrDrop(objRepo, bottle)) {
                player.mes("You find a clue bottle!")
            } else {
                player.mes("A clue bottle falls to the ground as your inventory is too full.")
            }
        }
    }

    private companion object {
        private val CLUE_TIERS =
            listOf(
                "obj.fishing_clue_bottle_beginner" to 0.2,
                "obj.fishing_clue_bottle_easy" to 1.7,
                "obj.fishing_clue_bottle_medium" to 2.0,
                "obj.fishing_clue_bottle_hard" to 3.3,
                "obj.fishing_clue_bottle_elite" to 10.0,
            )
    }
}
