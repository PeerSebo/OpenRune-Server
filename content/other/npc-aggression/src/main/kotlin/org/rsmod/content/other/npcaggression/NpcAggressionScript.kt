package org.rsmod.content.other.npcaggression

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NpcAggressionScript : PluginScript() {
    override fun ScriptContext.startup() {
        val generated = NpcAggressionFile.loadResource(NpcAggressionFile.GENERATED_RESOURCE)
        val overrides = NpcAggressionFile.loadResource(NpcAggressionFile.OVERRIDES_RESOURCE)
        val applied = apply(generated.npc, force = false)
        val forced = apply(overrides.npc, force = true)
        logger.debug { "Applied aggression to $applied npcs ($forced overrides)." }

        // Map NPCs copied hunt mode before scripts ran; update those existing instances on spawn.
        onEvent<NpcStateEvents.Create> {
            val typeMode = npc.type.huntMode
            if (npc.huntMode == null && typeMode != null) {
                ServerCacheManager.getHunt(typeMode)?.let(npc::setHuntMode)
            }
        }
    }

    internal fun apply(entries: List<NpcAggressionEntry>, force: Boolean): Int {
        var applied = 0
        for (entry in entries) {
            val id = entry.id.asRSCM(RSCMType.NPC)
            val type = ServerCacheManager.getNpc(id)
            if (type == null) {
                logger.warn { "Npc not in cache, skipping: ${entry.id}" }
                continue
            }
            if (!entry.aggressive) {
                type.huntMode = null
                applied++
                continue
            }
            // A None default is an explicit encounter-controlled actor (e.g. Hespori and
            // Kraken). Giving it a generic hunt would compete with its boss script.
            if (!force && (type.huntMode != null || type.defaultMode == NpcMode.None)) {
                continue
            }
            if (!type.hasOp(ATTACK_OP)) {
                continue
            }
            type.huntMode = if (type.attacksFromRange()) HUNT_CONSTANT_RANGED else HUNT_CONSTANT_MELEE
            applied++
        }
        return applied
    }

    private fun NpcServerType.attacksFromRange(): Boolean {
        val attackType = paramOrNull(params.npc_attack_type)
        if (attackType != null) {
            return RANGED_ATTACK_TYPES.any { attackType.isType(it) }
        }
        return paramOrNull(params.proj_travel) != null
    }

    private companion object {
        private val logger = InlineLogger()

        private const val ATTACK_OP = 2

        /** Hunt modes from `.data/gamevals/stalk.rscm`; there is no RSCM prefix for them. */
        private const val HUNT_CONSTANT_MELEE = 1
        private const val HUNT_CONSTANT_RANGED = 2

        private val RANGED_ATTACK_TYPES =
            listOf(
                "category.attacktype_light",
                "category.attacktype_standard",
                "category.attacktype_heavy",
                "category.attacktype_magic",
            )
    }
}
