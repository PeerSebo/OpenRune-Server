package org.rsmod.content.skills.mining.scripts

import jakarta.inject.Inject
import kotlin.math.floor
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MiningSideDropsScript
@Inject
constructor(
    private val random: GameRandom,
    private val objRepo: ObjRepository,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            if (product.isBonus || product.skill != "stat.mining") {
                return@onEvent
            }
            val source = product.source as? SkillingProductSource.Mining ?: return@onEvent
            rollClueGeode(source)
            rollCrystalShard()
        }
    }

    private fun SkillingActionCompleteEvent.rollClueGeode(source: SkillingProductSource.Mining) {
        val base = source.rockData.clueBaseChance
        if (base <= 0) {
            return
        }
        // Roll independently per eligible geode tier. TODO: apply Combat Achievement discounts.
        val scaledBase = floor(base * CLUE_BASE_SCALAR).toInt()
        val mining = player.miningLvl
        val ringOfWealthDiscount =
            "obj.ring_of_wealth_i" in player.worn && player.coords.isInWilderness(areaChecker)
        for ((geode, tierModifier) in CLUE_TIER_CHANCES) {
            var chance = floor(scaledBase / (100.0 + mining) * tierModifier).toInt()
            if (ringOfWealthDiscount) {
                chance /= 2
            }
            if (chance <= 0 || random.of(chance) != 0) {
                continue
            }
            if (player.invAddOrDrop(objRepo, geode)) {
                player.mes("You find a clue geode!")
            } else {
                player.mes("A clue geode falls to the ground as your inventory is too full.")
            }
        }
    }

    private fun SkillingActionCompleteEvent.rollCrystalShard() {
        if (MapSquareKey.from(player.coords).id != TRAHAEARN_REGION) {
            return
        }
        if (random.of(65) != 0) {
            return
        }
        if (player.invAdd(player.inv, "obj.prif_crystal_shard").success) {
            player.mes("You have obtained a crystal shard!")
        }
    }

    private companion object {
        private const val TRAHAEARN_REGION = 13250
        private const val CLUE_BASE_SCALAR = 0.7

        private val CLUE_TIER_CHANCES =
            listOf(
                "obj.mining_clue_geode_beginner" to 0.2,
                "obj.mining_clue_geode_easy" to 1.7,
                "obj.mining_clue_geode_medium" to 2.0,
                "obj.mining_clue_geode_hard" to 3.3,
                "obj.mining_clue_geode_elite" to 10.0,
            )
    }
}
