package org.rsmod.content.bosses.tormenteddemon

import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.invtx.select
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
class TormentedWeaponExchangeTest {
    @Test
    fun `missing final material does not consume the synapse or iron bar`() {
        val inventory = inventory(InvObj(synapse), InvObj(ironBar), null)
        val before = inventory.objs.toList()

        val result = transactions().transaction(autoCommit = true) {
            exchangeWeaponMaterials(select(inventory), weapon.id, staffMaterials())
        }

        assertFalse(result.success)
        assertEquals(before, inventory.objs.toList())
    }

    @Test
    fun `valid recipe works in a full inventory`() {
        val inventory = inventory(InvObj(synapse), InvObj(ironBar), InvObj(battlestaff))

        val result = transactions().transaction(autoCommit = true) {
            exchangeWeaponMaterials(select(inventory), weapon.id, staffMaterials())
        }

        assertTrue(result.success)
        assertEquals(InvObj(weapon), inventory[0])
        assertNull(inventory[1])
        assertNull(inventory[2])
    }

    @Test
    fun `rejected output preserves ingredients and their item variables`() {
        val inventory = inventory(InvObj(synapse), InvObj(ironBar), InvObj(battlestaff, vars = 77))
        val before = inventory.objs.toList()

        val result = transactions(restrictedOutputs = setOf(weapon.id)).transaction(autoCommit = true) {
            exchangeWeaponMaterials(select(inventory), weapon.id, staffMaterials())
        }

        assertFalse(result.success)
        assertEquals(before, inventory.objs.toList())
    }

    @Test
    fun `combining claws requires both claws and creates only one pair`() {
        val inventory = inventory(InvObj(claw), null)
        assertFalse(transactions().transaction(autoCommit = true) {
            exchangeWeaponMaterials(select(inventory), weapon.id, listOf(claw.id to 2))
        }.success)
        assertEquals(InvObj(claw), inventory[0])

        inventory[1] = InvObj(claw)
        assertTrue(transactions().transaction(autoCommit = true) {
            exchangeWeaponMaterials(select(inventory), weapon.id, listOf(claw.id to 2))
        }.success)
        assertEquals(InvObj(weapon), inventory[0])
        assertNull(inventory[1])
    }

    private fun staffMaterials() = listOf(synapse.id to 1, ironBar.id to 1, battlestaff.id to 1)

    private fun inventory(vararg items: InvObj?): Inventory =
        Inventory(InventoryServerType(size = items.size), arrayOf(*items))

    private fun transactions(restrictedOutputs: Set<Int> = emptySet()) =
        InvTransactions(emptyMap(), emptyMap(), emptyMap(), emptySet(), restrictedOutputs)

    private companion object {
        val synapse = ItemServerType(id = 1)
        val ironBar = ItemServerType(id = 2)
        val battlestaff = ItemServerType(id = 3)
        val weapon = ItemServerType(id = 4)
        val claw = ItemServerType(id = 5)
    }
}
