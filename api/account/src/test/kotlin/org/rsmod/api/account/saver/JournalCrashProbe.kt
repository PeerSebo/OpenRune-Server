package org.rsmod.api.account.saver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.openrune.ServerCacheManager
import java.nio.file.Path
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.game.entity.Player

object JournalCrashProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(241).close()
        val mapper = jacksonObjectMapper()

        if (args.getOrNull(2) == "pair") {
            val snapshots = mapper.readTree(Path.of(args[1]).toFile())
            val requests = snapshots.map { data ->
                @Suppress("UNCHECKED_CAST")
                val snapshot = mapper.convertValue(data, Map::class.java) as Map<String, Any?>
                AccountSaveRequest(data.required("accountId").intValue(), data.required("characterId").intValue(), Player(), {}).apply {
                    sequence = data.required("sequence").longValue()
                    saveGeneration = data.required("generation").textValue()
                    recoverySnapshot = snapshot
                }
            }
            AccountSaveJournal(mapper, Path.of(args[0])).persistAtomic(AtomicAccountSave(requests, emptyMap()))
            Runtime.getRuntime().halt(73)
        }

        @Suppress("UNCHECKED_CAST")
        val snapshot = mapper.readValue(Path.of(args[1]).toFile(), Map::class.java) as Map<String, Any?>
        val request = AccountSaveRequest((snapshot.getValue("accountId") as Number).toInt(),
            (snapshot.getValue("characterId") as Number).toInt(), Player(), {})
        request.sequence = (snapshot.getValue("sequence") as Number).toLong()
        request.saveGeneration = snapshot.getValue("generation") as String
        request.recoverySnapshot = snapshot
        AccountSaveJournal(mapper, Path.of(args[0])).persist(request)
        Runtime.getRuntime().halt(73)
    }
}
