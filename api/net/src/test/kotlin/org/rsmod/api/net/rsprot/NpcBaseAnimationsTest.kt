package org.rsmod.api.net.rsprot

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
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
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfo
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("rsprot-info-protocols")
class NpcBaseAnimationsTest {
    @Test
    fun `a transformed NPC preserves the complete native animation set for current and later observers`() = Fixture().use { f ->
        val parent = checkNotNull(ServerCacheManager.getNpc("npc.giants_foundry_kovac_multi".asRSCM()))
        f.output.setTransmog("npc.giants_foundry_kovac".asRSCM())
        f.output.setBaseAnimations(parent)
        val expected = listOf(-1, -1, 6372, 6372, 6371, 6371, -1, -1, -1, -1, -1, -1, -1, -1, 4670)
        assertEquals(expected, f.decodedAnimations())
        assertEquals(0x403c, f.overrideMask())
        assertTrue(f.tick() > 0)
        assertEquals(expected, f.decodedAnimations())
        assertTrue(f.includesAnimationsForNewObservers())
        f.addObserver()
        assertTrue(f.tick() > 0)
        assertEquals(2, f.avatar.getObserverCount())
        assertEquals(expected, f.decodedAnimations())
    }

    @Test
    fun `directional animations remain distinct and missing sequences actively clear old overrides`() = Fixture().use { f ->
        val native = checkNotNull(ServerCacheManager.getNpc("npc.giants_foundry_kovac_multi".asRSCM()))
        val distinct = native.copy(
            rotateLeftAnim = 1, rotateRightAnim = 2, walkAnim = 3, rotateBackAnim = 4,
            walkLeftAnim = 5, walkRightAnim = 6, runSequence = 7, runBackSequence = 8,
            runLeftSequence = 9, runRightSequence = 10, crawlSequence = 11,
            crawlBackSequence = 12, crawlLeftSequence = 13, crawlRightSequence = 14, standAnim = 15,
        )
        f.output.setBaseAnimations(distinct)
        assertEquals((1..15).toList(), f.decodedAnimations())
        f.tick()
        f.output.setBaseAnimations(native)
        assertEquals(-1, f.decodedAnimations()[0])
        assertEquals(-1, f.decodedAnimations()[6])
        assertEquals(-1, f.decodedAnimations()[10])
        assertEquals(4670, f.decodedAnimations().last())
        assertEquals(0x403c, f.overrideMask())
        f.tick()
        assertTrue(f.includesAnimationsForNewObservers())
    }

    @Test
    fun `absent animation set clears previous overrides and never encodes the invalid unsigned sentinel`() = Fixture().use { f ->
        val parent = checkNotNull(ServerCacheManager.getNpc("npc.giants_foundry_kovac_multi".asRSCM()))
        val native = checkNotNull(ServerCacheManager.getNpc("npc.giants_foundry_kovac".asRSCM()))
        f.output.setBaseAnimations(parent)
        f.output.setBaseAnimations(native)
        assertEquals(0, f.overrideMask())
        assertEquals(List(15) { -1 }, f.decodedAnimations())
        assertTrue(f.tick() > 0)
        assertFalse(f.includesAnimationsForNewObservers())
        f.output.setBaseAnimations(parent.copy(rotateLeftAnim = 65535))
        assertEquals(-1, f.decodedAnimations()[0])
        assertFalse(65535 in f.decodedAnimations())
    }

    private class Fixture : AutoCloseable {
        private val entities: EntityInfoProtocols
        private val protocols: InfoProtocols
        private val huffman: DefaultHuffmanCodecProvider
        private val observers = mutableListOf<Infos>()
        val avatar: NpcAvatar
        val output: RspNpcInfo

        init {
            val bits = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
            huffman = try { DefaultHuffmanCodecProvider(HuffmanCodec.create(bits)) } finally { bits.release() }
            val companion = EntityInfoProtocols::class.java.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
            val initialize = companion.javaClass.declaredMethods.single { it.name.startsWith("initialize") && it.parameterCount == 8 }
            initialize.isAccessible = true
            entities = initialize.invoke(companion, UnpooledByteBufAllocator.DEFAULT,
                listOf(OldSchoolClientType.DESKTOP), huffman, PlayerInfoSupplier(), NpcInfoSupplier(), WorldEntityInfoSupplier(), null, null) as EntityInfoProtocols
            protocols = InfoProtocols(entities.playerInfoProtocol, entities.npcInfoProtocol, entities.worldEntityInfoProtocol)
            addObserver()
            avatar = entities.npcAvatarFactory.alloc(1, "npc.giants_foundry_kovac_multi".asRSCM(), 0, 3369, 11486)
            output = RspNpcInfo(avatar)
        }

        fun addObserver() {
            val info = protocols.alloc(observers.size + 1, OldSchoolClientType.DESKTOP)
            info.updateRootCoord(0, 3369, 11485)
            info.updateRootBuildAreaCenteredOnPlayer(3369, 11485)
            observers += info
        }

        fun tick(): Int {
            protocols.npcInfoProtocol.update()
            return observers.sumOf { info ->
                val packet = info.npcInfo.internalPacketResult(NpcInfo.ROOT_WORLD).getOrThrow()
                try { packet.content().readableBytes() } finally { packet.release() }
            }
        }

        private fun baseAnimations(): Any {
            val extended = avatar.extendedInfo
            val blocks = extended.javaClass.getDeclaredField("blocks").apply { isAccessible = true }.get(extended)
            return blocks.javaClass.getDeclaredField("baseAnimationSet").apply { isAccessible = true }.get(blocks)
        }

        fun decodedAnimations(): List<Int> {
            val base = baseAnimations()
            val encoderClass = Class.forName("net.rsprot.protocol.game.outgoing.codec.npcinfo.extendedinfo.NpcBaseAnimationSetEncoder")
            val encoder = encoderClass.getDeclaredConstructor().newInstance()
            val precompute = encoderClass.declaredMethods.single {
                it.name.startsWith("precompute") && it.parameterTypes.last() == base.javaClass
            }
            val buffer = precompute.invoke(encoder, UnpooledByteBufAllocator.DEFAULT, huffman, base) as ByteBuf
            try {
                val b = IntArray(4) { buffer.readUnsignedByte().toInt() }
                val mask = b[0] or (b[1] shl 8) or (b[2] shl 16) or (b[3] shl 24)
                val orders = intArrayOf(1, 0, 2, 2, 1, 1, 3, 1, 3, 0, 2, 0, 3, 0, 0)
                val decoded = orders.mapIndexed { index, order ->
                    if (mask and (1 shl index) == 0) -1 else {
                        val first = buffer.readUnsignedByte().toInt()
                        val second = buffer.readUnsignedByte().toInt()
                        when (order) {
                            0 -> (first shl 8) or second
                            1 -> first or (second shl 8)
                            2 -> (first shl 8) or ((second - 128) and 255)
                            else -> ((first - 128) and 255) or (second shl 8)
                        }
                    }
                }
                assertFalse(buffer.isReadable)
                return decoded
            } finally {
                buffer.release()
            }
        }

        fun overrideMask(): Int {
            val base = baseAnimations()
            return base.javaClass.getDeclaredField("overrides").apply { isAccessible = true }.getInt(base)
        }

        fun includesAnimationsForNewObservers(): Boolean {
            val extended = avatar.extendedInfo
            val flags = extended.javaClass.declaredMethods.single { it.name.startsWith("getLowToHighResChangeExtendedInfoFlags") }.apply { isAccessible = true }.invoke(extended) as Int
            val baseFlag = extended.javaClass.getDeclaredField("BAS_CHANGE").getInt(null)
            return flags and baseFlag != 0
        }

        override fun close() {
            observers.forEach(protocols::dealloc)
            entities.npcAvatarFactory.release(avatar)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
