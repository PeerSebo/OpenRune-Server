package org.rsmod.api.registry.account

import java.util.concurrent.ConcurrentLinkedQueue
import org.rsmod.api.account.saver.request.AccountSaveCallback
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.game.entity.Player

internal class LogoutSaveQueue(
    private val requestSave: (Player, AccountSaveCallback) -> Unit,
    private val removePlayer: (Player) -> Unit,
) {
    private val responses = ConcurrentLinkedQueue<QueuedResponse>()
    private val retries = LinkedHashMap<Player, Long>()

    @Volatile private var cycle = 0L

    fun save(player: Player) {
        requestSave(player) { response ->
            responses.add(QueuedResponse(response, cycle + PREPARATION_RETRY_DELAY))
        }
    }

    fun pendingPreparations(): List<Player> {
        val pending = retries.keys.toMutableSet()
        for ((response) in responses) {
            if (response.requiresRetry()) {
                pending.add(response.player)
            } else {
                pending.remove(response.player)
            }
        }
        return pending.toList()
    }

    fun process(count: Int) {
        cycle++
        var remaining = count
        while (remaining > 0) {
            val next = responses.poll() ?: break
            remaining--
            val (response, retryAt) = next
            val player = response.player
            if (response.requiresRetry()) {
                retries.putIfAbsent(player, retryAt)
                continue
            }

            retries.remove(player)
            removePlayer(player)
        }

        val iterator = retries.entries.iterator()
        while (remaining > 0 && iterator.hasNext()) {
            val (player, retryAt) = iterator.next()
            if (cycle < retryAt) continue
            remaining--
            iterator.remove()
            save(player)
        }
    }

    private data class QueuedResponse(val response: AccountSaveResponse, val retryAt: Long)

    private fun AccountSaveResponse.requiresRetry(): Boolean =
        this is AccountSaveResponse.Failure

    private companion object {
        private const val PREPARATION_RETRY_DELAY = 50
    }
}
