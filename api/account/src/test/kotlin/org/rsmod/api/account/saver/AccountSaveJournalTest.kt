package org.rsmod.api.account.saver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.or2.sql.OpenRuneSql
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
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
import org.rsmod.api.account.character.appearance.CharacterAppearanceApplier
import org.rsmod.api.account.character.appearance.CharacterAppearancePipeline
import org.rsmod.api.account.character.inv.CharacterInventoryApplier
import org.rsmod.api.account.character.inv.CharacterInventoryPipeline
import org.rsmod.api.account.character.stats.CharacterStatApplier
import org.rsmod.api.account.character.stats.CharacterStatPipeline
import org.rsmod.api.account.character.withRecoveryData
import org.rsmod.api.account.loader.AccountLoaderService
import org.rsmod.api.account.loader.request.AccountLoadAuth
import org.rsmod.api.account.loader.request.AccountLoadRequest
import org.rsmod.api.account.loader.request.AccountLoadResponse
import org.rsmod.api.account.persistence.UnresolvedVarps
import org.rsmod.api.account.saver.AccountSavingShutdownTest.Fixture
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.journal.AccountSaveLease
import org.rsmod.api.account.saver.journal.SaveRevision
import org.rsmod.api.account.saver.journal.SaveRevisionStore
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("PlayerPersistenceHints")
class AccountSaveJournalTest {
    @TempDir lateinit var directory: Path
    private val mapper = jacksonObjectMapper()
    private fun journal() = AccountSaveJournal(mapper, directory.resolve("journal"))

    private fun request(player: Player) = AccountSaveRequest(player.accountId, player.characterId, player, {})

    private fun recover(f: Fixture, player: Player, pipelines: Set<CharacterDataStage.Pipeline> = emptySet()) =
        f.database.withTransactionBlocking {
            f.repository.clearOnlinePresenceClaimedOnWorld(it, 1)
            journal().recover(it, player.characterId, player.accountId, f.repository, pipelines)
        }

    private fun records(player: Player): List<Path> =
        Files.newDirectoryStream(directory.resolve("journal/${player.characterId}"), "*.json").use { it.toList() }

    @Test
    fun `unavailable saved varps survive login frozen save and journal recovery`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Unavailable Variables", CoordGrid(3100, 3200))
        f.saveDirect(player)
        val unknown = mapOf("varp.removed_feature_fixture" to 42, "2147483648" to 19)
        f.database.withTransactionBlocking { connection ->
            connection.prepareStatement(OpenRuneSql.text("game/character/character_varps_insert.sql")).use { statement ->
                for ((key, value) in unknown) {
                    statement.setInt(1, player.characterId)
                    statement.setString(2, key)
                    statement.setInt(3, value)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
        val loaded = f.reload(player)
        assertEquals(unknown, UnresolvedVarps.get(loaded))
        val known = "varp.consumables_stamina_remaining".asRSCM()
        loaded.vars.backing[known] = 137
        val frozen = f.repository.prepareSave(loaded, 1)
        loaded.vars.backing[known] = 0
        UnresolvedVarps.apply(loaded, UnresolvedVarps(emptyMap()))
        val checkpoint = mapper.valueToTree<JsonNode>(frozen.recoveryData.getValue("character"))
        val restored = f.repository.restoreSave(checkpoint)
        f.database.withTransactionBlocking { restored.save(it, player.characterId) }
        val replayed = f.reload(player)
        assertEquals(unknown, UnresolvedVarps.get(replayed))
        assertEquals(137, replayed.vars.backing[known])
        f.saveDirect(replayed)
        assertEquals(unknown, UnresolvedVarps.get(f.reload(player)))
    }

    @Test
    fun `unavailable varps remain unchanged when replay rolls back`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Variable Rollback", CoordGrid(3100, 3200))
        UnresolvedVarps.apply(player, UnresolvedVarps(mapOf("varp.removed_feature_fixture" to 42)))
        f.saveDirect(player)
        UnresolvedVarps.apply(player, UnresolvedVarps(mapOf("varp.removed_feature_fixture" to 99)))
        journal().persist(request(player).also(f.saver::queue))
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking {
                journal().recover(it, player.characterId, player.accountId, f.repository, emptySet())
                error("Simulated failure after replay writes")
            }
        }
        assertEquals(mapOf("varp.removed_feature_fixture" to 42), UnresolvedVarps.get(f.reload(player)))
        assertEquals(1, records(player).size)
        recover(f, player)
        assertEquals(mapOf("varp.removed_feature_fixture" to 99), UnresolvedVarps.get(f.reload(player)))
    }

    @Test
    fun `checkpoint survives abrupt child JVM halt without shutdown hooks`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Process Crash", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        val request = request(player).also(f.saver::queue)
        val snapshot = directory.resolve("snapshot.json")
        mapper.writeValue(snapshot.toFile(), request.recoverySnapshot)
        val arguments = directory.resolve("java.args")
        fun quote(value: String) = "\"${value.replace('\\', '/')}\""
        Files.writeString(arguments, listOf("-cp", quote(System.getProperty("account.test.classpath")),
            JournalCrashProbe::class.java.name, quote(directory.resolve("journal").toString()),
            quote(snapshot.toString())).joinToString("\n"))
        val log = directory.resolve("crash-probe.log")
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "@$arguments").redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Child did not finish")
            assertEquals(73, process.exitValue(), Files.readString(log))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        assertEquals(request.sequence, recover(f, player).sequence)
        assertEquals(listOf(3222, 3218, 10000), f.savedPosition(player))
    }

    @Test
    fun `loader recovers before applying metadata and rejects only the damaged character`() = Fixture(directory).use { f ->
        val bad = f.newCharacter("Blocked Login", CoordGrid(3100, 3200))
        val good = f.newCharacter("Recovered Login", CoordGrid(3101, 3201))
        for (player in listOf(bad, good)) {
            f.saveDirect(player)
            player.coords = CoordGrid(3222, 3218)
            journal().persist(request(player).also(f.saver::queue))
        }
        Files.writeString(records(bad).single(), "damaged")
        f.database.withTransactionBlocking { f.repository.clearOnlinePresenceClaimedOnWorld(it, 1) }
        val loader = AccountLoaderService(f.database, f.repository, emptySet(),
            ServerConfig("Journal test", 0, 240, "test", 1), journal(),
            AccountSaveRecovery(mapper, directory.resolve("recovery")))
        val responses = mutableListOf<AccountLoadResponse>()
        for (player in listOf(bad, good)) {
            loader.queue(AccountLoadRequest.StrictSearch(AccountLoadAuth.UnknownDevice, player.username, { responses += it }))
        }
        runBlocking { loader.run() }
        assertTrue(responses[0] is AccountLoadResponse.Err.Exception)
        val response = responses[1] as AccountLoadResponse.Ok.LoadAccount
        val loaded = Player().also { target -> response.transforms.forEach { it.apply(target) } }
        assertEquals(CoordGrid(3222, 3218), loaded.coords)
        assertFalse(AccountSaveLease.get(loaded).claimed)
        assertEquals(2L, AccountSaveLease.get(loaded).revision.sequence)
    }

    @Test
    fun `new account can claim its first authenticated session and save`() = Fixture(directory).use { f ->
        val loader = AccountLoaderService(f.database, f.repository, emptySet(),
            ServerConfig("Journal test", 0, 240, "test", 1), journal(),
            AccountSaveRecovery(mapper, directory.resolve("recovery")))
        val responses = mutableListOf<AccountLoadResponse>()
        loader.queue(AccountLoadRequest.SearchOrCreateWithPassword({ "fixture-only" },
            AccountLoadAuth.UnknownDevice, "Fresh Journal", { responses += it }))
        runBlocking { loader.run() }
        val response = responses.single() as AccountLoadResponse.Ok.NewAccount
        val player = Player().also { target -> response.transforms.forEach { it.apply(target) } }
        val unclaimed = AccountSaveLease.get(player)
        assertFalse(unclaimed.claimed)
        val claimed = f.database.withTransactionBlocking { SaveRevisionStore.claim(it, player) }
        assertNotEquals(unclaimed.revision.generation, claimed.revision.generation)
        AccountSaveLease.apply(player, claimed)
        player.coords = CoordGrid(3222, 3218)
        f.saver.queue(request(player))
        runBlocking { f.saver.shutdown() }
        assertEquals(listOf(3222, 3218, player.runEnergy), f.savedPosition(player))
    }

    @Test
    fun `fresh process reader restores frozen checkpoint before SQL and leaves character offline`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Journal Restore", CoordGrid(3100, 3200))
        f.saveDirect(player)
        val pipelines = setOf(CharacterInventoryPipeline(CharacterInventoryApplier()),
            CharacterStatPipeline(CharacterStatApplier()),
            CharacterAppearancePipeline(CharacterAppearanceApplier(), mapper))
        val saver = f.newSaver(pipelines)
        val key = AttributeKey<List<Int>>("journal_fixture")
        val values = mutableListOf(4, 5)
        player.attr[key] = values
        player.coords = CoordGrid(3222, 3218)
        player.invMap.getOrPut("inv.inv")[0] = InvObj("obj.coins", 1234)
        player.statMap.setBaseLevel("stat.attack", 30)
        player.statMap.setCurrentLevel("stat.attack", 20)
        player.appearance.setColour(0, 5)
        val request = request(player)
        saver.queue(request)
        journal().persist(request)
        player.coords = CoordGrid(3300, 3300)
        values.add(9)
        player.statMap.setCurrentLevel("stat.attack", 29)
        player.appearance.setColour(0, 1)
        player.invMap.getOrPut("inv.inv")[0] = InvObj("obj.coins", 1)

        val restored = recover(f, player, pipelines)
        assertEquals(request.sequence, restored.sequence)
        assertEquals(listOf(3222, 3218, 10000), f.savedPosition(player))
        assertFalse(f.savedOnline(player))
        val loaded = f.reload(player, pipelines)
        assertEquals(1234, loaded.invMap.getOrPut("inv.inv")[0]?.count)
        assertEquals(listOf(4, 5), loaded.attr.toPersistentMap()["journal_fixture"])
        assertEquals(20, loaded.statMap.getCurrentLevel("stat.attack").toInt())
        assertEquals(5, loaded.appearance.coloursSnapshot()[0].toInt())
        assertEquals(restored, recover(f, player, pipelines))
        assertEquals(1, records(player).size)
    }

    @Test
    fun `commit without file cleanup is detected without replaying the transaction`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Committed Journal", CoordGrid(3222, 3218))
        val request = request(player)
        f.saver.queue(request)
        journal().persist(request)
        f.database.withTransactionBlocking { connection ->
            request.preparedSegments!!.forEach { it.save(connection, player.characterId) }
            SaveRevisionStore.update(connection, player.characterId, SaveRevision(request.saveGeneration!!, request.sequence))
        }
        player.coords = CoordGrid(3300, 3301)
        f.saveDirect(player)
        recover(f, player)
        assertEquals(listOf(3300, 3301, 10000), f.savedPosition(player))
        journal().committed(player.characterId, SaveRevision(request.saveGeneration!!, request.sequence))
        assertTrue(records(player).isEmpty())
    }

    @Test
    fun `late cleanup cannot delete a newer sessions checkpoint`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Cleanup Race", CoordGrid(3100, 3200))
        val old = request(player).also(f.saver::queue)
        journal().persist(old)
        val nextGeneration = UUID.randomUUID().toString()
        val next = request(player).apply {
            sequence = 1
            saveGeneration = nextGeneration
            recoverySnapshot = old.recoverySnapshot!! + ("generation" to nextGeneration)
        }
        journal().persist(next)
        journal().committed(player.characterId, SaveRevision(old.saveGeneration!!, old.sequence))
        assertEquals(listOf("$nextGeneration-1.json"), records(player).map { it.fileName.toString() })
    }

    @Test
    fun `newer session fences old journal and old active writer`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Fenced Writer", CoordGrid(3100, 3200))
        f.saveDirect(player)
        val responses = mutableListOf<AccountSaveResponse>()
        val request = AccountSaveRequest(player.accountId, player.characterId, player, { responses += it })
        f.saver.queue(request)
        journal().persist(request)
        val next = SaveRevision(UUID.randomUUID().toString(), 0)
        f.database.withTransactionBlocking { SaveRevisionStore.update(it, player.characterId, next) }
        assertEquals(next, recover(f, player))
        assertThrows(IllegalStateException::class.java) { runBlocking { f.saver.shutdown() } }
        assertTrue(responses.single() is AccountSaveResponse.Failure)
        assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
        assertEquals(next, recover(f, player))
    }

    @Test
    fun `a duplicate login cannot replay a live sessions in flight save`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Active Writer", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        journal().persist(request(player).also(f.saver::queue))
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking {
                journal().recover(it, player.characterId, player.accountId, f.repository, emptySet())
            }
        }
        assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
        recover(f, player)
        assertEquals(listOf(3222, 3218, 10000), f.savedPosition(player))
    }

    @Test
    fun `login claim rejects a snapshot changed since account loading`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Login Fence", CoordGrid(3100, 3200))
        val loaded = f.database.withTransactionBlocking { SaveRevisionStore.lock(it, player.characterId) }
        AccountSaveLease.apply(player, AccountSaveLease(loaded))
        f.database.withTransactionBlocking { SaveRevisionStore.update(it, player.characterId, loaded.copy(sequence = 7)) }
        assertThrows(IllegalStateException::class.java) {
            f.database.withTransactionBlocking { SaveRevisionStore.claim(it, player) }
        }
        assertEquals(7L, f.database.withTransactionBlocking { SaveRevisionStore.lock(it, player.characterId).sequence })
    }

    @Test
    fun `highest complete checkpoint wins over an older queued retry`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Latest Journal", CoordGrid(3100, 3200))
        val first = request(player).also(f.saver::queue)
        journal().persist(first)
        player.coords = CoordGrid(3222, 3218)
        val second = request(player).also(f.saver::queue)
        journal().persist(second)
        assertEquals(second.sequence, recover(f, player).sequence)
        runBlocking { f.saver.shutdown() }
        assertEquals(listOf(3222, 3218, 10000), f.savedPosition(player))
        assertTrue(records(player).isEmpty())
    }

    @Test
    fun `corrupt or unsupported journal blocks only its character`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Corrupt Journal", CoordGrid(3100, 3200))
        val other = f.newCharacter("Unaffected Journal", CoordGrid(3111, 3222))
        f.saveDirect(player)
        val request = request(player).also(f.saver::queue)
        journal().persist(request)
        val file = records(player).single()
        val original = Files.readString(file)
        for (bad in listOf("{", original.replace("\"version\":1", "\"version\":99"),
            original.replace("3100", "3999"))) {
            Files.writeString(file, bad)
            assertThrows(Exception::class.java) { recover(f, player) }
            assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
            recover(f, other)
        }
    }

    @Test
    fun `failed replay rolls back every segment and keeps journal for a later restart`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Atomic Replay", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        val pipeline = ReplayPipeline(fail = true)
        val saver = f.newSaver(setOf(pipeline))
        val request = request(player).also(saver::queue)
        journal().persist(request)
        assertThrows(IllegalStateException::class.java) { recover(f, player, setOf(pipeline)) }
        assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
        assertEquals(0L, f.database.withTransactionBlocking { SaveRevisionStore.lock(it, player.characterId).sequence })
        assertEquals(request.sequence, recover(f, player, setOf(ReplayPipeline(false))).sequence)
        assertEquals(listOf(3222, 3218, 10000), f.savedPosition(player))
    }

    @Test
    fun `missing recovery codec does not allow a partial restore`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Unknown Codec", CoordGrid(3100, 3200))
        f.saveDirect(player)
        player.coords = CoordGrid(3222, 3218)
        val saver = f.newSaver(setOf(ReplayPipeline(false)))
        val request = request(player).also(saver::queue)
        journal().persist(request)
        assertThrows(IllegalStateException::class.java) { recover(f, player) }
        assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
    }

    @Test
    fun `journal storage failure prevents SQL writes and successful acknowledgement`() = Fixture(directory).use { f ->
        val player = f.newCharacter("Journal IO Failure", CoordGrid(3100, 3200))
        f.saveDirect(player)
        Files.writeString(directory.resolve("journal"), "unavailable")
        player.coords = CoordGrid(3222, 3218)
        val responses = mutableListOf<AccountSaveResponse>()
        f.saver.queue(AccountSaveRequest(player.accountId, player.characterId, player, { responses += it }))
        assertThrows(IllegalStateException::class.java) { runBlocking { f.saver.shutdown() } }
        assertTrue(responses.single() is AccountSaveResponse.Failure)
        assertEquals(listOf(3100, 3200, 10000), f.savedPosition(player))
    }

    @Test
    fun `legacy unversioned recovery requires review before login`() {
        val folder = Files.createDirectory(directory.resolve("recovery"))
        Files.writeString(folder.resolve("12-old-1.json"), """{"characterId":12,"sequence":1}""")
        val recovery = AccountSaveRecovery(mapper, folder)
        assertThrows(IllegalStateException::class.java) {
            recovery.requireSafeLogin(12, SaveRevision(UUID.randomUUID().toString(), 0))
        }
        recovery.requireSafeLogin(13, SaveRevision(UUID.randomUUID().toString(), 0))
    }

    private class ReplayPipeline(private val fail: Boolean) : CharacterDataStage.Pipeline {
        override val recoveryKey: String = "test"
        override fun append(connection: DatabaseConnection, metadata: CharacterMetadataList) {}
        override fun save(connection: DatabaseConnection, player: Player, characterId: Int) {}
        override fun prepareSave(player: Player): CharacterDataStage.PreparedSave =
            CharacterDataStage.PreparedSave { _, _ -> }.withRecoveryData("test", 1)
        override fun restoreSave(data: JsonNode): CharacterDataStage.PreparedSave =
            CharacterDataStage.PreparedSave { _, _ -> check(!fail) { "Simulated replay failure" } }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
