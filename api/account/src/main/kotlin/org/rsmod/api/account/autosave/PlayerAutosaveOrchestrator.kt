package org.rsmod.api.account.autosave

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.InvScope
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.account.AccountManager
import org.rsmod.api.account.saver.request.AccountSaveResponse
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.attr.AttributeMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints

@Singleton
public class PlayerAutosaveOrchestrator
@Inject
constructor(
    private val accountManager: AccountManager,
) {
    private val logger = InlineLogger()

    private val queuedSave: MutableSet<Player> =
        ConcurrentHashMap.newKeySet()

    private val dirtyPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    private val retryAfterCycle = ConcurrentHashMap<Player, Int>()
    private val preparingPlayer = ThreadLocal<Player?>()

    @Volatile private var saveCycle: Int = 0

    private var periodicCounter: Int = PERIODIC_INTERVAL_CYCLES

    init {
        PlayerPersistenceHints.bind(this::onPersistenceRelevantChange)
        AttributeMap.persistenceMutationSink = { key ->
            onPersistentAttrMutated(key)
        }
    }

    /**
     * Run once per game tick after inventory updates (while [InvScope.Perm] inventories may still
     * report [org.rsmod.game.inv.Inventory.hasModifiedSlots]).
     */
    public fun processEndOfTick(players: Iterable<Player>) {
        saveCycle++
        val active = players.filter { it.canProcess && it.persistenceSaveEligible() }.toSet()
        dirtyPlayers.retainAll(active)
        retryAfterCycle.keys.retainAll(active)
        for (player in active) {
            if (player.invMap.values.any { it.type.scope == InvScope.Perm && it.hasModifiedSlots() }) {
                dirtyPlayers.add(player)
            }
        }
        periodicCounter--
        if (periodicCounter <= 0) {
            periodicCounter = PERIODIC_INTERVAL_CYCLES
            dirtyPlayers.addAll(active)
        }
        for (player in active) {
            if (player in dirtyPlayers && player !in queuedSave &&
                saveCycle >= retryAfterCycle.getOrDefault(player, 0)) requestBackgroundSave(player)
        }
    }

    private fun onPersistentAttrMutated(@Suppress("UNUSED_PARAMETER") key: AttributeKey<*>) {
        val player = PlayerPersistenceHints.activeOrNull() ?: return
        onPersistenceRelevantChange(player)
    }

    private fun onPersistenceRelevantChange(player: Player) {
        if (!player.persistenceSaveEligible() || preparingPlayer.get() === player) {
            return
        }
        dirtyPlayers.add(player)
    }

    private fun requestBackgroundSave(player: Player) {
        if (!player.persistenceSaveEligible()) {
            return
        }
        if (!queuedSave.add(player)) {
            return
        }
        dirtyPlayers.remove(player)
        retryAfterCycle.remove(player)
        preparingPlayer.set(player)
        try {
            accountManager.save(player) { response ->
                if (response is AccountSaveResponse.Failure) scheduleRetry(player)
                queuedSave.remove(player)
                when (response) {
                    is AccountSaveResponse.Success, is AccountSaveResponse.QueueFull -> {}
                    is AccountSaveResponse.ExcessiveRetries ->
                        logger.error { "Background autosave failed after retries for character ${player.characterId}" }
                    is AccountSaveResponse.RecoveryFailure ->
                        logger.error { "Background autosave and local recovery failed for character ${player.characterId}" }
                    is AccountSaveResponse.InternalShutdownError ->
                        logger.error { "Background autosave shutdown error for character ${player.characterId}" }
                    is AccountSaveResponse.PreparationFailure ->
                        logger.error { "Background autosave preparation failed for character ${player.characterId}" }
                }
            }
        } catch (failure: Exception) {
            scheduleRetry(player)
            queuedSave.remove(player)
            logger.error(failure) { "Could not queue background save for character ${player.characterId}" }
        } finally {
            preparingPlayer.remove()
        }
    }

    private fun scheduleRetry(player: Player) {
        retryAfterCycle[player] = saveCycle + FAILED_SAVE_RETRY_CYCLES
        dirtyPlayers.add(player)
    }

    private companion object {
        private const val PERIODIC_INTERVAL_CYCLES: Int = 500
        private const val FAILED_SAVE_RETRY_CYCLES: Int = 50
    }
}

private fun Player.persistenceSaveEligible(): Boolean =
    accountId > 0 && characterId > 0 && processedMapClock > 0
