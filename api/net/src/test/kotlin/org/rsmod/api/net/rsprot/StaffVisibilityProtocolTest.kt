package org.rsmod.api.net.rsprot

import io.netty.buffer.ByteBuf
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
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.worker.DefaultProtocolWorker
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("rsprot-info-protocols")
class StaffVisibilityProtocolTest {
    @Test fun `native avatar hidden alone still leaks low resolution location`() = Fixture(false).use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2))
        val observer = f.login(2, staff.player.coords.translate(1, 0))
        f.tick()
        f.hide(staff)
        f.tick()
        assertFalse(observer.client.active[1])
        assertNotEquals(0, observer.client.regions[1], "Control must demonstrate the native low-resolution leak")
    }

    @Test fun `known staff is removed from wire and coarse position becomes unused slot`() = Fixture().use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2))
        val observer = f.login(2, staff.player.coords.translate(1, 0))
        f.tick()
        assertTrue(observer.client.active[1])
        f.hide(staff)
        f.tick()
        assertFalse(observer.client.active[1])
        assertEquals(0, observer.client.regions[1])
        assertTrue(staff.client.active[1], "The administrator retains their own player")
        assertEquals(staff.player.coords, staff.client.coords[1])
        assertFalse(String(observer.packet, Charsets.ISO_8859_1).contains("HiddenStaffMarker"))
    }

    @Test fun `hidden movement crosses every coarse boundary without changing observer packets`() = Fixture().use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2), hidden = true)
        val observer = f.login(2, CoordGrid(9001, 10000, 2))
        repeat(3) { f.tick() }
        val quietPacket = observer.packet.copyOf()
        for (dest in listOf(CoordGrid(100, 200, 0), CoordGrid(8193, 200, 1), CoordGrid(100, 8193, 3), CoordGrid(9000, 10000, 2))) {
            f.move(staff, dest)
            f.tick()
            assertFalse(observer.client.active[1])
            assertEquals(0, observer.client.regions[1])
            assertArrayEquals(quietPacket, observer.packet, "No movement or identity bits should be emitted to this observer")
            assertEquals(dest, staff.client.coords[1], "Hidden staff must still receive their own movement")
        }
    }

    @Test fun `login during hide tick uses its own zero baseline and reveal restores exact coordinate`() = Fixture().use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2))
        val established = f.login(2, staff.player.coords.translate(1, 0))
        f.tick()
        f.hide(staff)
        val newcomer = f.login(3, staff.player.coords.translate(2, 0))
        assertEquals(0, newcomer.client.regions[1], "RebuildLogin must already conceal the staff slot")
        f.tick()
        assertEquals(0, established.client.regions[1])
        assertEquals(0, newcomer.client.regions[1], "The previous-to-zero delta must not be applied twice")
        assertFalse(newcomer.client.active[1])
        f.hide(staff, false)
        f.tick()
        for (observer in listOf(established, newcomer)) {
            assertTrue(observer.client.active[1])
            assertEquals(staff.player.coords, observer.client.coords[1])
        }
    }

    @Test fun `hidden public chat overhead text and sequence remain owner only`() = Fixture().use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2), hidden = true)
        val observer = f.login(2, CoordGrid(9001, 10000, 2))
        repeat(3) { f.tick() }
        val quietObserver = observer.packet.copyOf()
        val quietOwner = staff.packet.copyOf()
        with(staff.infos.playerInfo.avatar.extendedInfo) {
            setChat(0, 0, 2, false, "Private staff test", null)
            setSay("Private overhead test")
            setSequence(832, 0)
        }
        f.tick()
        assertArrayEquals(quietObserver, observer.packet)
        assertFalse(quietOwner.contentEquals(staff.packet), "Owner must still receive their own extended info")
        assertFalse(observer.client.active[1])
        assertEquals(0, observer.client.regions[1])
    }

    @Test fun `pooled avatar and session do not retain invisibility after logout`() = Fixture().use { f ->
        val staff = f.login(1, CoordGrid(9000, 10000, 2), hidden = true)
        val observer = f.login(2, CoordGrid(9001, 10000, 2))
        f.tick()
        f.logout(staff)
        f.tick()
        val replacement = f.login(1, CoordGrid(9000, 10000, 2))
        f.tick()
        assertFalse(replacement.player.staffInvisible)
        assertFalse(replacement.infos.playerInfo.avatar.hidden)
        assertTrue(observer.client.active[1])
        assertEquals(replacement.player.coords, observer.client.coords[1])
    }

    private class Fixture(private val privacy: Boolean = true) : AutoCloseable {
        private val visibility = StaffVisibilityProtocol()
        private val clients = linkedMapOf<Int, Peer>()
        private val protocols: InfoProtocols

        init {
            val bits = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
            val huffman = try { DefaultHuffmanCodecProvider(HuffmanCodec.create(bits)) } finally { bits.release() }
            // The production network factory calls this same initializer. Its Kotlin-internal
            // companion is accessed only by this fixture to avoid creating a network listener.
            val companion = EntityInfoProtocols::class.java.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            val initialize = companion.javaClass.declaredMethods.single { it.name.startsWith("initialize") && it.parameterCount == 8 }
            initialize.isAccessible = true
            val entities = initialize.invoke(
                companion,
                UnpooledByteBufAllocator.DEFAULT,
                listOf(OldSchoolClientType.DESKTOP),
                huffman,
                PlayerInfoSupplier(playerInfoProtocolWorker = if (privacy) visibility else DefaultProtocolWorker()),
                NpcInfoSupplier(),
                WorldEntityInfoSupplier(),
                null, null,
            ) as EntityInfoProtocols
            protocols = InfoProtocols(entities.playerInfoProtocol, entities.npcInfoProtocol, entities.worldEntityInfoProtocol)
        }

        fun login(index: Int, coords: CoordGrid, hidden: Boolean = false): Peer {
            val player = Player().apply { slotId = index; this.coords = coords; staffInvisible = hidden }
            val infos = protocols.alloc(index, OldSchoolClientType.DESKTOP)
            if (privacy) visibility.register(player, infos.playerInfo) else infos.playerInfo.avatar.hidden = hidden
            infos.updateRootCoord(coords.level, coords.x, coords.z)
            infos.updateRootBuildAreaCenteredOnPlayer(coords.x, coords.z)
            infos.playerInfo.avatar.extendedInfo.setName(if (index == 1) "HiddenStaffMarker" else "Observer$index")
            val rebuild = RebuildLoginV2(coords.x shr 3, coords.z shr 3, -1, infos.playerInfo)
            val decoder = try { ClientMovement(index, rebuild.gpiInitBlock) } finally { rebuild.release() }
            return Peer(player, infos, decoder).also { clients[index] = it }
        }

        fun hide(peer: Peer, hidden: Boolean = true) {
            peer.player.staffInvisible = hidden
            if (privacy) visibility.maskHiddenPlayers() else peer.infos.playerInfo.avatar.hidden = hidden
        }

        fun move(peer: Peer, coords: CoordGrid) {
            peer.player.coords = coords
            peer.infos.updateRootCoord(coords.level, coords.x, coords.z)
            peer.infos.updateRootBuildAreaCenteredOnPlayer(coords.x, coords.z)
        }

        fun tick() {
            protocols.playerInfoProtocol.update()
            for (peer in clients.values) {
                val packet = peer.infos.playerInfo.internalPacketResult().getOrThrow()
                peer.packet = ByteArray(packet.content().readableBytes()).also { packet.content().getBytes(0, it) }
                peer.client.update(peer.packet)
                packet.consume()
            }
        }

        fun logout(peer: Peer) {
            if (privacy) visibility.unregister(peer.player)
            protocols.dealloc(peer.infos)
            clients.remove(peer.player.slotId)
        }

        override fun close() { clients.values.toList().forEach(::logout) }
    }

    private class Peer(val player: Player, val infos: Infos, val client: ClientMovement) {
        var packet = byteArrayOf()
    }

    private class ClientMovement(index: Int, init: ByteBuf) {
        val active = BooleanArray(2048)
        val regions = IntArray(2048)
        val coords = arrayOfNulls<CoordGrid>(2048)
        private val flags = IntArray(2048)

        init {
            val bytes = ByteArray(init.readableBytes()).also { init.getBytes(init.readerIndex(), it) }
            val bits = Bits(bytes)
            val own = bits.read(30)
            coords[index] = CoordGrid((own ushr 14) and 0x3fff, own and 0x3fff, own ushr 28)
            active[index] = true
            for (other in 1 until 2048) if (other != index) regions[other] = bits.read(18)
        }

        fun update(bytes: ByteArray) {
            val bits = Bits(bytes)
            val high = (1 until 2048).filter { active[it] }
            val low = (1 until 2048).filter { !active[it] }
            for ((indices, stationary) in listOf(high to 0, high to 1, low to 1, low to 0)) {
                var skip = 0
                for (index in indices) {
                    if ((flags[index] and 1) != stationary) continue
                    if (skip > 0) { skip--; flags[index] = flags[index] or 2; continue }
                    if (bits.read(1) == 0) {
                        skip = when (bits.read(2)) { 0 -> 0; 1 -> bits.read(5); 2 -> bits.read(8); else -> bits.read(11) }
                        flags[index] = flags[index] or 2
                    } else if (indices === high) {
                        high(bits, index)
                    } else if (low(bits, index)) flags[index] = flags[index] or 2
                }
                assertEquals(0, skip, "Skip count must end at the protocol pass boundary")
                bits.align()
            }
            for (index in 1 until 2048) flags[index] = flags[index] ushr 1
        }

        private fun high(bits: Bits, index: Int) {
            val extended = bits.read(1) != 0
            val old = checkNotNull(coords[index])
            when (bits.read(2)) {
                0 -> if (!extended) {
                    active[index] = false
                    regions[index] = (old.level shl 16) or ((old.x ushr 13) shl 8) or (old.z ushr 13)
                    coords[index] = null
                    if (bits.read(1) != 0) low(bits, index)
                }
                1 -> { val delta = WALK[bits.read(3)]; coords[index] = old.translate(delta.first, delta.second) }
                2 -> { val delta = RUN[bits.read(4)]; coords[index] = old.translate(delta.first, delta.second) }
                3 -> {
                    val far = bits.read(1) != 0
                    val packed = bits.read(if (far) 30 else 12)
                    val plane = (old.level + (packed ushr if (far) 28 else 10)) and 3
                    var x = (packed ushr if (far) 14 else 5) and if (far) 0x3fff else 31
                    var z = packed and if (far) 0x3fff else 31
                    if (!far) { if (x > 15) x -= 32; if (z > 15) z -= 32 }
                    coords[index] = CoordGrid((old.x + x) and 0x3fff, (old.z + z) and 0x3fff, plane)
                }
            }
        }

        private fun low(bits: Bits, index: Int): Boolean {
            when (bits.read(2)) {
                0 -> {
                    if (bits.read(1) != 0) low(bits, index)
                    val x = bits.read(13)
                    val z = bits.read(13)
                    bits.read(1)
                    val region = regions[index]
                    coords[index] = CoordGrid((((region ushr 8) and 255) shl 13) + x, ((region and 255) shl 13) + z, region ushr 16)
                    active[index] = true
                    return true
                }
                1 -> {
                    val plane = ((regions[index] ushr 16) + bits.read(2)) and 3
                    regions[index] = (regions[index] and 0xffff) or (plane shl 16)
                }
                2 -> {
                    val packed = bits.read(5)
                    val delta = WALK[packed and 7]
                    shiftRegion(index, packed ushr 3, delta.first, delta.second)
                }
                3 -> { val packed = bits.read(18); shiftRegion(index, packed ushr 16, (packed ushr 8) and 255, packed and 255) }
            }
            return false
        }

        private fun shiftRegion(index: Int, level: Int, x: Int, z: Int) {
            val old = regions[index]
            regions[index] = ((((old ushr 16) + level) and 3) shl 16) or
                (((((old ushr 8) and 255) + x) and 255) shl 8) or (((old and 255) + z) and 255)
        }

        private class Bits(private val bytes: ByteArray) {
            private var position = 0
            fun read(count: Int): Int {
                var result = 0
                repeat(count) {
                    check(position < bytes.size * 8) { "Movement packet ended early" }
                    result = (result shl 1) or ((bytes[position ushr 3].toInt() ushr (7 - (position and 7))) and 1)
                    position++
                }
                return result
            }
            fun align() { position = (position + 7) and -8 }
        }

        companion object {
            val WALK = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
            val RUN = listOf(-2 to -2, -1 to -2, 0 to -2, 1 to -2, 2 to -2, -2 to -1, 2 to -1, -2 to 0, 2 to 0, -2 to 1, 2 to 1, -2 to 2, -1 to 2, 0 to 2, 1 to 2, 2 to 2)
        }
    }
}
