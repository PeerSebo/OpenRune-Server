package org.rsmod.content.skills.magic.telegrab

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TelekineticGrabRestrictionTest {
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private val transactionField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }

    private val storageField by lazy {
        Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
            .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    }

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun transactions() {
        oldTransactions = transactionField.get(null)
        oldStorage = storageField.get(null)
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterEach fun restore() {
        transactionField.set(null, oldTransactions)
        storageField.set(null, oldStorage)
    }

    @Test fun `forbidden ground bones fail before spell requirements or costs are touched`() {
        val f = Fixture()
        val ground = f.ground("obj.bull_bones")
        val rune = checkNotNull(ServerCacheManager.getItem("obj.lawrune".asRSCM()))
        f.player.inv[0] = InvObj(rune, 1)
        val before = f.player.inv.objs.toList()
        complete { with(f.script) { f.access.castTelegrab(unused<MagicSpell>(), ground) } }
        assertEquals(before, f.player.inv.objs.toList())
        assertEquals(0, f.player.statMap.getFineXP("stat.magic"))
        assertEquals(1, f.registry.count())
    }

    @Test fun `delivery rechecks pickup restrictions after the projectile flight`() {
        val f = Fixture()
        val ground = f.ground("obj.bones")
        f.denyAll = true
        with(f.script) { f.access.deliver(ground) }
        assertEquals(1, f.registry.count())
        assertTrue(f.player.inv.objs.all { it == null })
    }

    @Test fun `ordinary ground items still arrive through the normal inventory transaction`() {
        val f = Fixture()
        val ground = f.ground("obj.bones")
        with(f.script) { f.access.deliver(ground) }
        assertEquals(0, f.registry.count())
        assertEquals(1, f.player.inv.count("obj.bones"))
        with(f.script) { f.access.deliver(ground) }
        assertEquals(1, f.player.inv.count("obj.bones"))
    }

    private class Fixture {
        var denyAll = false
        val player = Player().apply {
            observerUUID = 1; coords = CoordGrid(3200, 3200)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        }
        val updates = ZoneUpdateMap()
        val registry = ObjRegistry(updates)
        val objs = ObjRepository(MapClock(100), registry)
        val access = ProtectedAccess(player, GameCoroutine(), unused<ProtectedAccessContext>())
        val validator = PlayerObjTakeValidator(setOf(PlayerObjTakeValidateHook { _, _, type ->
            if (denyAll || type.id == "obj.bull_bones".asRSCM()) "These bones must be buried." else null
        }))
        val script = TelekineticGrabScript(MagicSpellRegistry(), unused<MagicRuneManager>(), objs,
            WorldRepository(updates), validator)
        fun ground(type: String): Obj = Obj.fromOwner(player, player.coords.translateX(3), type, 1)
            .also { assertTrue(objs.add(it, 100)) }
    }

    private fun complete(block: suspend () -> Unit) {
        var finished = false
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); finished = true }
        })
        assertTrue(finished)
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
