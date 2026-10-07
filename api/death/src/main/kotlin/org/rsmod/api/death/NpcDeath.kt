package org.rsmod.api.death

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.map.CoordGrid

@Singleton
public class NpcDeath
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val players: PlayerList,
    private val objRepo: ObjRepository,
    private val deathDropHooks: Set<NpcDeathDropHook>,
    private val deathKillHooks: Set<NpcDeathKillHook>,
) {
    private var lootTrackerEventId: Int = 0

    public suspend fun deathNoDrops(access: StandardNpcAccess) {
        access.death(npcRepo, players)
    }

    public suspend fun deathWithDrops(
        access: StandardNpcAccess,
        dropCoords: CoordGrid = access.coords,
    ) {
        // One-life NPC removal clears its uid and ownership indexes. Deliver loot/kill hooks
        // after the death animation but before that removal so instance rewards retain a hero.
        access.death(npcRepo, players) { access.npc.spawnDeathDrops(dropCoords) }
    }

    private fun Npc.spawnDeathDrops(dropCoords: CoordGrid) {
        // TODO: Drop tables.
        val hero = findHero(players)
        if (hero != null) {
            val duration = hero.lootDropDuration ?: constants.lootdrop_duration
            val lootTrackerEventId = nextLootTrackerEventId()

            val droppedRemains =
                paramOrNull(params.dropped_remains)
                    ?: ServerCacheManager.getItem("obj.bones".asRSCM())
                    ?: error("No bones")
            val ctx = NpcDeathDropContext(
                hero = hero,
                npc = this,
                dropType = droppedRemains,
                dropCoords = dropCoords,
                duration = duration,
                objRepo = objRepo
            )

            var dropConsumed = false
            for (hook in deathDropHooks) {
                if (hook.tryConsume(ctx)) {
                    dropConsumed = true
                    break
                }
            }
            if (!dropConsumed) {
                val spawned = objRepo.add(droppedRemains, dropCoords, duration, hero)
                ClientScripts.lootTrackerAddLoot(
                    hero,
                    visType.id,
                    lootTrackerEventId,
                    spawned.type,
                    spawned.count,
                )

                var dropConsumed = false
                for (hook in deathDropHooks) {
                    if (hook.tryConsume(ctx)) {
                        dropConsumed = true
                        break
                    }
                }
                if (!dropConsumed) {
                    val spawned = objRepo.add(droppedRemains, dropCoords, duration, hero)
                    ClientScripts.lootTrackerAddLoot(
                        hero,
                        visType.id,
                        lootTrackerEventId,
                        spawned.type,
                        spawned.count,
                    )
                }
            }

            val killCtx =
                NpcDeathKillContext(
                    hero = hero,
                    npc = this,
                    lootTrackerEventId = lootTrackerEventId,
                    dropCoords = dropCoords,
                )
            for (hook in deathKillHooks) {
                hook.onKill(killCtx)
            }
        }
    }

    private fun nextLootTrackerEventId(): Int {
        lootTrackerEventId = if (lootTrackerEventId == Int.MAX_VALUE) 1 else lootTrackerEventId + 1
        return lootTrackerEventId
    }

    public fun spawnDrops(access: StandardNpcAccess, dropCoords: CoordGrid = access.coords) {
        access.npc.spawnDeathDrops(dropCoords)
    }
}

private var Player.lastCombat: Int by intVarp("varp.lastcombat")
private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
private var Npc.aggressivePlayer by typePlayerUidVarn("varn.aggressive_player")

/**
 * Runs after the NPC is already dead. Overrides must handle drops explicitly; this sequence
 * does not spawn them.
 */
public suspend fun StandardNpcAccess.death(
    npcRepo: NpcRepository,
    players: PlayerList,
    beforeRemoval: (() -> Unit)? = null,
) {
    walk(coords)
    noneMode()
    hideAllOps()
    arriveDelay()

    val aggressivePlayer = npc.aggressivePlayer
    if (aggressivePlayer != null) {
        val player = aggressivePlayer.resolve(players)

        val deathSound = paramOrNull(params.death_sound)
        if (deathSound != null && player != null) {
            player.soundSynth(deathSound)
        }

        // TODO(combat): Should we assert that npc.uid will always match player.aggressiveNpc at
        // this point?

        if (player != null && player.aggressiveNpc == npc.uid) {
            player.lastCombat = 0
        }
    }

    val deathAnim = param(params.death_anim)
    anim(RSCM.getReverseMapping(RSCMType.SEQ, deathAnim.id))
    delay(deathAnim)

    beforeRemoval?.invoke()

    if (npc.respawns) {
        npcRepo.despawn(npc, npc.type.respawnRate)
        return
    }

    npcRepo.del(npc, Int.MAX_VALUE)
}
