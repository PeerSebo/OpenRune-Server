package org.rsmod.api.db.jdbc

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class EmbeddedPostgresOwnerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a second JVM cannot claim or stop the first JVM database`() {
        val data = directory.resolve("postgres")
        val first = EmbeddedPostgresOwner()
        first.start(data)
        try {
            val (url, user, password) = checkNotNull(first.jdbcTriple())
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    for (setting in listOf("fsync", "synchronous_commit", "full_page_writes")) {
                        statement.executeQuery("SHOW $setting").use { rows ->
                            assertTrue(rows.next())
                            assertEquals("on", rows.getString(1), "$setting must preserve committed player saves")
                        }
                    }
                }
            }
            execute(first, "CREATE TABLE ownership_sentinel (value INTEGER)")
            execute(first, "INSERT INTO ownership_sentinel VALUES (42)")
            val output = directory.resolve("contender.log")
            val arguments = directory.resolve("contender.args")
            Files.writeString(arguments, listOf(
                "-cp", checkNotNull(System.getProperty("openrune.test.classpath")),
                EmbeddedPostgresContender::class.java.name, data.toString(),
            ).joinToString("\n", transform = ::javaArgument))
            val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            val child = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(), "@$arguments",
            ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Competing JVM must fail promptly")
                val log = Files.readString(output)
                assertEquals(0, child.exitValue(), log)
                assertTrue(log.contains("REFUSED_WITHOUT_STOP_AUTHORITY"), log)
            } finally {
                if (child.isAlive) {
                    child.destroyForcibly()
                    assertTrue(child.waitFor(10, TimeUnit.SECONDS))
                }
            }
            assertEquals(42, query(first, "SELECT value FROM ownership_sentinel"))
        } finally {
            first.stop(5_000)
        }
    }

    @Test
    fun `failed live foreign startup leaves the original database running and releases the contender lease`() {
        val data = directory.resolve("postgres")
        val contender = EmbeddedPostgresOwner()
        EmbeddedPostgres.builder().setDataDirectory(data.toFile()).setCleanDataDirectory(false)
            .setRegisterShutdownHook(false).start().use { foreign ->
                val original = Files.readAllBytes(data.resolve("postmaster.pid"))
                val failure = assertThrows(IllegalStateException::class.java) { contender.start(data) }
                assertTrue(failure.message.orEmpty().contains("will not borrow or kill"), failure.message)
                contender.stop(5_000)
                contender.forceStopNow()
                assertArrayEquals(original, Files.readAllBytes(data.resolve("postmaster.pid")))
                foreign.postgresDatabase.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("CREATE TABLE foreign_sentinel (value INTEGER)")
                        statement.execute("INSERT INTO foreign_sentinel VALUES (73)")
                    }
                }
            }
        contender.start(data)
        try {
            assertEquals(73, query(contender, "SELECT value FROM foreign_sentinel"))
        } finally {
            contender.stop(5_000)
        }
    }

    @Test
    fun `malformed PID metadata is preserved without starting or acquiring stop authority`() {
        val data = Files.createDirectory(directory.resolve("postgres"))
        val pid = data.resolve("postmaster.pid")
        Files.writeString(pid, "incomplete metadata\n")
        val contender = EmbeddedPostgresOwner()
        val failure = assertThrows(IllegalStateException::class.java) { contender.start(data) }
        assertTrue(failure.message.orEmpty().contains("Malformed PostgreSQL PID"), failure.message)
        contender.stop(5_000)
        contender.forceStopNow()
        assertEquals("incomplete metadata\n", Files.readString(pid))
        val again = assertThrows(IllegalStateException::class.java) { contender.start(data) }
        assertTrue(again.message.orEmpty().contains("Malformed PostgreSQL PID"), again.message)
    }

    @Test
    fun `a verified dead stale PID is recoverable without removing the database`() {
        val data = directory.resolve("postgres")
        val initial = EmbeddedPostgresOwner()
        initial.start(data)
        val oldMetadata: String
        try {
            execute(initial, "CREATE TABLE recovery_sentinel (value INTEGER)")
            execute(initial, "INSERT INTO recovery_sentinel VALUES (91)")
            oldMetadata = Files.readString(data.resolve("postmaster.pid"))
        } finally {
            initial.stop(5_000)
        }
        val oldPid = oldMetadata.lineSequence().first().trim().toLong()
        assertFalse(ProcessHandle.of(oldPid).map { it.isAlive }.orElse(false))
        Files.writeString(data.resolve("postmaster.pid"), oldMetadata)
        val recovered = EmbeddedPostgresOwner()
        recovered.start(data)
        try {
            assertEquals(91, query(recovered, "SELECT value FROM recovery_sentinel"))
        } finally {
            recovered.stop(5_000)
        }
    }

    @Test
    fun `concurrent stops finish before replacement and an old owner cannot stop the later incarnation`() {
        val data = directory.resolve("postgres")
        val old = EmbeddedPostgresOwner()
        old.start(data)
        try {
            execute(old, "CREATE TABLE replacement_sentinel (value INTEGER)")
            execute(old, "INSERT INTO replacement_sentinel VALUES (117)")
        } finally {
            val stops = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val release = CountDownLatch(1)
            try {
                val results = List(2) {
                    stops.submit {
                        ready.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        old.stop(5_000)
                    }
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                release.countDown()
                results.forEach { it.get(20, TimeUnit.SECONDS) }
            } finally {
                release.countDown()
                stops.shutdownNow()
                assertTrue(stops.awaitTermination(20, TimeUnit.SECONDS))
                old.stop(5_000)
            }
        }
        old.stop(5_000)
        assertNull(old.jdbcTriple())
        val replacement = EmbeddedPostgresOwner()
        replacement.start(data)
        try {
            old.forceStopNow()
            old.stop(5_000)
            assertEquals(117, query(replacement, "SELECT value FROM replacement_sentinel"))
        } finally {
            replacement.stop(5_000)
        }
    }

    @Test
    fun `changed postmaster incarnation prevents close and watchdog while retaining the lease`() {
        val data = directory.resolve("postgres")
        val owner = EmbeddedPostgresOwner()
        owner.start(data)
        val pidFile = data.resolve("postmaster.pid")
        val original = Files.readAllBytes(pidFile)
        try {
            val changed = original.toString(Charsets.UTF_8).lines().toMutableList()
            changed[2] = (changed[2].trim().toLong() + 60).toString()
            Files.writeString(pidFile, changed.joinToString("\n"))
            val failure = assertThrows(IllegalStateException::class.java) { owner.stop(5_000) }
            assertTrue(failure.message.orEmpty().contains("identity changed"), failure.message)
            owner.forceStopNow()
            assertEquals(1, query(owner, "SELECT 1"))
            val contender = EmbeddedPostgresOwner()
            val locked = assertThrows(IllegalStateException::class.java) { contender.start(data) }
            assertTrue(locked.message.orEmpty().contains("Another OpenRune process owns"), locked.message)
            contender.stop(5_000)
            contender.forceStopNow()
            assertEquals(1, query(owner, "SELECT 1"))
        } finally {
            Files.write(pidFile, original)
            owner.stop(5_000)
        }
    }

    private fun execute(owner: EmbeddedPostgresOwner, sql: String) {
        val (url, user, password) = checkNotNull(owner.jdbcTriple())
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private fun query(owner: EmbeddedPostgresOwner, sql: String): Int {
        val (url, user, password) = checkNotNull(owner.jdbcTriple())
        return DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> check(rows.next()); rows.getInt(1) }
            }
        }
    }

    private fun javaArgument(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

object EmbeddedPostgresContender {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val contender = EmbeddedPostgresOwner()
        val failure = runCatching { contender.start(Path.of(arguments.single())) }.exceptionOrNull()
        contender.stop(5_000)
        contender.forceStopNow()
        check(failure is IllegalStateException && failure.message.orEmpty().contains("Another OpenRune process owns")) {
            "Expected an ownership refusal, got $failure"
        }
        println("REFUSED_WITHOUT_STOP_AUTHORITY")
    }
}
