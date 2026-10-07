package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.player.forceDisconnect
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.ShuffledPlayerList

public class PlayerInputProcess
@Inject
constructor(
    private val mapClock: MapClock,
    private val players: ShuffledPlayerList,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process() {
        for (player in players) {
            player.tryOrDisconnect {
                updateMapClock()
                assignPrevCoords()
                readClientInput()
            }
        }
    }

    private fun Player.updateMapClock() {
        currentMapClock = mapClock.cycle
    }

    private fun Player.assignPrevCoords() {
        previousCoords = coords
    }

    private fun Player.readClientInput() {
        client.read(this)
    }

    private inline fun Player.tryOrDisconnect(block: Player.() -> Unit) =
        try {
            block(this)
        } catch (e: Exception) {
            forceDisconnect()
            exceptionHandler.handle(e) { "Error processing input cycle for player: $this." }
        } catch (e: NotImplementedError) {
            forceDisconnect()
            exceptionHandler.handle(e) { "Error processing input cycle for player: $this." }
        }
}
