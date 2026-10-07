package org.rsmod.content.bosses.kalphitequeen

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KalphiteRopesTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private val field by lazy { Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true } }

    private val storageField by lazy { Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true } }

    @BeforeEach fun services() {
        oldTransactions = field.get(null)
        oldStorage = storageField.get(null)
        field.set(null, InvTransactions.from())
        storageField.set(null, PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restore() {
        field.set(null, oldTransactions)
        storageField.set(null, oldStorage)
    }

    @Test fun `each entrance consumes one rope and sets the correct native visual`() {
        val p = player()
        p.inv[0] = InvObj("obj.rope")
        p.inv[1] = InvObj("obj.rope")
        assertEquals(KalphiteRopes.Attachment.Attached, KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE))
        assertEquals(1, p.inv.count("obj.rope"))
        assertEquals(1, p.vars[KalphiteLairAccess.SURFACE_ROPE])
        assertEquals(0, p.vars["varbit.kalphite_chamber_entrance"])
        assertEquals(KalphiteRopes.Attachment.Attached, KalphiteRopes.attach(p, KalphiteLairAccess.CHAMBER_ROPE))
        assertEquals(0, p.inv.count("obj.rope"))
        assertEquals(1, p.vars["varbit.kalphite_chamber_entrance"])
    }

    @Test fun `no rope cannot create a free entrance and repeated attachment cannot consume another`() {
        val p = player()
        assertEquals(KalphiteRopes.Attachment.MissingRope, KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE))
        assertEquals(0, p.vars[KalphiteLairAccess.SURFACE_ROPE])
        p.inv[0] = InvObj("obj.rope")
        p.inv[1] = InvObj("obj.rope")
        KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE)
        assertEquals(KalphiteRopes.Attachment.AlreadyAttached, KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE))
        assertEquals(1, p.inv.count("obj.rope"))
    }

    @Test fun `ordinary ropes clear on exit and login`() {
        val p = player()
        p.inv[0] = InvObj("obj.rope")
        p.inv[1] = InvObj("obj.rope")
        KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE)
        KalphiteRopes.attach(p, KalphiteLairAccess.CHAMBER_ROPE)
        KalphiteRopes.leave(p, KalphiteLairAccess.CHAMBER_ROPE)
        assertEquals(0, p.vars["varbit.kalphite_chamber_entrance"])
        assertEquals(1, p.vars[KalphiteLairAccess.SURFACE_ROPE])
        KalphiteRopes.login(p)
        assertEquals(0, p.vars[KalphiteLairAccess.SURFACE_ROPE])
    }

    @Test fun `hard diary retains both ropes after they have been installed`() {
        val p = player()
        VarPlayerIntMapSetter.set(p, KalphiteLairAccess.HARD_DIARY, 1)
        assertEquals(KalphiteRopes.Attachment.MissingRope, KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE))
        p.inv[0] = InvObj("obj.rope")
        p.inv[1] = InvObj("obj.rope")
        KalphiteRopes.attach(p, KalphiteLairAccess.SURFACE_ROPE)
        KalphiteRopes.attach(p, KalphiteLairAccess.CHAMBER_ROPE)
        KalphiteRopes.leave(p, KalphiteLairAccess.CHAMBER_ROPE)
        KalphiteRopes.login(p)
        assertEquals(1, p.vars[KalphiteLairAccess.SURFACE_ROPE])
        assertEquals(1, p.vars[KalphiteLairAccess.CHAMBER_ROPE])
        assertEquals(1, p.vars["varbit.kalphite_chamber_entrance"])
    }

    private fun player() = Player().apply {
        inv = Inventory(InventoryServerType(size = 28, flags = InventoryServerType.PROTECT_FLAG), arrayOfNulls(28))
    }
}
