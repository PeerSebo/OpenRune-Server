package org.rsmod.content.minigames.fishingtrawler

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

// Instance marker coordinates are translated; derive tentacle positions from the copied Murphy marker.
@Singleton
class TrawlerManager
@Inject
constructor(
    private val instanceManager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val random: GameRandom,
) {
    private val voyages = HashMap<Long, TrawlerVoyage>()

    fun joinOrCreateVoyage(player: Player, tick: Int): InstanceManager.Result {
        val area =
            InstanceArea.copyRegions(
                centerRegionId = TrawlerConstants.MAIN_SHIP_REGION,
                gridSize = 1,
                enterCoord =
                    RegionLocal(
                        level = 0,
                        regionZoneX = TrawlerConstants.SHIP_MX,
                        regionZoneZ = TrawlerConstants.SHIP_MZ,
                        localX = TrawlerConstants.MURPHY_ON_SHIP_LX,
                        localZ = TrawlerConstants.MURPHY_ON_SHIP_LZ,
                    ),
                exitCoord = CoordGrid(TrawlerConstants.LOBBY_X, TrawlerConstants.LOBBY_Z, 0),
            )
        val settings =
            InstanceSettings(
                maxPlayers = 5,
                destroyWhenEmpty = false,
                description = "Fishing Trawler",
            )
        val instanceSpec = settings.withArea(area, settingsRowId = 0)

        val result =
            instanceManager.joinOrCreateServerOwned(
                player = player,
                key = TrawlerConstants.INSTANCE_KEY,
                spec = instanceSpec,
                currentTick = tick,
                access = InstanceAccess.Public,
                filter = { session -> voyages[session.id.value]?.phase == TrawlerPhase.LOBBY },
            )
        if (result is InstanceManager.Result.Joined) {
            val voyage =
                voyages.getOrPut(result.session.id.value) { createVoyage(result.session, result.enter) }
            if (voyage.lobbyStartTick < 0) {
                voyage.lobbyStartTick = tick
            }
        }
        return result
    }

    fun voyageForPlayer(player: Player): TrawlerVoyage? {
        val session = instanceManager.sessionForPlayer(player) ?: return null
        return voyages[session.id.value]
    }

    private fun createVoyage(session: InstanceSession, murphyEnter: CoordGrid): TrawlerVoyage {
        val voyage = TrawlerVoyage(session)
        val originalMurphyX = TrawlerConstants.SHIP_MX * 64 + TrawlerConstants.MURPHY_ON_SHIP_LX
        val originalMurphyZ = TrawlerConstants.SHIP_MZ * 64 + TrawlerConstants.MURPHY_ON_SHIP_LZ
        val dx = murphyEnter.x - originalMurphyX
        val dz = murphyEnter.z - originalMurphyZ

        val originalPortX = TrawlerConstants.SHIP_MX * 64 + TrawlerConstants.TENTACLE_PORT_LX
        val originalPortZ = TrawlerConstants.SHIP_MZ * 64 + TrawlerConstants.TENTACLE_PORT_LZ
        val originalStarboardX = TrawlerConstants.SHIP_MX * 64 + TrawlerConstants.TENTACLE_STARBOARD_LX
        val originalStarboardZ = TrawlerConstants.SHIP_MZ * 64 + TrawlerConstants.TENTACLE_STARBOARD_LZ

        voyage.tentaclePortCoord =
            CoordGrid(originalPortX + dx, originalPortZ + dz, murphyEnter.level)
        voyage.tentacleStarboardCoord =
            CoordGrid(originalStarboardX + dx, originalStarboardZ + dz, murphyEnter.level)
        return voyage
    }

    fun tick(tick: Int) {
        for (voyage in voyages.values.toList()) {
            when (voyage.phase) {
                TrawlerPhase.LOBBY -> tickLobby(voyage, tick)
                TrawlerPhase.SAILING -> tickSailing(voyage, tick)
                TrawlerPhase.SINKING -> Unit
                TrawlerPhase.DOCKED -> tickDocked(voyage, tick)
            }
        }
    }

    private fun tickLobby(voyage: TrawlerVoyage, tick: Int) {
        if (voyage.lobbyStartTick < 0) {
            return
        }
        if (tick >= voyage.lobbyStartTick + TrawlerConstants.LOBBY_WAIT_TICKS) {
            beginSailing(voyage, tick)
        }
    }

    private fun beginSailing(voyage: TrawlerVoyage, tick: Int) {
        voyage.phase = TrawlerPhase.SAILING
        voyage.session.acceptingPlayers = false
        voyage.sailStartTick = tick
        voyage.startOccupants.addAll(voyage.session.occupants)
        voyage.nextLeakOpenTick = tick + TrawlerConstants.LEAK_REOPEN_DELAY_TICKS
        voyage.nextWaterRiseTick = tick + TrawlerConstants.WATER_RISE_INTERVAL_TICKS
        voyage.nextTentacleAttackTick = tick + TrawlerConstants.TENTACLE_FIRST_ATTACK_DELAY_TICKS
        messageOccupants(voyage, "The ship casts off! Keep her afloat for five minutes.")
    }

    private fun tickSailing(voyage: TrawlerVoyage, tick: Int) {
        if (tick >= voyage.sailStartTick + TrawlerConstants.SAIL_TICKS) {
            beginDocking(voyage, tick)
            return
        }

        if (voyage.activeLeakSlot < 0 && tick >= voyage.nextLeakOpenTick) {
            openLeak(voyage, random.of(TrawlerConstants.HULL_SLOT_COUNT))
        }

        if (voyage.activeLeakSlot >= 0 && tick >= voyage.nextWaterRiseTick) {
            voyage.nextWaterRiseTick = tick + TrawlerConstants.WATER_RISE_INTERVAL_TICKS
            voyage.waterLevel =
                (voyage.waterLevel + TrawlerConstants.WATER_RISE_PER_TICK)
                    .coerceAtMost(TrawlerConstants.WATER_SINK_THRESHOLD)
            if (voyage.waterLevel >= TrawlerConstants.WATER_SINK_THRESHOLD) {
                beginSinking(voyage, tick)
                return
            }
        }

        val tentacleNpc = voyage.tentacleNpc
        if (tentacleNpc == null) {
            if (
                voyage.tentacleAttacksUsed < TrawlerConstants.TENTACLE_ATTACK_COUNT_SOLO &&
                    tick >= voyage.nextTentacleAttackTick
            ) {
                spawnTentacle(voyage, tick)
            }
        } else if (tick >= voyage.tentacleDespawnTick) {
            despawnTentacle(voyage)
        }
    }

    private fun openLeak(voyage: TrawlerVoyage, slot: Int) {
        voyage.activeLeakSlot = slot
        voyage.leakState[slot] = TrawlerConstants.HULL_STATE_LEAKING
        setHullVarbitForOccupants(voyage, slot, TrawlerConstants.HULL_STATE_LEAKING)
    }

    fun patchLeak(voyage: TrawlerVoyage, slot: Int, tick: Int) {
        if (voyage.leakState[slot] != TrawlerConstants.HULL_STATE_LEAKING) {
            return
        }
        voyage.leakState[slot] = TrawlerConstants.HULL_STATE_PATCHED
        voyage.activeLeakSlot = -1
        voyage.nextLeakOpenTick = tick + TrawlerConstants.LEAK_REOPEN_DELAY_TICKS
        setHullVarbitForOccupants(voyage, slot, TrawlerConstants.HULL_STATE_PATCHED)
    }

    private fun setHullVarbitForOccupants(voyage: TrawlerVoyage, slot: Int, value: Int) {
        val varbit = TrawlerConstants.hullVarbit(slot)
        for (player in occupantsOf(voyage)) {
            VarPlayerIntMapSetter.set(player, varbit, value)
        }
    }

    private fun spawnTentacle(voyage: TrawlerVoyage, tick: Int) {
        val coord =
            if (voyage.tentacleOnStarboardNext) {
                voyage.tentacleStarboardCoord
            } else {
                voyage.tentaclePortCoord
            } ?: return
        val type = ServerCacheManager.getNpc(TrawlerConstants.TENTACLE_NPC.asRSCM(RSCMType.NPC)) ?: return
        val npc = Npc(type, coord)
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        voyage.tentacleNpc = npc
        voyage.tentacleAttacksUsed++
        voyage.tentacleDespawnTick = tick + TrawlerConstants.TENTACLE_DESPAWN_AFTER_TICKS
        voyage.tentacleOnStarboardNext = !voyage.tentacleOnStarboardNext
        voyage.nextTentacleAttackTick = tick + TrawlerConstants.TENTACLE_ATTACK_INTERVAL_TICKS
        messageOccupants(
            voyage,
            "<col=ff0000>An enormous tentacle bursts through the hull!</col>",
        )
    }

    fun despawnTentacle(voyage: TrawlerVoyage) {
        val npc = voyage.tentacleNpc ?: return
        voyage.tentacleNpc = null
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun beginSinking(voyage: TrawlerVoyage, tick: Int) {
        voyage.phase = TrawlerPhase.SINKING
        despawnTentacle(voyage)
        messageOccupants(voyage, "<col=ff0000>The ship is taking on too much water - she's going down!</col>")
        val exit = CoordGrid(TrawlerConstants.SINK_EXIT_X, TrawlerConstants.SINK_EXIT_Z, 0)
        // TODO: apply 1-2 sink damage through a protected queue.
        for (occupant in voyage.session.occupants.toList()) {
            val player = playerList.firstOrNull { it.uuid == occupant } ?: continue
            instanceManager.leave(player, voyage.session, tick)
            player.coords = exit
        }
        endVoyage(voyage, tick)
    }

    private fun beginDocking(voyage: TrawlerVoyage, tick: Int) {
        voyage.phase = TrawlerPhase.DOCKED
        voyage.dockStartTick = tick
        despawnTentacle(voyage)

        val totalActions = voyage.actionsPerformed.values.sum()
        voyage.totalCatch =
            (TrawlerConstants.CATCH_BASE + totalActions * TrawlerConstants.CATCH_PER_CONTRIBUTION_ACTION)
                .toInt()

        val divisor = voyage.startOccupants.size.coerceAtLeast(1)
        val perPlayer = voyage.totalCatch / divisor
        for (playerId in voyage.startOccupants) {
            val contribution = voyage.contribution[playerId] ?: 0
            var share = perPlayer
            // TODO: apply the Elite Ardougne diary reward bonus.
            if (contribution >= TrawlerConstants.MAX_CONTRIBUTION) {
                share = (share * 3) / 2
            }
            voyage.pendingFishReward[playerId] = share
        }
        messageOccupants(
            voyage,
            "Murphy brings the trawler in to dock. Collect your catch from the reward net!",
        )
    }

    private fun tickDocked(voyage: TrawlerVoyage, tick: Int) {
        if (tick >= voyage.dockStartTick + TrawlerConstants.DOCK_TICKS) {
            endVoyage(voyage, tick)
        }
    }

    private fun endVoyage(voyage: TrawlerVoyage, tick: Int) {
        voyages.remove(voyage.session.id.value)
        instanceManager.endServerOwned(voyage.session, tick)
    }

    private fun occupantsOf(voyage: TrawlerVoyage): List<Player> =
        voyage.session.occupants.mapNotNull { id -> playerList.firstOrNull { it.uuid == id } }

    private fun messageOccupants(voyage: TrawlerVoyage, text: String) {
        for (player in occupantsOf(voyage)) {
            player.mes(text, ChatType.Engine)
        }
    }
}
