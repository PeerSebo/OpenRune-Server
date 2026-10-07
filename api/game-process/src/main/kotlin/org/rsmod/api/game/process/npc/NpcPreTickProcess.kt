package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntProcessor
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList

public class NpcPreTickProcess
@Inject
constructor(
    private val npcList: NpcList,
    private val mapClock: MapClock,
    private val registry: NpcRegistry,
    private val playerHunt: NpcPlayerHuntProcessor,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process() {
        for (npc in npcList) {
            npc.tryOrDespawn {
                updateMapClock()
                huntProcess()
            }
        }
    }

    /** Refresh the NPC map clock before input-side callbacks can schedule work against it. */
    private fun Npc.updateMapClock() {
        currentMapClock = mapClock.cycle
    }

    private fun Npc.huntProcess() {
        playerHunt.process(this)
    }

    private inline fun Npc.tryOrDespawn(block: Npc.() -> Unit) =
        try {
            block(this)
        } catch (e: Exception) {
            registry.del(this)
            exceptionHandler.handle(e) { "Error processing pre-tick for npc: $this." }
        } catch (e: NotImplementedError) {
            registry.del(this)
            exceptionHandler.handle(e) { "Error processing pre-tick for npc: $this." }
        }
}
