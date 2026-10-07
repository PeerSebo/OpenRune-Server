package dev.openrune.gamevals

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GameValDiscoveryTest {
    @TempDir lateinit var root: Path

    @Test
    fun `source symbols load while generated copies never override them`() {
        setup()
        write("content/skill/src/main/resources/gamevals.toml", "skill_state", 62000)
        write("api/example/src/main/resources/gamevals.toml", "api_state", 62001)
        for (name in listOf("build", "out", "target", ".gradle", ".git", "node_modules")) {
            write("content/skill/$name/copy/gamevals.toml", "skill_state", 63000)
            write("api/example/$name/copy/gamevals.toml", "api_state", 63001)
        }
        val provider = load()
        assertEquals(mapOf("varp.native" to 1, "varp.varplayer_0" to 0, "varp.skill_state" to 62000,
            "varp.api_state" to 62001), provider.mappings["varp"])
        assertEquals(1, provider.maxBaseId("varp"))
        assertEquals(root.resolve("content/skill/src/main/resources/gamevals.toml").toFile(),
            provider.sourceOf("varp", "skill_state"))
    }

    @Test
    fun `similarly named source directories remain discoverable`() {
        setup()
        write("content/building/src/main/resources/gamevals.toml", "building_state", 62000)
        write("content/outpost/src/main/resources/gamevals.toml", "outpost_state", 62001)
        assertEquals(62000, load().mappings["varp"]?.get("varp.building_state"))
        assertEquals(62001, load().mappings["varp"]?.get("varp.outpost_state"))
    }

    @Test
    fun `workspace parent names do not exclude real content roots`() {
        root = root.resolve("build/out/project")
        setup()
        write("content/skill/src/main/resources/gamevals.toml", "skill_state", 62000)
        write("content/skill/build/gamevals.toml", "skill_state", 63000)
        write("api/example/src/main/resources/gamevals.toml", "api_state", 62001)
        assertEquals(62000, load().mappings["varp"]?.get("varp.skill_state"))
        assertEquals(62001, load().mappings["varp"]?.get("varp.api_state"))
    }

    @Test
    fun `conflicting real sources still fail rather than silently losing mappings`() {
        setup()
        write("content/first/gamevals.toml", "collision", 62000)
        write("content/second/gamevals.toml", "collision", 62001)
        assertThrows(IllegalArgumentException::class.java) { load() }
    }

    @Test
    fun `generated directories can disappear during repeated provider loads`() {
        setup()
        write("content/skill/src/main/resources/gamevals.toml", "skill_state", 62000)
        val generated = root.resolve("content/skill/build/volatile")
        Files.createDirectories(generated.parent)
        val running = AtomicBoolean(true)
        val workerFailure = AtomicReference<Throwable>()
        val started = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            try {
            while (running.get()) {
                Files.createDirectories(generated)
                val file = generated.resolve("gamevals.toml")
                Files.writeString(file, "[gamevals.varp]\nskill_state = 63000\n")
                started.countDown()
                Files.delete(file)
                Files.delete(generated)
            }
            } catch (failure: Throwable) {
                workerFailure.set(failure)
                started.countDown()
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(100) { assertEquals(62000, load().mappings["varp"]?.get("varp.skill_state")) }
        } finally {
            running.set(false)
            worker.join(5000)
        }
        assertFalse(worker.isAlive)
        assertNull(workerFailure.get())
    }

    @Test
    fun `fallback varp names fill only native unnamed IDs`() {
        setup()
        write("content/skill/src/main/resources/gamevals.toml", "skill_state", 62000)
        val provider = load()
        val symbols = checkNotNull(provider.mappings["varp"])
        assertEquals(0, symbols["varp.varplayer_0"])
        assertEquals(1, symbols["varp.native"])
        assertFalse(symbols.containsKey("varp.varplayer_1"))
        assertFalse(symbols.containsKey("varp.varplayer_2"))
        assertFalse(symbols.containsKey("varp.varplayer_62000"))
        assertEquals(62000, symbols["varp.skill_state"])
    }

    private fun setup() {
        GameValDat.write(root.resolve(".data/gamevals-binary/gamevals.dat").toFile(),
            mapOf("varp" to listOf("native=1")))
    }

    private fun write(path: String, name: String, value: Int) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, "[gamevals.varp]\n$name = $value\n")
    }

    private fun load(): GameValProvider = GameValProvider.loadIsolated(root.toString() + File.separator)
}
