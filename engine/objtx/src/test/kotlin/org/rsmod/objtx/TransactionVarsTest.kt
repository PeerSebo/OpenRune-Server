package org.rsmod.objtx

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class TransactionVarsTest {
    @Test
    fun `nonzero vars include the sign bit`() {
        assertFalse(TransactionObj(CHARGED_WEAPON).hasVars)
        assertTrue(TransactionObj(CHARGED_WEAPON, vars = 1).hasVars)
        assertTrue(TransactionObj(CHARGED_WEAPON, vars = Int.MIN_VALUE).hasVars)
        assertTrue(TransactionObj(CHARGED_WEAPON, vars = -1).hasVars)
    }

    @Test
    fun `banking a weapon with high bit charges preserves its vars and separate slot`() {
        // Blowpipe scale counts occupy bits 18..31; 8192 scales sets the sign bit.
        val weapon = TransactionObj(CHARGED_WEAPON, vars = (8192 shl 18) or (50 shl 4) or 3)
        val source = inventory(TransactionInventory.NormalStack, weapon)
        val existing = weapon.copy(vars = 42)
        val bank = inventory(TransactionInventory.AlwaysStack, existing, null)

        val result = transfer(source, bank)

        assertTrue(result.success)
        assertNull(source.output[0])
        assertEquals(listOf(existing, weapon), bank.output.toList())
    }

    @Test
    fun `inserting multiple high bit var objs never stacks them`() {
        val bank = inventory(TransactionInventory.AlwaysStack, null, null)
        val transaction = transaction(bank)
        transaction.insert {
            into = bank
            obj = CHARGED_WEAPON
            vars = Int.MIN_VALUE
            strictCount = 2
        }
        val result = commit(transaction)

        assertTrue(result.success)
        assertEquals(List(2) { TransactionObj(CHARGED_WEAPON, vars = Int.MIN_VALUE) }, bank.output.toList())
    }

    @Test
    fun `high bit var objs cannot be converted to bank notes`() {
        val bank = inventory(TransactionInventory.AlwaysStack, null)
        val transaction = transaction(bank)
        transaction.certLookup = mapOf(CHARGED_WEAPON to TransactionObjTemplate(NOTED_WEAPON, 0))
        val result = transaction.InsertQuery(into = bank, obj = CHARGED_WEAPON, vars = Int.MIN_VALUE, cert = true).result()

        assertEquals(TransactionResult.VarObjIncorrectlyHasCert, result)
        assertNull(bank.image[0])
    }

    @Test
    fun `an unnoted item with a certificate counterpart retains its per item state`() {
        val plain = TransactionObj(CHARGED_WEAPON, count = 3)
        val loan = TransactionObj(CHARGED_WEAPON, vars = 1 shl 30)
        val source = inventory(TransactionInventory.NormalStack, loan)
        val bank = inventory(TransactionInventory.AlwaysStack, plain, null)
        val transaction = transaction(source, bank)
        transaction.certLookup = mapOf(CHARGED_WEAPON to TransactionObjTemplate(NOTED_WEAPON, 0))
        transaction.transfer {
            from = source; into = bank; fromSlot = 0; count = 1; untransform = true
        }
        assertTrue(commit(transaction).success)
        assertNull(source.output[0])
        assertEquals(listOf(plain, loan), bank.output.toList())
    }

    @Test
    fun `a noted item cannot receive per item state even without requesting certification`() {
        val bank = inventory(TransactionInventory.AlwaysStack, null)
        val transaction = transaction(bank)
        transaction.certLookup = mapOf(NOTED_WEAPON to TransactionObjTemplate(CHARGED_WEAPON, 1))
        val result = transaction.InsertQuery(into = bank, obj = NOTED_WEAPON, vars = 1).result()
        assertEquals(TransactionResult.VarObjIncorrectlyHasCert, result)
        assertNull(bank.image[0])
    }

    @Test
    fun `reordering high bit charged weapons swaps them without merging charges`() {
        val first = TransactionObj(CHARGED_WEAPON, vars = Int.MIN_VALUE)
        val second = first.copy(vars = Int.MIN_VALUE or 10)
        val bank = inventory(TransactionInventory.AlwaysStack, first, second)
        val transaction = transaction(bank)
        transaction.swap {
            from = bank
            into = bank
            fromSlot = 0
            intoSlot = 1
            merge = true
        }
        val result = commit(transaction)

        assertTrue(result.success)
        assertEquals(listOf(second, first), bank.output.toList())
    }

    @Test
    fun `preferred transfer of charged weapons moves only those that fit`() {
        val first = TransactionObj(CHARGED_WEAPON, vars = 10)
        val second = first.copy(vars = Int.MIN_VALUE)
        val bank = inventory(TransactionInventory.AlwaysStack, first, second)
        val backpack = inventory(TransactionInventory.NormalStack, null)

        val result = transfer(bank, backpack, count = 2, strict = false)

        assertEquals(TransactionResult.Ok(requested = 2, completed = 1), result.single())
        assertEquals(listOf(null, second), bank.output.toList())
        assertEquals(listOf(first), backpack.output.toList())
    }

    @Test
    fun `preferred transfer with no space leaves every charged weapon in its source`() {
        val first = TransactionObj(CHARGED_WEAPON, vars = 10)
        val second = first.copy(vars = Int.MIN_VALUE)
        val source = inventory(TransactionInventory.AlwaysStack, first, second)
        val occupied = TransactionObj(OTHER_ITEM)
        val destination = inventory(TransactionInventory.NormalStack, occupied)

        val result = transfer(source, destination, count = 2, strict = false)

        assertEquals(TransactionResult.NotEnoughSpace, result.single())
        assertEquals(listOf(first, second), source.output.toList())
        assertEquals(listOf(occupied), destination.output.toList())
    }

    @Test
    fun `strict transfer rolls back when not every charged weapon fits`() {
        val first = TransactionObj(CHARGED_WEAPON, vars = 10)
        val second = first.copy(vars = Int.MIN_VALUE)
        val source = inventory(TransactionInventory.AlwaysStack, first, second)
        val destination = inventory(TransactionInventory.NormalStack, null)

        val result = transfer(source, destination, count = 2, strict = true)

        assertEquals(TransactionResult.NotEnoughSpace, result.single())
        assertEquals(listOf(first, second), source.output.toList())
        assertNull(destination.output[0])
    }

    @Test
    fun `deposit all reports the actual available number of charged weapons`() {
        val first = TransactionObj(CHARGED_WEAPON, vars = Int.MIN_VALUE)
        val second = first.copy(vars = 10)
        val backpack = inventory(TransactionInventory.NormalStack, first, second)
        val bank = inventory(TransactionInventory.AlwaysStack, null, null)

        val result = transfer(backpack, bank, count = Int.MAX_VALUE, strict = false)

        assertEquals(TransactionResult.Ok(requested = 2, completed = 2), result.single())
        assertTrue(backpack.output.all { it == null })
        assertEquals(listOf(first, second), bank.output.toList())
    }

    @Test
    fun `ordinary uncharged stacks continue merging`() {
        val source = inventory(TransactionInventory.AlwaysStack, TransactionObj(OTHER_ITEM, count = 5))
        val bank = inventory(TransactionInventory.AlwaysStack, TransactionObj(OTHER_ITEM, count = 10))

        val result = transfer(source, bank, count = Int.MAX_VALUE, strict = false)

        assertEquals(TransactionResult.Ok(requested = 5, completed = 5), result.single())
        assertNull(source.output[0])
        assertEquals(TransactionObj(OTHER_ITEM, count = 15), bank.output[0])
    }

    private fun inventory(
        stackType: TransactionInventory.StackType,
        vararg objs: TransactionObj?,
    ): TransactionInventory<TransactionObj> {
        val output = Array(objs.size) { objs[it] }
        return TransactionInventory(stackType, output, output.copyOf())
    }

    private fun transaction(
        vararg inventories: TransactionInventory<TransactionObj>,
    ): Transaction<TransactionObj> =
        Transaction<TransactionObj>(input = { it }, output = { it }).apply {
            inventories.forEach { register(it) }
        }

    private fun transfer(
        from: TransactionInventory<TransactionObj>,
        into: TransactionInventory<TransactionObj>,
        count: Int = 1,
        strict: Boolean = true,
    ): TransactionResultList<TransactionObj> {
        val transaction = transaction(from, into)
        try {
            transaction.transfer {
                this.from = from
                this.into = into
                this.fromSlot = 0
                this.count = count
                this.strict = strict
            }
        } catch (_: TransactionCancellation) {
        }
        return commit(transaction)
    }

    private fun commit(transaction: Transaction<TransactionObj>): TransactionResultList<TransactionObj> =
        transaction.results().also { if (it.success) it.commitAll() }

    private companion object {
        private const val CHARGED_WEAPON = 1
        private const val NOTED_WEAPON = 2
        private const val OTHER_ITEM = 3
    }
}
