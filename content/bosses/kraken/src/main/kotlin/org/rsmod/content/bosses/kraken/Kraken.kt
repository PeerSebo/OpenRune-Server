package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.script.*
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Kraken @Inject constructor(
    private val deps: BossDeps,
    private val npcs: NpcList,
    private val interactions: AiPlayerInteractions,
    private val requirement: KrakenAttackHook,
    private val death: NpcDeath,
    private val ray: RayCastValidator,
) : PluginScript() {
    private data class Fight(val boss: Npc, val tentacles: List<Npc>, val cycle: KrakenCycle = KrakenCycle())
    private val fights = mutableMapOf<Npc, Fight>()
    private val bossSpec = boss(KrakenRules.Pool, KrakenRules.Boss) {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("kraken.attack")) }
        phase("fight", lockMovement = true) { rotationSelector { +then(attack) } }
    }
    private val tentacleSpec = boss(KrakenRules.TentaclePool, KrakenRules.Tentacle) {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("kraken.tentacle")) }
        phase("fight", lockMovement = true) { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("kraken.attack") { _, npc, target, _ -> attack(npc, target, false) }
        deps.extensionRegistry.register("kraken.tentacle") { _, npc, target, _ -> attack(npc, target, true) }
        for (spec in listOf(bossSpec, tentacleSpec)) BossCombat.register(this, spec, deps)
        for (symbol in KrakenRules.Types) onEvent<NpcHitEvents.BeforeImpact>(symbol.asRSCM()) {
            val player = if (hit.isFromPlayer) hit.resolvePlayerSource(deps.playerList) else null
            val awake = npc.visType.id == KrakenRules.Boss.asRSCM() || npc.visType.id == KrakenRules.Tentacle.asRSCM()
            damage = if (player != null && requirement.requirement(player) != null) 0 else KrakenRules.incoming(damage, hit.type, awake)
        }
        for (symbol in listOf(KrakenRules.Pool, KrakenRules.TentaclePool)) {
            val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM()))
            onNpcHit(type) {
                if (!hit.isFromPlayer) return@onNpcHit
                val player = hit.resolvePlayerSource(deps.playerList) ?: return@onNpcHit
                if (hit.type != HitType.Melee) disturb(npc, player, explosive = false)
            }
        }
        val pool = checkNotNull(ServerCacheManager.getNpc(KrakenRules.Pool.asRSCM()))
        val explosive = checkNotNull(ServerCacheManager.getItem("obj.fishing_explosive".asRSCM()))
        onOpNpcU(pool, explosive) { throwExplosive(it.npc) }
        onApNpcU(pool, explosive) {
            if (isWithinApRange(it.npc, 8)) { player.abortRoute(); throwExplosive(it.npc) }
        }
        for (symbol in listOf(KrakenRules.Pool, KrakenRules.Boss)) {
            onNpcQueue(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), "queue.death") {
                val drop = npc.findHero(deps.playerList)?.coords ?: npc.spawnCoords.translate(2, -10)
                fights.remove(npc)?.tentacles?.forEach(::submerge)
                death.deathWithDrops(this, drop)
            }
        }
        for (symbol in listOf(KrakenRules.TentaclePool, KrakenRules.Tentacle)) {
            onNpcQueue(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), "queue.death") {
                death.deathNoDrops(this)
            }
        }
        onEvent<NpcStateEvents.Respawn> {
            if (npc.id == KrakenRules.Pool.asRSCM()) reset(fights.remove(npc))
        }
        onEvent<NpcStateEvents.Delete> { fights.remove(npc) }
        onEvent<GameLifecycle.LateCycle> {
            for (fight in fights.values.toList()) {
                if (!fight.boss.isSlotAssigned) { fights.remove(fight.boss); continue }
                if (fight.boss.isVisible && fight.boss.hitpoints > 0 &&
                    deps.playerList.none { it.isValidTarget() && inRoom(fight.boss, it.coords) }) {
                    reset(fight); fights.remove(fight.boss)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.throwExplosive(pool: Npc) {
        requirement.requirement(player)?.let { mes(it); return }
        if (pool.visType.id != KrakenRules.Pool.asRSCM()) return
        val fight = fightFor(pool) ?: return
        if (fight.cycle.awake) return
        if (!inRoom(pool, player.coords) || !ray.hasLineOfSight(player.coords, pool.coords, destWidth = pool.size, destLength = pool.size)) return
        if (!invDel(inv, "obj.fishing_explosive", 1).success) return
        anim("seq.human_throw")
        disturb(pool, player, explosive = true)
        opNpc2(pool)
    }

    private fun fightFor(npc: Npc): Fight? {
        val boss = if (npc.id == KrakenRules.Pool.asRSCM()) npc else npcs.firstOrNull {
            it.id == KrakenRules.Pool.asRSCM() && it.level == npc.level && it.coords.chebyshevDistance(npc.coords) < 12
        } ?: return null
        return fights.getOrPut(boss) {
            Fight(boss, npcs.filter { it.id == KrakenRules.TentaclePool.asRSCM() && it.level == boss.level && it.coords.chebyshevDistance(boss.coords) < 12 }
                .sortedWith(compareBy({ it.x }, { it.z })))
        }.takeIf { it.tentacles.size == 4 }
    }

    private fun disturb(npc: Npc, player: Player, explosive: Boolean) {
        requirement.requirement(player)?.let { player.mes(it); return }
        val fight = fightFor(npc) ?: return
        if (!inRoom(fight.boss, player.coords)) return
        if (npc !== fight.boss) {
            val index = fight.tentacles.indexOf(npc)
            if (index < 0) return
            fight.cycle.disturbTentacle(index)
            surface(npc, KrakenRules.Tentacle, player)
        } else if (fight.cycle.awaken(explosive)) {
            for (tentacle in fight.tentacles) surface(tentacle, KrakenRules.Tentacle, player)
            surface(fight.boss, KrakenRules.Boss, player)
        } else if (!fight.cycle.awake) player.mes("You must disturb all four smaller whirlpools first.")
    }

    private fun surface(npc: Npc, symbol: String, player: Player) {
        if (npc.visType.id == symbol.asRSCM()) return
        npc.transmog(checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM())), Int.MAX_VALUE)
        npc.copyStats(npc.visType)
        npc.showAllOps()
        npc.anim(if (symbol == KrakenRules.Boss) "seq.slayer_kraken_arise" else "seq.tentacle_monster_spawn")
        npc.opPlayer2(player, interactions)
        deps.encounterRegistry.of(npc).lastAbilityTick = deps.mapClock.cycle
    }

    private fun attack(npc: Npc, target: Player, tentacle: Boolean) {
        val expected = if (tentacle) KrakenRules.Tentacle else KrakenRules.Boss
        if (npc.visType.id != expected.asRSCM() || !target.isValidTarget() || npc.hitpoints <= 0 ||
            npc.level != target.level || !npc.isWithinDistance(target, 10)) return
        if (!ray.hasLineOfSight(npc.coords, target.coords, srcWidth = npc.size, srcLength = npc.size)) return
        npc.anim(if (tentacle) "seq.tentacle_monster_attack" else "seq.swan_queen_spellcast")
        val gfx = if (tentacle) "spotanim.waterwave_travel" else "spotanim.firewave_travel"
        val projectile = ProjAnim.fromNpcToPlayer(npc, target, gfx.asRSCM(), Flight)
        deps.worldRepo.projAnim(projectile)
        val damage = if (deps.accuracy.rollMagicalRangedAccuracy(npc, target, deps.random)) deps.random.of(if (tentacle) 3 else 29) else 0
        target.finishNpcHit(npc, projectile.serverCycles, HitType.Typeless, damage, deps.playerHitModifier)
    }

    private fun reset(fight: Fight?) {
        if (fight == null) return
        fight.cycle.reset()
        (listOf(fight.boss) + fight.tentacles).forEach(::submerge)
    }

    @OptIn(InternalApi::class)
    private fun submerge(npc: Npc) {
        if (!npc.isSlotAssigned || !npc.isVisible || npc.hitpoints <= 0) return
        npc.clearInteractionRoute(); npc.resetFaceEntity(); npc.resetTransmog(); npc.copyStats(npc.type)
        npc.clearHeroPoints(); npc.showAllOps(); npc.mode = NpcMode.None
        deps.encounterRegistry.remove(npc)
    }

    internal companion object {
        val Flight = ProjAnimType(startHeight = 50, endHeight = 30, delay = 30, angle = 15,
            lengthAdjustment = 20, progress = 0, stepMultiplier = 3)
        fun inRoom(boss: Npc, tile: CoordGrid): Boolean = tile.level == boss.level &&
            tile.x in boss.spawnCoords.x - 10..boss.spawnCoords.x + 16 &&
            tile.z in boss.spawnCoords.z - 12..boss.spawnCoords.z + 13
    }
}
