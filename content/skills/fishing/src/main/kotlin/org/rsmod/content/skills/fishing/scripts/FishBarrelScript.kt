package org.rsmod.content.skills.fishing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.advanced.onOpBankside
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.fishing.table.FishingCatch
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FishBarrelScript : PluginScript() {

    private val rawFish: Set<String> by lazy {
        FishingCatch.allRows()
            .filter { it.xpTenths > 0 && !it.item.isStackable }
            .mapTo(mutableSetOf()) { it.item.internalName }
            .apply { add(RAW_KARAMBWAN) }
    }

    override fun ScriptContext.startup() {
        for (barrel in ALL_BARRELS) {
            onOpHeld1(barrel) { fill() }
            onOpHeld2(barrel) { check() }
            onOpHeld3(barrel) { toggleOpen() }
            val type =
                checkNotNull(ServerCacheManager.getItem(barrel.asRSCM(RSCMType.OBJ))) {
                    "Missing obj type: $barrel"
                }
            onOpBankside(type) { emptyIntoBank(player) }
        }

        onOpHeldU(FISH_SACK, BARREL_CLOSED) { combine(BARREL_CLOSED, SACK_CLOSED) }
        onOpHeldU(FISH_SACK, BARREL_OPEN) { combine(BARREL_OPEN, SACK_OPEN) }

        onEvent<SkillingActionCompleteEvent> {
            val ctx = context
            if (ctx is SkillingActionContext.Product && ctx.source is SkillingProductSource.Fishing) {
                tryAutoDeposit(player, ctx.item, ctx.count)
            }
        }
    }

    private fun ProtectedAccess.combine(barrel: String, sackBarrel: String) {
        val delSack = invDel(inv, FISH_SACK, count = 1)
        if (!delSack.success) {
            return
        }
        val delBarrel = invDel(inv, barrel, count = 1)
        if (!delBarrel.success) {
            invAdd(inv, FISH_SACK, count = 1)
            return
        }
        invAdd(inv, sackBarrel, count = 1)
        mes("You attach the fish sack to the barrel, making a fish sack barrel.")
    }

    private fun emptyIntoBank(player: Player) {
        val store = player.attr.getOrPut(FISH_BARREL_ATTR) { mutableMapOf() }
        if (store.isEmpty()) {
            player.mes("The fish barrel is empty.")
            return
        }
        val bank = player.invMap.getOrPut("inv.bank")
        val iterator = store.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            player.invAdd(bank, entry.key, entry.value)
            iterator.remove()
        }
        player.mes("You empty the fish barrel into your bank.")
    }

    private fun ProtectedAccess.contents(): MutableMap<String, Int> =
        player.attr.getOrPut(FISH_BARREL_ATTR) { mutableMapOf() }

    private fun MutableMap<String, Int>.total(): Int = values.sum()

    private fun ProtectedAccess.fill() {
        val store = contents()
        var filled = false
        for (name in rawFish) {
            if (store.total() >= CAPACITY) break
            val have = invTotal(inv, name)
            if (have <= 0) continue
            val move = minOf(have, CAPACITY - store.total())
            if (move <= 0) break
            val delete = invDel(inv, name, count = move)
            if (!delete.success) continue
            store[name] = (store[name] ?: 0) + move
            filled = true
        }
        mes(if (filled) "You fill the fish barrel." else "You have no raw fish to store, or the barrel is full.")
    }

    private fun ProtectedAccess.check() {
        val store = contents()
        if (store.isEmpty()) {
            mes("The fish barrel is empty.")
            return
        }
        mes("The fish barrel contains (${store.total()}/$CAPACITY):")
        for ((name, count) in store) {
            val display = ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))?.name ?: name
            mes("$display x $count")
        }
    }

    private fun ProtectedAccess.toggleOpen() {
        val (from, to, opened) =
            when {
                BARREL_OPEN in inv -> Triple(BARREL_OPEN, BARREL_CLOSED, false)
                BARREL_CLOSED in inv -> Triple(BARREL_CLOSED, BARREL_OPEN, true)
                SACK_OPEN in inv -> Triple(SACK_OPEN, SACK_CLOSED, false)
                SACK_CLOSED in inv -> Triple(SACK_CLOSED, SACK_OPEN, true)
                else -> return
            }
        invReplace(inv, from, 1, to)
        mes(if (opened) "You open the fish barrel." else "You close the fish barrel.")
    }

    private fun tryAutoDeposit(player: Player, item: String, count: Int) {
        if (BARREL_OPEN !in player.inv && SACK_OPEN !in player.inv) return
        if (item !in rawFish) return
        val store = player.attr.getOrPut(FISH_BARREL_ATTR) { mutableMapOf() }
        val room = CAPACITY - store.total()
        if (room <= 0) return
        val move = minOf(count, room)
        val delete = player.invDel(player.inv, item, count = move)
        if (!delete.success) return
        store[item] = (store[item] ?: 0) + move
    }

    companion object {
        internal fun storedCount(player: Player, item: String): Int = player.attr[FISH_BARREL_ATTR]?.get(item) ?: 0

        // Rollback only this catch's deposit, preserving fish already in the barrel.
        internal fun consumeNewlyStored(player: Player, item: String, previousCount: Int): Boolean {
            val store = player.attr[FISH_BARREL_ATTR] ?: return false
            val count = store[item] ?: return false
            if (count <= previousCount) return false
            if (count == 1) store.remove(item) else store[item] = count - 1
            return true
        }
        private const val BARREL_CLOSED = "obj.fish_barrel_closed"
        private const val BARREL_OPEN = "obj.fish_barrel_open"
        private const val SACK_CLOSED = "obj.fish_sack_barrel_closed"
        private const val SACK_OPEN = "obj.fish_sack_barrel_open"
        private const val FISH_SACK = "obj.fish_sack"
        private const val RAW_KARAMBWAN = "obj.tbwt_raw_karambwan"
        private const val CAPACITY = 28

        private val ALL_BARRELS = listOf(BARREL_CLOSED, BARREL_OPEN, SACK_CLOSED, SACK_OPEN)

        private val FISH_BARREL_ATTR =
            AttributeKey<MutableMap<String, Int>>(persistenceKey = "fish_barrel")
    }
}
