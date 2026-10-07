@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.HunterRumourPrey
import org.rsmod.content.skills.hunter.configs.RumourMaster
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

internal object HunterRumours {
    fun completed(player: Player): Int = player.rumoursCompleted
    fun activeMaster(player: Player): RumourMaster? = RumourMaster.entries.firstOrNull { it.saveCode == player.rumourActiveMaster }
    fun assigned(player: Player, master: RumourMaster): HunterRumourPrey? = player.rumourPrey(master)
    fun currentName(player: Player): String? = activeMaster(player)?.let { assigned(player, it)?.displayName }
    fun count(player: Player, master: RumourMaster): Int = player.rumourCatchCount(master)
    private fun partSlot(player: Player, prey: HunterRumourPrey): Int = player.inv.objs.indexOfFirst {
        it?.id == prey.part.asRSCM() && it.vars == prey.partTag
    }
    fun hasEarnedPart(player: Player, prey: HunterRumourPrey): Boolean = partSlot(player, prey) >= 0

    fun canUse(player: Player, master: RumourMaster): Boolean =
        player.rumoursUnlocked && player.statMap.getBaseLevel("stat.hunter").toInt() >= master.level &&
            (!player.rumoursResetLock || master == RumourMaster.GILMAN) &&
            (master != RumourMaster.WOLF || QuestRequirements.hasCompleted(player, "quest_atfirstlight"))

    fun eligible(player: Player, master: RumourMaster): List<HunterRumourPrey> {
        val blocked = RumourMaster.entries.filter { it != master }.mapNotNull { assigned(player, it) }.toSet()
        return HunterRumourPrey.entries.filter {
            master.pool in it.masters && player.statMap.getBaseLevel("stat.hunter").toInt() >= it.level && it !in blocked &&
                (player.rumoursBackToBack || player.rumourLastPrey != it.saveCode) &&
                (it.quest == null || QuestRequirements.hasCompleted(player, it.quest)) &&
                (it != HunterRumourPrey.HERBIBOAR || player.statMap.getBaseLevel("stat.herblore").toInt() >= 31)
        }
    }

    fun assign(player: Player, master: RumourMaster, random: GameRandom): HunterRumourPrey? {
        if (!canUse(player, master)) return null
        val previousMaster = activeMaster(player)
        if (previousMaster != null && previousMaster != master) restoreTemporary(player, previousMaster)
        val old = assigned(player, master)
        val completedWith = player.rumourRerollMaster
        if (old != null && completedWith != 0 && completedWith != master.saveCode) {
            val replacement = random.pickOrNull(eligible(player, master).filter { it != old })
            if (replacement != null) {
                player.setRumourPrey(master, old, saved = true)
                player.setRumourCatchCount(master, count(player, master), saved = true)
                player.setRumourPrey(master, replacement)
                player.setRumourCatchCount(master, 0)
            }
        }
        player.rumourRerollMaster = 0
        val prey = assigned(player, master) ?: random.pickOrNull(eligible(player, master))?.also {
            player.setRumourPrey(master, it)
            player.setRumourCatchCount(master, 0)
        } ?: return null
        player.rumourActiveMaster = master.saveCode
        return prey
    }

    // Partial-outfit pity rounding is provisional.
    fun pity(player: Player, prey: HunterRumourPrey): Int {
        val bonus = HunterGuildOutfit.rareBonus(player)
        if (bonus == 500) return prey.method.fullOutfitPity
        if (bonus == 180) return when (prey.method) {
            org.rsmod.content.skills.hunter.configs.RumourMethod.FALCONRY -> 19
            org.rsmod.content.skills.hunter.configs.RumourMethod.PIT -> 28
            org.rsmod.content.skills.hunter.configs.RumourMethod.BUTTERFLY -> 78
            else -> prey.method.pity * (10_000 - bonus) / 10_000
        }
        return (prey.method.pity * (10_000 - bonus) / 10_000).coerceAtLeast(1)
    }

    fun caught(player: Player, npc: String, random: GameRandom, groundDrop: (InvObj) -> Unit) {
        val master = activeMaster(player) ?: return
        val prey = assigned(player, master) ?: return
        if (prey.npc != npc) return
        val count = (count(player, master).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.setRumourCatchCount(master, count)
        if (HunterRumourPrey.entries.any { player.inv.count(it.part) > 0 }) return
        val pity = count >= pity(player, prey)
        if (!pity && random.of(prey.method.denominator * 10_000) >= 10_000 + HunterGuildOutfit.rareBonus(player)) return
        val inserted = player.invAdd(player.inv, prey.part, 1, vars = prey.partTag).success
        if (!inserted && pity) { player.mes("Make room in your inventory to collect the rare creature part."); return }
        if (!inserted) groundDrop(InvObj(checkNotNull(ServerCacheManager.getItem(prey.part.asRSCM())), 1, prey.partTag))
        player.mes("<col=00a000>You find a rare creature part! Take it back to the Hunter Guild.</col>")
    }

    fun complete(player: Player, master: RumourMaster, xpMods: XpModifiers): Boolean {
        val prey = assigned(player, master) ?: return false
        if (!canUse(player, master) || !hasEarnedPart(player, prey)) return false
        val partSlot = partSlot(player, prey)
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, prey.part.asRSCM(), 1, slot = partSlot)
            add(inv, master.sack.asRSCM(), 1)
        }
        if (!result.success) { player.mes("You need room for your hunters' loot sack."); return false }
        player.setRumourPrey(master, null); player.setRumourCatchCount(master, 0)
        player.setRumourPrey(master, null, saved = true); player.setRumourCatchCount(master, 0, saved = true)
        player.rumourRerollMaster = master.saveCode
        if (master == RumourMaster.GILMAN) player.rumoursResetLock = false
        if (activeMaster(player) == master) player.rumourActiveMaster = 0
        player.rumourLastPrey = prey.saveCode
        player.rumoursCompleted = (completed(player).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val xp = (player.statMap.getBaseLevel("stat.hunter").toInt() + 5) * master.xpMultiplier
        player.statAdvance("stat.hunter", xp * xpMods.get(player, "stat.hunter"))
        player.mes("You complete the rumour and receive a hunters' loot sack. Rumours completed: ${completed(player)}.")
        return true
    }

    private fun restoreTemporary(player: Player, master: RumourMaster) {
        val original = player.rumourPrey(master, saved = true) ?: return
        player.setRumourPrey(master, original)
        player.setRumourCatchCount(master, player.rumourCatchCount(master, saved = true))
        player.setRumourPrey(master, null, saved = true); player.setRumourCatchCount(master, 0, saved = true)
    }

    fun canReset(player: Player): Boolean = canUse(player, RumourMaster.GILMAN) &&
        assigned(player, RumourMaster.GILMAN) != null && activeMaster(player) != RumourMaster.GILMAN

    fun reset(player: Player, random: GameRandom): Boolean {
        if (!canReset(player)) return false
        val old = assigned(player, RumourMaster.GILMAN)
        RumourMaster.entries.forEach {
            player.setRumourPrey(it, null); player.setRumourCatchCount(it, 0)
            player.setRumourPrey(it, null, saved = true); player.setRumourCatchCount(it, 0, saved = true)
        }
        player.rumourActiveMaster = 0
        player.rumourRerollMaster = 0
        val prey = random.pickOrNull(eligible(player, RumourMaster.GILMAN).filter { it != old }) ?: return false
        player.setRumourPrey(RumourMaster.GILMAN, prey)
        player.rumourActiveMaster = RumourMaster.GILMAN.saveCode
        player.rumoursResetLock = true
        return true
    }
}
