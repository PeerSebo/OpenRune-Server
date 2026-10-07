package org.rsmod.content.quest.area.lumbridge

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.region.Region
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class BrutusCombatGate @Inject constructor() : NpcAttackValidateHook {
    private data class Encounter(val owner: Player, val region: Region)

    private val encounters = HashMap<Npc, Encounter>()

    fun register(npc: Npc, owner: Player, region: Region) {
        encounters[npc] = Encounter(owner, region)
    }

    fun remove(npc: Npc) {
        encounters.remove(npc)
    }

    fun allows(player: Player, npc: Npc): Boolean {
        val encounter = encounters[npc] ?: return false
        return encounter.owner === player &&
            encounter.region.containsCoord(player.coords) &&
            encounter.region.containsCoord(npc.coords) &&
            player.coords.level == npc.coords.level
    }

    fun incoming(npc: Npc, source: Player?, damage: Int): Int {
        val encounter = encounters[npc] ?: return 0
        return if (allows(source ?: encounter.owner, npc)) damage else 0
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (TYPES.none(npc::isType) && TYPES.none(npc::isVisType)) {
            return NpcAttackValidateResult.Pass
        }
        return if (allows(player, npc)) NpcAttackValidateResult.Pass
        else NpcAttackValidateResult.Deny("You must release Brutus into his field before fighting him.")
    }

    companion object {
        val TYPES = listOf(
            IdesOfMilkRefs.Bull,
            IdesOfMilkRefs.PenBull,
            "npc.cowboss_unnamed",
            "npc.cowboss_routefind",
        )
    }
}

internal class BrutusCombatScript @Inject constructor(
    private val gate: BrutusCombatGate,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Spawn>(IdesOfMilkRefs.PenBull.asRSCM()) { npc.hideAllOps() }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.isType(IdesOfMilkRefs.PenBull)) npc.hideAllOps()
        }
        onEvent<NpcStateEvents.Delete> { gate.remove(npc) }
        for (type in BrutusCombatGate.TYPES) {
            onEvent<NpcHitEvents.BeforeImpact>(type.asRSCM()) {
                val source = if (hit.isFromPlayer) hit.resolvePlayerSource(players) else null
                damage = if (hit.isFromPlayer && source == null) 0 else gate.incoming(npc, source, damage)
            }
        }
    }
}
