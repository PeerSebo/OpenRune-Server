package org.rsmod.content.bosses.giantmole

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class GiantMole @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    private val burrowing = IdentityHashMap<Npc, Any>()
    private val tracker = MoleTracker(deps)

    override val spec = boss("npc.mole_giant") {
        stats(attackRate = 4)
        val melee = ability("claw") {
            anim("seq.mole_attack")
            sound("synth.MOLE_ATTACK")
            hit { damage(0..21).rollWithAccuracy(); type(Melee) }
        }
        phase("combat") { rotationSelector { +then(melee) } }
    }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        val type = checkNotNull(ServerCacheManager.getNpc("npc.mole_giant".asRSCM(RSCMType.NPC)))
        onNpcHit(type) {
            // This event is AFTER damage. A queued attack must not trigger an early burrow.
            if (hit.isFromPlayer && canBurrow(npc.hitpoints, hit.type, hit.damage) &&
                npc !in burrowing && deps.random.of(4) == 0) {
                burrow(npc)
            }
        }
        onEvent<NpcStateEvents.Delete> { burrowing.remove(npc) }
        onEvent<NpcStateEvents.Respawn> { burrowing.remove(npc) }
        onEvent<GameLifecycle.LateCycle> { tracker.tick() }
    }

    internal fun burrow(npc: Npc) {
        val destination = burrowDestination(npc.coords, npc.size) ?: return
        val token = Any()
        burrowing[npc] = token
        val encounter = deps.encounter(npc)
        encounter.invulnerable = true
        npc.clearInteractionRoute()
        npc.resetFaceEntity()
        npc.mode = NpcMode.None
        npc.movementLocked = true
        npc.ignoreCombatInteractions = true
        npc.anim("seq.mole_burrow_down")
        npc.spotanim("spotanim.mole_mud_cloud_spotanim")
        deps.worldRepo.soundArea(npc.coords, "synth.MOLE_BURROW_DOWN", radius = 8)
        for (player in deps.playerList) {
            if (player.coords.level == npc.coords.level && player.coords.chebyshevDistance(npc.coords) <= 8 &&
                deps.random.of(3) == 0) {
                val inventoryChanged = MoleLightSources.extinguish(player.inv)
                val wornChanged = MoleLightSources.extinguish(player.worn)
                if (inventoryChanged || wornChanged) player.mes("The mole's digging extinguishes your light source.")
            }
        }
        deps.worldQueues.add(2) {
            if (burrowing[npc] !== token || npc.hitpoints <= 0) return@add
            npc.telejump(deps.collision, destination)
            npc.anim("seq.mole_burrow_up")
            npc.spotanim("spotanim.mole_mud_hole_up_spotanim")
            deps.worldRepo.soundArea(destination, "synth.MOLE_BURROW_UP", radius = 8)
        }
        deps.worldQueues.add(4) {
            if (burrowing[npc] !== token) return@add
            burrowing.remove(npc)
            encounter.invulnerable = false
            npc.movementLocked = false
            npc.ignoreCombatInteractions = false
            npc.defaultMode() // Native burrowing loses the target; it must not chase back.
        }
    }

    internal fun burrowDestination(origin: CoordGrid, size: Int): CoordGrid? {
        val open = openBurrowSites(origin, size, deps.collision)
        return if (open.isEmpty()) null else open[deps.random.of(open.size)]
    }

    internal companion object {
        val BURROW_SITES = listOf(
            CoordGrid(1759, 5189), CoordGrid(1738, 5225), CoordGrid(1775, 5175),
            CoordGrid(1755, 5204), CoordGrid(1738, 5192), CoordGrid(1744, 5173),
            CoordGrid(1778, 5238), CoordGrid(1760, 5184), CoordGrid(1743, 5222),
        )

        fun canBurrow(remainingHp: Int, type: HitType, damage: Int): Boolean =
            remainingHp in 10..100 && !(type == HitType.Magic && damage == 0)

        fun openBurrowSites(origin: CoordGrid, size: Int, collision: CollisionFlagMap): List<CoordGrid> =
            BURROW_SITES.filter { tile ->
                tile.level == origin.level && tile.chebyshevDistance(origin) >= 8 &&
                    (0 until size).all { dx ->
                        (0 until size).all { dz -> openLanding(collision, tile.translate(dx, dz)) }
                    }
            }

        fun openLanding(collision: CollisionFlagMap, tile: CoordGrid): Boolean =
            collision[tile.x, tile.z, tile.level] and
                (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) == 0
    }
}
