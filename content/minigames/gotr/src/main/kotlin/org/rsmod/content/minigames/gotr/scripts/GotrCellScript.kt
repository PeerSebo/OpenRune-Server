package org.rsmod.content.minigames.gotr.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onApNpc2
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc2
import org.rsmod.content.minigames.gotr.GotrDefence
import org.rsmod.content.minigames.gotr.GotrDefenceRules
import org.rsmod.content.minigames.gotr.GotrGame
import org.rsmod.content.minigames.gotr.GotrPhase
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GotrCellScript @Inject constructor(private val game: GotrGame, private val defence: GotrDefence) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.gotr_uncharged_cells") { takeUncharged(10) }
        onOpLoc2("loc.gotr_uncharged_cells") { takeUncharged(1) }
        onOpLoc1("loc.gotr_weak_cells") { takeWeak() }
        onOpLoc2("loc.gotr_weak_cells") { if (insideArena() && !inv.contains(CHISEL)) invAdd(inv, CHISEL, 1) }
        for (tile in TILE_SYMBOLS) {
            onOpLoc1(tile) { useTile(it.loc.coords) }
            for (cell in GotrDefenceRules.cells + UNCHARGED) onOpLocU(tile, cell) { useTile(it.loc.coords, cell) }
            onOpLocU(tile, FRAGMENT) { useTile(it.loc.coords) }
        }
        for ((station, catalytic) in listOf("loc.gotr_guardian_spawn_east" to false, "loc.gotr_guardian_spawn_west" to true)) {
            onOpLoc1(station) { assemble(catalytic) }
            onOpLocU(station, CHISEL) { assemble(catalytic) }
            for (cell in GotrDefenceRules.cells + UNCHARGED) onOpLocU(station, cell) { assemble(catalytic, cell) }
        }
        onOpHeld1(UNCHARGED) { mes("An uncharged magical cell built to hold runic energy.") }
        for ((index, cell) in GotrDefenceRules.cells.withIndex()) onOpHeld1(cell) {
            mes("A magical cell holding " + TIER_NAMES[index] + " runic energy.")
        }
        // Prevent ordinary auto-retaliation: these NPCs' combat belongs to the arena controller.
        for (npc in COMBAT_NPCS) {
            onOpNpc2(npc) { }
            onApNpc2(npc) { player.abortRoute() }
        }
    }

    private fun ProtectedAccess.takeUncharged(amount: Int) {
        if (!insideArena()) return
        val count = minOf(amount, 10 - inv.count(UNCHARGED))
        if (count <= 0) { mes("You can't carry any more uncharged cells."); return }
        if (invAdd(inv, UNCHARGED, count).failure) mes("You don't have enough inventory space.")
    }

    private fun ProtectedAccess.takeWeak() {
        if (!insideArena()) return
        if (GotrDefenceRules.cells.any { inv.contains(it) }) {
            mes("You can only carry one charged cell at a time.")
            return
        }
        if (invAdd(inv, GotrDefenceRules.cells[0], 1).failure) mes("You don't have enough inventory space.")
    }

    private suspend fun ProtectedAccess.useTile(coords: CoordGrid, selected: String? = null) {
        if (!insideArena() || !defence.isCellTile(coords)) return
        if (game.phase == GotrPhase.GRACE) { mesbox("The Guardian is dormant. Its energy might return soon."); return }
        if (defence.needsRepair(coords)) {
            if (inv.count(FRAGMENT) < GotrDefenceRules.REPAIR_FRAGMENTS) {
                mes("You need 12 guardian fragments to repair this cell tile.")
                return
            }
            if (invDel(inv, FRAGMENT, GotrDefenceRules.REPAIR_FRAGMENTS).failure) return
            if (!defence.repair(coords)) {
                check(invAdd(inv, FRAGMENT, GotrDefenceRules.REPAIR_FRAGMENTS).success)
                return
            }
            anim("seq.gotr_chisel_essence")
            game.rewardCell(player, GotrDefenceRules.REPAIR_ENERGY, GotrDefenceRules.REPAIR_ENERGY, 0.0)
            mes("You repair the cell tile.")
            return
        }
        val selection = selectCell(selected) ?: return
        if (invDel(inv, selection.first, 1).failure) return
        val use = defence.placeCell(coords, selection.second)
        if (use == null) {
            check(invAdd(inv, selection.first, 1).success)
            return
        }
        anim("seq.human_pickupfloor")
        if (selection.first != UNCHARGED && game.phase == GotrPhase.ACTIVE) {
            val xp = GotrDefenceRules.barrierXp[selection.second - 1]
            val reward = if (use.wasFull) defence.fullBarrierReward(player.displayName, use.energy, xp) else use.energy to xp
            game.rewardCell(player, reward.first, reward.first, reward.second)
        }
        mes("The " + TIER_NAMES[selection.second - 1] + " cell is consumed as it <col=ff0000>" + use.verb + "</col> the barrier.")
    }

    private suspend fun ProtectedAccess.assemble(catalytic: Boolean, selected: String? = null) {
        if (!insideArena()) return
        if (game.phase == GotrPhase.GRACE) { mesbox("The Guardian is dormant. Its energy might return soon."); return }
        if (!inv.contains(CHISEL)) { mes("You need a chisel to assemble a guardian."); return }
        if (defence.guardianCount() >= defence.guardianLimit()) { mes("There are already too many guardians active."); return }
        val selection = selectCell(selected) ?: return
        anim("seq.gotr_chisel_guardian") // The sequence contains its own skeletal sound.
        delay(2)
        if (!insideArena() || game.phase == GotrPhase.GRACE || !inv.contains(CHISEL)) return
        if (selection.first == UNCHARGED && game.phase != GotrPhase.LOBBY) return
        if (defence.guardianCount() >= defence.guardianLimit()) { mes("There are already too many guardians active."); return }
        if (invDel(inv, selection.first, 1).failure) return
        if (!defence.assemble(selection.second, catalytic)) {
            check(invAdd(inv, selection.first, 1).success)
            return
        }
        if (selection.first != UNCHARGED && game.phase == GotrPhase.ACTIVE) {
            val energy = GotrDefenceRules.guardianEnergy[selection.second - 1]
            game.rewardCell(player, if (catalytic) 0 else energy, if (catalytic) energy else 0, GotrDefenceRules.barrierXp[selection.second - 1])
            player.statAdvance("stat.crafting", 80.0)
        }
    }

    private fun ProtectedAccess.selectCell(selected: String?): Pair<String, Int>? {
        val cell = selected ?: GotrDefenceRules.cells.firstOrNull { inv.contains(it) }
            ?: UNCHARGED.takeIf { game.phase == GotrPhase.LOBBY && inv.contains(it) }
        if (cell == UNCHARGED && game.phase == GotrPhase.LOBBY && inv.contains(cell)) return cell to 1
        val tier = GotrDefenceRules.cells.indexOf(cell) + 1
        if (tier <= 0 || cell == null || !inv.contains(cell)) {
            mes("You need a charged cell to do this.")
            return null
        }
        return cell to tier
    }

    private fun ProtectedAccess.insideArena(): Boolean {
        val c = player.coords
        return game.isMember(player) && c.level == 0 && c.x in 3600..3635 && c.z in 9485..9529
    }

    private companion object {
        const val UNCHARGED = "obj.gotr_cell_uncharged"
        const val CHISEL = "obj.chisel"
        const val FRAGMENT = "obj.gotr_guardian_fragment"
        val TIER_NAMES = listOf("weak", "medium", "strong", "overcharged")
        val TILE_SYMBOLS = listOf("loc.gotr_cell_tile_inactive", "loc.gotr_cell_tile_broken", "loc.gotr_cell_tile_broken_repairable") + (1..4).map { "loc.gotr_cell_tile_tier" + it }
        val COMBAT_NPCS = listOf("npc.gotr_abyssal_leech", "npc.gotr_abyssal_walker", "npc.gotr_abyssal_pyramid", "npc.gotr_guardian_air", "npc.gotr_guardian_water", "npc.gotr_guardian_earth", "npc.gotr_guardian_fire", "npc.gotr_guardian_mind", "npc.gotr_guardian_chaos", "npc.gotr_guardian_death", "npc.gotr_guardian_blood")
    }
}
