package org.rsmod.content.other.npccombatanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.TypedParamType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onGameStartup
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcCombatAnimsScript : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { applyAll() }
    }

    private fun applyAll() {
        val generated = NpcCombatAnimsFile.loadResource(NpcCombatAnimsFile.GENERATED_RESOURCE)
        val overrides = NpcCombatAnimsFile.loadResource(NpcCombatAnimsFile.OVERRIDES_RESOURCE)
        val applied = apply(generated.npc, force = false)
        val forced = apply(overrides.npc, force = true)
        logger.debug { "Applied combat animations to $applied npcs ($forced overrides)." }
    }

    private fun apply(entries: List<NpcCombatAnimEntry>, force: Boolean): Int {
        var applied = 0
        for (entry in entries) {
            val id = entry.id.asRSCM(RSCMType.NPC)
            val type = ServerCacheManager.getNpc(id)
            if (type == null) {
                logger.warn { "Npc not in cache, skipping: ${entry.id}" }
                continue
            }
            if (type.apply(entry, force)) {
                applied++
            }
        }
        return applied
    }

    private fun NpcServerType.apply(entry: NpcCombatAnimEntry, force: Boolean): Boolean {
        val values = mutableMapOf<Int, Any>()
        fun put(param: TypedParamType<*>, value: Int?) {
            if (value == null) {
                return
            }
            if (!force && paramMap?.contains(param) == true) {
                return
            }
            values[param.id] = value
        }
        put(params.attack_anim, entry.attackAnim?.asRSCM(RSCMType.SEQ))
        put(params.defend_anim, entry.defendAnim?.asRSCM(RSCMType.SEQ))
        put(params.death_anim, entry.deathAnim?.asRSCM(RSCMType.SEQ))
        put(params.npc_attack_type, entry.attackType?.asRSCM(RSCMType.CATEGORY))
        put(params.attack_sound, entry.attackSound)
        put(params.defend_sound, entry.defendSound)
        put(params.death_sound, entry.deathSound)
        put(params.proj_travel, entry.projTravel?.asRSCM(RSCMType.SPOTANIM))
        put(params.proj_type, entry.projType?.asRSCM(RSCMType.PROJANIM))

        var changed = false
        if (values.isNotEmpty()) {
            paramMap = ParamMap(primitiveMap = paramMap?.primitiveMap.orEmpty() + values)
            changed = true
        }
        val range = entry.attackRange
        if (range != null && (force || attackRange < range)) {
            attackRange = range
            if (maxRange < range) {
                maxRange = range
            }
            changed = true
        }
        return changed
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
