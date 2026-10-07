package org.rsmod.content.skills.construction.features

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.basePrayerLvl
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.poh.PohManager
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChapelScript
@Inject
constructor(
    private val manager: PohManager,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    private val litBurnerIds by lazy { BURNERS.map { it.lit.asRSCM(RSCMType.LOC) }.toSet() }

    override fun ScriptContext.startup() {
        for (altar in ALTARS) {
            onOpLoc1(altar.loc) { prayAtAltar() }
        }

        val bones = SkillPrayerRow.all().filterNot { it.ashes }
        for (altar in ALTARS) {
            for (row in bones) {
                onOpLocU(altar.loc, row.item.internalName) { offerBones(it.vis, row, altar.tier) }
            }
        }

        for (burner in BURNERS) {
            onOpLoc1(burner.unlit) { lightBurner(it.loc, burner.lit, relight = false) }
            onOpLocU(burner.unlit, TINDERBOX) { lightBurner(it.vis, burner.lit, relight = false) }
            onOpLoc1(burner.lit) { lightBurner(it.loc, burner.lit, relight = true) }
        }
    }

    private suspend fun ProtectedAccess.prayAtAltar() {
        arriveDelay()
        anim(PRAY_ANIM)
        delay(2)

        val missing = player.basePrayerLvl - player.prayerLvl
        if (missing > 0) {
            statAdd(PRAYER_STAT, constant = missing, percent = 0)
            mes("You recharge your Prayer points.")
        } else {
            mes("You already have full prayer points.")
        }
    }

    // TODO: queue bone offerings so interrupted protected access cannot discard a pending offer.
    private suspend fun ProtectedAccess.offerBones(
        altar: BoundLocInfo,
        row: SkillPrayerRow,
        tier: Int,
    ) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only make offerings at an altar in your own house.")
            return
        }
        arriveDelay()

        val bone = row.item.internalName
        while (inv.count(bone) > 0 && isWithinDistance(altar, 1)) {
            anim(SACRIFICE_ANIM)
            spotanimMap(worldRepo, SACRIFICE_SPOTANIM, altar.coords)

            if (shouldConsume() && invDel(inv, bone, 1).failure) {
                return
            }

            // Prayer XP is stored in tenths; apply the altar multiplier before truncating.
            val litBurners = countLitBurners(altar)
            val percent = TIER_PERCENTS.getValue(tier) + BURNER_BONUS_PERCENT * litBurners
            val awardedTenths = (row.xpTenths * percent) / 100
            statAdvance(PRAYER_STAT, awardedTenths / 10.0)
            mes(offeringMessage(litBurners))

            delay(OFFER_INTERVAL)
        }
    }

    private fun countLitBurners(altar: BoundLocInfo): Int {
        val zone = ZoneKey.from(altar.coords)
        return locRepo.findAll(zone).count { it.id in litBurnerIds }.coerceAtMost(MAX_BURNERS)
    }

    private fun offeringMessage(litBurners: Int): String =
        when (litBurners) {
            0 -> "The gods accept your offering."
            1 -> "The gods are pleased with your offering."
            else -> "The gods are very pleased with your offering."
        }

    private suspend fun ProtectedAccess.lightBurner(
        burner: BoundLocInfo,
        litLoc: String,
        relight: Boolean,
    ) {
        if (!manager.isInOwnHouse(player)) {
            mes("You can only do that in your own house.")
            return
        }
        if (invTotal(inv, TINDERBOX) < 1) {
            mes("You need a tinderbox to light the burner.")
            return
        }
        if (invTotal(inv, MARRENTILL) < 1) {
            mes("You need a clean marrentill to burn in the incense burner.")
            return
        }
        arriveDelay()

        val result = invDel(inv, MARRENTILL, 1)
        if (result.failure) {
            return
        }
        anim(LIGHT_ANIM)
        delay(1)

        locRepo.change(burner, litLoc, burnDuration())
        mes(if (relight) "You refuel the incense burner." else "You light the incense burner.")
    }

    private fun burnDuration(): Int = (BURN_TICKS_MIN..BURN_TICKS_MAX).random()

    private data class ChapelAltar(val loc: String, val tier: Int)

    private data class ChapelBurner(val unlit: String, val lit: String)

    private companion object {
        const val PRAYER_STAT = "stat.prayer"
        const val PRAY_ANIM = "seq.human_pray"
        const val SACRIFICE_ANIM = "seq.human_bone_sacrifice"
        const val SACRIFICE_SPOTANIM = "spotanim.poh_bone_sacrifice"
        const val LIGHT_ANIM = "seq.human_createfire"

        const val TINDERBOX = "obj.tinderbox"

        const val MARRENTILL = "obj.marentill"

        const val OFFER_INTERVAL = 4

        const val BURNER_BONUS_PERCENT = 50

        const val MAX_BURNERS = 2

        const val BURN_TICKS_MIN = 200
        const val BURN_TICKS_MAX = 250

        val TIER_PERCENTS =
            mapOf(1 to 100, 2 to 110, 3 to 125, 4 to 150, 5 to 175, 6 to 200, 7 to 250)

        val ALTARS = buildList {
            for (god in listOf("saradomin", "zamorak", "guthix", "gnomechild")) {
                for (tier in 1..7) {
                    add(ChapelAltar("loc.poh_altar_${god}_$tier", tier))
                }
            }
        }

        val BURNERS =
            listOf(
                ChapelBurner("loc.poh_torch_5", "loc.poh_torch_5_lit"),
                ChapelBurner("loc.poh_torch_6", "loc.poh_torch_6_lit"),
                ChapelBurner("loc.poh_torch_7", "loc.poh_torch_7_lit"),
            )
    }
}
