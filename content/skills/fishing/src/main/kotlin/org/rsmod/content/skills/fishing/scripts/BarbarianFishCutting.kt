package org.rsmod.content.skills.fishing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.fishing.configs.FishingParams
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BarbarianFishCutting
@Inject
constructor(private val random: GameRandom, private val xpMods: XpModifiers) : PluginScript() {

    override fun ScriptContext.startup() {
        for (fish in LeapingFish.entries) {
            onOpHeldU(FishingParams.KNIFE, fish.item) { startCutting(fish) }
        }
        onPlayerQueueWithArgs<LeapingFish>(FishingParams.CUT_QUEUE) { continueCutting(it.args) }
    }

    private fun ProtectedAccess.startCutting(fish: LeapingFish) {
        // Replace the pending chain so knife-spamming cannot stack automatic cuts.
        clearWeakQueue(FishingParams.CUT_QUEUE)
        if (cutOne(fish)) {
            cutQueue(fish)
        }
    }

    private fun ProtectedAccess.continueCutting(fish: LeapingFish) {
        if (cutOne(fish)) {
            cutQueue(fish)
        }
    }

    // Queue-handler rescheduling requires +1.
    private fun ProtectedAccess.cutQueue(fish: LeapingFish) {
        weakQueue(FishingParams.CUT_QUEUE, FishingParams.CUT_TICKS + 1, fish)
    }

    private fun ProtectedAccess.cutOne(fish: LeapingFish): Boolean {
        if (FishingParams.KNIFE !in inv || fish.item !in inv) {
            return false
        }

        val cookingLvl = player.cookingLvl
        val gotProduct = random.of(fish.productDenominator) < cookingLvl
        val gotOffcuts = gotProduct && random.of(fish.offcutsDenominator) < fish.offcutsNumerator

        if (!invDel(inv, fish.item, count = 1).success) {
            return false
        }
        if (gotProduct && invAdd(inv, fish.product, count = 1).failure) {
            invAdd(inv, fish.item, count = 1)
            mesInvFull()
            return false
        }
        if (gotOffcuts && invAdd(inv, FishingParams.FISH_OFFCUTS, count = 1).failure) {
            // Gutting can need an extra slot; roll back the fish when both outputs cannot fit.
            invDel(inv, fish.product, count = 1)
            invAdd(inv, fish.item, count = 1)
            mesInvFull()
            return false
        }

        if (gotProduct) {
            statAdvance("stat.cooking", fish.cookingXp * xpMods.get(player, "stat.cooking"))
        }

        anim(FishingParams.CUT_ANIM)
        if (gotProduct) {
            spam("You cut open the fish and extract some ${fish.productName}.")
        } else {
            spam("You cut open the fish but fail to extract anything useful.")
        }

        return fish.item in inv
    }

    private fun ProtectedAccess.mesInvFull() {
        mes("You don't have enough inventory space to cut up the fish.")
    }

    enum class LeapingFish(
        val item: String,
        val product: String,
        val productName: String,
        val productDenominator: Int,
        val offcutsNumerator: Int,
        val offcutsDenominator: Int,
        val cookingXp: Double,
    ) {
        Trout(
            item = "obj.brut_spawning_trout",
            product = FishingParams.ROE,
            productName = "roe",
            productDenominator = 150,
            offcutsNumerator = 1,
            offcutsDenominator = 2,
            cookingXp = 10.0,
        ),
        Salmon(
            item = "obj.brut_spawning_salmon",
            product = FishingParams.ROE,
            productName = "roe",
            productDenominator = 80,
            offcutsNumerator = 3,
            offcutsDenominator = 4,
            cookingXp = 10.0,
        ),
        Sturgeon(
            item = "obj.brut_sturgeon",
            product = FishingParams.CAVIAR,
            productName = "caviar",
            productDenominator = 80,
            offcutsNumerator = 5,
            offcutsDenominator = 6,
            cookingXp = 15.0,
        ),
    }
}
