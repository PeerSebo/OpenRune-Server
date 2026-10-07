package dev.openrune.pack

import dev.openrune.cache.tools.tasks.impl.defs.PackConfig
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("pack-config-registry")
class PluginServerConfigTest {
    @TempDir lateinit var root: Path

    @Test
    fun `server overrides never enter native config tasks`() {
        val resources = root.resolve("pack").toFile()
        val native = File(resources, "configs").apply { mkdirs() }
        val server = File(resources, "server").apply { mkdirs() }
        val packs = packs(resources)

        assertEquals(listOf(native), packs.configDirectories())
        assertEquals(listOf(native, server), packs.serverConfigDirectories())
        val nativeTasks = packs.buildPackTasks(emptyList()).filterIsInstance<PackConfig>()
        val inputs = nativeTasks.map(::directory)
        assertEquals(listOf(File("../.data/raw-cache/"), native), inputs)
        assertFalse(server in inputs)
    }

    @Test
    fun `server only content does not create an empty native pack task`() {
        val resources = root.resolve("pack").toFile()
        val server = File(resources, "server").apply { mkdirs() }
        val packs = packs(resources)

        assertEquals(emptyList<File>(), packs.configDirectories())
        assertEquals(listOf(server), packs.serverConfigDirectories())
        val nativeTasks = packs.buildPackTasks(emptyList()).filterIsInstance<PackConfig>()
        assertEquals(listOf(File("../.data/raw-cache/")), nativeTasks.map(::directory))
    }

    @Test
    fun `absent server resource directory is not submitted to server config packing`() {
        val resources = root.resolve("pack").toFile()
        val native = File(resources, "configs").apply { mkdirs() }
        val packs = packs(resources)

        assertEquals(listOf(native), packs.serverConfigDirectories())
    }

    private fun packs(resources: File): PluginPacks =
        PluginPacks(
            root.toFile(),
            listOf(object : PluginPack() {
                override fun resourceRoot(): File = resources
            }),
        )

    private fun directory(task: PackConfig): File =
        PackConfig::class.java.getDeclaredField("directory").let {
            it.isAccessible = true
            it.get(task) as File
        }
}
