package org.rsmod.api.game.process

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.db.gateway.GameDbSynchronizer
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.GameProcess
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

public class MainGameProcess
@Inject
constructor(
    private val gameCycle: GameCycle,
    private val eventBus: EventBus,
    private val playerList: PlayerList,
    private val dbSync: GameDbSynchronizer,
    private val accountRegistry: AccountRegistry?,
) : GameProcess {
    public constructor(
        gameCycle: GameCycle,
        eventBus: EventBus,
        playerList: PlayerList,
        dbSync: GameDbSynchronizer,
    ) : this(gameCycle, eventBus, playerList, dbSync, null)

    override fun startup() {
        eventBus.publish(GameLifecycle.Startup)
    }

    override fun cycle() {
        gameCycle.tick()
    }

    override fun preShutdown() {
        eventBus.publish(GameLifecycle.PreShutdown)
        setShutdownFlags()
        fastForwardServices()
        fastForwardCycles()
        logRemainingPlayers()
    }

    override fun shutdown() {
        eventBus.publish(GameLifecycle.Shutdown)
    }

    private fun setShutdownFlags() {
        for (player in playerList) {
            player.pendingShutdown = true
        }
    }

    private fun fastForwardServices() {
        dbSync.blockingFastForwardShutdown()
    }

    private fun fastForwardCycles() {
        repeat(SHUTDOWN_MAX_SIMULATIONS) { cycle() }
    }

    private fun logRemainingPlayers() {
        val remaining = playerList.filterNot(Player::loggingOut)
        if (remaining.isNotEmpty()) {
            val names = remaining.take(250).joinToString { "'${it.displayName}'" }
            logger.error { "${remaining.size} players were unable to logout properly: $names" }
        }

        val pendingPreparations = accountRegistry?.pendingLogoutPreparations().orEmpty()
        if (pendingPreparations.isNotEmpty()) {
            logger.error { logoutPreparationFailureMessage(pendingPreparations) }
        }
    }

    private companion object {
        /**
         * The maximum number of [GameCycle.tick] functions that will be called in a tight,
         * non-delayed loop before the server is shutdown.
         *
         * This is a safeguard to ensure any pending actions in the server are handled appropriately
         * and that players are gracefully logged out before the server shuts down.
         */
        private const val SHUTDOWN_MAX_SIMULATIONS = 1024
        private val logger = InlineLogger()
    }
}

internal fun logoutPreparationFailureMessage(players: List<Player>): String {
    val names = players.take(250).joinToString { "'${it.displayName}'" }
    return "${players.size} players still have unsaved logout state because save preparation failed: $names"
}
