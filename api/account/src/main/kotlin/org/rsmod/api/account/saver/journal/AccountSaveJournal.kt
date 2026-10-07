package org.rsmod.api.account.saver.journal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.UUID
import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.parsers.json.Json

public class AccountSaveJournal internal constructor(
    private val mapper: ObjectMapper,
    private val directory: Path,
) {
    private val atomic = AtomicSaveJournal(mapper, directory.resolve("_atomic"))
    internal fun persistAtomic(group: org.rsmod.api.account.saver.AtomicAccountSave) = atomic.persist(group)
    internal fun saveAtomic(connection: DatabaseConnection, group: org.rsmod.api.account.saver.AtomicAccountSave) = atomic.save(connection, group)
    internal fun committedAtomic(group: org.rsmod.api.account.saver.AtomicAccountSave) = atomic.committed(group)

    @Inject public constructor(@Json mapper: ObjectMapper) :
        this(mapper, Path.of(".data", "account-save-journal"))

    internal fun persist(request: AccountSaveRequest) {
        val generation = checkNotNull(request.saveGeneration)
        val payload = mapper.writeValueAsString(checkNotNull(request.recoverySnapshot))
        val bytes = mapper.writeValueAsBytes(mapOf(
            "version" to 1, "sha256" to digest(payload), "payload" to payload,
        ))
        val folder = directory.resolve(request.characterId.toString())
        Files.createDirectories(folder)
        val destination = folder.resolve("$generation-${request.sequence}.json")
        if (Files.exists(destination)) {
            check(Files.readAllBytes(destination).contentEquals(bytes)) { "Journal identity collision: $destination" }
            return
        }
        val temporary = Files.createTempFile(folder, ".save-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, destination, ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    public fun recover(
        connection: DatabaseConnection,
        characterId: Int,
        accountId: Int,
        repository: CharacterAccountRepository,
        pipelines: Set<CharacterDataStage.Pipeline>,
    ): SaveRevision {
        atomic.recover(connection, characterId, accountId, repository, pipelines)
        val current = SaveRevisionStore.lock(connection, characterId)
        val records = records(characterId)
        val pending = records.filter { it.generation == current.generation && it.sequence > current.sequence }
        val latest = pending.maxByOrNull { it.sequence } ?: return current
        check(!repository.hasActiveSession(connection, characterId, SESSION_STALE_SECONDS)) {
            "An active session still owns this pending checkpoint"
        }
        check(latest.data.required("accountId").intValue() == accountId) { "Journal account mismatch" }
        val restored = decodeSaveSnapshot(latest.data, repository, pipelines)
        for (segment in restored) segment.save(connection, characterId)
        val revision = SaveRevision(latest.generation, latest.sequence)
        SaveRevisionStore.update(connection, characterId, revision)
        // Leave files until a committed save: the caller's surrounding transaction can still roll back.
        return revision
    }

    internal fun committed(characterId: Int, revision: SaveRevision) {
        for (record in records(characterId)) {
            if (record.generation == revision.generation && record.sequence <= revision.sequence) {
                Files.deleteIfExists(record.path)
            }
        }
    }

    private fun records(characterId: Int): List<Record> {
        val folder = directory.resolve(characterId.toString())
        if (!Files.isDirectory(folder)) return emptyList()
        return Files.newDirectoryStream(folder, "*.json").use { paths ->
            paths.map { path ->
                val envelope = mapper.readTree(Files.readAllBytes(path))
                check(envelope.required("version").intValue() == 1) { "Unsupported journal schema: $path" }
                val payload = checkNotNull(envelope.required("payload").textValue())
                check(digest(payload) == envelope.required("sha256").textValue()) { "Damaged save journal: $path" }
                val data = mapper.readTree(payload)
                check(data.required("version").intValue() == 1) { "Unsupported snapshot schema: $path" }
                check(data.required("characterId").intValue() == characterId) { "Journal character mismatch: $path" }
                val generation = UUID.fromString(data.required("generation").textValue()).toString()
                val sequence = data.required("sequence").longValue()
                check(sequence > 0 && path.fileName.toString() == "$generation-$sequence.json") {
                    "Invalid journal identity: $path"
                }
                Record(path, generation, sequence, data)
            }
        }
    }

    private fun digest(payload: String): String =
        MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SESSION_STALE_SECONDS = 120L
    }

    private data class Record(val path: Path, val generation: String, val sequence: Long, val data: JsonNode)
}
