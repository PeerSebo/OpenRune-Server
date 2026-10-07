package org.rsmod.server.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.core.spi.FilterReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupConsoleFilterTest {
    @Test
    fun `successful dependency startup details stay out of the console`() {
        val filter = StartupConsoleFilter()
        for ((logger, message) in STARTUP_DETAILS) {
            assertEquals(FilterReply.DENY, filter.decide(event(logger, message)), message)
        }
    }

    @Test
    fun `warnings errors and exceptions always survive matching startup detail text`() {
        val filter = StartupConsoleFilter()
        for ((logger, message) in STARTUP_DETAILS) {
            for (level in listOf(Level.WARN, Level.ERROR)) {
                assertEquals(FilterReply.NEUTRAL, filter.decide(event(logger, message, level)))
            }
            val withFailure = event(logger, message).apply {
                setThrowableProxy(ThrowableProxy(IllegalStateException("Decoder failure")))
            }
            assertEquals(FilterReply.NEUTRAL, filter.decide(withFailure))
        }
    }

    @Test
    fun `ready endpoints migrations and ordinary runtime information remain visible`() {
        val messages = listOf(
            "org.rsmod.server.app.GameServer" to "Server ready in 12s",
            "net.rsprot.protocol.api.NetworkService" to "Bound to ports: 43594",
            "io.ktor.server.Application" to
                "OpenRune Central is online (http=8080, world-link=9091)",
            "dev.or2.central.db.FlywayMigrator" to
                "PostgreSQL: postgres@localhost:1234/postgres — Flyway applied 2 migration(s)",
            "dev.openrune.cache.filestore.definition.DefinitionDecoder" to
                "Reload requested by administrator",
            "org.rsmod.api.account.saver.AccountSavingService" to
                "Account saving service recovered after 1 failure(s).",
            "org.rsmod.content.other.commands.AdminCommands" to
                "Reboot initiated by 'Admin'.",
            "org.rsmod.other.DecoderMonitor" to "5947 EnumDecoder definitions loaded in 55ms",
        )
        val filter = StartupConsoleFilter()
        for ((logger, message) in messages) {
            assertEquals(FilterReply.NEUTRAL, filter.decide(event(logger, message)), message)
        }
    }

    @Test
    fun `console filtering leaves full dependency and debug diagnostics in the file sink`() {
        val context = LoggerContext()
        try {
            val filter = StartupConsoleFilter().apply { start() }
            val console = ListAppender<ILoggingEvent>().apply {
                setContext(context)
                addFilter(filter)
                start()
            }
            val file = ListAppender<ILoggingEvent>().apply {
                setContext(context)
                start()
            }
            context.getLogger(Logger.ROOT_LOGGER_NAME).apply {
                level = Level.INFO
                addAppender(console)
                addAppender(file)
            }
            val (decoderName, detail) = STARTUP_DETAILS.first()
            val decoder = context.getLogger(decoderName)
            decoder.info(detail)
            decoder.warn(detail)
            context.getLogger("org.rsmod.server.app.GameServer").apply {
                level = Level.DEBUG
                debug("Startup phases: scripts=8s")
                info("Server ready in 12s")
            }

            assertEquals(listOf(detail, "Server ready in 12s"), console.list.map { it.message })
            assertEquals(listOf(Level.WARN, Level.INFO), console.list.map { it.level })
            assertEquals(
                listOf(detail, detail, "Startup phases: scripts=8s", "Server ready in 12s"),
                file.list.map { it.message },
            )
        } finally {
            context.stop()
        }
    }

    private fun event(logger: String, text: String, severity: Level = Level.INFO): LoggingEvent =
        LoggingEvent().apply {
            loggerName = logger
            message = text
            level = severity
        }

    private companion object {
        val STARTUP_DETAILS = listOf(
            "dev.openrune.cache.filestore.definition.DefinitionDecoder" to
                "5947 EnumDecoder definitions loaded in 55ms",
            "dev.openrune.definition.constants.ConstantProvider" to
                "Loaded provider 'GameValProvider' with 42 tables in 34 ms",
            "dev.openrune.net.CacheJs5GroupProvider" to "Loaded 117276 JS5 responses",
            "net.rsprot.protocol.api.NetworkService" to "Started in: 40.431800ms",
            "net.rsprot.protocol.api.NetworkService" to "Revision: 240",
            "net.rsprot.protocol.api.NetworkService" to "Supported client types: Desktop",
            "io.ktor.server.Application" to
                "Autoreload is disabled because the development mode is off.",
            "io.ktor.server.Application" to "Application started in 0.542 seconds.",
            "io.ktor.server.Application" to "Responding at http://127.0.0.1:8080",
            "dev.or2.central.db.FlywayMigrator" to
                "PostgreSQL: postgres@localhost:1234/postgres — Flyway applied 0 migration(s)",
            "dev.or2.central.discord.DiscordBotService" to
                "Discord bot disabled (no bot token configured)",
            "dev.or2.central.notify.PgNotifyService" to
                "Listening for PostgreSQL NOTIFY on 8 channel(s)",
        )
    }
}
