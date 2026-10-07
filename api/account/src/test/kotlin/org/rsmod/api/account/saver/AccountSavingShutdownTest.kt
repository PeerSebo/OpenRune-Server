package org.rsmod.api.account.saver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.inject.Provider
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.autosave.PlayerAutosaveOrchestrator
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.appearance.CharacterAppearanceApplier
import org.rsmod.api.account.character.appearance.CharacterAppearancePipeline
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.main.CharacterAccountApplier
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.character.stats.CharacterStatApplier
import org.rsmod.api.account.character.stats.CharacterStatPipeline
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.account.loader.AccountLoaderService
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.journal.AccountSaveLease
import org.rsmod.api.account.saver.journal.SaveRevisionStore
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.attr.AttributeMap
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.jdbc.GameConnection
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.db.jdbc.GameDatabaseService
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid
import org.rsmod.server.services.Service
import org.rsmod.server.services.ServiceManager
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("PlayerPersistenceHints")
class AccountSavingShutdownTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `shutdown commits latest character state before closing its shared database`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Shutdown Save", CoordGrid(3200, 3200))
        val other = f.newCharacter("Unrelated", CoordGrid(3000, 3100))
        f.saveDirect(other)
        player.coords = CoordGrid(3222, 3218)
        player.runEnergy = 543
        player.pendingLogout = false
        player.loggingOut = true
        player.pendingShutdown = true
        val responses = mutableListOf<AccountSaveResponse>()
        f.saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, { responses += it }))

        // Do not schedule the writer: this specifically exercises the final pending-request drain.
        val drainingSaver = object : Service {
            override suspend fun startup() {}
            override suspend fun shutdown() = f.saver.shutdown()
        }
        val manager = f.manager(drainingSaver)
        manager.shutdown()
        assertEquals(ServiceManager.ShutdownResult.Clean, manager.awaitShutdown())
        assertEquals(1, responses.size)
        assertTrue(responses.single() is AccountSaveResponse.Success)
        assertEquals(listOf(3222, 3218, 543), f.savedPosition(player))
        assertFalse(f.savedOnline(player), "A drained logout save must not restore online presence")
        assertEquals(listOf(3000, 3100, 10_000), f.savedPosition(other))
        assertThrows(IllegalStateException::class.java) { f.database.withTransactionBlocking { } }
        Unit
    }

    @Test
    fun `saving and reloading preserves raw run energy without rescaling old values`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Energy Save", CoordGrid(3222, 3218))
        for (energy in listOf(543, 0, 10_000)) {
            player.runEnergy = energy
            f.saveDirect(player)
            assertEquals(listOf(3222, 3218, energy), f.savedPosition(player))
            val reloaded = f.reload(player)
            assertEquals(energy, reloaded.runEnergy)
            assertEquals(player.characterId, reloaded.characterId)
        }
    }

    @Test
    fun `cancelled writer retains in flight and remaining batch without duplicate callbacks`(): Unit = Fixture(directory).use { f ->
        val first = f.newCharacter("Batch First", CoordGrid(3101, 3201))
        val interrupted = f.newCharacter("Batch Second", CoordGrid(3102, 3202))
        val last = f.newCharacter("Batch Third", CoordGrid(3103, 3203))
        val cancelled = AtomicBoolean()
        val responses = mutableListOf<AccountSaveResponse>()
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                if (characterId == interrupted.characterId && cancelled.compareAndSet(false, true)) {
                    throw CancellationException("Simulated worker cancellation during a batch")
                }
            }
        }
        val saver = f.newSaver(setOf(pipeline))
        for (player in listOf(first, interrupted, last)) {
            saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, { responses += it }))
        }
        val manager = f.manager(saver)
        val result = manager.awaitShutdown()
        assertTrue(result is ServiceManager.ShutdownResult.Report)
        assertTrue(cancelled.get())
        assertEquals(listOf(first, interrupted, last), responses.map { it.player })
        assertTrue(responses.all { it is AccountSaveResponse.Success })
        for (player in listOf(first, interrupted, last)) {
            assertEquals(listOf(player.x, player.z, player.runEnergy), f.savedPosition(player))
        }
    }

    @Test
    fun `logout transaction cannot commit another players unfinished save`(): Unit = Fixture(directory).use { f ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val saving = executor.submit {
                f.database.withTransactionBlocking {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val logout = executor.submit {
                f.database.withTransactionBlocking { secondEntered.countDown() }
            }
            assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS))
            release.countDown()
            saving.get(5, TimeUnit.SECONDS)
            logout.get(5, TimeUnit.SECONDS)
            assertEquals(0L, secondEntered.count)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `late committed JDBC success is accepted once without a redundant retry`(): Unit = lateAttempt(false)

    @Test
    fun `outer shutdown deadline reports queued requests after a slow success callback`(): Unit = Fixture(directory).use { f ->
        val first = f.newCharacter("Deadline First", CoordGrid(3100, 3200))
        val queued = f.newCharacter("Deadline Queued", CoordGrid(3000, 3100))
        f.saveDirect(first)
        f.saveDirect(queued)
        first.coords = CoordGrid(3101, 3201)
        queued.coords = CoordGrid(3001, 3101)
        val writes = AtomicInteger()
        val responses = mutableListOf<AccountSaveResponse>()
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                writes.incrementAndGet()
            }
        }
        val saver = f.newSaver(setOf(pipeline))
        for (player in listOf(first, queued)) {
            saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, {
                responses += it
                if (player === first) {
                    // The save has committed and its inner timeout is complete. A slow callback
                    // crosses the real outer drain deadline on the undispatched success path.
                    Thread.sleep(30_200)
                }
            }))
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking(Dispatchers.IO) { saver.shutdown() }
        }
        assertEquals(1, writes.get())
        assertEquals(2, responses.size)
        assertEquals(AccountSaveResponse.Success(first), responses[0])
        assertEquals(AccountSaveResponse.RecoveryFailure(queued), responses[1])
        assertEquals(listOf(3101, 3201, 10_000), f.savedPosition(first))
        assertEquals(listOf(3000, 3100, 10_000), f.savedPosition(queued))
    }

    @Test
    fun `late JDBC failure retries once and emits one callback with latest state`(): Unit = lateAttempt(true)

    private fun lateAttempt(fail: Boolean): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Late Save", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        val attempts = AtomicInteger()
        val responses = mutableListOf<AccountSaveResponse>()
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                if (attempts.incrementAndGet() == 1) {
                    // Exceed the actual 2-second shutdown-request timeout while simulating a JDBC
                    // driver that cannot suspend. A successful commit is accepted once by the
                    // coroutine timeout's documented undispatched fast path; failure retries once.
                    Thread.sleep(2200)
                    if (fail) throw IllegalStateException("Late database failure")
                }
            }
        }
        val saver = f.newSaver(setOf(pipeline))
        val request = AccountSaveRequest(player.accountId, player.characterId, player, { responses += it })
        saver.queue(request)
        // Dispatch away from runBlocking's event loop so the timeout fires while JDBC is blocked.
        runBlocking(Dispatchers.IO) { saver.shutdown() }
        assertEquals(if (fail) 2 else 1, attempts.get())
        assertEquals(if (fail) 1 else 0, request.attempts)
        assertEquals(1, responses.size)
        assertTrue(responses.single() is AccountSaveResponse.Success)
        assertEquals(listOf(3222, 3218, 10_000), f.savedPosition(player))
    }

    @Test
    fun `queued account save freezes coordinates vars and nested legacy documents`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Frozen Save", CoordGrid(3100, 3200))
        val document = mutableMapOf("collected" to mutableListOf(1, 2))
        player.attr[AttributeKey<Map<String, List<Int>>>("snapshot_fixture")] = document
        player.vars.backing["varp.consumables_stamina_remaining".asRSCM()] = 137
        val responses = mutableListOf<AccountSaveResponse>()
        f.saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))

        player.coords = CoordGrid(3300, 3400)
        player.runEnergy = 500
        player.vars.backing["varp.consumables_stamina_remaining".asRSCM()] = 0
        document.getValue("collected").add(3)
        runBlocking { f.saver.shutdown() }

        val saved = f.reload(player)
        assertEquals(CoordGrid(3100, 3200), saved.coords)
        assertEquals(10_000, saved.runEnergy)
        assertEquals(137, saved.vars["varp.consumables_stamina_remaining"])
        assertEquals(mapOf("collected" to listOf(1, 2)), saved.attr.toPersistentMap()["snapshot_fixture"])
        assertTrue(responses.single() is AccountSaveResponse.Success)
    }

    @Test
    fun `queued inventories stats and appearance use one captured version`(): Unit = Fixture(directory).use { f ->
        val pipelines = linkedSetOf(
            CharacterInventoryPipeline(CharacterInventoryApplier()),
            CharacterStatPipeline(CharacterStatApplier()),
            CharacterAppearancePipeline(CharacterAppearanceApplier(), jacksonObjectMapper()),
        )
        val saver = f.newSaver(pipelines)
        val player = f.newCharacter("Frozen Segments", CoordGrid(3100, 3200))
        val inv = player.invMap.getOrPut("inv.inv")
        inv[0] = InvObj("obj.coins", 1234)
        player.statMap.setBaseLevel("stat.attack", 30)
        player.statMap.setCurrentLevel("stat.attack", 20)
        player.appearance.setColour(0, 5)
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, {}))

        inv[0] = InvObj("obj.coins", 99)
        player.statMap.setCurrentLevel("stat.attack", 29)
        player.appearance.setColour(0, 1)
        runBlocking { saver.shutdown() }
        val saved = f.reload(player, pipelines)
        assertEquals(1234, saved.invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(20, saved.statMap.getCurrentLevel("stat.attack").toInt())
        assertEquals(5, saved.appearance.coloursSnapshot()[0].toInt())
    }

    @Test
    fun `older failed snapshot never overwrites a newer committed logout save`(): Unit = Fixture(directory).use { f ->
        val writes = AtomicInteger()
        val preparations = AtomicInteger()
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                if (writes.incrementAndGet() == 1) error("First snapshot write failed")
            }
        }
        val saver = f.newSaver(setOf(pipeline), setOf(PlayerSavePreparationHook { preparations.incrementAndGet() }))
        val player = f.newCharacter("Retry Ordering", CoordGrid(3100, 3200))
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        player.coords = CoordGrid(3200, 3300)
        player.loggingOut = true
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        runBlocking { saver.shutdown() }

        assertEquals(2, preparations.get())
        assertEquals(2, writes.get())
        assertEquals(2, responses.size)
        assertTrue(responses.all { it is AccountSaveResponse.Success })
        assertEquals(listOf(3200, 3300, 10_000), f.savedPosition(player))
        assertFalse(f.savedOnline(player))
    }

    @Test
    fun `preparation failure returns failure before any database write`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Prepare Failure", CoordGrid(3100, 3200))
        f.saveDirect(player)
        val saver = f.newSaver(emptySet(), setOf(PlayerSavePreparationHook { error("Preparation failed") }))
        val responses = mutableListOf<AccountSaveResponse>()
        player.coords = CoordGrid(3300, 3400)
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        assertThrows(IllegalStateException::class.java) { runBlocking { saver.shutdown() } }
        assertTrue(responses.single() is AccountSaveResponse.PreparationFailure)
        assertEquals(listOf(3100, 3200, 10_000), f.savedPosition(player))
    }

    @Test
    fun `autosave waits for end of tick coalesces hints and retains changes made during a save`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Tick Save", CoordGrid(3100, 3200)).apply { processedMapClock = 100 }
        val captured = mutableListOf<CoordGrid>()
        val saver = f.newSaver(emptySet(), setOf(PlayerSavePreparationHook {
            captured += it.coords
            PlayerPersistenceHints.notify(it)
        }))
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val loader = unsafe.allocateInstance(AccountLoaderService::class.java) as AccountLoaderService
        val oldAttrSink = AttributeMap.persistenceMutationSink
        val autosave = PlayerAutosaveOrchestrator(AccountManager(loader, saver))
        try {
            repeat(3) { PlayerPersistenceHints.notify(player) }
            assertTrue(captured.isEmpty())
            player.coords = CoordGrid(3200, 3300)
            autosave.processEndOfTick(listOf(player))
            assertEquals(listOf(CoordGrid(3200, 3300)), captured)

            player.coords = CoordGrid(3300, 3400)
            PlayerPersistenceHints.notify(player)
            autosave.processEndOfTick(listOf(player))
            assertEquals(1, captured.size)
            runBlocking { saver.run() }
            assertEquals(listOf(3200, 3300, 10_000), f.savedPosition(player))
            autosave.processEndOfTick(listOf(player))
            assertEquals(listOf(CoordGrid(3200, 3300), CoordGrid(3300, 3400)), captured)
            runBlocking { saver.run() }
            autosave.processEndOfTick(listOf(player))
            assertEquals(2, captured.size, "Snapshot preparation must not queue itself again")
            assertEquals(listOf(3300, 3400, 10_000), f.savedPosition(player))
        } finally {
            AttributeMap.persistenceMutationSink = oldAttrSink
            PlayerPersistenceHints.bind {}
            PlayerPersistenceHints.leave()
        }
    }

    @Test
    fun `autosave preparation failure remains dirty and retries after fifty ticks`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Prepare Retry", CoordGrid(3100, 3200)).apply { processedMapClock = 100 }
        f.saveDirect(player)
        val captured = mutableListOf<Int>()
        val saver = f.newSaver(emptySet(), setOf(PlayerSavePreparationHook {
            captured += it.runEnergy
            if (captured.size == 1) error("First preparation failed")
        }))
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val loader = unsafe.allocateInstance(AccountLoaderService::class.java) as AccountLoaderService
        val oldAttrSink = AttributeMap.persistenceMutationSink
        val autosave = PlayerAutosaveOrchestrator(AccountManager(loader, saver))
        try {
            player.coords = CoordGrid(3200, 3300)
            PlayerPersistenceHints.notify(player)
            autosave.processEndOfTick(listOf(player))
            assertEquals(listOf(10_000), captured)
            runBlocking { saver.run() }
            assertEquals(listOf(3100, 3200, 10_000), f.savedPosition(player))

            player.runEnergy = 543
            repeat(49) {
                autosave.processEndOfTick(listOf(player))
                assertEquals(1, captured.size, "A failed preparation must respect its retry delay")
            }
            autosave.processEndOfTick(listOf(player))
            assertEquals(listOf(10_000, 543), captured)
            assertEquals(listOf(3100, 3200, 10_000), f.savedPosition(player))
            runBlocking { saver.run() }
            assertEquals(listOf(3200, 3300, 543), f.savedPosition(player))

            repeat(100) { autosave.processEndOfTick(listOf(player)) }
            assertEquals(2, captured.size, "Successful retry must clear the pending autosave")
            runBlocking { saver.run() }
            assertEquals(listOf(3200, 3300, 543), f.savedPosition(player))
        } finally {
            AttributeMap.persistenceMutationSink = oldAttrSink
            PlayerPersistenceHints.bind {}
            PlayerPersistenceHints.leave()
        }
    }

    @Test
    fun `exhausted retries preserve the frozen complete save and fail shutdown`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Recovery Save", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        player.invMap.getOrPut("inv.inv")[0] = InvObj("obj.coins", 1234)
        val document = mutableListOf(1, 2)
        player.attr[AttributeKey<List<Int>>("recovery_fixture")] = document
        val attempts = AtomicInteger()
        val failed = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun prepareSave(player: Player): CharacterDataStage.PreparedSave =
                CharacterDataStage.PreparedSave { _, _ ->
                    attempts.incrementAndGet()
                    error("Unavailable database")
                }.withRecoveryData("testFailure", null)

            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                attempts.incrementAndGet()
                error("Unavailable database")
            }
        }
        val pipelines = linkedSetOf(
            CharacterInventoryPipeline(CharacterInventoryApplier()),
            CharacterStatPipeline(CharacterStatApplier()),
            CharacterAppearancePipeline(CharacterAppearanceApplier(), jacksonObjectMapper()),
            failed,
        )
        val saver = f.newSaver(pipelines)
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        player.coords = CoordGrid(3300, 3400)
        player.invMap.getOrPut("inv.inv")[0] = InvObj("obj.coins", 5)
        document.add(3)

        assertThrows(IllegalStateException::class.java) { runBlocking { saver.shutdown() } }

        assertEquals(4, attempts.get())
        assertEquals(listOf(AccountSaveResponse.ExcessiveRetries(player)), responses)
        assertEquals(listOf(3100, 3200, 10_000), f.savedPosition(player))
        val files = Files.list(directory.resolve("recovery")).use { it.toList() }
        assertEquals(1, files.size)
        val json = jacksonObjectMapper().readTree(files.single().toFile())
        assertEquals(player.characterId, json["characterId"].asInt())
        val segments = json["segments"]
        assertEquals(3222, segments[0]["character"]["x"].asInt())
        assertEquals("[1,2]", segments[0]["character"]["attributeJson"]["recovery_fixture"].asText())
        assertEquals(1234, segments[1]["inventories"][0]["slots"][0]["count"].asInt())
        assertTrue(segments[2]["stats"].isArray)
        assertFalse(segments[3]["appearanceJson"].asText().isBlank())
        assertEquals(0, json["unrecoverableSegments"].asInt())
    }

    @Test
    fun `successful newer snapshot resolves failed recovery without replaying stale data`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Recovery Newer", CoordGrid(3100, 3200))
        val broken = AtomicBoolean(true)
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                check(!broken.get()) { "Unavailable database" }
            }
        }
        val saver = f.newSaver(setOf(pipeline))
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        repeat(4) { runBlocking { saver.run() } }
        assertEquals(listOf(AccountSaveResponse.RecoveryFailure(player)), responses)
        assertEquals(1L, Files.list(directory.resolve("recovery")).use { it.count() })
        val incomplete = Files.list(directory.resolve("recovery")).use { it.findFirst().orElseThrow() }
        assertEquals(1, jacksonObjectMapper().readTree(incomplete.toFile())["unrecoverableSegments"].asInt())

        broken.set(false)
        player.coords = CoordGrid(3222, 3218)
        player.loggingOut = true
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        runBlocking { saver.shutdown() }

        assertEquals(listOf(3222, 3218, 10_000), f.savedPosition(player))
        assertFalse(f.savedOnline(player))
        assertEquals(listOf(AccountSaveResponse.RecoveryFailure(player), AccountSaveResponse.Success(player)), responses)
        val files = Files.list(directory.resolve("recovery")).use { it.toList() }
        assertEquals(1, files.size)
        assertTrue(files.single().fileName.toString().endsWith(".json.resolved"))
    }

    @Test
    fun `failure to write recovery is distinct from a safely preserved terminal failure`(): Unit = Fixture(directory).use { f ->
        Files.writeString(directory.resolve("recovery"), "directory blocked by a file")
        val player = f.newCharacter("Recovery Blocked", CoordGrid(3100, 3200))
        val pipeline = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                error("Unavailable database")
            }
        }
        val saver = f.newSaver(setOf(pipeline))
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))

        assertThrows(IllegalStateException::class.java) { runBlocking { saver.shutdown() } }

        assertEquals(listOf(AccountSaveResponse.RecoveryFailure(player)), responses)
        assertEquals("directory blocked by a file", Files.readString(directory.resolve("recovery")))
    }

    @Test
    fun `a full save queue rejects before preparation and accepts a safe retry`(): Unit = Fixture(directory).use { f ->
        val player = f.newCharacter("Queue Retry", CoordGrid(3100, 3200))
        val preparations = AtomicInteger()
        val saver = f.newSaver(emptySet(), setOf(PlayerSavePreparationHook { preparations.incrementAndGet() }))
        val queue = AccountSavingService::class.java.getDeclaredField("pendingRequests")
            .apply { isAccessible = true }.get(saver) as AccountSaveQueue
        repeat(4096) { assertTrue(queue.reserve()) }
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        assertEquals(listOf(AccountSaveResponse.QueueFull(player)), responses)
        assertEquals(0, preparations.get())
        repeat(4096) { queue.cancelReservation() }
        player.coords = CoordGrid(3222, 3218)
        saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, responses::add))
        runBlocking { saver.shutdown() }
        assertEquals(AccountSaveResponse.Success(player), responses.last())
        assertEquals(1, preparations.get())
        assertEquals(listOf(3222, 3218, 10_000), f.savedPosition(player))
        assertEquals(0, queue.trackedSessions())
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }

    internal class Fixture(private val directory: Path) : AutoCloseable {
        private val postgres = EmbeddedPostgres.builder()
            .setDataDirectory(directory.resolve("postgres").toFile())
            .setRegisterShutdownHook(false)
            .start()
        private val config = DatabaseConfig(postgres.getJdbcUrl("postgres", "postgres"), "postgres", "", false)
        private val provider = Provider { config }
        val database = GameDatabase(provider)
        private val databaseService = GameDatabaseService(provider, GameConnection(provider), database)
        private var closedByManager = false
        val repository = CharacterAccountRepository(jacksonObjectMapper(), CharacterAccountApplier())
        private val server = ServerConfig("Shutdown test", 0, 240, "test", 1)
        val saver = newSaver(emptySet())

        init {
            try {
                runBlocking { databaseService.startup() }
                database.withTransactionBlocking { SaveRevisionStore.install(it) }
            } catch (t: Throwable) {
                postgres.close()
                throw t
            }
        }

        fun newSaver(pipelines: Set<CharacterDataStage.Pipeline>, hooks: Set<PlayerSavePreparationHook> = emptySet()) =
            AccountSavingService(database, repository, pipelines, server, hooks,
                AccountSaveRecovery(jacksonObjectMapper(), directory.resolve("recovery")),
                AccountSaveJournal(jacksonObjectMapper(), directory.resolve("journal")))

        fun newCharacter(name: String, coords: CoordGrid): Player = database.withTransactionBlocking { connection ->
            val account = checkNotNull(repository.insertOrSelectAccountId(connection, name, "test-only"))
            val character = checkNotNull(repository.insertAndSelectCharacterId(connection, account))
            Player().apply {
                accountId = account
                characterId = character
                displayName = name
                username = name
                this.coords = coords
                AccountSaveLease.apply(this, AccountSaveLease(SaveRevisionStore.lock(connection, character)))
                AccountSaveLease.apply(this, SaveRevisionStore.claim(connection, this))
            }
        }

        fun saveDirect(player: Player) = database.withTransactionBlocking { connection ->
            repository.save(connection, player, player.accountId, player.characterId, server.world)
        }

        fun reload(player: Player, pipelines: Set<CharacterDataStage.Pipeline> = emptySet()): Player = database.withTransactionBlocking { connection ->
            val metadata = checkNotNull(repository.selectAndCreateMetadataList(connection, player.username))
            for (pipeline in pipelines) pipeline.append(connection, metadata)
            Player().also { target -> metadata.forEach { it.apply(target) } }
        }

        fun manager(worker: Service): ServiceManager {
            val managedDatabase = object : Service by databaseService {
                override suspend fun startup() {}
                override suspend fun shutdown() {
                    databaseService.shutdown()
                    closedByManager = true
                }
            }
            return ServiceManager.create(linkedSetOf(managedDatabase, worker)).also {
                assertEquals(ServiceManager.StartResult.Healthy, runBlocking { it.awaitStartup() })
            }
        }

        fun savedPosition(player: Player): List<Int> =
            DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
                connection.prepareStatement("SELECT x, z, run_energy FROM public.account_characters WHERE id = ?").use { statement ->
                    statement.setInt(1, player.characterId)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        listOf(rows.getInt(1), rows.getInt(2), rows.getInt(3))
                    }
                }
            }

        fun savedOnline(player: Player): Boolean =
            DriverManager.getConnection(config.jdbcUrl, config.user, config.password).use { connection ->
                connection.prepareStatement("SELECT online_central_world_id, online_session_heartbeat FROM public.account_characters WHERE id = ?").use { statement ->
                    statement.setInt(1, player.characterId)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        rows.getObject(1) != null || rows.getObject(2) != null
                    }
                }
            }

        override fun close() {
            try {
                if (!closedByManager) runBlocking { databaseService.shutdown() }
            } finally {
                postgres.close()
            }
        }
    }
}
