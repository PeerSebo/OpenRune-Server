package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.PlayerQueueList
import org.rsmod.game.queue.QueueCategory

public class PlayerQueueProcessor
internal constructor(
    private val eventBus: EventBus,
    private val launchQueue: (Player, PlayerQueueEvents.Protected<*>) -> Unit,
) {
    @Inject
    public constructor(eventBus: EventBus, protectedAccess: ProtectedAccessLauncher) :
        this(eventBus, { player, event ->
            protectedAccess.launch(player) { eventBus.publish(this, event) }
        })

    public fun process(player: Player) {
        // Incoming hits stop weak actions and dialogs without replacing combat or movement.
        if (player.queueList.strongQueues > 0 && player.hasStrongQueueDue()) {
            player.ifClose(eventBus)
        }
        player.publishExpiredQueues()
        player.publishExpiredWeakQueues()
    }

    private fun Player.hasStrongQueueDue(): Boolean =
        queueList.anyDue(QueueCategory.Strong, currentMapClock)

    private fun Player.publishExpiredQueues() {
        while (queueList.isNotEmpty) {
            var processedNone = true

            val iterator = queueList.iterator() ?: break
            while (iterator.hasNext()) {
                val queue = iterator.next()

                if (queue.processedCycle != currentMapClock) {
                    queue.processedCycle = currentMapClock
                    queue.remainingCycles--
                }

                val accelerate = pendingLogout && queue.category == QueueCategory.LongAccelerate.id
                if (accelerate) {
                    queue.remainingCycles = 0
                }

                if (queue.remainingCycles > 0) {
                    continue
                }

                if (queue.shouldCloseModals()) {
                    ifClose(eventBus)
                }

                if (canLaunchQueue(queue)) {
                    processedNone = false
                    iterator.remove()
                    publish(queue)
                }
            }
            iterator.cleanUp()

            if (processedNone || queueList.size == 1) {
                break
            }
        }
    }

    private fun PlayerQueueList.Queue.shouldCloseModals(): Boolean =
        closeModals && (category == QueueCategory.Strong.id || category == QueueCategory.Soft.id)

    private fun Player.canLaunchQueue(queue: PlayerQueueList.Queue): Boolean =
        when (queue.category) {
            QueueCategory.Soft.id -> true
            // A suspended script must finish before another protected queue launches.
            QueueCategory.Strong.id -> !isModalButtonProtected
            else -> !isAccessProtected
        }

    private fun Player.publish(queue: PlayerQueueList.Queue) {
        if (queue.category == QueueCategory.Soft.id) {
            val event = PlayerQueueEvents.Soft(this, queue.args, queue.id)
            eventBus.publish(event)
            return
        }
        publishProtected(queue)
    }

    private fun Player.publishProtected(queue: PlayerQueueList.Queue) {
        val event = PlayerQueueEvents.Protected(queue.args, queue.id)
        launchQueue(this, event)
    }

    private fun Player.publishExpiredWeakQueues() {
        while (weakQueueList.isNotEmpty) {
            var processedNone = true

            val iterator = weakQueueList.iterator() ?: break
            while (iterator.hasNext()) {
                val queue = iterator.next()

                if (queue.processedCycle != currentMapClock) {
                    queue.processedCycle = currentMapClock
                    queue.remainingCycles--
                }

                if (queue.remainingCycles > 0) {
                    continue
                }

                if (!isAccessProtected) {
                    processedNone = false
                    iterator.remove()
                    publishProtected(queue)
                }
            }
            iterator.cleanUp()

            // Re-sweep whenever a handler was published: the second pass gives a queue added by
            // that handler its add-tick decrement (the documented n-1 semantics production
            // loops compensate for). The old `|| queueList.size == 1` clause inspected the
            // NORMAL queue list, so an unrelated pending queue (e.g. an in-flight "queue.hit")
            // skipped the re-sweep and ran production loops one tick slow; a size check on the
            // weak list would be just as wrong, since a lone self-requeueing loop is the
            // common case that NEEDS the re-sweep.
            if (processedNone) {
                break
            }
        }
    }
}
