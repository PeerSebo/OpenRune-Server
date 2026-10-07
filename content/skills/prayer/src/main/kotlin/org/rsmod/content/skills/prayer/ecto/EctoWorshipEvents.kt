package org.rsmod.content.skills.prayer.ecto

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EctoWorshipEvents : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.ahoy_ectofuntus") { worshipEctofuntus() }
    }

    private suspend fun ProtectedAccess.worshipEctofuntus() {
        // Pair shared actionDelay with the worship-owned clock.
        if (actionDelay >= mapClock && skillAnimDelay >= mapClock) {
            return
        }

        if (!inv.contains("obj.bucket_ectoplasm")) {
            doubleobjbox(
                "obj.bucket_ectoplasm",
                "obj.pot_bonemeal",
                "You need a bucket of slime and some bonemeal to do this.",
            )
            return
        }
        val recipe = ECTO_RECIPES.firstOrNull { inv.contains(it.bonemeal) }
        if (recipe == null) {
            doubleobjbox(
                "obj.bucket_ectoplasm",
                "obj.pot_bonemeal",
                "You need a bucket of slime and some bonemeal to do this.",
            )
            return
        }

        if (recipe.prayerLevel > 1 && player.basePrayerLvl < recipe.prayerLevel) {
            mes("You need a Prayer level of at least ${recipe.prayerLevel} to worship with this bonemeal.")
            return
        }

        if (ectoTokens + TOKENS_PER_WORSHIP > MAX_UNCLAIMED_TOKENS) {
            mes("There isn't room to put any more in.")
            return
        }

        stopAction()
        actionDelay = maxOf(actionDelay, mapClock + WORSHIP_BLOCK_TICKS)
        skillAnimDelay = mapClock + WORSHIP_BLOCK_TICKS

        anim("seq.ahoy_prayer")

        val consumeInputs = shouldConsume()
        if (consumeInputs) {
            if (invDel(inv, "obj.bucket_ectoplasm", 1).failure) {
                return
            }
            if (invDel(inv, recipe.bonemeal, 1).failure) {
                invAdd(inv, "obj.bucket_ectoplasm", 1)
                return
            }
            if (invAdd(inv, "obj.bucket_empty", 1).failure) {
                invAdd(inv, recipe.bonemeal, 1)
                invAdd(inv, "obj.bucket_ectoplasm", 1)
                mes("You don't have enough inventory space.")
                return
            }
            if (invAdd(inv, "obj.pot_empty", 1).failure) {
                invDel(inv, "obj.bucket_empty", 1)
                invAdd(inv, recipe.bonemeal, 1)
                invAdd(inv, "obj.bucket_ectoplasm", 1)
                mes("You don't have enough inventory space.")
                return
            }
        }

        // The table already includes Ectofuntus's 4x multiplier.
        statAdvance("stat.prayer", recipe.xp)
        ectoTokens += TOKENS_PER_WORSHIP
    }

    private companion object {
        const val TOKENS_PER_WORSHIP = 5
        const val MAX_UNCLAIMED_TOKENS = 1000
        const val WORSHIP_BLOCK_TICKS = 4
    }
}
