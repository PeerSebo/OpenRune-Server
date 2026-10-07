package org.rsmod.api.account.saver

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Inject
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant
import java.util.UUID
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.api.parsers.json.Json

public class AccountSaveRecovery internal constructor(
    private val mapper: ObjectMapper,
    private val directory: Path,
) {
    @Inject public constructor(@Json mapper: ObjectMapper) :
        this(mapper, Path.of(".data", "account-save-recovery"))

    private val session = UUID.randomUUID().toString()
    private val pending = mutableMapOf<Int, MutableMap<Long, Path>>()

    internal fun unresolvedCount(): Long {
        if (!Files.isDirectory(directory)) return 0
        return Files.list(directory).use { files ->
            files.filter { it.fileName.toString().endsWith(".json") }.count()
        }
    }

    public fun requireSafeLogin(characterId: Int, revision: org.rsmod.api.account.saver.journal.SaveRevision) {
        if (!Files.isDirectory(directory)) return
        Files.newDirectoryStream(directory, "$characterId-*.json").use { paths ->
            for (path in paths) {
                val data = mapper.readTree(Files.readAllBytes(path))
                val generation = data.path("generation").textValue()
                check(generation != null && (generation != revision.generation ||
                    data.required("sequence").longValue() <= revision.sequence)) {
                    "Unresolved legacy save requires review before login: $path"
                }
            }
        }
    }

    internal fun capture(request: AccountSaveRequest): Map<String, Any?> {
        val segments = checkNotNull(request.preparedSegments)
        return mapOf(
            "version" to 1,
            "session" to session,
            "sequence" to request.sequence,
            "generation" to request.saveGeneration,
            "capturedAt" to Instant.now().toString(),
            "accountId" to request.accountId,
            "characterId" to request.characterId,
            "segments" to segments.map { it.recoveryData },
            "unrecoverableSegments" to segments.count { it.recoveryData.isEmpty() },
        )
    }

    internal fun preserve(request: AccountSaveRequest): Path {
        val existing = pending[request.characterId]?.get(request.sequence)
        if (existing != null) return existing
        Files.createDirectories(directory)
        val destination = directory.resolve("${request.characterId}-$session-${request.sequence}.json")
        val temporary = Files.createTempFile(directory, ".save-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val bytes = ByteBuffer.wrap(mapper.writeValueAsBytes(checkNotNull(request.recoverySnapshot)))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            Files.move(temporary, destination, ATOMIC_MOVE)
            pending.getOrPut(request.characterId) { mutableMapOf() }[request.sequence] = destination
            return destination
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    internal fun committed(characterId: Int, sequence: Long) {
        val saved = pending[characterId] ?: return
        val iterator = saved.iterator()
        while (iterator.hasNext()) {
            val (failedSequence, path) = iterator.next()
            if (failedSequence > sequence) continue
            Files.move(path, path.resolveSibling("${path.fileName}.resolved"), ATOMIC_MOVE)
            iterator.remove()
        }
        if (saved.isEmpty()) pending.remove(characterId)
    }
}
