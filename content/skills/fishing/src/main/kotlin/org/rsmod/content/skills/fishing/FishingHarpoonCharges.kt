package org.rsmod.content.skills.fishing

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

// Zero vars on an existing active harpoon means a legacy full-charge item.
object FishingHarpoonCharges {
    enum class Kind(val active: String, val inactive: String, val initial: Int, val maximum: Int, val crystal: Boolean = false) {
        INFERNAL("obj.infernal_harpoon", "obj.infernal_harpoon_empty", 5_000, 5_000),
        TRAILBLAZER("obj.trailblazer_harpoon", "obj.trailblazer_harpoon_empty", 5_000, 5_000),
        RELOADED("obj.trailblazer_reloaded_harpoon", "obj.trailblazer_reloaded_harpoon_empty", 5_000, 5_000),
        CRYSTAL("obj.crystal_harpoon", "obj.crystal_harpoon_inactive", 10_000, 20_000, true),
    }
    data class Selection(val inventory: Inventory, val slot: Int, val item: InvObj, val kind: Kind)

    fun kind(item: InvObj): Kind? = Kind.entries.firstOrNull { item.id == it.active.asRSCM() || item.id == it.inactive.asRSCM() }
    fun charges(item: InvObj): Int {
        val kind = kind(item) ?: return 0
        if (item.id != kind.active.asRSCM()) return 0
        return if (item.vars == 0) kind.initial else item.vars.coerceIn(0, kind.maximum)
    }
    fun isCrystal(item: InvObj?): Boolean = item != null && kind(item)?.crystal == true && charges(item) > 0
    fun isInfernal(item: InvObj?): Boolean = item != null && kind(item)?.crystal == false && charges(item) > 0

    fun select(player: Player, item: InvObj?): Selection? {
        item ?: return null
        val kind = kind(item) ?: return null
        for (inventory in listOf(player.worn, player.inv)) {
            val slot = inventory.objs.indexOfFirst { it === item }
            if (slot >= 0) return Selection(inventory, slot, item, kind)
        }
        return null
    }
    fun valid(player: Player, selected: Selection): Boolean =
        (selected.inventory === player.inv || selected.inventory === player.worn) &&
            selected.inventory[selected.slot] === selected.item && selected.item.count == 1

    // Transactions recreate InvObj instances; retain identity only across this non-suspending add/delete.
    fun refreshAfterTransaction(player: Player, selected: Selection?): Selection? {
        selected ?: return null
        if (selected.inventory !== player.inv && selected.inventory !== player.worn) return null
        val current = selected.inventory[selected.slot] ?: return null
        if (current != selected.item || current.count != 1) return null
        return selected.copy(item = current)
    }

    fun consume(player: Player, selected: Selection?, random: GameRandom): Boolean {
        if (selected == null || !valid(player, selected) || charges(selected.item) <= 0) return false
        val preserved = selected.kind.crystal &&
            listOf("obj.elven_signet", "obj.celestial_signet", "obj.celestial_signet_charged").any { it in player.worn } && random.of(10) == 0
        val left = charges(selected.item) - if (preserved) 0 else 1
        selected.inventory[selected.slot] = if (left == 0) InvObj(selected.kind.inactive) else selected.item.copy(vars = left)
        if (left == 0) player.mes("Your ${if (selected.kind.crystal) "crystal" else "infernal"} harpoon has run out of charges.")
        return true
    }

    fun recharge(player: Player, selected: Selection, material: String, requested: Int = 1): Boolean {
        if (!valid(player, selected) || selected.inventory !== player.inv || requested <= 0) return false
        val kind = selected.kind
        val current = charges(selected.item)
        val amount: Int
        val next: Int
        if (kind.crystal) {
            if (material != "obj.prif_crystal_shard") return false
            amount = minOf(requested, player.inv.count(material), (kind.maximum - current + 99) / 100)
            if (amount <= 0) return false
            next = minOf(kind.maximum, current + amount * 100)
        } else {
            if (material != "obj.smouldering_stone" && material != "obj.dragon_harpoon") return false
            if (current > 0) { player.mes("Your infernal harpoon must be empty before it can be recharged."); return false }
            amount = 1
            next = kind.initial
        }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, selected.item.id, 1, slot = selected.slot)
            delete(inv, material.asRSCM(), amount)
            add(inv, kind.active.asRSCM(), 1, vars = next, slot = selected.slot)
        }
        if (result.success) player.mes("Your harpoon now has $next charges remaining.")
        return result.success
    }
}
