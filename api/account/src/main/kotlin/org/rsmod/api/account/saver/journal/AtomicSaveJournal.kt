package org.rsmod.api.account.saver.journal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.rsmod.api.account.saver.AtomicAccountSave
import org.rsmod.api.db.DatabaseConnection

internal class AtomicSaveJournal(private val mapper: ObjectMapper, private val directory: Path) {
    private fun payload(group: AtomicAccountSave): String = mapper.writeValueAsString(mapOf(
        "version" to 2, "id" to group.id, "audit" to group.audit,
        "snapshots" to group.requests.map { checkNotNull(it.recoverySnapshot) },
    ))

    fun persist(group: AtomicAccountSave) {
        val payload = payload(group)
        val bytes = mapper.writeValueAsBytes(mapOf("version" to 1, "sha256" to digest(payload), "payload" to payload))
        Files.createDirectories(directory)
        val destination = directory.resolve("${group.id}.json")
        if (Files.exists(destination)) {
            check(Files.readAllBytes(destination).contentEquals(bytes)) { "Joint journal identity collision" }
            return
        }
        val temporary = Files.createTempFile(directory, ".joint-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temporary, destination, ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }

    fun save(connection: DatabaseConnection, group: AtomicAccountSave) {
        val payload = payload(group)
        if (hasReceipt(connection, group.id, digest(payload))) return
        val requests = group.requests.sortedBy { it.characterId }
        val revisions = requests.map { SaveRevisionStore.lock(connection, it.characterId) }
        if (hasReceipt(connection, group.id, digest(payload))) return
        for ((request, revision) in requests.zip(revisions)) {
            check(revision.generation == request.saveGeneration && revision.sequence < request.sequence) {
                "Joint checkpoint no longer owns character ${request.characterId}"
            }
        }
        for (request in requests) {
            for (segment in checkNotNull(request.preparedSegments)) segment.save(connection, request.characterId)
            SaveRevisionStore.update(connection, request.characterId,
                SaveRevision(checkNotNull(request.saveGeneration), request.sequence))
        }
        receipt(connection, group.id, payload)
    }

    fun recover(
        connection: DatabaseConnection,
        characterId: Int,
        accountId: Int,
        repository: CharacterAccountRepository,
        pipelines: Set<CharacterDataStage.Pipeline>,
    ) {
        if (!Files.isDirectory(directory)) return
        Files.newDirectoryStream(directory, "*.json").use { paths ->
            for (path in paths) {
                val envelope = mapper.readTree(Files.readAllBytes(path))
                check(envelope.required("version").intValue() == 1) { "Unsupported joint journal" }
                val payload = checkNotNull(envelope.required("payload").textValue())
                check(digest(payload) == envelope.required("sha256").textValue()) { "Damaged joint journal: $path" }
                val data = mapper.readTree(payload)
                check(data.required("version").intValue() == 2) { "Unsupported joint checkpoint" }
                val id = UUID.fromString(data.required("id").textValue()).toString()
                check(path.fileName.toString() == "$id.json") { "Invalid joint journal identity" }
                val snapshots = data.required("snapshots")
                check(snapshots.isArray && snapshots.size() == 2)
                val members = snapshots.sortedBy { it.required("characterId").intValue() }
                val ids = members.map { it.required("characterId").intValue() }
                check(ids.distinct().size == 2 && ids.all { it > 0 })
                val own = members.singleOrNull { it.required("characterId").intValue() == characterId } ?: continue
                check(own.required("accountId").intValue() == accountId) { "Joint journal account mismatch" }
                if (hasReceipt(connection, id, digest(payload))) continue
                val revisions = ids.map { SaveRevisionStore.lock(connection, it) }
                if (hasReceipt(connection, id, digest(payload))) continue
                val prepared = members.map { snapshot -> decodeSaveSnapshot(snapshot, repository, pipelines) }
                for ((snapshot, current) in members.zip(revisions)) {
                    val cid = snapshot.required("characterId").intValue()
                    check(!repository.hasActiveSession(connection, cid, 120L)) { "Active session owns joint checkpoint" }
                    check(current.generation == snapshot.required("generation").textValue() &&
                        current.sequence < snapshot.required("sequence").longValue()) {
                        "Joint checkpoint revision mismatch for character $cid"
                    }
                }
                for ((snapshot, segments) in members.zip(prepared)) {
                    val cid = snapshot.required("characterId").intValue()
                    for (segment in segments) segment.save(connection, cid)
                    SaveRevisionStore.update(connection, cid, SaveRevision(
                        UUID.fromString(snapshot.required("generation").textValue()).toString(),
                        snapshot.required("sequence").longValue()))
                }
                receipt(connection, id, payload)
            }
        }
    }

    fun committed(group: AtomicAccountSave) { Files.deleteIfExists(directory.resolve("${group.id}.json")) }

    private fun hasReceipt(connection: DatabaseConnection, id: String, expected: String): Boolean =
        connection.prepareStatement("SELECT payload_sha256 FROM public.character_atomic_saves WHERE transaction_id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { row ->
                if (!row.next()) false else {
                    check(row.getString(1) == expected) { "Joint transaction identity collision" }
                    true
                }
            }
        }

    private fun receipt(connection: DatabaseConnection, id: String, payload: String) {
        connection.prepareStatement("INSERT INTO public.character_atomic_saves(transaction_id, payload_sha256, payload) VALUES (?, ?, ?)").use {
            it.setString(1, id); it.setString(2, digest(payload)); it.setString(3, payload)
            check(it.executeUpdate() == 1)
        }
    }

    private fun digest(payload: String): String = MessageDigest.getInstance("SHA-256")
        .digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun decodeSaveSnapshot(
    data: JsonNode,
    repository: CharacterAccountRepository,
    pipelines: Set<CharacterDataStage.Pipeline>,
): List<CharacterDataStage.PreparedSave> {
    check(data.required("version").intValue() == 1) { "Unsupported checkpoint schema" }
    check(data.required("sequence").longValue() > 0)
    check(data.required("unrecoverableSegments").intValue() == 0) { "Incomplete save checkpoint" }
    val segments = data.required("segments")
    check(segments.isArray && segments.all { it.isObject && it.size() == 1 })
    val keyed = segments.map { it.properties().single() }
    check(keyed.map { it.key }.distinct().size == keyed.size)
    val owners = pipelines.associateBy { checkNotNull(it.recoveryKey) { "Pipeline has no recovery codec" } }
    check(owners.size == pipelines.size && keyed.map { it.key }.toSet() == owners.keys + "character") {
        "Checkpoint pipeline set changed"
    }
    val values = keyed.associate { it.key to it.value }
    return listOf(repository.restoreSave(values.getValue("character"))) +
        owners.map { (key, pipeline) -> pipeline.restoreSave(values.getValue(key)) }
}
