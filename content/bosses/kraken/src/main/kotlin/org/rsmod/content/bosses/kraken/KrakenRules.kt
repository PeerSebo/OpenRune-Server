package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.module.PluginModule

internal object KrakenRules {
    const val Boss = "npc.slayer_kraken_boss"
    const val Pool = "npc.slayer_kraken_boss_whirlpool"
    const val Tentacle = "npc.slayer_kraken_boss_tentacle"
    const val TentaclePool = "npc.slayer_kraken_boss_tentacle_whirlpool"
    val Types = listOf(Boss, Pool, Tentacle, TentaclePool)
    fun incoming(damage: Int, style: HitType, awake: Boolean): Int = when {
        !awake || style == HitType.Melee -> 0
        style == HitType.Ranged -> if (damage == 0) 0 else (damage / 7).coerceAtLeast(1)
        else -> damage
    }
    fun eligible(level: Int, remaining: Int, assigned: Boolean): Boolean = level >= 87 && remaining > 0 && assigned
}

internal class KrakenCycle {
    private val raised = mutableSetOf<Int>()
    var awake = false
        private set
    fun disturbTentacle(index: Int) { require(index in 0..3); raised += index }
    fun awaken(explosive: Boolean): Boolean {
        if (awake || (!explosive && raised.size != 4)) return false
        raised += 0..3
        awake = true
        return true
    }
    fun reset() { raised.clear(); awake = false }
}

class KrakenModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcAttackValidateHook>(KrakenAttackHook::class.java)
        addSetBinding<PlayerDeathRedirectHook>(KrakenGraveHook::class.java)
    }
}

class KrakenGraveHook @Inject constructor(private val instances: InstanceManager) : PlayerDeathRedirectHook {
    override fun destination(player: Player) = instances.sessionForPlayer(player)
        ?.takeIf { session -> session.key == KrakenCove.Key && instances.npcsForInstance(session.id).any {
            it.id == KrakenRules.Pool.asRSCM() && Kraken.inRoom(it, player.coords)
        } }
        ?.let { KrakenCove.OutsideRoom }
}

class KrakenAttackHook @Inject constructor(
    private val areas: AreaChecker,
) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (KrakenRules.Types.none { it.asRSCM() == npc.id || it.asRSCM() == npc.visType.id }) return NpcAttackValidateResult.Pass
        val reason = requirement(player)
        if (reason != null) return NpcAttackValidateResult.Deny(reason)
        // Manual spells can be cast with a melee weapon stance.
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    internal fun requirement(player: Player): String? {
        if (player.statBase("stat.slayer") < 87) return "You need a Slayer level of 87 to fight the Kraken."
        val task = player.vars["varp.slayer_target"]
        val boss = Npc(checkNotNull(ServerCacheManager.getNpc(KrakenRules.Boss.asRSCM())), KrakenCove.BossSpawn)
        if (!KrakenRules.eligible(player.statBase("stat.slayer"), player.vars["varp.slayer_count"],
                SlayerTaskManager.countsAsTaskKill(boss, task, player.vars["varbit.slayer_target_bossid"]))) return "You can only fight the Kraken while assigned cave krakens or the Kraken."
        // The real cove coordinate keeps Konar's location check valid inside a copied room.
        if (!SlayerTaskManager.countsKillTowardTask(player, boss, areas)) return "Your Slayer assignment requires a different location."
        return null
    }
}
