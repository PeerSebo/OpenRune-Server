package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.baseVar
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarrockGardenPatchesTest {
    private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    private val storage = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    private var previousTransactions: Any? = null
    private var previousStorage: Any? = null
    private var previousVirtual: InvVirtualStorage? = null

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
        previousTransactions = transactions.get(null)
        previousStorage = storage.get(null)
        previousVirtual = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll fun restore() {
        transactions.set(null, previousTransactions)
        storage.set(null, previousStorage)
        InvVirtualStorageHolder.instance = previousVirtual
    }

    @Test fun `all seven garden bed types dispatch raking and retain native saved progress`() {
        val rows = DbHelper.table("dbtable.varrock_garden_patches").toList()
        assertEquals(7, rows.size)
        for (row in rows) {
            val id = row.column("dbcol.varrock_garden_patches:loc", LocIdCodec)
            val f = Fixture(id)
            assertEquals(VarpLifetime.Perm, f.state.baseVar.scope)
            f.start()
            assertEquals(f.loc.coords, f.player.pendingFaceSquare)
            assertEquals("seq.farming_raking".asRSCM(), f.player.pendingSequence.id)
            repeat(2) { f.tick() }
            assertEquals(0, f.player.vars[f.state])
            assertEquals(0, f.player.inv.count("obj.weeds"))
            f.tick()
            assertEquals(1, f.player.vars[f.state])
            repeat(6) { f.tick() }
            assertEquals(3, f.player.vars[f.state])
            assertEquals(3, f.player.inv.count("obj.weeds"))
            assertEquals(120, f.player.statMap.getFineXP("stat.farming"))
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(0, f.player.vars["varbit.garden_quest"])
            f.start()
            repeat(6) { f.tick() }
            assertEquals(3, f.player.inv.count("obj.weeds"))
        }
    }

    @Test fun `missing rake is reported without animating or clearing weeds`() {
        val f = Fixture()
        f.player.inv[0] = null
        f.start()
        assertTrue(f.client.messages.any { "need a rake" in it.toString() })
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        assertEquals(0, f.player.vars[f.state])
    }

    @Test fun `curved patches retain native progress planted stages and raking interactions`() {
        val rows = DbHelper.table("dbtable.varrock_garden_corners").toList()
        assertEquals(5, rows.size)
        for (row in rows) {
            val original = checkNotNull(ServerCacheManager.getObject(row.column("dbcol.varrock_garden_corners:patch", LocIdCodec)))
            val id = row.column("dbcol.varrock_garden_corners:corner", LocIdCodec)
            val corner = checkNotNull(ServerCacheManager.getObject(id))
            assertEquals(original.multiVarBit, corner.multiVarBit)
            assertEquals(original.width, corner.width)
            assertEquals(original.length, corner.length)
            assertEquals(original.transforms!!.drop(4), corner.transforms!!.drop(4))
            assertTrue(original.transforms!!.take(4).zip(corner.transforms!!.take(4)).all { (a, b) -> a != b })
            val f = Fixture(id)
            f.start()
            repeat(9) { f.tick() }
            assertEquals(3, f.player.vars[f.state])
            assertEquals(3, f.player.inv.count("obj.weeds"))
            assertEquals(120, f.player.statMap.getFineXP("stat.farming"))
            assertEquals(0, f.player.vars["varbit.garden_quest"])
        }
    }

    @Test fun `walking away cancels the unfinished rake without awarding weeds`() {
        val f = Fixture()
        f.start()
        f.tick()
        f.player.cancelActiveCoroutine()
        repeat(8) { f.tick() }
        assertEquals(0, f.player.vars[f.state])
        assertEquals(0, f.player.inv.count("obj.weeds"))
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
    }

    @Test fun `raking one bed preserves the adjacent bed's progress and planted crops`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.garden_snowdrops_varbit", 2)
        f.start()
        repeat(9) { f.tick() }
        assertEquals(2, f.player.vars["varbit.garden_snowdrops_varbit"])
        VarPlayerIntMapSetter.set(f.player, f.state, 5)
        f.start()
        repeat(9) { f.tick() }
        assertEquals(5, f.player.vars[f.state])
        assertEquals(3, f.player.inv.count("obj.weeds"))
    }

    private class Fixture(id: Int = "loc.garden_delphinium_patch".asRSCM()) {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3224, 3475)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            inv[0] = InvObj("obj.rake")
        }
        private val type = checkNotNull(ServerCacheManager.getObject(id))
        val state = checkNotNull(ServerCacheManager.getVarbit(type.multiVarBit))
        val loc = BoundLocInfo(LocInfo(2, CoordGrid(3225, 3475), LocEntity(id, 10, 0)), type)
        private val events = EventBus()
        private val script = VarrockGardenPatches(unused(), XpModifiers(emptySet()))
        private val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun start() {
            val event = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            player.launch {
                assertTrue(events.publish(ProtectedAccess(player, this, ProtectedAccessContextFactory.empty()), event))
            }
        }

        fun tick() {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            player.activeCoroutine?.advance()
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

    private companion object {
        fun unused(): ObjRepository {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(ObjRepository::class.java) as ObjRepository
        }
    }
}
