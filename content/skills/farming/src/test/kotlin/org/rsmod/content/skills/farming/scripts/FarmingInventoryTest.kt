package org.rsmod.content.skills.farming.scripts

import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.select
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingInventoryTest {
    @Test
    fun `watering consumes one dose and replaces seedling with a full inventory`() {
        val inv = inventory(seedling, fullCan, filler)
        assertTrue(water(inv))
        assertEquals(listOf(watered, emptyCan, filler), inv.objs.map { it?.id })
    }

    @Test
    fun `missing watering can leaves seedling unchanged`() {
        val inv = inventory(seedling, filler)
        val before = inv.objs.toList()
        assertFalse(water(inv))
        assertEquals(before, inv.objs.toList())
    }

    @Test
    fun `missing seedling does not consume a can dose`() {
        val inv = inventory(fullCan, filler)
        val before = inv.objs.toList()
        assertFalse(water(inv))
        assertEquals(before, inv.objs.toList())
    }

    @Test
    fun `compost fills an existing bucket in a full inventory`() {
        val inv = inventory(bucket, filler)
        assertTrue(transactions.transaction(true) {
            takeCompostProduct(select(inv), compost, bucket)
        }.success)
        assertEquals(listOf(compost, filler), inv.objs.map { it?.id })
    }

    @Test
    fun `compost cannot manufacture a free bucket`() {
        val inv = inventory(null, filler)
        val before = inv.objs.toList()
        assertFalse(transactions.transaction(true) {
            takeCompostProduct(select(inv), compost, bucket)
        }.success)
        assertEquals(before, inv.objs.toList())
    }

    @Test
    fun `rotten tomatoes need space but no bucket`() {
        val inv = inventory(null, filler)
        assertTrue(transactions.transaction(true) {
            takeCompostProduct(select(inv), rottenTomato, null)
        }.success)
        assertEquals(rottenTomato, inv[0]?.id)
        assertFalse(transactions.transaction(true) {
            takeCompostProduct(select(inv), rottenTomato, null)
        }.success)
    }

    @Test
    fun `prepared compost collection leaves bucket intact until bin accepts the take`() {
        val inv = inventory(bucket, filler)
        val collection = transactions.transaction(false) {
            takeCompostProduct(select(inv), compost, bucket)
        }
        assertTrue(collection.success)
        assertEquals(bucket, inv[0]?.id)
        collection.commitAll()
        assertEquals(compost, inv[0]?.id)
    }

    @Test
    fun `gardener accepts payment split across noted and unnoted produce`() {
        val inv = inventory(payment, payment, null)
        inv[2] = InvObj(ItemServerType(id = notedPayment), 3)
        assertTrue(transactions.transaction(true) {
            takeGardenerPayment(select(inv), payment, notedPayment, 2, 5)
        }.success)
        assertTrue(inv.objs.all { it == null })
    }

    @Test
    fun `insufficient mixed gardener payment restores every item`() {
        val inv = inventory(payment, notedPayment)
        val before = inv.objs.toList()
        assertFalse(transactions.transaction(true) {
            takeGardenerPayment(select(inv), payment, notedPayment, 1, 3)
        }.success)
        assertEquals(before, inv.objs.toList())
    }

    private fun water(inv: Inventory): Boolean = transactions.transaction(true) {
        waterSeedling(select(inv), seedling, watered, fullCan, emptyCan)
    }.success

    private fun inventory(vararg ids: Int?): Inventory = Inventory(
        InventoryServerType(size = ids.size),
        ids.map { id -> id?.let { InvObj(ItemServerType(id = it)) } }.toTypedArray(),
    )

    private companion object {
        const val seedling = 1
        const val watered = 2
        const val fullCan = 3
        const val emptyCan = 4
        const val filler = 5
        const val bucket = 6
        const val compost = 7
        const val rottenTomato = 8
        const val payment = 9
        const val notedPayment = 10
        val transactions = InvTransactions(emptyMap(), emptyMap(), emptyMap(), setOf(notedPayment), emptySet())
    }
}
