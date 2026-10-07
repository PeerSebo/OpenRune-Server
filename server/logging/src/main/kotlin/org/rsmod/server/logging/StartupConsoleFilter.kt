package org.rsmod.server.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply

class StartupConsoleFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply {
        if (!event.level.isGreaterOrEqual(Level.INFO)) return FilterReply.DENY
        if (event.level != Level.INFO || event.throwableProxy != null) return FilterReply.NEUTRAL
        val message = event.formattedMessage
        val detail = when (event.loggerName) {
            "dev.openrune.cache.filestore.definition.DefinitionDecoder" ->
                DECODER_COUNT.matches(message)
            "dev.openrune.definition.constants.ConstantProvider" ->
                PROVIDER_COUNT.matches(message)
            "dev.openrune.net.CacheJs5GroupProvider" -> JS5_COUNT.matches(message)
            "net.rsprot.protocol.api.NetworkService" ->
                message.startsWith("Started in: ") ||
                    message.startsWith("Revision: ") ||
                    message.startsWith("Supported client types: ")
            "io.ktor.server.Application" ->
                message == "Autoreload is disabled because the development mode is off." ||
                    APPLICATION_TIMING.matches(message) ||
                    message.startsWith("Responding at http://") ||
                    message.startsWith("Responding at https://")
            "dev.or2.central.db.FlywayMigrator" ->
                message.startsWith("PostgreSQL: ") &&
                    message.endsWith("Flyway applied 0 migration(s)")
            "dev.or2.central.discord.DiscordBotService" ->
                message == "Discord bot disabled (no bot token configured)"
            "dev.or2.central.notify.PgNotifyService" -> NOTIFY_COUNT.matches(message)
            else -> false
        }
        return if (detail) FilterReply.DENY else FilterReply.NEUTRAL
    }

    private companion object {
        val DECODER_COUNT = Regex("\\d+ \\w+Decoder definitions loaded in \\d+ms")
        val PROVIDER_COUNT = Regex("Loaded provider '.+' with \\d+ tables in \\d+ (?:ms|ns)")
        val JS5_COUNT = Regex("Loaded \\d+ JS5 responses")
        val APPLICATION_TIMING = Regex("Application started in [\\d.]+ seconds\\.")
        val NOTIFY_COUNT = Regex("Listening for PostgreSQL NOTIFY on \\d+ channel\\(s\\)")
    }
}
