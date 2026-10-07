package org.rsmod.api.account.loader

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.account.loader.request.AccountLoadAuth
import org.rsmod.api.account.loader.request.AccountLoadRequest
import org.rsmod.api.account.loader.request.AccountLoadResponse
import org.rsmod.api.account.saver.AccountSaveRecovery
import org.rsmod.api.account.saver.journal.AccountSaveJournal
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.server.config.ServerConfig
import sun.misc.Unsafe

class AccountLoaderAdmissionTest {
    @Test fun `concurrent submissions obey the limit and shutdown rejects every accepted request`() {
        val loader = loader()
        val replies = AtomicInteger()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val results = (1..2500).map { id -> executor.submit<Boolean> {
                loader.queue(AccountLoadRequest.StrictSearch(AccountLoadAuth.UnknownDevice, "test-$id") {
                    assertEquals(AccountLoadResponse.Err.ShutdownInProgress, it)
                    replies.incrementAndGet()
                })
            } }
            assertEquals(2000, results.count { it.get(5, TimeUnit.SECONDS) })
            runBlocking { loader.shutdown() }
            assertEquals(2000, replies.get())
            assertFalse(loader.queue(AccountLoadRequest.StrictSearch(AccountLoadAuth.UnknownDevice, "late") {}))
        } finally { executor.shutdownNow() }
    }

    @Test fun `one broken callback cannot strand the rest of a shutdown drain`() {
        val loader = loader()
        val replies = AtomicInteger()
        loader.queue(AccountLoadRequest.StrictSearch(AccountLoadAuth.UnknownDevice, "broken") { error("callback") })
        loader.queue(AccountLoadRequest.StrictSearch(AccountLoadAuth.UnknownDevice, "next") { replies.incrementAndGet() })
        runBlocking { loader.shutdown() }
        assertEquals(1, replies.get())
    }

    private fun loader() = AccountLoaderService(unused<GameDatabase>(), unused<CharacterAccountRepository>(),
        emptySet(), unused<ServerConfig>(), unused<AccountSaveJournal>(), unused<AccountSaveRecovery>())

    private inline fun <reified T> unused(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
    }
}
