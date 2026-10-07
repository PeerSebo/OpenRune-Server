package org.rsmod.api.net.central.embed

import com.zaxxer.hikari.HikariDataSource
import dev.or2.central.embed.OpenRuneCentralEmbeddedServer
import dev.or2.central.notify.PgNotifyService
import dev.or2.central.util.config.centralRuntimeConfigFromJdbc
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Path
import java.util.concurrent.ScheduledExecutorService
import javax.sql.DataSource
import kotlin.reflect.KClass
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("embedded-central-koin")
class CentralShutdownBridgeTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `original Central cleanup runs before Koin closes and other stopped listeners stay deferred`() {
        System.setProperty("io.ktor.server.engine.ShutdownHook", "false")
        EmbeddedPostgres.builder().setDataDirectory(directory.resolve("postgres").toFile())
            .setRegisterShutdownHook(false).start().use { postgres ->
                val runtime = centralRuntimeConfigFromJdbc(
                    jdbcUrl = postgres.getJdbcUrl("postgres", "postgres"), dbUser = "postgres", dbPassword = "",
                    dbMaximumPoolSize = 4, worldLinkPort = 0, httpPort = 0, serverName = "Lifecycle test",
                    worldLinkSoBacklog = 64, loginTimingLogs = false, socialPmTraceLogs = false,
                )
                val central = OpenRuneCentralEmbeddedServer(0, runtime)
                try {
                    central.start()
                    val engine = field(field(central, "delegate"), "server")
                    val application = engine.javaClass.getMethod("getApplication").invoke(engine)
                    val monitor = engine.javaClass.getMethod("getMonitor").invoke(engine)
                    val koin = Class.forName("org.koin.ktor.ext.ApplicationExtKt")
                        .getMethod("getKoin", Class.forName("io.ktor.server.application.Application"))
                        .invoke(null, application)
                    val pool = get(koin, DataSource::class) as HikariDataSource
                    val notify = get(koin, PgNotifyService::class) as PgNotifyService
                    val scheduler = get(koin, ScheduledExecutorService::class) as ScheduledExecutorService
                    val notifyThread = field(notify, "thread") as Thread
                    assertFalse(pool.isClosed)
                    assertTrue(notifyThread.isAlive)
                    val bridge = CentralShutdownBridge.install(central)
                    val events = mutableListOf<String>()
                    subscribe(monitor, "org.koin.ktor.plugin.KoinApplicationEventsKt", "getKoinApplicationStopPreparing") {
                        assertSame(pool, get(koin, DataSource::class))
                        assertTrue(pool.isClosed)
                        assertTrue(field(notify, "stopped") as Boolean)
                        assertTrue(scheduler.isShutdown)
                        events += "resources-closed-before-koin"
                    }
                    subscribe(monitor, "io.ktor.server.application.DefaultApplicationEventsKt", "getApplicationStopped") {
                        events += "application-stopped"
                    }
                    central.stop()
                    bridge.throwIfFailed()
                    assertEquals(listOf("resources-closed-before-koin", "application-stopped"), events)
                    assertTrue(pool.isClosed)
                    notifyThread.join(5000)
                    assertFalse(notifyThread.isAlive, "The LISTEN worker must stop before PostgreSQL closes")
                    postgres.postgresDatabase.connection.use { connection ->
                        connection.createStatement().use { it.execute("SELECT 1") }
                    }
                } finally {
                    central.stop()
                }
            }
    }

    private fun field(owner: Any, name: String): Any =
        checkNotNull(owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner))

    private fun get(koin: Any, type: KClass<*>): Any = koin.javaClass.getMethod(
        "get", KClass::class.java, Class.forName("org.koin.core.qualifier.Qualifier"), Function0::class.java,
    ).invoke(koin, type, null, null)

    private fun subscribe(monitor: Any, eventOwner: String, eventGetter: String, action: () -> Unit) {
        val event = Class.forName(eventOwner).getMethod(eventGetter).invoke(null)
        val handler: (Any) -> Unit = { action() }
        monitor.javaClass.getMethod("subscribe", Class.forName("io.ktor.events.EventDefinition"), Function1::class.java)
            .invoke(monitor, event, handler)
    }
}
