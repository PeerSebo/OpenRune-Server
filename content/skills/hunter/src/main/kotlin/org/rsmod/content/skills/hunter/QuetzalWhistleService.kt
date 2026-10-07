package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.QuetzalDestination
import org.rsmod.content.skills.hunter.configs.QuetzalWhistle
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal object QuetzalWhistleService {
    const val PITRI = "npc.hunter_guild_pitri"
    val MENU = AttributeKey<Selection>()
    data class Selection(val slot: Int, val item: InvObj)

    val foods = linkedMapOf("obj.hg_seedsack" to 1, "obj.huntingbeast_wild_meat" to 1,
        "obj.hunting_larupia_meat" to 1, "obj.huntingbeast_barbed_meat" to 1,
        "obj.hunting_graahk_meat" to 2, "obj.hunting_kyatt_meat" to 2, "obj.hunting_fennecfox_meat" to 2,
        "obj.hunting_antelopesun_meat" to 3, "obj.huntingbeast_speedy2_meat" to 3, "obj.hunting_antelopemoon_meat" to 3)

    fun blueprint(player: Player): Boolean {
        if (HunterRumours.completed(player) < 10) { player.mes("Complete 10 Hunter Rumours to earn a quetzal whistle blueprint."); return false }
        val fee = if (player.basicBlueprintClaimed) 500 else 0
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            if (fee > 0) delete(inv, "obj.coins".asRSCM(), fee)
            add(inv, checkNotNull(QuetzalWhistle.BASIC.blueprint).asRSCM(), 1)
        }
        if (!result.success) { player.mes("You need inventory space${if (fee > 0) " and 500 coins" else ""} for the blueprint."); return false }
        player.basicBlueprintClaimed = true
        player.mes("Use the blueprint's Craft option with a knife and willow logs, then bring hunter meat to Pitri to charge your whistle.")
        return true
    }

    fun craft(player: Player, blueprint: String): Boolean {
        val tier = QuetzalWhistle.entries.firstOrNull { it.blueprint != null && (it.blueprint == blueprint || "${it.blueprint}_torn" == blueprint) } ?: return false
        if (player.inv.count("obj.knife") <= 0) { player.mes("You need a knife to craft a quetzal whistle."); return false }
        val previousSlot = tier.previous?.let { obj -> player.inv.objs.indexOfFirst { it?.id == obj.asRSCM() } }
        if (previousSlot == -1) { player.mes("You need the previous tier of quetzal whistle to upgrade it."); return false }
        val previous = previousSlot?.let { player.inv[it] }
        val charges = previous?.let { QuetzalWhistle.from(it)?.charges(it) } ?: 0
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, blueprint.asRSCM(), 1)
            delete(inv, checkNotNull(tier.logs).asRSCM(), 1)
            if (previous != null) delete(inv, previous.id, 1, slot = previousSlot)
            add(inv, tier.obj.asRSCM(), 1, vars = charges)
        }
        if (!result.success) { player.mes("You need the blueprint, suitable logs and space to craft this whistle."); return false }
        if (tier == QuetzalWhistle.ENHANCED) player.enhancedBlueprintSeen = true
        if (tier == QuetzalWhistle.PERFECTED) { player.perfectedWhistleCrafted = true; player.perfectedBlueprintSeen = true }
        player.mes("You carefully craft a quetzal whistle.")
        return true
    }

    fun recharge(player: Player): Boolean {
        val remaining = player.inv.objs.map { it?.count ?: 0 }.toIntArray()
        val consumed = mutableMapOf<Int, Int>()
        val replacements = mutableMapOf<Int, InvObj>()
        var credits = 0
        for ((slot, item) in player.inv.objs.withIndex()) {
            item ?: continue
            val tier = QuetzalWhistle.from(item) ?: continue
            if (tier == QuetzalWhistle.INFINITE) continue
            var charges = tier.charges(item)
            for ((food, value) in foods) {
                for ((foodSlot, meat) in player.inv.objs.withIndex()) {
                    if (meat?.id != food.asRSCM() || charges >= tier.capacity) continue
                    val count = minOf(remaining[foodSlot], (tier.capacity - charges + value - 1) / value)
                    remaining[foodSlot] -= count
                    if (count > 0) {
                        consumed[foodSlot] = (consumed[foodSlot] ?: 0) + count
                        charges = (charges + count * value).coerceAtMost(tier.capacity)
                        credits += count * value
                    }
                }
            }
            if (charges != tier.charges(item)) replacements[slot] = item.copy(vars = charges)
        }
        if (consumed.isEmpty()) { player.mes("Bring an unfilled quetzal whistle and unnoted hunter meat or quetzal feed in your inventory."); return false }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            for ((slot, count) in consumed) delete(inv, checkNotNull(player.inv[slot]).id, count, slot = slot)
            for ((slot, item) in replacements) { delete(inv, item.id, 1, slot = slot); add(inv, item.id, 1, vars = item.vars, slot = slot) }
        }
        if (!result.success) return false
        credit(player, credits)
        player.mes("Pitri feeds the quetzals and recharges your whistles.")
        return true
    }

    fun imbue(player: Player): Boolean {
        if (player.inv.count(QuetzalWhistle.PERFECTED.obj) <= 0) { player.mes("Bring a perfected quetzal whistle to imbue."); return false }
        var needed = 10_000 - feedCredit(player)
        var credits = 0
        val consumed = mutableMapOf<Int, Int>()
        for ((slot, item) in player.inv.objs.withIndex()) {
            if (item == null || needed <= 0) continue
            val value = foods.entries.firstOrNull { (obj, _) ->
                item.id == obj.asRSCM() || ServerCacheManager.getItem(obj.asRSCM())?.certlink == item.id
            }?.value ?: continue
            val count = minOf(item.count, (needed + value - 1) / value)
            consumed[slot] = count; credits += count * value; needed -= count * value
        }
        val complete = feedCredit(player) + credits >= 10_000
        if (consumed.isEmpty() && !complete) { player.mes("Pitri needs ${10_000 - feedCredit(player)} more charges worth of hunter food. Noted meat is accepted for imbuing."); return false }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            for ((slot, count) in consumed) delete(inv, checkNotNull(player.inv[slot]).id, count, slot = slot)
            if (complete) { delete(inv, QuetzalWhistle.PERFECTED.obj.asRSCM(), 1); add(inv, QuetzalWhistle.INFINITE.obj.asRSCM(), 1) }
        }
        if (!result.success) return false
        credit(player, credits)
        if (complete) player.infiniteWhistleUnlocked = true
        player.mes(if (complete) "Pitri imbues your perfected whistle with unlimited charges." else "Pitri has received ${feedCredit(player)} of the 10,000 charges needed to imbue your whistle.")
        return true
    }

    fun reclaim(player: Player): Boolean {
        val infinite = player.infiniteWhistleUnlocked
        if (!infinite && !player.perfectedWhistleCrafted) { player.mes("You have not crafted a perfected whistle yet."); return false }
        val output = if (infinite) QuetzalWhistle.INFINITE.obj else QuetzalWhistle.PERFECTED.obj
        if (!infinite && HunterGuildRewards.owns(player, output)) { player.mes("You already have a perfected whistle."); return false }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            if (infinite) delete(inv, "obj.coins".asRSCM(), 50_000)
            add(inv, output.asRSCM(), 1)
        }
        player.mes(if (result.success) "Pitri gives you a replacement whistle." else "You need inventory space${if (infinite) " and 50,000 coins" else ""}.")
        return result.success
    }

    fun buildLanding(player: Player, site: QuetzalDestination): Boolean {
        if (site.built || site.unlocked(player)) return false
        if (!QuestRequirements.hasCompleted(player, "quest_twilightspromise")) { player.mes("Speak to Renu after completing Twilight's Promise before building landing sites."); return false }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, "obj.hg_seedsack".asRSCM(), 10)
            delete(inv, "obj.limestonebrick".asRSCM(), 3)
            delete(inv, "obj.softclay".asRSCM(), 4)
        }
        if (!result.success) { player.mes("You need 10 quetzal feed, 3 limestone bricks and 4 soft clay to build this landing site."); return false }
        VarPlayerIntMapSetter.set(player, site.varbit, 1)
        VarPlayerIntMapSetter.set(player, "varp.quetzals_unlocked", player.vars["varp.quetzals_unlocked"] or (1 shl site.nativeId))
        player.mes("You build the quetzal landing site.")
        return true
    }

    fun selection(player: Player, slot: Int): Selection? {
        val item = player.inv[slot] ?: return null
        val tier = QuetzalWhistle.from(item) ?: return null
        if (tier.charges(item) <= 0) { player.mes("Your quetzal whistle has no charges remaining. Pitri can recharge it."); return null }
        return Selection(slot, item)
    }
    fun valid(player: Player, selection: Selection): Boolean = player.inv[selection.slot] === selection.item && QuetzalWhistle.from(selection.item)?.charges(selection.item)?.let { it > 0 } == true
    fun spend(player: Player, selection: Selection): Boolean {
        if (!valid(player, selection)) return false
        val tier = checkNotNull(QuetzalWhistle.from(selection.item))
        if (tier != QuetzalWhistle.INFINITE) player.inv[selection.slot] = selection.item.copy(vars = tier.charges(selection.item) - 1)
        return true
    }
    fun feedCredit(player: Player): Int = player.quetzalFeedCredit.coerceIn(0, 10_000)
    private fun credit(player: Player, amount: Int) { player.quetzalFeedCredit = (feedCredit(player) + amount).coerceAtMost(10_000) }
}
