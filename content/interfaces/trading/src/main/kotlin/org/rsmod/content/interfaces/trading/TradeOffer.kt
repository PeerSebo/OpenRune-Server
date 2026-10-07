package org.rsmod.content.interfaces.trading

import org.rsmod.game.inv.InvObj

internal class TradeOffer(val original: List<InvObj?>) {
    private val reserved = IntArray(original.size)
    fun available(slot: Int): Int = (original.getOrNull(slot)?.count ?: 0) - (reserved.getOrNull(slot) ?: 0)
    fun change(slot: Int, quantity: Int, adding: Boolean): Boolean {
        if (slot !in reserved.indices || quantity <= 0 || original[slot] == null) return false
        val item = checkNotNull(original[slot])
        val same = original.indices.filter { original[it]?.id == item.id && original[it]?.vars == item.vars }
            .sumOf { reserved[it].toLong() }
        val count = minOf(quantity, if (adding) minOf(available(slot), (Int.MAX_VALUE - same).toInt()) else reserved[slot])
        if (count <= 0) return false
        reserved[slot] += if (adding) count else -count
        return true
    }
    fun addMatching(slot: Int, quantity: Int): Boolean {
        if (quantity <= 0) return false
        val item = original.getOrNull(slot) ?: return false
        var left = quantity
        val before = items().sumOf { it.count.toLong() }
        for (source in listOf(slot) + original.indices.filter { it != slot }) {
            if (original[source]?.id != item.id || original[source]?.vars != item.vars) continue
            val old = reserved[source]
            change(source, left, true)
            left -= reserved[source] - old
            if (left == 0) break
        }
        return items().sumOf { it.count.toLong() } > before
    }
    fun remaining(): List<InvObj?> = original.mapIndexed { slot, obj ->
        obj?.let { (it.count - reserved[slot]).takeIf { count -> count > 0 }?.let { count -> it.copy(count = count) } }
    }
    fun items(): List<InvObj> = original.mapIndexedNotNull { slot, obj ->
        obj?.takeIf { reserved[slot] > 0 }?.copy(count = reserved[slot])
    }
    fun display(): List<InvObj> {
        val groups = items().groupBy { it.id to it.vars }
        return groups.values.map { same ->
            val count = same.sumOf { it.count.toLong() }
            require(count <= Int.MAX_VALUE)
            same.first().copy(count = count.toInt())
        }
    }
    fun removeDisplay(slot: Int, quantity: Int): Boolean {
        if (quantity <= 0) return false
        val item = display().getOrNull(slot) ?: return false
        var left = minOf(quantity, item.count)
        for (source in original.indices) {
            val obj = original[source] ?: continue
            if (obj.id != item.id || obj.vars != item.vars) continue
            val take = minOf(left, reserved[source])
            reserved[source] -= take
            left -= take
            if (left == 0) break
        }
        return true
    }
}

internal object TradePlan {
    fun build(
        a: TradeOffer,
        b: TradeOffer,
        tradeable: (Int) -> Boolean,
        stackable: (Int) -> Boolean,
    ): Pair<List<InvObj?>, List<InvObj?>>? {
        if ((a.items() + b.items()).any { it.count <= 0 || !tradeable(it.id) }) return null
        val first = receive(a.remaining(), b.items(), stackable) ?: return null
        val second = receive(b.remaining(), a.items(), stackable) ?: return null
        return first to second
    }

    private fun receive(base: List<InvObj?>, items: List<InvObj>, stackable: (Int) -> Boolean): List<InvObj?>? {
        val result = base.toMutableList()
        for (item in items) {
            if (stackable(item.id)) {
                val existing = result.indexOfFirst { it?.id == item.id && it.vars == item.vars }
                val slot = if (existing >= 0) existing else result.indexOfFirst { it == null }
                if (slot < 0) return null
                val count = (result[slot]?.count?.toLong() ?: 0) + item.count
                if (count > Int.MAX_VALUE) return null
                result[slot] = item.copy(count = count.toInt())
            } else {
                if (item.count > result.count { it == null }) return null
                repeat(item.count) { result[result.indexOfFirst { it == null }] = item.copy(count = 1) }
            }
        }
        return result
    }
}
