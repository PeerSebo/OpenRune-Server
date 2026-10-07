package dev.openrune.pack

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CacheDelegate
import dev.openrune.cache.tools.tasks.impl.defs.PackConfig
import dev.openrune.definition.DefinitionCodec
import dev.openrune.definition.codec.ObjectCodec
import dev.openrune.definition.type.ObjectType
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("pack-config-registry")
class InheritedObjectCodecTest {
    @TempDir lateinit var root: Path

    private val native = ObjectCodec(241)
    private val inherited = ObjectCodec(241)

    @Test
    fun `native contrast and other fields survive repeated inherited encoding`() {
        for (contrast in listOf(-128, -1, 0, 1, 2, 4, 127)) {
            val original = encode(native, ObjectType(
                contrast = contrast,
                sizeX = 3,
                sizeY = 2,
                ambient = -4,
                animationId = 123,
                objectModels = mutableListOf(456),
                originalColours = mutableListOf(12),
                modifiedColours = mutableListOf(34),
            ))
            var encoded = original
            repeat(5) {
                val decoded = inherited.loadData(1, encoded)
                assertEquals(contrast, decoded.contrast)
                encoded = encode(inherited, decoded)
                assertEquals(contrast, decoded.contrast)
                assertEquals(contrast, native.loadData(1, encoded).contrast)
                assertArrayEquals(original, encoded)
            }
        }
    }

    @Test
    fun `explicit contrast uses native units without mutating the caller`() {
        for (contrast in listOf(-25, 0, 25, 100)) {
            val definition = ObjectType(contrast = contrast)
            assertArrayEquals(encode(native, definition), encode(inherited, definition))
            assertEquals(contrast, native.loadData(1, encode(inherited, definition)).contrast)
            assertEquals(contrast, definition.contrast)
        }
    }

    @Test
    fun `packing inherits parent contrast for both new and existing targets`() {
        val configs = Files.createDirectory(root.resolve("configs"))
        Files.writeString(configs.resolve("objects.toml"), """
            [[object]]
            id = 2
            inherit = 1
            sizeX = 4

            [[object]]
            id = 3
            inherit = 1

            [[object]]
            id = 4
            inherit = 1
            contrast = 50
        """.trimIndent())
        val task = PackConfig(configs.toFile()).apply { revision = 241 }
        val directory = Files.createDirectory(root.resolve("cache"))
        Files.createFile(directory.resolve("main_file_cache.dat2"))
        Files.createFile(directory.resolve("main_file_cache.idx255"))
        val library = CacheLibrary.create(directory.toString())
        try {
            library.createIndex(id = 2)
            val cache = CacheDelegate(library)
            cache.write(2, 6, 1, encode(native, ObjectType(contrast = 25, sizeY = 3)))
            cache.write(2, 6, 2, encode(native, ObjectType(contrast = -100)))
            repeat(3) {
                task.init(cache)
                assertEquals(25, native.loadData(2, cache.data(2, 6, 2)).contrast)
                assertEquals(4, native.loadData(2, cache.data(2, 6, 2)).sizeX)
                assertEquals(3, native.loadData(2, cache.data(2, 6, 2)).sizeY)
                assertEquals(25, native.loadData(3, cache.data(2, 6, 3)).contrast)
                assertEquals(50, native.loadData(4, cache.data(2, 6, 4)).contrast)
                assertNull(cache.data(2, 6, 5))
            }
        } finally {
            library.close()
        }
    }

    @Test
    fun `plugin tasks retain native contrast units`() {
        val resources = Files.createDirectories(root.resolve("pack/configs")).parent.toFile()
        PluginPacks(root.toFile(), listOf(object : PluginPack() {
            override fun resourceRoot() = resources
        })).buildPackTasks(emptyList())
        assertEquals(ObjectCodec::class, PackConfig.packTypes.getValue("object").codecClass)
    }

    private fun encode(codec: DefinitionCodec<ObjectType>, definition: ObjectType): ByteArray {
        val buffer = Unpooled.buffer()
        return try {
            with(codec) { buffer.encode(definition) }
            ByteArray(buffer.readableBytes()).also { buffer.readBytes(it) }
        } finally {
            buffer.release()
        }
    }
}
