package org.rsmod.content.other.pouches.lootingbag

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.events.interact.HeldBanksideEvents
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.IfCloseSub
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.ifCloseModal
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class LootingBagPanelStateTest {
    @Test fun `bag inspection and deposit modes clear when their interface closes`() {
        val f = Fixture()
        f.player.inv[0] = InvObj(LootingBags.CLOSED)
        f.player.lootingBag[0] = InvObj("obj.bronze_arrow", 20)
        f.openBag(deposit = false)
        assertEquals(1, f.player.vars[View])
        assertTrue(f.player.lootingBag.type.id in f.player.transmittedInvAddQueue)
        f.closeBag()
        assertFalse(View in f.player.vars)
        assertFalse(f.player.lootingBag.type.id in f.player.transmittedInvAddQueue)

        VarPlayerIntMapSetter.set(f.player, "varbit.inside_wilderness", 1)
        f.openBag(deposit = true)
        assertEquals(2, f.player.vars[View])
        f.closeBag()
        assertFalse(View in f.player.vars)
        assertFalse(f.player.lootingBag.type.id in f.player.transmittedInvAddQueue)

        val type = checkNotNull(ServerCacheManager.getVarp(View.asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    @Test fun `opening dismissing and reopening the panel preserves contents and stops transmission once`() {
        val f = Fixture()
        f.player.lootingBag[0] = InvObj("obj.bronze_arrow", 20)
        f.open()
        assertEquals(1, f.player.vars[Panel])
        assertTrue(f.player.lootingBag.type.id in f.player.transmittedInvAddQueue)
        f.dismiss()
        assertFalse(Panel in f.player.vars)
        assertFalse(f.player.lootingBag.type.id in f.player.transmittedInvAddQueue)
        val messages = f.client.messages.size
        f.dismiss()
        assertEquals(messages, f.client.messages.size)
        f.open()
        assertEquals(1, f.player.vars[Panel])
        f.closeBank()
        assertFalse(Panel in f.player.vars)
        assertEquals(20, f.player.lootingBag.physicalCount("obj.bronze_arrow"))
    }

    @Test fun `absent and explicitly false panel state do not stop unrelated inventory transmission`() {
        for (state in listOf<Int?>(null, 0)) {
            val f = Fixture()
            state?.let { VarPlayerIntMapSetter.set(f.player, Panel, it) }
            val messages = f.client.messages.size
            f.dismiss()
            f.closeBank()
            assertEquals(messages, f.client.messages.size)
            assertEquals(state != null, Panel in f.player.vars)
        }
    }

    @Test fun `bank panel is temporary server state excluded from saved player vars`() {
        val f = Fixture()
        f.open()
        val restored = Player().apply {
            vars.backing.putAll(f.player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertFalse(Panel in restored.vars)
        val type = checkNotNull(ServerCacheManager.getVarp(Panel.asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    private class Fixture {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            inv = Inventory.create("inv.inv")
        }
        private val events = EventBus()
        init {
            val prices = object : MarketPrices {
                override fun get(type: ItemServerType): Int? = null
            }
            with(LootingBagScript(unused(), unused(), prices)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
        fun open() {
            val type = checkNotNull(ServerCacheManager.getItem(LootingBags.CLOSED.asRSCM()))
            assertTrue(events.publish(HeldBanksideEvents.Type(player, 0, type)))
        }
        fun openBag(deposit: Boolean) {
            val obj = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            runAction {
                val event = if (deposit) HeldObjEvents.Op3(0, obj, type, player.inv)
                    else HeldObjEvents.Op2(0, obj, type, player.inv)
                assertTrue(events.publish(this, event))
            }
        }
        fun closeBag() {
            player.ifCloseModal("interface.wilderness_lootingbag", events)
        }
        fun closeBank() {
            assertTrue(events.publish(IfCloseSub(player, UserInterface("interface.bankside".asRSCM()), Component.NULL)))
        }
        fun dismiss() {
            runAction {
                val button = ServerCacheManager.fromComponent("component.bankside:lootingbag_dismiss".asRSCM())
                assertTrue(events.publish(this, IfModalButton(button, -1, null, IfButtonOp.Op1)))
            }
        }
        private fun runAction(block: suspend ProtectedAccess.() -> Unit) {
            var outcome: Result<Unit>? = null
            val action: suspend () -> Unit = {
                val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
                val access = ProtectedAccess(player, GameCoroutine(), context)
                access.block()
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            checkNotNull(outcome).getOrThrow()
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val Panel = "varp.lootingbag_bank_panel"
        private const val View = "varp.lootingbag_view"

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run { isAccessible = true; get(null) as Unsafe }
            return unsafe.allocateInstance(T::class.java) as T
        }
    }
}
