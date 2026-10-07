package dev.openrune.pack

import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PluginPacksCs2OverridesTest {
    @TempDir lateinit var root: Path

    @Test fun `authored scripts remain in their pack and retain their declarations`() {
        val resources = resources("first")
        val script = write(resources, "cs2/script/caller.cs2", "[proc,caller]()(int)\nreturn(~base(1));\n")
        val loose = write(resources, "cs2/loose.cs2", "[proc,loose](long $" + "value)(long)\nreturn($" + "value);\n")
        val overrides = packs(resources).cs2Overrides(root.resolve("compiler").toFile())
        assertEquals(setOf(script.parentFile, loose), overrides.sources.toSet())
        assertTrue(script.readText().contains("return(~base(1))"))
        assertTrue(loose.readText().contains("(long)"))
    }

    @Test fun `repeated staging removes stale copied scripts without touching native libraries`() {
        val resources = resources("first")
        write(resources, "cs2/script/caller.cs2", "[proc,caller]\n")
        val compiler = root.resolve("compiler").toFile()
        val stale = write(compiler, "custom/obsolete.cs2", "[proc,obsolete]\n")
        val library = write(compiler, "script/base.cs2", "[proc,base](int $" + "value)(int)\n")
        val first = packs(resources).cs2Overrides(compiler)
        val second = packs(resources).cs2Overrides(compiler)
        assertEquals(first, second)
        assertFalse(stale.exists())
        assertEquals("[proc,base](int $" + "value)(int)\n", library.readText())
    }

    @Test fun `later pack symbols replace only the same name`() {
        val first = resources("first")
        val second = resources("second")
        write(first, "cs2/symbols/interface.sym", "1102\tcommand_browser\n42\tauthored\n")
        write(second, "cs2/symbols/interface.sym", "1098\tcommand_browser\n")
        val symbols = packs(first, second).cs2Overrides(root.resolve("compiler").toFile()).symbols
        assertEquals(listOf("1098\tcommand_browser", "42\tauthored"), symbols["interface"])
    }

    @Test fun `component ownership replaces stale ids and preserves type suffixes`() {
        val first = resources("first")
        val second = resources("second")
        write(first, "cs2/symbols/component.sym", "1102:0\tcommand_browser:universe\n42:0\tauthored:child\n")
        write(second, "cs2/symbols/component.sym", "1098:0\tcommand_browser:universe\n")
        write(second, "cs2/symbols/varp.sym", "64000\tcustom_price\tint\n")
        val symbols = packs(first, second).cs2Overrides(root.resolve("compiler").toFile()).symbols
        assertEquals(listOf("1098:0\tcommand_browser:universe", "42:0\tauthored:child"), symbols["component"])
        assertEquals(listOf("64000\tcustom_price\tint"), symbols["varp"])
    }

    @Test fun `disabled packs contribute neither scripts nor symbols`() {
        val resources = resources("disabled")
        write(resources, "cs2/script/disabled.cs2", "[proc,disabled]\n")
        write(resources, "cs2/symbols/interface.sym", "1098\tdisabled\n")
        val pack = object : PluginPack() {
            override fun resourceRoot() = resources
            override fun isEnabled(projectRoot: File) = false
        }
        val overrides = PluginPacks(root.toFile(), listOf(pack)).cs2Overrides(root.resolve("compiler").toFile())
        assertTrue(overrides.sources.isEmpty())
        assertTrue(overrides.symbols.isEmpty())
    }

    @Test fun `removing an override removes its ownership on the next build`() {
        val resources = resources("first")
        val symbol = write(resources, "cs2/symbols/interface.sym", "1098\tcommand_browser\n")
        val compiler = root.resolve("compiler").toFile()
        assertEquals(listOf("1098\tcommand_browser"), packs(resources).cs2Overrides(compiler).symbols["interface"])
        assertTrue(symbol.delete())
        assertTrue(packs(resources).cs2Overrides(compiler).symbols.isEmpty())
    }

    @Test fun `empty packs preserve unrelated compiler symbols`() {
        val compiler = root.resolve("compiler").toFile()
        val native = write(compiler, "symbols/interface.sym", "42\tnative\n")
        val overrides = packs(resources("empty")).cs2Overrides(compiler)
        assertEquals("42\tnative\n", native.readText())
        assertTrue(overrides.sources.isEmpty())
        assertTrue(overrides.symbols.isEmpty())
    }

    private fun packs(vararg resources: File) = PluginPacks(root.toFile(), resources.map { resource ->
        object : PluginPack() { override fun resourceRoot() = resource }
    })
    private fun resources(name: String) = root.resolve(name).toFile().apply { mkdirs() }
    private fun write(directory: File, name: String, text: String) = File(directory, name).apply {
        parentFile.mkdirs()
        writeText(text)
    }
}
