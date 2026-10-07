package org.rsmod.content.other.xprates

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.realm.config.updater.RealmConfigUpdater
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.content.other.login.LoginXpRatesScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class XpRatesDispatchTest {
    @Test
    fun `disabled one times override preserves configured ten times rate and saved xp`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\nrate: 1.0\nglobal-rate: 1.0\n")

        fixture.login()

        assertEquals(10.0, fixture.player.xpRate)
        assertEquals(1.0, fixture.player.globalXpRate)
        fixture.assertSavedProgress()
        assertEquals(250, fixture.player.statAdvance(STAT, 25.0))
        assertEquals(SAVED_XP + 250, fixture.player.statMap.getXP(STAT))
    }

    @Test
    fun `realm event multiplies configured rate exactly once`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n", eventRate = 2.0)

        fixture.login()

        assertEquals(10.0, fixture.player.xpRate)
        assertEquals(2.0, fixture.player.globalXpRate)
        assertEquals(500, fixture.player.statAdvance(STAT, 25.0))
    }

    @Test
    fun `enabled override replaces baseline before engine login finishes`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: true\nrate: 4.0\nglobal-rate: 1.5\n")

        fixture.login()

        assertEquals(4.0, fixture.player.xpRate)
        assertEquals(1.5, fixture.player.globalXpRate)
        fixture.assertSavedProgress()
        assertEquals(150, fixture.player.statAdvance(STAT, 25.0))
    }

    @Test
    fun `saved personal override survives login baseline assignment`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n")
        fixture.login()
        fixture.command("setxprate", "6")
        fixture.player.xpRate = 1.0

        fixture.login()

        assertEquals(6.0, fixture.player.xpRate)
        fixture.assertSavedProgress()
        assertEquals(150, fixture.player.statAdvance(STAT, 25.0))
    }

    @Test
    fun `personal override survives when optional enforcement is disabled`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: true\nrate: 4.0\nglobal-rate: 1.5\nenforce-on-login: false\n")
        fixture.login()
        fixture.command("setxprate", "6")

        fixture.login()

        assertEquals(6.0, fixture.player.xpRate)
        assertEquals(1.5, fixture.player.globalXpRate)
        assertEquals(225, fixture.player.statAdvance(STAT, 25.0))
    }

    @Test
    fun `disabling override on reload restores configured game and realm rates`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: true\nrate: 4.0\nglobal-rate: 1.5\n", eventRate = 2.0)
        fixture.login()
        fixture.file.writeText("enabled: false\n")

        fixture.command("reloadxprates")

        assertEquals(10.0, fixture.player.xpRate)
        assertEquals(2.0, fixture.player.globalXpRate)
        fixture.assertSavedProgress()
        assertEquals(500, fixture.player.statAdvance(STAT, 25.0))
    }

    @Test
    fun `disabled override cannot reset existing levels`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\nreset-existing-levels: true\n")

        fixture.login()

        fixture.assertSavedProgress()
    }

    @Test
    fun `legacy personal rate and zero reset stamp import through real login dispatch`(@TempDir dir: Path) {
        val fixture = Fixture(dir,
            "enabled: true\nenforce-on-login: false\nreset-existing-levels: true\n")
        fixture.player.attr.putAllFromPersistence(mapOf(
            "xp_rates_personal_hundredths" to 625,
            "xp_rates_levels_reset" to 0,
        ))

        fixture.login()

        assertEquals(6.25, fixture.player.xpRate)
        assertEquals(0, XpRateState.levelsResetStamp(fixture.player))
        assertTrue(fixture.player.attr.toPersistentMap().isEmpty())
        fixture.assertSavedProgress()
    }

    @Test
    fun `modern zero personal rate wins over old save during login`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n")
        fixture.player.attr.putAllFromPersistence(mapOf("xp_rates_personal_hundredths" to 625))
        VarPlayerIntMapSetter.set(fixture.player, "varp.xp_rates_personal_hundredths", 0)

        fixture.login()

        assertEquals(0.0, fixture.player.xpRate)
        assertEquals(0, XpRateState.personalRateHundredths(fixture.player))
        assertTrue(fixture.player.attr.toPersistentMap().isEmpty())
        fixture.assertSavedProgress()
    }

    @Test
    fun `migrated absence keeps ten times default despite stale personal attribute`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n")
        VarPlayerIntMapSetter.set(fixture.player, "varp.xp_rates_personal_migrated", 1)
        fixture.player.attr.putAllFromPersistence(mapOf("xp_rates_personal_hundredths" to 625))

        fixture.login()
        fixture.command("reloadxprates")

        assertEquals(10.0, fixture.player.xpRate)
        assertTrue(fixture.player.attr.toPersistentMap().isEmpty())
        fixture.assertSavedProgress()
    }

    @Test
    fun `enforcement overrides active rate without erasing saved personal preference`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: true\nrate: 4.0\n")
        fixture.player.attr.putAllFromPersistence(mapOf("xp_rates_personal_hundredths" to 625))

        fixture.login()

        assertEquals(4.0, fixture.player.xpRate)
        assertEquals(625, XpRateState.personalRateHundredths(fixture.player))
        fixture.file.writeText("enabled: false\n")
        fixture.command("reloadxprates")
        assertEquals(6.25, fixture.player.xpRate)
        fixture.assertSavedProgress()
    }

    @Test
    fun `set rate command before login replaces legacy rate and preserves reset stamp`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n")
        fixture.player.attr.putAllFromPersistence(mapOf(
            "xp_rates_personal_hundredths" to 250,
            "xp_rates_levels_reset" to -7,
        ))

        fixture.command("setxprate", "6.257")

        assertEquals(6.257, fixture.player.xpRate)
        assertEquals(626, XpRateState.personalRateHundredths(fixture.player))
        fixture.login()
        assertEquals(6.26, fixture.player.xpRate)
        assertEquals(-7, XpRateState.levelsResetStamp(fixture.player))
        assertTrue(fixture.player.attr.toPersistentMap().isEmpty())
        fixture.assertSavedProgress()
    }

    @Test
    fun `one time automatic reset stamps before subsequent login and survives restored vars`(@TempDir dir: Path) {
        val config = "enabled: true\nreset-existing-levels: true\n"
        val fixture = Fixture(dir, config)

        fixture.login()

        assertEquals(1, XpRateState.levelsResetStamp(fixture.player))
        assertEquals(0, fixture.player.statMap.getXP(STAT))
        val restored = Fixture(dir, config)
        restored.player.vars.backing.putAll(fixture.player.vars.backing)
        restored.login()
        restored.assertSavedProgress()
    }

    @Test
    fun `command personal rate survives restored vars and real initialization sequence`(@TempDir dir: Path) {
        val fixture = Fixture(dir, "enabled: false\n")
        fixture.login()
        fixture.command("setxprate", "6.25")
        val restored = Fixture(dir, "enabled: false\n")
        restored.player.vars.backing.putAll(fixture.player.vars.backing)
        restored.player.attr.putAllFromPersistence(mapOf("xp_rates_personal_hundredths" to 100))

        restored.login()

        assertEquals(6.25, restored.player.xpRate)
        assertTrue(restored.player.attr.toPersistentMap().isEmpty())
        restored.assertSavedProgress()
    }

    private class Fixture(dir: Path, overrideYaml: String, eventRate: Double = 1.0) {
        val file = dir.resolve("xp-rates.yml").also { it.writeText(overrideYaml) }
        private val events = EventBus()
        private val commands = CheatCommandMap()
        val player = Player().apply {
            client = RecordingClient()
            modLevel = Rights.ADMINISTRATOR
            xpRate = 3.0
            statMap.setXP(STAT, SAVED_XP)
            statMap.setBaseLevel(STAT, 99)
            statMap.setCurrentLevel(STAT, 99)
        }
        private val players = PlayerList().apply { this[1] = player }
        private val realm = Realm("xp-test").apply {
            updateConfig(RealmConfig(1, null, null, 3.0, eventRate, CoordGrid(3222, 3218),
                CoordGrid(3222, 3218), false, false, true))
        }
        private val serverConfig = ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(
            """
            name: XP test
            game-port: 43594
            revision: 240
            environment: LIVE
            world: 1
            gameplay:
              xp-rate: 10.0
            """.trimIndent(),
            ServerConfig::class.java,
        )

        init {
            val context = ScriptContext(events, commands, EngineQueueCache())
            val overrides = XpRatesScript(realm, RealmConfigUpdater(realm, players), players,
                XpLevelReset(InvisibleLevels(emptySet())), unused(), serverConfig, XpRateConfigLoader(file))
            with(overrides) { context.startup() }
            with(LoginXpRatesScript(realm, serverConfig)) { context.startup() }
        }

        fun login() {
            assertTrue(events.publish(SessionStateEvent.Initialize(player)))
            assertEquals(10.0, player.xpRate)
            assertTrue(events.publish(SessionStateEvent.Login(player)))
            events.publish(SessionStateEvent.EngineLogin(player))
        }

        fun command(command: String, vararg args: String) {
            assertTrue(commands.execute(player, command, args.toList()))
        }

        fun assertSavedProgress() {
            assertEquals(SAVED_XP, player.statMap.getXP(STAT))
            assertEquals(99, player.statMap.getBaseLevel(STAT).toInt())
            assertEquals(99, player.statMap.getCurrentLevel(STAT).toInt())
        }
    }

    private class RecordingClient : Client<Any, Any> {
        override fun write(message: Any) {}
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private const val STAT = "stat.woodcutting"
        private const val SAVED_XP = 14_000_000

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

        private fun unused(): ProtectedAccessLauncher {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            return unsafe.allocateInstance(ProtectedAccessLauncher::class.java) as ProtectedAccessLauncher
        }
    }
}
