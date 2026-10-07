package org.rsmod.content.skills.fishing.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.cooking.CookingFoodsRow
import org.rsmod.content.skills.fishing.configs.FishingParams
import org.rsmod.content.skills.fishing.table.FishingCatch
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishOffcutsScript : PluginScript() {
    override fun ScriptContext.startup() {
        val cookable = CookingFoodsRow.all().mapTo(HashSet()) { it.input.internalName }
        val rawFish =
            FishingCatch.allRows()
                .filter { it.xpTenths > 0 }
                .map { it.item.internalName }
                .filterTo(HashSet()) { it in cookable }

        for (fish in rawFish) {
            val offcut = if (fish in FINE_OFFCUT_FISH) FINE_FISH_OFFCUTS else FISH_OFFCUTS
            onOpHeldU(KNIFE, fish) { startCutting(CutJob(fish, offcut)) }
        }
        onOpHeldU(KNIFE, RAW_KARAMBWAN) { startCutting(CutJob(RAW_KARAMBWAN, FISH_OFFCUTS)) }

        onPlayerQueueWithArgs<CutJob>(FishingParams.OFFCUTS_CUT_QUEUE) { continueCutting(it.args) }
    }

    private fun ProtectedAccess.startCutting(job: CutJob) {
        // Repeated clicks must not accelerate an active offcut-cutting job.
        if (FishingParams.OFFCUTS_CUT_QUEUE in player.weakQueueList) {
            return
        }
        if (cutOne(job)) {
            cutQueue(job)
        }
    }

    private fun ProtectedAccess.continueCutting(job: CutJob) {
        if (cutOne(job)) {
            cutQueue(job)
        }
    }

    // Queue-handler rescheduling requires +1.
    private fun ProtectedAccess.cutQueue(job: CutJob) {
        weakQueue(FishingParams.OFFCUTS_CUT_QUEUE, FishingParams.CUT_TICKS + 1, job)
    }

    private fun ProtectedAccess.cutOne(job: CutJob): Boolean {
        if (FishingParams.KNIFE !in inv || job.fish !in inv) {
            return false
        }

        val delete = invDel(inv, job.fish, count = 1)
        if (!delete.success) {
            return false
        }
        val add = invAdd(inv, job.offcut, count = 1)
        if (!add.success) {
            invAdd(inv, job.fish, count = 1)
            mesInvFull()
            return false
        }
        statAdvance("stat.cooking", CUT_XP)
        anim(FishingParams.CUT_ANIM)
        spam("You cut the fish into offcuts.")
        return job.fish in inv
    }

    private fun ProtectedAccess.mesInvFull() {
        mes("You don't have enough inventory space to cut up the fish.")
    }

    private data class CutJob(val fish: String, val offcut: String)

    private companion object {
        private const val KNIFE = "obj.knife"

        private const val FISH_OFFCUTS = "obj.brut_fish_cuts"
        private const val FINE_FISH_OFFCUTS = "obj.sailing_fine_fish_offcuts"
        private const val CUT_XP = 2.0
        private const val RAW_KARAMBWAN = "obj.tbwt_raw_karambwan"

        private val FINE_OFFCUT_FISH =
            setOf(
                "obj.raw_shark",
                "obj.raw_seaturtle",
                "obj.raw_anglerfish",
                "obj.raw_dark_crab",
                "obj.raw_mantaray",
                "obj.raw_yellowfin",
                "obj.raw_halibut",
                "obj.raw_bluefin",
                "obj.raw_jumbo_squid",
                "obj.raw_marlin",
            )
    }
}
