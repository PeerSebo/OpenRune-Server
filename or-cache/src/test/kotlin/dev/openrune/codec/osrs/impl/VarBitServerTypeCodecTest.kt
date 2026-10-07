package dev.openrune.codec.osrs.impl

import dev.openrune.codec.osrs.VarBitServerDecoder
import dev.openrune.definition.codec.VarBitCodec
import dev.openrune.definition.type.VarBitType
import dev.openrune.filesystem.Cache
import dev.openrune.types.varp.VarpServerType
import dev.openrune.types.varp.VarpTransmitLevel
import io.netty.buffer.Unpooled
import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VarBitServerTypeCodecTest {
    private val codec = VarBitServerTypeCodec()

    @Test
    fun `server parents retain full integers across unsigned-short boundaries`() {
        for (parent in listOf(0, 82, 84, 65535, 65536, 65618, 65619, 65620, Int.MAX_VALUE)) {
            for ((start, end) in listOf(0 to 0, 0 to 31, 2 to 5, 6 to 9, 31 to 31)) {
                val source = VarBitType(id = 40, varp = parent, startBit = start, endBit = end)
                assertEquals(source, roundtrip(source), "parent $parent bits $start..$end")
            }
        }
    }

    @Test
    fun `existing Drew endpoint survives without reinterpreting its bit range`() {
        val source = VarBitType(id = 65453, varp = 65617, startBit = 16, endBit = 32)
        assertEquals(source, roundtrip(source))
    }

    @Test
    fun `native definitions are inherited unchanged and TOML overlays take precedence`() {
        val native = VarBitType(id = 1, varp = 84, startBit = 2, endBit = 5)
        val custom = native.copy(varp = 65620)
        val newOnly = VarBitType(id = 2, varp = 65618, startBit = 0, endBit = 31)
        val packing = VarBitServerTypeCodec(mapOf(1 to native), mapOf(1 to custom, 2 to newOnly))
        assertEquals(custom, codec.loadData(1, packing.encodeToBuffer(VarBitType(id = 1))))
        assertEquals(newOnly, codec.loadData(2, packing.encodeToBuffer(VarBitType(id = 2))))
        val inherited = VarBitServerTypeCodec(mapOf(1 to native))
        assertEquals(native, codec.loadData(1, inherited.encodeToBuffer(VarBitType(id = 1))))
        assertEquals(84, native.varp)
    }

    @Test
    fun `native decoding stays compatible while server overlay repairs the parent`() {
        val source = VarBitType(id = 1, varp = 65620, startBit = 2, endBit = 5)
        val nativeBytes = byteArrayOf(1, 0, 84, 2, 5, 0)
        val nativeCodec = VarBitCodec()
        val native = nativeCodec.loadData(source.id, nativeBytes)
        assertEquals(source.copy(varp = 84), native)
        val definitions = mutableMapOf(source.id to native)
        definitions[source.id] = roundtrip(source)
        assertEquals(source, definitions.getValue(source.id))
        assertEquals(84, native.varp)
        assertArrayEquals(byteArrayOf(1, 0, 84, 2, 5, 0), nativeBytes)
    }

    @Test
    fun `negative parents and invalid ranges are rejected on encode and decode`() {
        val invalid = listOf(
            VarBitType(varp = -1),
            VarBitType(startBit = -1),
            VarBitType(startBit = 32, endBit = 32),
            VarBitType(startBit = 5, endBit = 4),
            VarBitType(startBit = 0, endBit = 32),
            VarBitType(startBit = 1, endBit = 33),
        )
        for (source in invalid) {
            assertThrows(IllegalArgumentException::class.java) { codec.encodeToBuffer(source) }
            val buffer = Unpooled.buffer()
            try {
                buffer.writeByte(1).writeInt(source.varp).writeByte(source.startBit).writeByte(source.endBit).writeByte(0)
                val failure = assertThrows(IllegalStateException::class.java) { codec.loadData(source.id, buffer) }
                assertEquals("Unable to decode VarBitType [${source.id}]", failure.message)
                buffer.readerIndex(0)
                val builder = codec.builder(source.id)
                assertThrows(IllegalArgumentException::class.java) {
                    with(codec) { builder.read(buffer.readUnsignedByte().toInt(), buffer) }
                }
            } finally {
                buffer.release()
            }
        }
    }

    @Test
    fun `server decoder preserves native map without archive and overlays only available entries`() {
        val native = VarBitType(id = 1, varp = 84, startBit = 2, endBit = 5)
        val untouched = VarBitType(id = 2, varp = 100, startBit = 0, endBit = 1)
        val definitions = mutableMapOf(1 to native, 2 to untouched)
        val decoder = VarBitServerDecoder()
        decoder.load(cache(emptyMap()), definitions)
        assertSame(native, definitions.getValue(1))
        assertSame(untouched, definitions.getValue(2))
        val corrected = native.copy(varp = 65620)
        decoder.load(cache(mapOf(1 to codec.encodeToBuffer(corrected))), definitions)
        assertEquals(corrected, definitions.getValue(1))
        assertSame(untouched, definitions.getValue(2))
    }

    @Test
    fun `high parents must exist and stay outside client transmission`() {
        val source = VarBitType(varp = 65620)
        assertDoesNotThrow {
            VarBitServerTypeCodec.validateParentTransmission(source, VarpServerType(id = 65620, transmit = VarpTransmitLevel.Never))
        }
        val invalid = listOf(null, VarpServerType(id = 84, transmit = VarpTransmitLevel.Never)) +
            listOf(VarpTransmitLevel.OnSetAlways, VarpTransmitLevel.OnSetDifferent).map {
                VarpServerType(id = 65620, transmit = it)
            }
        for (parent in invalid) assertThrows(IllegalArgumentException::class.java) {
            VarBitServerTypeCodec.validateParentTransmission(source, parent)
        }
        assertDoesNotThrow {
            VarBitServerTypeCodec.validateParentTransmission(source.copy(varp = 65535), VarpServerType(id = 65535))
        }
    }

    private fun roundtrip(source: VarBitType): VarBitType = codec.loadData(source.id, codec.encodeToBuffer(source))

    private fun cache(files: Map<Int, ByteArray>): Cache = Proxy.newProxyInstance(
        Cache::class.java.classLoader,
        arrayOf(Cache::class.java),
    ) { _, method, arguments ->
        assertEquals(2, arguments[0])
        assertEquals(88, arguments[1])
        when (method.name) {
            "files" -> files.keys.toIntArray()
            "data" -> files[arguments[2] as Int]
            else -> error("Unexpected cache call: ${method.name}")
        }
    } as Cache
}
