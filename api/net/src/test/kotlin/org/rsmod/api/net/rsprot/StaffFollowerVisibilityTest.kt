package org.rsmod.api.net.rsprot

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.api.EntityInfoProtocols
import net.rsprot.protocol.api.suppliers.NpcInfoSupplier
import net.rsprot.protocol.api.suppliers.PlayerInfoSupplier
import net.rsprot.protocol.api.suppliers.WorldEntityInfoSupplier
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.InfoProtocols
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfo
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.StaffVisibilityChanged
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("rsprot-info-protocols")
class StaffFollowerVisibilityTest {
    @Test
    fun `owned follower disappears and returns without losing NPC identity or state`() = Fixture().use { f ->
        val npc = f.spawn("npc.skillpet_thieving", f.owner)
        val uid = npc.uid
        val origin = npc.coords
        val owner = npc.spawnOwner
        val mode = npc.mode
        f.tick()
        assertEquals(1, f.observers(npc))
        f.toggle(true)
        f.tick()
        assertEquals(0, f.observers(npc))
        assertSame(npc, f.npcs[npc.slotId])
        assertEquals(uid, npc.uid)
        assertEquals(origin, npc.coords)
        assertEquals(owner, npc.spawnOwner)
        assertEquals(mode, npc.mode)
        assertFalse(npc.hidden, "Only the output gate changes")
        f.toggle(false)
        f.tick()
        assertEquals(1, f.observers(npc))
    }

    @Test
    fun `new cats and transformed pets spawned while hidden never acquire an observer`() = Fixture().use { f ->
        f.toggle(true)
        for (symbol in listOf("npc.kittenpet1", "npc.growncat", "npc.skillpet_thieving_panda")) {
            val npc = f.spawn(symbol, f.owner)
            assertTrue(npc.type.isFollower, symbol)
            f.tick()
            assertEquals(0, f.observers(npc), symbol)
        }
        f.toggle(false)
        f.tick()
        for (npc in f.npcs) assertEquals(1, f.observers(npc))
    }

    @Test
    fun `unowned followers another owners pet and owned combat NPCs remain visible`() = Fixture().use { f ->
        val unowned = f.spawn("npc.skillpet_thieving", null)
        val otherPet = f.spawn("npc.kittenpet1", f.otherOwner)
        val monster = f.spawn("npc.chicken", f.owner)
        assertFalse(monster.type.isFollower)
        f.toggle(true)
        f.tick()
        for (npc in listOf(unowned, otherPet, monster)) assertEquals(1, f.observers(npc))
    }

    @Test
    fun `staff reveal preserves lifecycle hiding and lifecycle reveal preserves staff suppression`() = Fixture().use { f ->
        val npc = f.spawn("npc.skillpet_thieving", f.owner)
        val protocol = npc.infoProtocol as RspNpcInfo
        protocol.hide()
        f.toggle(true)
        f.tick()
        f.toggle(false)
        f.tick()
        assertEquals(0, f.observers(npc))
        f.toggle(true)
        protocol.reveal()
        f.tick()
        assertEquals(0, f.observers(npc))
        f.toggle(false)
        f.tick()
        assertEquals(1, f.observers(npc))
    }

    private class Fixture : AutoCloseable {
        val npcs = NpcList()
        private val players = PlayerList()
        private val events = EventBus()
        val owner = player(1)
        val otherOwner = player(2)
        private val entities: EntityInfoProtocols
        private val protocols: InfoProtocols
        private val info: net.rsprot.protocol.game.outgoing.info.Infos
        private var nextNpc = 1

        init {
            val bits = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
            val huffman = try { DefaultHuffmanCodecProvider(HuffmanCodec.create(bits)) } finally { bits.release() }
            val companion = EntityInfoProtocols::class.java.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            val initialize = companion.javaClass.declaredMethods.single { it.name.startsWith("initialize") && it.parameterCount == 8 }
            initialize.isAccessible = true
            entities = initialize.invoke(companion, UnpooledByteBufAllocator.DEFAULT,
                listOf(OldSchoolClientType.DESKTOP), huffman, PlayerInfoSupplier(), NpcInfoSupplier(), WorldEntityInfoSupplier(), null, null) as EntityInfoProtocols
            protocols = InfoProtocols(entities.playerInfoProtocol, entities.npcInfoProtocol, entities.worldEntityInfoProtocol)
            info = protocols.alloc(1, OldSchoolClientType.DESKTOP)
            info.updateRootCoord(0, 3200, 3200)
            info.updateRootBuildAreaCenteredOnPlayer(3200, 3200)
            with(StaffFollowerVisibility(npcs, players)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        private fun player(index: Int): Player = Player().apply {
            slotId = index; uuid = index.toLong(); assignUid()
            coords = CoordGrid(3200, 3200)
            players[index] = this
        }

        fun spawn(symbol: String, owner: Player?): Npc {
            val npc = Npc(symbol, CoordGrid(3201, 3200)).apply {
                slotId = nextNpc++; assignUid(); mode = NpcMode.PlayerFollow
                if (owner != null) spawnOwner = owner.uid
            }
            npcs[npc.slotId] = npc
            npc.infoProtocol = RspNpcInfo(entities.npcAvatarFactory.alloc(npc.slotId, npc.id, npc.level, npc.x, npc.z))
            return npc
        }

        fun toggle(hidden: Boolean) {
            owner.staffInvisible = hidden
            events.publish(StaffVisibilityChanged(owner))
        }

        fun tick() {
            events.publish(GameLifecycle.LateCycle)
            protocols.npcInfoProtocol.update()
            info.npcInfo.internalPacketResult(NpcInfo.ROOT_WORLD).getOrThrow().release()
        }

        fun observers(npc: Npc): Int = (npc.infoProtocol as RspNpcInfo).rspAvatar.getObserverCount()

        override fun close() {
            protocols.dealloc(info)
            for (npc in npcs) entities.npcAvatarFactory.release((npc.infoProtocol as RspNpcInfo).rspAvatar)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
