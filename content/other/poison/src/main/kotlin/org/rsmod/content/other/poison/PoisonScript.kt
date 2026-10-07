package org.rsmod.content.other.poison

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.impl.NpcPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PoisonScript
@Inject
constructor(private val npcList: NpcList, private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { applyPoisonousNpcs() }
        onEvent<PlayerHitEvents.Impact> { onPlayerHit(player, hit) }
        onEvent<NpcHitEvents.AnyImpact> { onNpcHit(npc, hit) }
    }

    private fun applyPoisonousNpcs() {
        val file = PoisonousNpcsFile.loadResource()
        var applied = 0
        for (entry in file.npc) {
            val type = ServerCacheManager.getNpc(entry.id.asRSCM(RSCMType.NPC))
            if (type == null) {
                logger.warn { "Npc not in cache, skipping: ${entry.id}" }
                continue
            }
            val severity = PlayerPoison.severityForInitialDamage(entry.damage)
            val values = mapOf(params.npc_poison_severity.id to severity)
            type.paramMap = ParamMap(primitiveMap = type.paramMap?.primitiveMap.orEmpty() + values)
            applied++
        }
        logger.debug { "Marked $applied npcs as poisonous." }
    }

    private fun onPlayerHit(player: Player, hit: Hit) {
        if (!hit.canCarryPoison()) {
            return
        }
        if (hit.isFromNpc) {
            val npc = hit.resolveNpcSource(npcList) ?: return
            val severity = npc.visType.paramOrNull(params.npc_poison_severity) ?: 0
            if (severity > 0 && random.of(NPC_POISON_ONE_IN) == 0) {
                PlayerPoison.tryPoison(player, source = npc)
            }
            return
        }
        if (hit.isFromPlayer) {
            val poison = WeaponPoison.of(hit) ?: return
            if (random.of(poison.oneIn) == 0) {
                PlayerPoison.tryPoison(player, initialDamage = poison.damage)
            }
        }
    }

    private fun onNpcHit(npc: Npc, hit: Hit) {
        if (!hit.isFromPlayer || !hit.canCarryPoison()) {
            return
        }
        if ((npc.visType.paramOrNull(params.poison_immunity) ?: 0) > 0) {
            return
        }
        val poison = WeaponPoison.of(hit) ?: return
        if (random.of(poison.oneIn) == 0) {
            NpcPoison.tryPoison(npc, poison.damage)
        }
    }

    private fun Hit.canCarryPoison(): Boolean =
        damage > 0 && (type == HitType.Melee || type == HitType.Ranged)

    private data class WeaponPoison(val damage: Int, val oneIn: Int) {
        companion object {
            fun of(hit: Hit): WeaponPoison? {
                val ranged = hit.type == HitType.Ranged
                // Ranged hits carry the ammunition as the secondary obj; thrown weapons have none
                // and are the righthand obj themselves.
                val source =
                    if (ranged) hit.secondaryType() ?: hit.righthandType() else hit.righthandType()
                val tier = source?.poisonTier() ?: return null
                return if (ranged) {
                    WeaponPoison(damage = RANGED_DAMAGE[tier - 1], oneIn = RANGED_POISON_ONE_IN)
                } else {
                    WeaponPoison(damage = MELEE_DAMAGE[tier - 1], oneIn = MELEE_POISON_ONE_IN)
                }
            }

            private fun ItemServerType.poisonTier(): Int? {
                val name =
                    runCatching { RSCM.getReverseMapping(RSCMType.OBJ, id) }.getOrNull()
                        ?: return null
                return when {
                    name.endsWith("_p++") -> 3
                    name.endsWith("_p+") -> 2
                    name.endsWith("_p") -> 1
                    else -> null
                }
            }

            private val MELEE_DAMAGE = intArrayOf(4, 5, 6)
            private val RANGED_DAMAGE = intArrayOf(2, 3, 4)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PoisonousNpc(val id: String, val damage: Int)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PoisonousNpcsFile(val npc: List<PoisonousNpc> = emptyList()) {
        companion object {
            private val mapper: ObjectMapper = ObjectMapper(TomlFactory()).registerKotlinModule()

            fun loadResource(): PoisonousNpcsFile {
                val stream =
                    PoisonousNpcsFile::class.java.classLoader.getResourceAsStream(
                        POISONOUS_NPCS_RESOURCE
                    ) ?: error("Missing resource: $POISONOUS_NPCS_RESOURCE")
                return stream.use { parse(it.readBytes().decodeToString()) }
            }

            fun parse(toml: String): PoisonousNpcsFile = mapper.readValue(toml)
        }
    }

    private companion object {
        private val logger = InlineLogger()

        const val POISONOUS_NPCS_RESOURCE = "poisonous-npcs.toml"

        private const val NPC_POISON_ONE_IN = 4

        private const val MELEE_POISON_ONE_IN = 4

        private const val RANGED_POISON_ONE_IN = 8
    }
}
