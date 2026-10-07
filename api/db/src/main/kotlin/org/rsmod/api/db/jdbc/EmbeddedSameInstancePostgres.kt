package org.rsmod.api.db.jdbc

import java.nio.file.Path
import org.rsmod.api.server.config.ServerConfig

public object EmbeddedSameInstancePostgres {
    private val owner = EmbeddedPostgresOwner()

    public fun ensureStarted(config: ServerConfig) {
        val central = config.central ?: return
        if (!central.sameInstance) return
        val postgres = central.postgres ?: return
        if (postgres.jdbcUrl.isNotBlank()) return
        owner.start(Path.of(postgres.embeddedPgdataDir.trim().ifEmpty { ".data/postgres" }))
    }

    public fun jdbcTripleIfEmbedded(): Triple<String, String, String>? = owner.jdbcTriple()

    public fun stop(timeoutMs: Long = 5_000L) {
        owner.stop(timeoutMs)
    }

    public fun forceStopNow() {
        owner.forceStopNow()
    }
}
