package org.rsmod.api.farming.hook

import jakarta.inject.Inject
import org.rsmod.api.farming.FarmingAttributes
import org.rsmod.api.farming.FarmingBinManager
import org.rsmod.api.farming.FarmingConstants
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingSceneIndex
import org.rsmod.api.farming.FarmingVisiblePatches
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Refresh shared render slots after build-area changes and periodically for growth; login
 * resolves all persisted patches and bins first.
 */
internal class FarmingRefreshScript
@Inject
constructor(
    private val manager: FarmingManager,
    private val binManager: FarmingBinManager,
    private val scene: FarmingSceneIndex,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            manager.resolveAll(player)
            binManager.resolveAll(player)
            player.attr.remove(FarmingAttributes.VISIBLE)
        }
        onEvent<GameLifecycle.LateCycle> { process() }
    }

    private fun process() {
        for (player in playerList) {
            processPlayer(player)
        }
    }

    private fun processPlayer(player: Player) {
        val buildArea = player.buildArea
        if (buildArea == CoordGrid.NULL) {
            return
        }
        val cached = player.attr[FarmingAttributes.VISIBLE]
        if (cached == null || cached.buildAreaPacked != buildArea.packed) {
            val patches = scene.visiblePatches(buildArea)
            val bins = scene.visibleBins(buildArea)
            player.attr[FarmingAttributes.VISIBLE] =
                FarmingVisiblePatches(buildArea.packed, patches, bins)
            manager.refresh(player, patches)
            binManager.refresh(player, bins)
            return
        }
        if (cached.isEmpty) {
            return
        }
        val stagger = mapClock.cycle + player.slotId
        if (stagger % FarmingConstants.REFRESH_INTERVAL_TICKS != 0) {
            return
        }
        manager.refresh(player, cached.patches)
        binManager.refresh(player, cached.bins)
    }
}
