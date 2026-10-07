package org.rsmod.api.account.saver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.CharacterMetadataList
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.account.saver.AccountSavingShutdownTest.Fixture
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.journal.SaveRevisionStore
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("PlayerPersistenceHints")
class AtomicAccountSaveTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper()
    private fun journal() = AccountSaveJournal(mapper, directory.resolve("journal"))

    @Test fun `joint inventories commit once and remain fenced until game acknowledgement`() = Fixture(directory).use { f ->
        val inventory = CharacterInventoryPipeline(CharacterInventoryApplier())
        val saver = f.newSaver(setOf(inventory))
        val a = player(f, "Trade A", 100)
        val b = player(f, "Trade B", 200)
        a.inv[0] = InvObj("obj.coins", 50)
        b.inv[0] = InvObj("obj.coins", 250)
        val group = checkNotNull(saver.queueAtomic(a, b, mapOf("kind" to "trade")))
        assertFalse(a.canProcess)
        assertTrue(a.isAccessProtected)
        val responses = mutableListOf<AccountSaveResponse>()
        saver.queue(AccountSaveRequest(a.accountId, a.characterId, a, responses::add))
        assertEquals(listOf(AccountSaveResponse.QueueFull(a)), responses)
        runBlocking { saver.run() }
        assertEquals(true, group.succeeded)
        assertEquals(50, f.reload(a, setOf(inventory)).invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(250, f.reload(b, setOf(inventory)).invMap.getOrPut("inv.inv")[0]?.count)
        assertFalse(a.canProcess)
        f.database.withTransactionBlocking { journal().saveAtomic(it, group) }
        assertEquals(250, f.reload(b, setOf(inventory)).invMap.getOrPut("inv.inv")[0]?.count)
        group.release()
        assertTrue(a.canProcess)
        assertNull(b.pendingAccountCommit)
    }

    @Test fun `failure writing second character rolls back both and retry commits the same transaction`() = Fixture(directory).use { f ->
        val a = player(f, "Rollback A", 100)
        val b = player(f, "Rollback B", 200)
        f.saveDirect(a); f.saveDirect(b)
        val broken = AtomicBoolean(true)
        val pipeline = object : CharacterDataStage.Pipeline {
            override val recoveryKey = "failure_fixture"
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {
                if (characterId == b.characterId && broken.get()) error("Second write interrupted")
            }
            override fun prepareSave(player: Player) = CharacterDataStage.PreparedSave { c, id -> save(c, player, id) }
                .withRecoveryData(recoveryKey, emptyMap<String, Any>())
            override fun restoreSave(data: JsonNode) = CharacterDataStage.PreparedSave { _, _ -> }
        }
        val saver = f.newSaver(setOf(pipeline))
        a.coords = CoordGrid(3222, 3218); b.coords = CoordGrid(3223, 3218)
        val group = checkNotNull(saver.queueAtomic(a, b))
        runBlocking { saver.run() }
        assertNull(group.succeeded)
        assertEquals(3100, f.savedPosition(a)[0])
        assertEquals(3100, f.savedPosition(b)[0])
        assertThrows(IllegalStateException::class.java) { group.release() }
        broken.set(false)
        runBlocking { saver.run() }
        assertEquals(true, group.succeeded)
        assertEquals(3222, f.savedPosition(a)[0])
        assertEquals(3223, f.savedPosition(b)[0])
    }

    @Test fun `published joint record recovers both inventories before either login and replay is idempotent`() = Fixture(directory).use { f ->
        val inventory = CharacterInventoryPipeline(CharacterInventoryApplier())
        val pipelines = setOf<CharacterDataStage.Pipeline>(inventory)
        val a = player(f, "Recover A", 100)
        val b = player(f, "Recover B", 200)
        f.saveDirect(a); f.saveDirect(b)
        a.inv[0] = InvObj("obj.coins", 25); b.inv[0] = InvObj("obj.coins", 275)
        val group = checkNotNull(f.newSaver(pipelines).queueAtomic(a, b))
        journal().persistAtomic(group)
        val recover = {
            f.database.withTransactionBlocking {
                f.repository.clearOnlinePresenceClaimedOnWorld(it, 1)
                journal().recover(it, b.characterId, b.accountId, f.repository, pipelines)
            }
        }
        recover()
        assertEquals(25, f.reload(a, pipelines).invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(275, f.reload(b, pipelines).invMap.getOrPut("inv.inv")[0]?.count)
        recover()
        assertEquals(275, f.reload(b, pipelines).invMap.getOrPut("inv.inv")[0]?.count)
    }

    @Test fun `joint replay rollback preserves both old revisions and its recovery file`() = Fixture(directory).use { f ->
        val a = player(f, "Replay A", 100); val b = player(f, "Replay B", 200)
        f.saveDirect(a); f.saveDirect(b)
        a.coords = CoordGrid(3222, 3218); b.coords = CoordGrid(3223, 3218)
        val group = checkNotNull(f.saver.queueAtomic(a, b))
        journal().persistAtomic(group)
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking {
                f.repository.clearOnlinePresenceClaimedOnWorld(it, 1)
                journal().recover(it, a.characterId, a.accountId, f.repository, emptySet())
                error("Replay commit interrupted")
            }
        }
        assertEquals(3100, f.savedPosition(a)[0])
        assertEquals(3100, f.savedPosition(b)[0])
        assertTrue(Files.exists(directory.resolve("journal/_atomic/${group.id}.json")))
    }

    @Test fun `older single checkpoint cannot overwrite a joint inventory transfer`() = Fixture(directory).use { f ->
        val a = player(f, "Old A", 100); val b = player(f, "Old B", 200)
        val old = AccountSaveRequest(a.accountId, a.characterId, a, {})
        f.saver.queue(old)
        journal().persist(old)
        a.coords = CoordGrid(3222, 3218)
        val group = checkNotNull(f.saver.queueAtomic(a, b))
        journal().persistAtomic(group)
        f.database.withTransactionBlocking { journal().saveAtomic(it, group) }
        f.database.withTransactionBlocking {
            journal().recover(it, a.characterId, a.accountId, f.repository, emptySet())
        }
        assertEquals(3222, f.savedPosition(a)[0])
    }

    @Test fun `new login generation refuses an uncommitted joint save for both members`() = Fixture(directory).use { f ->
        val a = player(f, "Fence A", 100); val b = player(f, "Fence B", 200)
        f.saveDirect(a); f.saveDirect(b)
        val group = checkNotNull(f.saver.queueAtomic(a, b))
        journal().persistAtomic(group)
        f.database.withTransactionBlocking {
            val current = SaveRevisionStore.lock(it, b.characterId)
            SaveRevisionStore.update(it, b.characterId, current.copy(generation = java.util.UUID.randomUUID().toString()))
        }
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking { journal().saveAtomic(it, group) }
        }
        assertNull(group.succeeded)
    }

    @Test fun `damaged joint journal blocks recovery without changing either character`() = Fixture(directory).use { f ->
        val a = player(f, "Damage A", 100); val b = player(f, "Damage B", 200)
        f.saveDirect(a); f.saveDirect(b)
        val group = checkNotNull(f.saver.queueAtomic(a, b))
        journal().persistAtomic(group)
        val path = directory.resolve("journal/_atomic/${group.id}.json")
        Files.writeString(path, Files.readString(path).replace("\"sha256\":\"", "\"sha256\":\"bad"))
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking { journal().recover(it, a.characterId, a.accountId, f.repository, emptySet()) }
        }
        assertEquals(3100, f.savedPosition(a)[0])
    }

    private fun player(f: Fixture, name: String, coins: Int) = f.newCharacter(name, CoordGrid(3100, 3200)).apply {
        inv = invMap.getOrPut("inv.inv")
        inv[0] = InvObj("obj.coins", coins)
    }

    @Test fun `shutdown flushes a complete joint save for both inventories`() = Fixture(directory).use { f ->
        val inventory = CharacterInventoryPipeline(CharacterInventoryApplier())
        val saver = f.newSaver(setOf(inventory))
        val a = player(f, "Shutdown A", 50); val b = player(f, "Shutdown B", 250)
        val group = checkNotNull(saver.queueAtomic(a, b))
        runBlocking { saver.shutdown() }
        assertEquals(true, group.succeeded)
        assertEquals(50, f.reload(a, setOf(inventory)).invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(250, f.reload(b, setOf(inventory)).invMap.getOrPut("inv.inv")[0]?.count)
    }

    @Test fun `simultaneous recovery locks both characters and applies the exchange only once`() = Fixture(directory).use { f ->
        val a = player(f, "Concurrent A", 100); val b = player(f, "Concurrent B", 200)
        f.saveDirect(a); f.saveDirect(b)
        a.coords = CoordGrid(3222, 3218); b.coords = CoordGrid(3223, 3218)
        val group = checkNotNull(f.saver.queueAtomic(a, b)); journal().persistAtomic(group)
        f.database.withTransactionBlocking { f.repository.clearOnlinePresenceClaimedOnWorld(it, 1) }
        val start = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(a, b).map { player -> pool.submit<Unit> {
                start.await()
                f.database.withTransactionBlocking {
                    journal().recover(it, player.characterId, player.accountId, f.repository, emptySet())
                }
            } }
            start.countDown()
            for (future in futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(3222, f.savedPosition(a)[0]); assertEquals(3223, f.savedPosition(b)[0])
        } finally { pool.shutdownNow() }
    }

    @Test fun `a missing recovery codec refuses admission and clears both barriers`() = Fixture(directory).use { f ->
        val missing = object : CharacterDataStage.Pipeline {
            override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
            override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {}
        }
        val a = player(f, "Codec A", 100); val b = player(f, "Codec B", 200)
        assertNull(f.newSaver(setOf(missing)).queueAtomic(a, b))
        assertNull(a.pendingAccountCommit); assertNull(b.pendingAccountCommit)
        assertTrue(a.canProcess); assertEquals(100, a.inv[0]?.count)
    }

    @Test fun `abrupt process termination after journal publication recovers both inventories`() = Fixture(directory).use { f ->
        val inventory = CharacterInventoryPipeline(CharacterInventoryApplier())
        val pipelines = setOf<CharacterDataStage.Pipeline>(inventory)
        val a = player(f, "Halt A", 100); val b = player(f, "Halt B", 200)
        f.saveDirect(a); f.saveDirect(b)
        a.inv[0] = InvObj("obj.coins", 10); b.inv[0] = InvObj("obj.coins", 290)
        val group = checkNotNull(f.newSaver(pipelines).queueAtomic(a, b))
        val snapshot = directory.resolve("pair-snapshot.json")
        mapper.writeValue(snapshot.toFile(), group.requests.map { it.recoverySnapshot })
        val arguments = directory.resolve("pair-java.args")
        fun quote(value: String) = "\"${value.replace('\\', '/')}\""
        Files.writeString(arguments, listOf("-cp", quote(System.getProperty("account.test.classpath")),
            JournalCrashProbe::class.java.name, quote(directory.resolve("journal").toString()),
            quote(snapshot.toString()), "pair").joinToString("\n"))
        val log = directory.resolve("pair-crash.log")
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "@$arguments").redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(73, process.exitValue(), Files.readString(log))
        } finally { if (process.isAlive) process.destroyForcibly() }
        f.database.withTransactionBlocking {
            f.repository.clearOnlinePresenceClaimedOnWorld(it, 1)
            journal().recover(it, a.characterId, a.accountId, f.repository, pipelines)
        }
        assertEquals(10, f.reload(a, pipelines).invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(290, f.reload(b, pipelines).invMap.getOrPut("inv.inv")[0]?.count)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
