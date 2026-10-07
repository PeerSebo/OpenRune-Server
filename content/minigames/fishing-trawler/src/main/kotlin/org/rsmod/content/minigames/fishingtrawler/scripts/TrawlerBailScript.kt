package org.rsmod.content.minigames.fishingtrawler.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.minigames.fishingtrawler.TrawlerConstants
import org.rsmod.content.minigames.fishingtrawler.TrawlerManager
import org.rsmod.content.minigames.fishingtrawler.TrawlerPhase
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TrawlerBailScript
@Inject
constructor(private val manager: TrawlerManager) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(TrawlerConstants.BUCKET_EMPTY) { bailWith(it.slot) }
        onOpHeld1(TrawlerConstants.BUCKET_FULL) { empty(it.slot) }
    }

    private suspend fun ProtectedAccess.bailWith(slot: Int) {
        val voyage = manager.voyageForPlayer(player)
        if (voyage == null || voyage.phase != TrawlerPhase.SAILING || voyage.waterLevel <= 0) {
            mes("There's no water to bail here.")
            return
        }
        if (!inv[slot].isType(emptyBucket)) return
        // TODO: verify bailing animation; human_pickupfloor is a silent placeholder.
        anim("seq.human_pickupfloor")
        delay(1)
        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING || voyage.waterLevel <= 0 ||
            !inv[slot].isType(emptyBucket)) return
        if (invReplace(inv, slot, count = 1, replacement = fullBucket).failure) return
        voyage.waterLevel--
        mes("You fill the bucket with water.")
    }

    private suspend fun ProtectedAccess.empty(slot: Int) {
        val voyage = manager.voyageForPlayer(player)
        val playerId = player.uuid
        if (voyage == null || playerId == null || voyage.phase != TrawlerPhase.SAILING) {
            mes("There's nowhere to empty the bucket here.")
            return
        }
        if (!inv[slot].isType(fullBucket)) return
        anim("seq.human_pickupfloor")
        delay(1)
        if (manager.voyageForPlayer(player) !== voyage ||
            voyage.phase != TrawlerPhase.SAILING || !inv[slot].isType(fullBucket)) return
        if (invReplace(inv, slot, count = 1, replacement = emptyBucket).failure) return
        voyage.addContribution(playerId, TrawlerConstants.POINTS_BAIL)
        mes("You empty the bucket over the side.")
    }

    private companion object {
        private val emptyBucket by lazy {
            checkNotNull(ServerCacheManager.getItem(TrawlerConstants.BUCKET_EMPTY.asRSCM(RSCMType.OBJ)))
        }
        private val fullBucket by lazy {
            checkNotNull(ServerCacheManager.getItem(TrawlerConstants.BUCKET_FULL.asRSCM(RSCMType.OBJ)))
        }
    }
}
