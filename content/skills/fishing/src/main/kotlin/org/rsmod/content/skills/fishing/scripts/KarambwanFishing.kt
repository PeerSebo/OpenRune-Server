package org.rsmod.content.skills.fishing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.skilling.SkillingAwardResult
import org.rsmod.api.player.skilling.awardSkillingProduct
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.script.onOpContentNpc1
import org.rsmod.api.script.onOpContentNpcU
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KarambwanFishing
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {

    private val karambwanType: ItemServerType by lazy {
        checkNotNull(ServerCacheManager.getItem(RAW_KARAMBWAN.asRSCM(RSCMType.OBJ))) {
            "Missing obj type: $RAW_KARAMBWAN"
        }
    }

    override fun ScriptContext.startup() {
        onOpHeldU(KARAMBWANJI, EMPTY_VESSEL) { loadVessel() }
        onOpContentNpcU(SPOT, LOADED_VESSEL) { opNpc1(it.npc) }
        onOpContentNpc1(SPOT) { fish(it.npc) }
    }

    private fun ProtectedAccess.loadVessel() {
        invReplace(inv, EMPTY_VESSEL, 1, LOADED_VESSEL)
        invDel(inv, KARAMBWANJI, count = 1)
        mes("You fill the karambwan vessel with karambwanji.")
    }

    private fun ProtectedAccess.fish(spot: Npc) {
        if (player.fishingLvl < LEVEL_REQ) {
            mes("You need a Fishing level of $LEVEL_REQ to fish here.")
            bailReset()
            return
        }

        if (
            invTotal(inv, LOADED_VESSEL) == 0 &&
                invTotal(inv, EMPTY_VESSEL) > 0 &&
                invTotal(inv, KARAMBWANJI) > 0
        ) {
            invReplace(inv, EMPTY_VESSEL, 1, LOADED_VESSEL)
            invDel(inv, KARAMBWANJI, count = 1)
        }

        if (invTotal(inv, LOADED_VESSEL) == 0) {
            mes("You need a karambwan vessel filled with karambwanji to fish here.")
            bailReset()
            return
        }

        if (inv.isFull()) {
            mes("You can't carry any more fish.")
            soundSynth("synth.PILLORY_WRONG")
            bailReset()
            return
        }

        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + ROLL_TICKS)
            skillAnimDelay = mapClock + ROLL_TICKS
            // Sequence 623 has no baked audio; karambwan fishing needs its server synth.
            anim(VESSEL_ANIM)
            spam("You cast out your vessel...")
            opNpc1(spot)
            return
        }

        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + ROLL_TICKS
            anim(VESSEL_ANIM)
        }

        if (actionDelay < mapClock) {
            // A foreign action consumed the clock; re-arm without granting a catch.
            actionDelay = mapClock + ROLL_TICKS
        } else if (actionDelay == mapClock) {
            actionDelay = mapClock + ROLL_TICKS
            if (statRandom("stat.fishing", SUCCESS_LOW, SUCCESS_HIGH, invisibleLvls)) {
                invReplace(inv, LOADED_VESSEL, 1, EMPTY_VESSEL)
                val product =
                    SkillingProduct(
                        player = player,
                        skill = "stat.fishing",
                        item = RAW_KARAMBWAN,
                        count = 1,
                        experience = XP * xpMods.get(player, "stat.fishing"),
                        source = SkillingProductSource.Fishing(spot, karambwanType),
                        depletes = false,
                    )
                when (awardSkillingProduct(product)) {
                    SkillingAwardResult.Success -> {
                        spam("You catch a karambwan!")
                    }
                    // If the catch cannot fit, restore the consumed vessel load.
                    SkillingAwardResult.InventoryFull,
                    SkillingAwardResult.Cancelled ->
                        invReplace(inv, EMPTY_VESSEL, 1, LOADED_VESSEL)
                }
            }
        }

        opNpc1(spot)
    }

    private fun ProtectedAccess.bailReset() {
        if (skillAnimDelay >= mapClock) {
            resetAnim()
        }
    }

    private companion object {
        private const val SPOT = "content.fishing_spot_karambwan"
        private const val KARAMBWANJI = "obj.tbwt_raw_karambwanji"
        private const val EMPTY_VESSEL = "obj.tbwt_karambwan_vessel"
        private const val LOADED_VESSEL = "obj.tbwt_karambwan_vessel_loaded_with_karambwanji"
        private const val RAW_KARAMBWAN = "obj.tbwt_raw_karambwan"
        private const val VESSEL_ANIM = "seq.human_fish_onspot"

        private const val LEVEL_REQ = 65
        private const val XP = 50.0

        // Catch cadence is unverified.
        private const val ROLL_TICKS = 3
        private const val SUCCESS_LOW = 5
        private const val SUCCESS_HIGH = 160
    }
}
