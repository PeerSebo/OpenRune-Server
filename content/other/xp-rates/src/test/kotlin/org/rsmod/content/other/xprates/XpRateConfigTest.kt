package org.rsmod.content.other.xprates

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class XpRateConfigTest {
    @Test
    fun `installing optional rates preserves existing server settings`() {
        assertFalse(XpRateConfig().enabled)
    }

    @Test
    fun `defaults to authentic osrs rates`() {
        val config = XpRateConfig()

        assertEquals(XpRateConfig.AUTHENTIC_RATE, config.rate)
        assertEquals(XpRateConfig.AUTHENTIC_RATE, config.globalRate)
        assertTrue(config.enforceOnLogin)
    }

    @Test
    fun `level reset is off by default`() {
        assertFalse(XpRateConfig().resetExistingLevels)
        assertFalse(XpRateConfigLoader().load(Path.of("does-not-exist.yml")).resetExistingLevels)
    }

    @Test
    fun `level reset can be turned on`(@TempDir dir: Path) {
        val file = dir.resolve("xp-rates.yml")
        file.writeText("reset-existing-levels: true\n")

        assertTrue(XpRateConfigLoader().load(file).resetExistingLevels)
    }

    @Test
    fun `sanitize clamps out of range rates`() {
        val tooHigh = XpRateConfig(rate = 1_000_000.0, globalRate = 0.0).sanitized()

        assertEquals(XpRateConfig.MAX_RATE, tooHigh.rate)
        assertEquals(XpRateConfig.MIN_RATE, tooHigh.globalRate)
    }

    @Test
    fun `sanitize replaces non-finite rates`() {
        val broken =
            XpRateConfig(rate = Double.POSITIVE_INFINITY, globalRate = Double.NaN).sanitized()

        assertEquals(XpRateConfig.AUTHENTIC_RATE, broken.rate)
        assertEquals(XpRateConfig.AUTHENTIC_RATE, broken.globalRate)
    }

    @Test
    fun `loads kebab case keys`(@TempDir dir: Path) {
        val file = dir.resolve("xp-rates.yml")
        file.writeText("rate: 5.0\nglobal-rate: 2.0\nenforce-on-login: false\n")

        val config = XpRateConfigLoader().load(file)

        assertEquals(5.0, config.rate)
        assertEquals(2.0, config.globalRate)
        assertEquals(false, config.enforceOnLogin)
    }

    @Test
    fun `load clamps rates read from disk`(@TempDir dir: Path) {
        val file = dir.resolve("xp-rates.yml")
        file.writeText("rate: -3.0\n")

        assertEquals(XpRateConfig.MIN_RATE, XpRateConfigLoader().load(file).rate)
    }

    @Test
    fun `malformed file falls back to authentic rates`(@TempDir dir: Path) {
        val file = dir.resolve("xp-rates.yml")
        file.writeText("rate: [not, a, number\n")

        assertEquals(XpRateConfig(), XpRateConfigLoader().load(file))
    }

    @Test
    fun `created default file parses back to the defaults`(@TempDir dir: Path) {
        val file = dir.resolve("xp-rates.yml")
        val loader = XpRateConfigLoader()

        assertEquals(XpRateConfig(), loader.loadOrCreate(file))
        assertTrue(file.exists())
        assertEquals(XpRateConfig(), loader.load(file))
    }
}
