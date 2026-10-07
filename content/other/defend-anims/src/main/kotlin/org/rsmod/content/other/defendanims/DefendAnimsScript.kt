package org.rsmod.content.other.defendanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DefendAnimsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { applyCorrections() }
    }

    private fun applyCorrections() {
        val sequenceIds = DefendAnims.allAnimations.associateWith { it.asRSCM(RSCMType.SEQ) }
        for ((seq, id) in sequenceIds) {
            checkNotNull(ServerCacheManager.getAnim(id)) { "Missing sequence in cache: $seq ($id)" }
        }

        var corrected = 0
        for (item in ServerCacheManager.getItems().values) {
            val seq = correctionFor(item) ?: continue
            item.paramMap = item.paramMap.withDefendAnim(sequenceIds.getValue(seq))
            corrected++
        }
        logger.debug { "Corrected the block animation of $corrected items." }
    }

    private fun correctionFor(item: ItemServerType): String? {
        if (item.isCert || item.isPlaceholder) {
            return null
        }
        val hasDefendAnim = item.paramMap?.contains(params.defend_anim) == true
        return DefendAnims.resolve(item.name, item.wearpos1, item.weaponCategory, hasDefendAnim)
    }

    // ParamMap.plus retains decoded values; rebuilding the map is required to expose the new key.
    private fun ParamMap?.withDefendAnim(sequenceId: Int): ParamMap {
        val primitives = this?.primitiveMap.orEmpty() + (params.defend_anim.id to sequenceId)
        return ParamMap(primitiveMap = primitives)
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
