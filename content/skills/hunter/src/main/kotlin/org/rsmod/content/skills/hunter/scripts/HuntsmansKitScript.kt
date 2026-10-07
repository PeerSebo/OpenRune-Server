package org.rsmod.content.skills.hunter.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class HuntsmansKitScript : PluginScript() {
    private var ProtectedAccess.quantity by intVarBit("varbit.ii_elnock_storage_selectedquantity")
    override fun ScriptContext.startup() {
        onOpHeld1(KIT) { open() }
        onOpHeld2(KIT) { fill() }
        onOpHeld3(KIT) { empty() }
        onOpHeldU(KIT) {
            if (!deposit(it.secondSlot, Int.MAX_VALUE)) mes("You cannot store that item in your huntsman's kit, or the kit is full.")
        }
        onOpHeld5(KIT) {
            val slot = it.slot
            val expected = inv[slot]?.id ?: return@onOpHeld5
            val text = if (player.kit.occupiedSpace() == 0) "Destroy your empty huntsman's kit? It may be hard to replace." else "Destroy your huntsman's kit and all of its contents?"
            if (choice2("Destroy it.", true, "Keep it.", false, title = text) && inv[slot]?.id == expected) {
                if (invDel(inv, KIT, 1, slot = slot, ignoreVirtualStorage = true).success) for (index in player.kit.indices) player.kit[index] = null
            }
        }
        onIfOpen(MAIN) { player.startInvTransmit(player.kit) }
        onIfClose(MAIN) { player.stopInvTransmit(player.kit) }
        onIfModalDrag(ITEMS) {
            if (inv.count(KIT) == 0) return@onIfModalDrag
            val from = it.selectedSlot ?: return@onIfModalDrag
            val into = it.targetSlot ?: return@onIfModalDrag
            val kit = player.kit
            if (from !in kit.indices || into !in kit.indices || from == into) return@onIfModalDrag
            val selected = kit[from] ?: return@onIfModalDrag
            val target = kit[into]
            if (selected.id != it.selectedObj) return@onIfModalDrag
            if ((target?.id ?: -1) != (it.targetObj?.takeUnless { id -> id == 65535 } ?: -1)) return@onIfModalDrag
            kit[from] = target
            kit[into] = selected
        }
        onIfModalButton(ITEMS) {
            if (it.op == IfButtonOp.Op10) objExamine(player.kit, it.comsub)
            else {
                val expected = it.obj?.id ?: return@onIfModalButton
                val amount = requested(it.op)
                if (player.kit[it.comsub]?.id == expected) withdraw(it.comsub, amount)
            }
        }
        onIfModalButton(SIDE_ITEMS) {
            if (it.op == IfButtonOp.Op10) objExamine(inv, it.comsub)
            else {
                val expected = it.obj?.id ?: return@onIfModalButton
                val amount = requested(it.op)
                if (inv[it.comsub]?.id == expected) deposit(it.comsub, amount)
            }
        }
        onIfModalButton("component.huntsmans_kit:fill") { fill() }
        onIfModalButton("component.huntsmans_kit:empty") { empty() }
        onIfModalButton("component.huntsmans_kit:1") { quantity = 0 }
        onIfModalButton("component.huntsmans_kit:5") { quantity = 1 }
        onIfModalButton("component.huntsmans_kit:all") { quantity = 2 }
        onIfModalButton("component.huntsmans_kit:x") { quantity = 3 }
    }

    private fun ProtectedAccess.open() {
        if (inv.count(KIT) == 0) return
        ifOpenMainSidePair(MAIN, SIDE)
        ifSetEvents(ITEMS, player.kit.indices, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10, IfEvent.DragTarget)
        ifSetEvents(SIDE_ITEMS, inv.indices, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10)
        for (button in listOf("fill", "empty", "1", "5", "all", "x")) ifSetEvents("component.huntsmans_kit:$button", 0..0, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.requested(op: IfButtonOp): Int = when (op) {
        IfButtonOp.Op1 -> when (quantity) { 1 -> 5; 2 -> Int.MAX_VALUE; 3 -> countDialog(); else -> 1 }
        IfButtonOp.Op2 -> 1
        IfButtonOp.Op3 -> 5
        IfButtonOp.Op4 -> countDialog()
        IfButtonOp.Op5 -> Int.MAX_VALUE
        else -> 0
    }

    internal fun ProtectedAccess.deposit(slot: Int, amount: Int): Boolean {
        if (inv.count(KIT) == 0 || slot !in inv.indices || amount <= 0) return false
        val obj = inv[slot] ?: return false
        val type = getInvObj(obj)
        if (type.isCert || type.internalName !in STORABLE) return false
        return invMoveFromSlot(from = inv, into = player.kit, fromSlot = slot, count = minOf(amount, obj.count), strict = false).success
    }

    internal fun ProtectedAccess.withdraw(slot: Int, amount: Int): Boolean {
        if (inv.count(KIT) == 0 || slot !in player.kit.indices || amount <= 0) return false
        val obj = player.kit[slot] ?: return false
        return invMoveFromSlot(from = player.kit, into = inv, fromSlot = slot, count = minOf(amount, obj.count), strict = false).success
    }

    internal fun ProtectedAccess.fill() {
        if (inv.count(KIT) == 0) return
        var moved = false
        for (slot in inv.indices) if (deposit(slot, Int.MAX_VALUE)) moved = true
        mes(if (moved) "You put your hunting equipment into the kit." else "You have no equipment that will fit into the kit.")
    }

    internal fun ProtectedAccess.empty() {
        if (inv.count(KIT) == 0) return
        var moved = false
        for (slot in player.kit.indices.reversed()) {
            if (player.kit[slot] == null) continue
            if (!withdraw(slot, Int.MAX_VALUE)) break
            moved = true
            if (player.kit[slot] != null) break
        }
        mes(if (moved) "You take hunting equipment out of the kit." else "Your kit is empty, or your inventory is full.")
    }

    companion object {
        const val KIT = "obj.huntsmans_kit"
        const val MAIN = "interface.huntsmans_kit"
        const val SIDE = "interface.huntsmans_kit_side"
        const val ITEMS = "component.huntsmans_kit:items"
        const val SIDE_ITEMS = "component.huntsmans_kit_side:items"
        val Player.kit: Inventory get() = invMap.getOrPut("inv.huntsmans_kit")

        val STORABLE = setOf(
            "obj.hunting_ojibway_bird_snare", "obj.noose_wand", "obj.hunting_butterfly_net", "obj.ii_magic_butterfly_net",
            "obj.butterfly_jar", "obj.hunting_box_trap", "obj.hunting_snare", "obj.torch_unlit", "obj.net", "obj.rope",
            "obj.magic_imp_box", "obj.magic_imp_box_full", "obj.magic_imp_box_half", "obj.hunting_teasing_stick", "obj.hg_hunter_spear",
            "obj.hunting_camoflauge_robe_wood", "obj.hunting_trousers_wood", "obj.hunting_camoflauge_robe_polar", "obj.hunting_trousers_polar",
            "obj.hunting_camoflauge_robe_jungle", "obj.hunting_trousers_jungle", "obj.hunting_camoflauge_robe_desert", "obj.hunting_trousers_desert",
            "obj.hunting_hat_jaguar", "obj.hunting_torso_jaguar", "obj.hunting_trousers_jaguar",
            "obj.hunting_hat_leopard", "obj.hunting_torso_leopard", "obj.hunting_trousers_leopard",
            "obj.hunting_hat_tiger", "obj.hunting_torso_tiger", "obj.hunting_trousers_tiger",
            "obj.skillcape_hunting_hood", "obj.skillcape_hunting", "obj.skillcape_hunting_trimmed",
            "obj.hg_hunter_hood", "obj.hg_hunter_top", "obj.hg_hunter_legs", "obj.hg_hunter_boots",
            "obj.ring_of_pursuit", "obj.ii_impling_jar", "obj.horn_of_plenty", "obj.horn_of_plenty_uncharged", "obj.gryphon_feather",
        )
    }
}
