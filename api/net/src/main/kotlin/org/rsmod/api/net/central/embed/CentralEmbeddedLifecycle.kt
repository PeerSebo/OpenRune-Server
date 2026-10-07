package org.rsmod.api.net.central.embed

import dev.or2.central.auth.PasswordAuthConfig
import dev.or2.central.embed.OpenRuneCentralEmbeddedServer
import dev.or2.central.util.config.centralRuntimeConfigFromJdbc
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.sql.SQLException
import org.rsmod.api.db.jdbc.EmbeddedSameInstancePostgres
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.server.config.SameInstanceCentralConfigValidation
import org.rsmod.api.server.config.ServerConfig

@Singleton
public class CentralEmbeddedLifecycle
@Inject
constructor(
    private val serverConfig: ServerConfig,
    private val openRuneCentral: OpenRuneCentralWorldLink,
) {
    private var server: OpenRuneCentralEmbeddedServer? = null
    private var shutdownBridge: CentralShutdownBridge? = null

    public fun startIfConfigured() {
        val c = serverConfig.central ?: return
        if (!c.sameInstance) {
            return
        }
        val pg = c.postgres ?: error(SameInstanceCentralConfigValidation.missingPostgresMessage())

        val jdbcFromYaml = pg.jdbcUrl.trim()
        val (jdbc, dbUser, dbPassword) =
            if (jdbcFromYaml.isNotEmpty()) {
                Triple(jdbcFromYaml, pg.user.trim().ifBlank { "openrune" }, pg.password)
            } else {
                val embeddedCreds =
                    EmbeddedSameInstancePostgres.jdbcTripleIfEmbedded()
                        ?: error(
                            "game.yml: `central.postgres.jdbc-url` is blank but embedded PostgreSQL did not start. " +
                                "Ensure [org.rsmod.server.app.GameBootstrap] calls " +
                                "`EmbeddedSameInstancePostgres.ensureStarted` before starting embedded Central."
                        )
                embeddedCreds
            }

        fun buildRuntime() =
            centralRuntimeConfigFromJdbc(
                jdbcUrl = jdbc,
                dbUser = dbUser,
                dbPassword = dbPassword,
                dbMaximumPoolSize = pg.poolSize,
                worldLinkPort = c.linkPort,
                httpPort = c.httpPort,
                serverName = serverConfig.name,
                worldLinkSoBacklog = 512,
                loginTimingLogs = serverConfig.loginTimingLogs,
                socialPmTraceLogs = serverConfig.socialPmTraceLogs,
            )

        val runtime = buildRuntime()
        openRuneCentral.applyPasswordAuth(
            PasswordAuthConfig(
                passwordHasher = runtime.auth.passwordHasher,
                bcryptCost = runtime.auth.bcryptCost,
                argon2Iterations = runtime.auth.argon2Iterations,
                argon2MemoryKib = runtime.auth.argon2MemoryKib,
            )
        )

        val centralServer = OpenRuneCentralEmbeddedServer(c.httpPort, runtime)
        startCentralPreservingDatabase(
            start = {
                centralServer.start()
                shutdownBridge = CentralShutdownBridge.install(centralServer)
            },
            stop = { centralServer.stop() },
        )
        server = centralServer
    }

    public fun stopIfRunning() {
        var failure: Throwable? = null
        try {
            server?.stop()
        } catch (cause: Throwable) {
            failure = cause
        } finally {
            server = null
            try {
                shutdownBridge?.throwIfFailed()
            } catch (cause: Throwable) {
                failure?.addSuppressed(cause) ?: run { failure = cause }
            }
            shutdownBridge = null
        }
        failure?.let { throw it }
    }
}

/**
 * Releases resources from a failed start and preserves its cause for diagnosis. A missing schema
 * object can indicate a migration mismatch in a populated database; it never authorizes a reset.
 */
internal fun startCentralPreservingDatabase(start: () -> Unit, stop: () -> Unit) {
    try {
        start()
    } catch (cause: Throwable) {
        val failure = IllegalStateException(
            "OpenRune Central could not start. No automatic database reset was attempted. " +
                "Inspect the underlying error and check the configured database connection and ports. " +
                "For schema errors, back up the existing database and apply the required compatible " +
                "schema migration before restarting.",
            cause,
        )
        try {
            stop()
        } catch (cleanupFailure: Throwable) {
            if (cleanupFailure !== cause) failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }
}

/**
 * Classifies whether a Central startup failure is safe to auto-recover from by wiping the
 * embedded database's `public` schema. Only a genuinely missing/uninitialized schema qualifies -
 * unrelated failures such as a port-bind conflict must never trigger a wipe.
 */
internal object CentralStartupFailure {
    /**
     * Only true for SQL errors whose SQLSTATE indicates Central's expected schema/tables don't
     * exist yet (a fresh, never-initialized embedded database) - not for unrelated startup
     * failures such as a port-bind conflict, which have no such cause. Walks the full cause chain
     * since the originating [SQLException] is typically wrapped by higher-level framework/driver
     * exceptions before reaching the caller's catch block.
     */
    internal fun indicatesUninitializedSchema(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is SQLException && cause.sqlState in UNINITIALIZED_SCHEMA_SQL_STATES) {
                return true
            }
            val next = cause.cause
            cause = if (next === cause) null else next
        }
        return false
    }

    /**
     * Postgres SQLSTATEs for missing schema objects: undefined_table, undefined_column,
     * invalid_schema_name. See https://www.postgresql.org/docs/current/errcodes-appendix.html
     */
    private val UNINITIALIZED_SCHEMA_SQL_STATES = setOf("42P01", "42703", "3F000")
}
