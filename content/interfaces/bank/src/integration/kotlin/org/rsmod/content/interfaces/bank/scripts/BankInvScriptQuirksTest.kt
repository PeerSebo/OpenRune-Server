package org.rsmod.content.interfaces.bank.scripts

import dev.openrune.types.util.UncheckedType
import net.rsprot.protocol.game.outgoing.inv.UpdateInvPartial
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.game.inv.InvObj

/* Obj transaction system is not thread-safe. */
@Execution(ExecutionMode.SAME_THREAD)
@OptIn(UncheckedType::class)
class BankInvScriptQuirksTest {
    @Test
    fun GameTestState.`withdraw un-certifiable obj as cert message`() =
        runGameTest(BankInvScript::class) {
            val noCert = firstItemServerType { it.certlink == 0 }
            val bank = openBank(player)
            bank[0] = InvObj(noCert, 100)
            player.bankTabSizeMain = 1
            player.withdrawCert = true

            player.clearInv()
            player.ifButton(bank_components.main_inventory, comsub = 0, op = MAIN_INV_WITHDRAW_1)
            advance()

            check(player.inv[0] == InvObj(noCert, 1)) {
                "Unexpected withdrawn obj result: ${player.inv[0]}"
            }

            assertMessageSent("This item cannot be withdrawn as a note.")
        }

    @Test
    fun GameTestState.`withdraw over non-stackable capacity message`() =
        runGameTest(BankInvScript::class) {
            val noStack = firstItemServerType { !it.isStackable }
            val bank = openBank(player)
            bank[0] = InvObj(noStack, 100)
            player.bankTabSizeMain = 1

            player.clearInv()
            player.ifButton(bank_components.main_inventory, comsub = 0, op = MAIN_INV_WITHDRAW_ALL)
            advance()

            check(player.inv.count(noStack) == player.inv.size) {
                "Unexpected withdrawn obj result: expected full inventory."
            }

            assertMessageSent("You don't have enough inventory space to withdraw that many.")
        }

    @Test
    fun GameTestState.`withdraw over stackable capacity message`() =
        runGameTest(BankInvScript::class) {
            val stackable = firstItemServerType { it.isStackable }
            val bank = openBank(player)
            bank[0] = InvObj(stackable, Int.MAX_VALUE)
            player.bankTabSizeMain = 1

            player.clearInv()
            player.inv[0] = InvObj(stackable, 1000)

            player.ifButton(bank_components.main_inventory, comsub = 0, op = MAIN_INV_WITHDRAW_ALL)
            advance()

            check(player.inv[0] == InvObj(stackable, Int.MAX_VALUE)) {
                "Unexpected withdrawn obj result: ${player.inv[0]}"
            }

            assertMessageSent("You don't have enough inventory space to withdraw that many.")
        }

    @Test
    fun GameTestState.`deposit over capacity message`() =
        runGameTest(BankInvScript::class) {
            val stackable = firstItemServerType { it.isStackable && !it.isCert }
            val bank = openBank(player)
            bank[0] = InvObj(stackable, 1000)
            player.bankTabSizeMain = 1

            player.clearInv()
            player.inv[0] = InvObj(stackable, Int.MAX_VALUE)

            player.ifButton(bank_components.side_inventory, comsub = 0, op = SIDE_INV_DEPOSIT_ALL)
            advance()

            check(bank[0] == InvObj(stackable, Int.MAX_VALUE)) {
                "Unexpected deposit obj result: ${bank[0]}"
            }

            assertMessageSent("You already have a full stack of that item in the bank.")
        }

    @Test
    fun GameTestState.`deposit inv that contains an unbankable obj message`() =
        runGameTest(BankInvScript::class) {
            val unbankable = firstItemServerType { it.param(params.no_bank) != 0 }
            openBank(player)

            player.fillInv()
            player.inv[0] = InvObj(unbankable)

            player.ifButton(bank_components.deposit_inventory)
            advance()

            check(player.inv[0] == InvObj(unbankable)) {
                "Unexpected deposit obj result: ${player.inv[0]}"
            }

            assertMessageSent("Some of your items cannot be stored in the bank.")
        }

    @Test
    fun GameTestState.`deposit inv that only has an unbankable obj message`() =
        runGameTest(BankInvScript::class) {
            val unbankable = firstItemServerType { it.param(params.no_bank) != 0 }
            openBank(player)

            player.clearInv()
            player.inv[0] = InvObj(unbankable)

            player.ifButton(bank_components.deposit_inventory)
            advance()

            check(player.inv[0] == InvObj(unbankable)) {
                "Unexpected deposit obj result: ${player.inv[0]}"
            }

            assertMessageSent("Your items cannot be stored in the bank.")
        }

    @Test
    fun GameTestState.`drag into extended tab slots`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 = listOf(InvObj(1, 1), InvObj(2, 1), InvObj(3, 1), InvObj(4, 1), InvObj(5, 1))

            val tab2 =
                listOf(
                    InvObj(6, 1),
                    null,
                    InvObj(7, 1),
                    InvObj(8, 1),
                    InvObj(9, 1),
                    null,
                    InvObj(10, 1),
                )

            val combined = (tab1 + tab2).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSize2 = tab2.size

            val tab1ExtendedComsub = bank.size + bank_comsubs.tab_extended_slots_offset.first

            player.insertMode = false
            player.ifButtonD(
                fromComponent = bank_components.main_inventory,
                fromComsub = 8,
                intoComsub = tab1ExtendedComsub,
            )

            val expectedTab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(8, 1),
                )

            val expectedTab2 =
                listOf(InvObj(6, 1), null, InvObj(7, 1), InvObj(9, 1), null, InvObj(10, 1))

            val expectedCombined = expectedTab1 + expectedTab2

            advance()
            assertEquals(expectedCombined, bank.objs.copyOf(expectedCombined.size).toList())
            assertEquals(expectedTab1.size, player.bankTabSize1)
            assertEquals(expectedTab2.size, player.bankTabSize2)
        }

    @Test
    fun GameTestState.`drag into extended tab slots taking empty slot`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(InvObj(1, 1), InvObj(2, 1), InvObj(3, 1), null, InvObj(4, 1), InvObj(5, 1))

            val tab2 =
                listOf(
                    InvObj(6, 1),
                    null,
                    InvObj(7, 1),
                    InvObj(8, 1),
                    InvObj(9, 1),
                    null,
                    InvObj(10, 1),
                )

            val combined = (tab1 + tab2).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSize2 = tab2.size

            val tab1ExtendedComsub = bank.size + bank_comsubs.tab_extended_slots_offset.first

            player.insertMode = false
            player.ifButtonD(
                fromComponent = bank_components.main_inventory,
                fromComsub = 9,
                intoComsub = tab1ExtendedComsub,
            )

            val expectedTab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    InvObj(8, 1),
                    InvObj(4, 1),
                    InvObj(5, 1),
                )

            val expectedTab2 =
                listOf(InvObj(6, 1), null, InvObj(7, 1), InvObj(9, 1), null, InvObj(10, 1))

            val expectedCombined = expectedTab1 + expectedTab2

            advance()
            assertEquals(expectedCombined, bank.objs.copyOf(expectedCombined.size).toList())
            assertEquals(expectedTab1.size, player.bankTabSize1)
            assertEquals(expectedTab2.size, player.bankTabSize2)
        }

    @Test
    fun GameTestState.`drag into extended tab slot of same tab is rejected`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(6, 1),
                )

            tab1.toTypedArray().copyInto(bank.objs)
            player.bankTabSize1 = tab1.size

            // Discard the initial full inventory update so it cannot hide the partial resync under test.
            advance()

            val tab1ExtendedComsub = bank.size + bank_comsubs.tab_extended_slots_offset.first

            player.insertMode = false
            player.ifButtonD(
                fromComponent = bank_components.main_inventory,
                fromComsub = 1,
                intoComsub = tab1ExtendedComsub,
            )

            advance()
            assertEquals(tab1, bank.objs.copyOf(tab1.size).toList())
            assertEquals(tab1.size, player.bankTabSize1)

            assertTrue(client.anyOf<UpdateInvPartial> { it.inventoryId == bank.type.id })
        }

    @Test
    fun GameTestState.`shift from higher to lower tab always shifts target tab objs to right`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(InvObj(1, 1), InvObj(2, 1), InvObj(3, 1), null, InvObj(4, 1), InvObj(5, 1))

            val tab2 =
                listOf(
                    InvObj(6, 1),
                    null,
                    InvObj(7, 1),
                    InvObj(8, 1),
                    InvObj(9, 1),
                    null,
                    InvObj(10, 1),
                )

            val combined = (tab1 + tab2).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSize2 = tab2.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 9, intoComsub = 4)

            val expectedTab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    null,
                    InvObj(8, 1),
                    InvObj(4, 1),
                    InvObj(5, 1),
                )

            val expectedTab2 =
                listOf(InvObj(6, 1), null, InvObj(7, 1), InvObj(9, 1), null, InvObj(10, 1))

            val expectedCombined = expectedTab1 + expectedTab2

            advance()
            assertEquals(expectedCombined, bank.objs.copyOf(expectedCombined.size).toList())
            assertEquals(expectedTab1.size, player.bankTabSize1)
            assertEquals(expectedTab2.size, player.bankTabSize2)
        }

    @Test
    fun GameTestState.`shift from lower to higher tab always shifts target tab objs to right`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(InvObj(1, 1), InvObj(2, 1), InvObj(3, 1), null, InvObj(4, 1), InvObj(5, 1))

            val tab2 =
                listOf(
                    InvObj(6, 1),
                    null,
                    InvObj(7, 1),
                    InvObj(8, 1),
                    InvObj(9, 1),
                    null,
                    InvObj(10, 1),
                )

            val combined = (tab1 + tab2).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSize2 = tab2.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 4, intoComsub = 9)

            val expectedTab1 = listOf(InvObj(1, 1), InvObj(2, 1), InvObj(3, 1), null, InvObj(5, 1))

            val expectedTab2 =
                listOf(
                    InvObj(6, 1),
                    null,
                    InvObj(7, 1),
                    InvObj(4, 1),
                    InvObj(8, 1),
                    InvObj(9, 1),
                    null,
                    InvObj(10, 1),
                )

            val expectedCombined = expectedTab1 + expectedTab2

            advance()
            assertEquals(expectedCombined, bank.objs.copyOf(expectedCombined.size).toList())
            assertEquals(expectedTab1.size, player.bankTabSize1)
            assertEquals(expectedTab2.size, player.bankTabSize2)
        }

    @Test
    fun GameTestState.`shift within same tab from lower to higher slot shifts to left`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(6, 1),
                )

            tab1.toTypedArray().copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 2, intoComsub = 5)

            val expectedTab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(3, 1),
                    InvObj(6, 1),
                )

            advance()
            assertEquals(expectedTab1, bank.objs.copyOf(expectedTab1.size).toList())
        }

    @Test
    fun GameTestState.`shift within same tab from higher to lower slot shifts to right`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(6, 1),
                )

            tab1.toTypedArray().copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 5, intoComsub = 2)

            val expectedTab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(5, 1),
                    InvObj(3, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(6, 1),
                )

            advance()
            assertEquals(expectedTab1, bank.objs.copyOf(expectedTab1.size).toList())
        }

    @Test
    fun GameTestState.`deposit new obj into focused tab compresses other tabs`() =
        runGameTest(BankInvScript::class) {
            val nonCert = firstItemServerType { !it.isCert }
            val bank = openBank(player)
            player.inv[0] = InvObj(nonCert)

            check(bank.isEmpty())

            val tab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(6, 1),
                )

            val mainTab =
                listOf(InvObj(7, 1), null, InvObj(8, 1), InvObj(9, 1), null, null, InvObj(10, 1))

            val combined = (tab1 + mainTab).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSizeMain = mainTab.size

            player.selectedTab = BankTab.Tab1
            player.ifButton(bank_components.side_inventory, comsub = 0, op = SIDE_INV_DEPOSIT_1)

            advance()

            check(bank[6] == InvObj(nonCert)) { "Unexpected deposit result: ${bank[3]}" }

            val expectedMainTab = listOf(InvObj(7, 1), InvObj(8, 1), InvObj(9, 1), InvObj(10, 1))
            val actualMainTab =
                bank.objs.copyOfRange(tab1.size + 1, (tab1.size + 1) + mainTab.size - 3)

            assertEquals(expectedMainTab, actualMainTab.toList())
        }

    @Test
    fun GameTestState.`deposit new obj into main tab does not compress other tabs`() =
        runGameTest(BankInvScript::class) {
            val nonCert = firstItemServerType { !it.isCert }
            val bank = openBank(player)
            player.inv[0] = InvObj(nonCert)

            check(bank.isEmpty())

            val tab1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    null,
                    InvObj(3, 1),
                    null,
                    InvObj(4, 1),
                    InvObj(5, 1),
                    InvObj(6, 1),
                )

            val mainTab = listOf(InvObj(7, 1), InvObj(8, 1), InvObj(9, 1), InvObj(10, 1))

            val combined = (tab1 + mainTab).toTypedArray()
            combined.copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.bankTabSizeMain = mainTab.size

            player.selectedTab = BankTab.Main
            player.ifButton(bank_components.side_inventory, comsub = 0, op = SIDE_INV_DEPOSIT_1)

            advance()
            check(bank[12] == InvObj(nonCert)) { "Unexpected deposit result: ${bank[12]}" }

            assertEquals(tab1, bank.objs.copyOf(tab1.size).toList())
        }

    @Test
    fun GameTestState.`shift 2+ slots in same tab shifts objs on the right side to the left`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val tab1 = listOf(InvObj(1, 1), null, null, null, null, InvObj(2, 1), InvObj(3, 1))

            tab1.toTypedArray().copyInto(bank.objs)
            player.bankTabSize1 = tab1.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 5, intoComsub = 1)

            val expectedTab1Pt1 = listOf(InvObj(1, 1), InvObj(2, 1), null, null, null, InvObj(3, 1))

            advance()
            assertEquals(expectedTab1Pt1, bank.objs.copyOf(expectedTab1Pt1.size).toList())

            player.ifButtonD(bank_components.main_inventory, fromComsub = 1, intoComsub = 4)

            val expectedTab1Pt2 = listOf(InvObj(1, 1), null, null, InvObj(2, 1), InvObj(3, 1))

            advance()
            assertEquals(expectedTab1Pt2, bank.objs.copyOf(expectedTab1Pt2.size).toList())
        }

    @Test
    fun GameTestState.`shift 2+ slots in main tab does not shift rest of objs`() =
        runGameTest(BankInvScript::class) {
            val bank = openBank(player)
            check(bank.isEmpty())

            val mainTab =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    null,
                    null,
                    null,
                    null,
                    InvObj(3, 1),
                    InvObj(4, 1),
                )

            mainTab.toTypedArray().copyInto(bank.objs)
            player.bankTabSizeMain = mainTab.size
            player.insertMode = true

            player.ifButtonD(bank_components.main_inventory, fromComsub = 6, intoComsub = 2)

            val expectedMainTabPt1 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    InvObj(3, 1),
                    null,
                    null,
                    null,
                    null,
                    InvObj(4, 1),
                )

            advance()
            assertEquals(expectedMainTabPt1, bank.objs.copyOf(expectedMainTabPt1.size).toList())

            player.ifButtonD(bank_components.main_inventory, fromComsub = 2, intoComsub = 6)

            val expectedTab1Pt2 =
                listOf(
                    InvObj(1, 1),
                    InvObj(2, 1),
                    null,
                    null,
                    null,
                    null,
                    InvObj(3, 1),
                    InvObj(4, 1),
                )

            advance()
            assertEquals(expectedTab1Pt2, bank.objs.copyOf(expectedTab1Pt2.size).toList())
        }
}
