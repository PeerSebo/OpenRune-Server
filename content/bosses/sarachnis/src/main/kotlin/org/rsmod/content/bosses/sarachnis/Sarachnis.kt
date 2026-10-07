package org.rsmod.content.bosses.sarachnis

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcMode
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.feet
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.flag.CollisionFlag

class Sarachnis @Inject constructor(
    deps: BossDeps,
    private val ray: RayCastValidator,
    private val routes: RouteFactory,
    private val interactions: AiPlayerInteractions,
    private val instances: InstanceManager,
) : BossPluginScript(deps) {
    private class Fight(val anchor: CoordGrid) {
        val cycle = SarachnisCycle()
        val young = mutableSetOf<Npc>()
        var walking = false
    }
    private val fights = mutableMapOf<Npc, Fight>()

    override val spec = boss("npc.sarachnis") {
        stats(attackRate = 4)
        val attack = ability("attack") { include(external("sarachnis.attack")) }
        phase("combat") { rotationSelector { +then(attack) } }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register("sarachnis.attack") { _, npc, target, _ -> attack(npc, target) }
        for ((symbol, magic) in listOf("npc.sarachnis_melee_spawn" to false, "npc.sarachnis_mage_spawn" to true)) {
            val key = "sarachnis.$symbol"
            val youngSpec = boss(symbol) {
                stats(attackRate = 4)
                val attack = ability("attack") { include(external(key)) }
                phase("combat") { rotationSelector { +then(attack) } }
            }
            BossCombat.register(this, youngSpec, deps)
            deps.extensionRegistry.register(key) { _, npc, target, _ -> youngAttack(npc, target, magic) }
        }
        onEvent<NpcStateEvents.Spawn>("npc.sarachnis".asRSCM()) {
            fights[npc] = Fight(npc.coords)
        }
        onEvent<NpcStateEvents.Hide>("npc.sarachnis".asRSCM()) { clear(npc) }
        onEvent<NpcStateEvents.Delete> { clear(npc) }
        onEvent<NpcStateEvents.Respawn> {
            clear(npc)
            if (npc.id == "npc.sarachnis".asRSCM()) fights[npc] = Fight(npc.coords)
        }
        onEvent<GameLifecycle.LateCycle> { tick() }
    }

    internal fun attack(npc: Npc, target: Player) {
        val fight = fights.getOrPut(npc) { Fight(npc.spawnCoords) }
        if (fight.walking || !target.isValidTarget() || !inRoom(fight.anchor, target.coords)) return
        val players = occupants(fight)
        if (fight.cycle.nextIsWeb()) {
            npc.say("Hsss!")
            npc.anim("seq.hosdun_spider_boss_attack_ranged")
            for (player in players) {
                deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, player,
                    "spotanim.sarachnis_web_proj".asRSCM(), FLIGHT))
                if (player.feet?.id != "obj.aranea_boots".asRSCM()) CombatEffects.freeze(player, 6)
            }
            val destinations = listOf(-3 to -3, -3 to 3, 3 to -3, 3 to 3)
                .map { (dx, dz) -> fight.anchor.translate(dx, dz) }
                .filter { open(it, npc.size) && it != npc.coords }
            val destination = destinations.maxByOrNull { it.chebyshevDistance(target.coords) } ?: return
            fight.walking = true
            npc.clearInteractionRoute()
            npc.mode = NpcMode.None
            npc.walkTo(routes, destination, speed = MoveSpeed.Run) {
                fight.walking = false
                npc.moveSpeed = npc.defaultMoveSpeed
                if (npc.isSlotAssigned && npc.isVisible && npc.hitpoints > 0) {
                    occupants(fight).firstOrNull()?.let { npc.apPlayer2(it, interactions) }
                }
            }
            return
        }
        val melee = distance(npc, target.coords) == 1
        npc.anim(if (melee) "seq.hosdun_spider_boss_attack_melee" else "seq.hosdun_spider_boss_attack_ranged")
        for (player in if (melee) listOf(target) else players) {
            if (!canReach(npc, player, if (melee) 1 else 16)) continue
            val accurate = if (melee) deps.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Crush, deps.random)
                else deps.accuracy.rollRangedAccuracy(npc, player, deps.random)
            if (!melee) deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, player,
                "spotanim.sarachnis_rangeproj".asRSCM(), FLIGHT))
            player.finishNpcHit(npc, if (melee) 1 else 2,
                if (melee) HitType.Melee else HitType.Ranged,
                if (accurate) deps.random.of(32) else 0, deps.playerHitModifier)
            // Protection (and invulnerability) suppress the leech. A successful zero roll still heals.
            val prayer = if (melee) "varbit.prayer_protectfrommelee" else "varbit.prayer_protectfrommissiles"
            if (accurate && player.vars[prayer] == 0 && !player.adminGodMode)
                npc.heal(if (melee) 5 else 10, showHitsplat = true)
        }
    }

    internal fun tick() {
        for ((npc, fight) in fights.toMap()) {
            if (!npc.isSlotAssigned || !npc.isVisible || npc.hitpoints <= 0) { clear(npc); continue }
            val players = occupants(fight)
            if (players.isEmpty()) {
                if (fight.cycle.started || fight.young.isNotEmpty()) {
                    removeYoung(fight)
                    npc.clearInteractionRoute()
                    npc.moveSpeed = npc.defaultMoveSpeed
                    npc.heal(npc.visType.hitpoints)
                    PathingEntityCommon.telejump(npc, deps.collision, fight.anchor)
                    npc.defaultMode()
                    fights[npc] = Fight(fight.anchor)
                }
                continue
            }
            repeat(fight.cycle.claimWaves(npc.hitpoints)) {
                for (player in players) summonPair(npc, fight, player)
            }
            fight.young.removeAll { !it.isSlotAssigned || !it.isVisible || it.hitpoints <= 0 }
        }
    }

    private fun summonPair(owner: Npc, fight: Fight, player: Player) {
        val players = occupants(fight)
        for (symbol in listOf("npc.sarachnis_melee_spawn", "npc.sarachnis_mage_spawn")) {
            val young = Npc(symbol, player.coords)
            val size = young.size
            val candidates = buildList {
                for (dx in -3..3) for (dz in -3..3) add(player.coords.translate(dx, dz))
            }.filter { tile -> inRoom(fight.anchor, tile) && inRoom(fight.anchor, tile.translate(size - 1, size - 1)) &&
                open(tile, size) && !overlaps(tile, size, owner.coords, owner.size) &&
                players.none { overlaps(tile, size, it.coords, 1) } &&
                fight.young.none { overlaps(tile, size, it.coords, it.size) } }
            val tile = candidates.minByOrNull { it.chebyshevDistance(player.coords) } ?: continue
            young.coords = tile
            deps.npcRepo.add(young, Int.MAX_VALUE)
            young.respawns = false
            fight.young += young
            instances.instanceForNpc(owner)?.let { instances.attachNpc(it, young) }
            young.apPlayer2(player, interactions)
        }
    }

    private fun youngAttack(npc: Npc, player: Player, magic: Boolean) {
        val fight = fights.values.firstOrNull { npc in it.young } ?: return
        if (!inRoom(fight.anchor, player.coords) || !canReach(npc, player, if (magic) 7 else 1)) return
        npc.anim("seq.spider_update_attack_large")
        val accurate = if (magic) deps.accuracy.rollMagicAccuracy(npc, player, deps.random)
            else deps.accuracy.rollMeleeAccuracy(npc, player, MeleeAttackType.Stab, deps.random)
        if (magic) deps.worldRepo.projAnim(ProjAnim.fromNpcToPlayer(npc, player,
            "spotanim.sarachnis_rangeproj".asRSCM(), FLIGHT))
        player.finishNpcHit(npc, if (magic) 2 else 1, if (magic) HitType.Magic else HitType.Melee,
            if (accurate) deps.random.of(if (magic) 12 else 14) else 0, deps.playerHitModifier)
    }

    private fun canReach(npc: Npc, player: Player, range: Int): Boolean =
        player.isValidTarget() && npc.coords.level == player.coords.level && distance(npc, player.coords) in 1..range &&
            ray.hasLineOfSight(player.coords, npc.coords, destWidth = npc.size, destLength = npc.size)

    private fun occupants(fight: Fight): List<Player> =
        deps.playerList.filter { it.isValidTarget() && inRoom(fight.anchor, it.coords) }

    private fun open(tile: CoordGrid, size: Int): Boolean = (0 until size).all { dx ->
        (0 until size).all { dz ->
            deps.collision[tile.x + dx, tile.z + dz, tile.level] and FLOOR_BLOCK == 0
        }
    }

    private fun clear(npc: Npc) { fights.remove(npc)?.let(::removeYoung) }
    private fun removeYoung(fight: Fight) {
        for (young in fight.young) if (young.isSlotAssigned && young.isVisible) deps.npcRepo.del(young, Int.MAX_VALUE)
        fight.young.clear()
    }

    internal companion object {
        val Spawn = CoordGrid(1840, 9900)
        val FLOOR_BLOCK = CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC
        val FLIGHT = ProjAnimType(startHeight = 30, endHeight = 30, delay = 0, angle = 10,
            lengthAdjustment = 15, progress = 0, stepMultiplier = 3)
        fun inRoom(anchor: CoordGrid, tile: CoordGrid): Boolean = tile.level == anchor.level &&
            tile.x in anchor.x - 7..anchor.x + 10 && tile.z in anchor.z - 7..anchor.z + 11
        fun distance(npc: Npc, tile: CoordGrid): Int = maxOf(
            (npc.coords.x - tile.x).coerceAtLeast(0), (tile.x - npc.coords.x - npc.size + 1).coerceAtLeast(0),
            (npc.coords.z - tile.z).coerceAtLeast(0), (tile.z - npc.coords.z - npc.size + 1).coerceAtLeast(0),
        )
        fun overlaps(a: CoordGrid, aSize: Int, b: CoordGrid, bSize: Int): Boolean =
            a.level == b.level && a.x < b.x + bSize && b.x < a.x + aSize &&
                a.z < b.z + bSize && b.z < a.z + aSize
    }
}

internal class SarachnisCycle {
    private var attacks = 0
    private var waves = 0
    var started = false
        private set
    fun nextIsWeb(): Boolean {
        started = true
        if (attacks == 4) { attacks = 0; return true }
        attacks++
        return false
    }
    fun claimWaves(hp: Int): Int {
        val required = when { hp <= 0 -> waves; hp <= 132 -> 2; hp <= 264 -> 1; else -> 0 }
        val count = (required - waves).coerceAtLeast(0)
        waves += count
        return count
    }
}
