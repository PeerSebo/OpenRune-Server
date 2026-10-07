package org.rsmod.content.generic.locs.web

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WebScript @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((intact, slashed) in Webs) {
            onOpLoc1(intact) { slash(it.loc, slashed, bestBlade()) }
            onOpLocU(intact) {
                if (inv[it.invSlot]?.id != it.objType.id) return@onOpLocU
                val blade = it.objType.takeIf { type ->
                    type.id == "obj.knife".asRSCM() || type.id == player.righthand?.id
                }
                slash(it.loc, slashed, blade)
            }
        }
    }

    private fun ProtectedAccess.bestBlade(): ItemServerType? =
        (sequenceOf(player.righthand) + inv.objs.asSequence().filter { it?.id == "obj.knife".asRSCM() })
            .filterNotNull()
            .mapNotNull { ServerCacheManager.getItem(it.id) }
            .maxByOrNull(::successChance)

    private suspend fun ProtectedAccess.slash(loc: BoundLocInfo, slashed: String, blade: ItemServerType?) {
        val chance = successChance(blade)
        if (chance == 0) {
            mes("You need an equipped slash weapon or a knife in your inventory to cut the web.")
            return
        }
        arriveDelay()
        if (locs.findExact(loc.coords, loc.shape)?.entity != loc.entity) return
        anim(if (blade?.id == "obj.knife".asRSCM()) "seq.human_knife_slash" else "seq.human_sword_slash")
        soundSynth("synth.STABSWORD_SLASH")
        delay(2)
        if (locs.findExact(loc.coords, loc.shape)?.entity != loc.entity) return
        if (random.of(100) < chance) {
            if (!locs.del(loc, Int.MAX_VALUE)) return
            locs.change(loc, slashed, RegrowthTicks)
            mes("You slash the web apart.")
        } else {
            mes("You fail to cut through the web.")
        }
    }

    internal companion object {
        const val RegrowthTicks = 100
        val Webs = mapOf(
            "loc.bigweb_slashable" to "loc.bigweb_slashed",
            "loc.bigweb_slashable_shape1" to "loc.bigweb_slashed_shape1",
            "loc.webwall" to "loc.sliced_web",
        )

        fun successChance(blade: ItemServerType?): Int {
            if (blade == null || blade.isCert) return 0
            if (blade.id == "obj.knife".asRSCM()) return 50
            if (blade.wearpos1 != Wearpos.RightHand.slot) return 0
            if (blade.id in WildernessSwords) return 100
            val slash = blade.paramOrNull(BaseParams.attack_slash) ?: 0
            if (slash <= 0) return 0
            val minimum = if (blade.weaponCategory == WeaponCategory.SlashSword) 50 else 20
            return slash.coerceIn(minimum, 100)
        }

        private val WildernessSwords by lazy {
            listOf("obj.wilderness_sword_easy", "obj.wilderness_sword_medium",
                "obj.wilderness_sword_hard", "obj.wilderness_sword_elite").map { it.asRSCM() }.toSet()
        }
    }
}
