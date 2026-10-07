package org.rsmod.content.interfaces.bank

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfSubType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfModalDrag
import org.rsmod.api.player.ui.IfOpenSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class BankRearrangeTest {
    @Test
    fun `opening a bank repairs missing main tab metadata and allows swapping`() {
        val f = Fixture()
        f.items("obj.coins", "obj.bones", "obj.logs")
        f.open()
        f.drag(0, 2)
        f.expect("obj.logs", "obj.bones", "obj.coins")
        assertEquals(3, f.player.vars[BankTab.Main.sizeVarBit])
    }

    @Test
    fun `opening a bank repairs undersized metadata and allows insertion`() {
        val f = Fixture()
        f.items("obj.coins", "obj.bones", "obj.logs", "obj.softclay")
        VarPlayerIntMapSetter.set(f.player, BankTab.Main.sizeVarBit, 1)
        VarPlayerIntMapSetter.set(f.player, "varbit.bank_insertmode", 1)
        f.open()
        f.drag(3, 0)
        f.expect("obj.softclay", "obj.coins", "obj.bones", "obj.logs")
        assertEquals(4, f.player.vars[BankTab.Main.sizeVarBit])
    }

    @Test
    fun `main tab repair preserves named tabs gaps and item quantities`() {
        val f = Fixture()
        f.bank[0] = InvObj("obj.bones", 5)
        f.bank[1] = InvObj("obj.logs", 9)
        f.bank[2] = InvObj("obj.coins", 100)
        f.bank[4] = InvObj("obj.softclay", 20)
        VarPlayerIntMapSetter.set(f.player, BankTab.Tab1.sizeVarBit, 2)
        VarPlayerIntMapSetter.set(f.player, BankTab.Main.sizeVarBit, 0)
        val before = f.bank.objs.toList()
        f.open()
        assertEquals(before, f.bank.objs.toList())
        assertEquals(2, f.player.vars[BankTab.Tab1.sizeVarBit])
        assertEquals(3, f.player.vars[BankTab.Main.sizeVarBit])
        f.drag(2, 4)
        assertEquals(InvObj("obj.softclay", 20), f.bank[2])
        assertEquals(InvObj("obj.coins", 100), f.bank[4])
        assertEquals(before.take(2), f.bank.objs.take(2))
    }

    @Test
    fun `reopening an empty bank clears a stale main tab size`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, BankTab.Main.sizeVarBit, 100)
        f.open()
        assertEquals(0, f.player.vars[BankTab.Main.sizeVarBit])
        assertTrue(f.bank.isEmpty())
    }

    private class Fixture {
        val player = Player().apply { inv = Inventory.create("inv.inv") }
        val bank = player.invMap.getOrPut("inv.bank")
        private val events = EventBus()
        private val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())

        init {
            with(BankInvScript(events, WornBonuses(), WeaponSpeeds(AttackStyles()))) {
                context.startup()
            }
        }

        fun items(vararg items: String) {
            items.forEachIndexed { slot, symbol -> bank[slot] = InvObj(symbol) }
        }

        fun open() {
            events.publish(IfOpenSub(player, UserInterface("interface.bankmain".asRSCM()), Component(0), IfSubType.Modal))
        }

        fun drag(from: Int, to: Int) {
            val coroutine = GameCoroutine("bank-rearrange-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty())
            val component = Component("component.bankmain:items".asRSCM())
            val event = IfModalDrag(from, bank[from]?.id, to, bank[to]?.id, component, component)
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = { assertTrue(events.publish(access, event)) }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            checkNotNull(completion).getOrThrow()
        }

        fun expect(vararg items: String) {
            assertEquals(items.map { it.asRSCM() }, bank.objs.take(items.size).map { it?.id })
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
