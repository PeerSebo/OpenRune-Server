package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class TemporossEncounterManager
@Inject
constructor(
    private val instanceManager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val eventBus: EventBus,
    private val random: GameRandom,
    locRepo: LocRepository,
    worldRepo: WorldRepository,
    collision: CollisionFlagMap,
) {
    internal val environment = TemporossEnvironment(locRepo, worldRepo, collision, npcRepo, instanceManager, random)

    private val games = HashMap<InstanceId, TemporossEncounter>()

    private val deckPlayers = LinkedHashSet<Long>()

    private var lobbyStartTick = -1

    private var announced10s = false

    private val castingOff = mutableListOf<CastOff>()

    private data class CastOff(val players: Set<Player>, val departTick: Int, val solo: Boolean)

    fun isCastingOff(player: Player): Boolean = castingOff.any { player in it.players }

    private val spec: InstanceSpec by lazy {
        InstanceSettings(maxPlayers = TemporossConstants.MAX_PLAYERS, destroyWhenEmpty = false)
            .withArea(TemporossConstants.arenaArea(), settingsRowId = 0)
    }

    fun isOnDeck(player: Player): Boolean = deckPlayers.contains(player.uuid)

    fun boardDeck(player: Player, currentTick: Int) {
        val id = player.uuid ?: return
        if (isCastingOff(player) || instanceManager.sessionForPlayer(player) != null) return
        if (!TemporossConstants.isAtBoarding(player.coords)) return
        if (deckPlayers.add(id)) {
            if (lobbyStartTick < 0) {
                lobbyStartTick = currentTick
                announced10s = false
            }
            player.mes(
                "The mission will begin in the next 30 seconds, or when the boat is full.",
                ChatType.Broadcast,
            )
        }
        openLobbyOverlay(player)
        refreshLobbyOverlay(currentTick)
    }

    fun leaveDeck(player: Player, currentTick: Int) {
        val id = player.uuid ?: return
        if (deckPlayers.remove(id)) {
            if (deckPlayers.isEmpty()) lobbyStartTick = -1
            player.ifCloseOverlay(TemporossConstants.LOBBY_HUD_INTERFACE, eventBus)
            refreshLobbyOverlay(currentTick)
        }
    }

    fun startSolo(player: Player, currentTick: Int) {
        val id = player.uuid ?: return
        beginCastOff(setOf(id), currentTick, solo = true)
    }

    fun tick(currentTick: Int) {
        driveLobby(currentTick)
        driveCastOff(currentTick)
        driveGames(currentTick)
        reclaimEmpty(currentTick)
    }

    private fun driveLobby(currentTick: Int) {
        if (lobbyStartTick < 0 || deckPlayers.isEmpty()) return

        val iterator = deckPlayers.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            val player = playerList.firstOrNull { it.uuid == id }
            if (player == null || !TemporossConstants.isAtBoarding(player.coords)) {
                iterator.remove()
                player?.ifCloseOverlay(TemporossConstants.LOBBY_HUD_INTERFACE, eventBus)
            }
        }
        if (deckPlayers.isEmpty()) {
            lobbyStartTick = -1
            announced10s = false
            return
        }

        val elapsed = currentTick - lobbyStartTick
        val ready =
            elapsed >= TemporossConstants.LOBBY_DURATION_TICKS ||
                deckPlayers.size >= TemporossConstants.MAX_PLAYERS
        if (ready) {
            beginCastOff(deckPlayers.toSet(), currentTick, solo = false)
            deckPlayers.clear()
            lobbyStartTick = -1
            announced10s = false
        } else {
            if (!announced10s && elapsed >= TemporossConstants.LOBBY_DURATION_TICKS - 16) {
                announced10s = true
                for (id in deckPlayers) {
                    val player = playerList.firstOrNull { it.uuid == id } ?: continue
                    player.mes("The mission will begin in 10 seconds.", ChatType.Broadcast)
                }
            }
            refreshLobbyOverlay(currentTick)
        }
    }

    private fun driveCastOff(currentTick: Int) {
        val ready = castingOff.filter { currentTick >= it.departTick }
        for (record in ready) {
            castingOff.remove(record)
            createArena(record.players, record.solo, currentTick)
        }
    }

    private fun driveGames(currentTick: Int) {
        for (session in instanceManager.serverOwnedSessions(TemporossConstants.INSTANCE_KEY)) {
            val game = games[session.id] ?: continue
            if (game.lastProcessedTick == currentTick) continue
            game.lastProcessedTick = currentTick
            if (isPlaying(game)) environment.tick(game, occupants(session), currentTick) { player, points -> awardPoints(player, game, points) }
            when (game.phase) {
                TemporossPhase.ENRAGED,
                TemporossPhase.ENERGY -> tickEnergy(session, game, currentTick)
                TemporossPhase.VULNERABLE -> tickVulnerable(session, game, currentTick)
                TemporossPhase.WON,
                TemporossPhase.STORM_LOSS -> tickReturn(session, game, currentTick)
                TemporossPhase.DEPARTING -> Unit
            }
            if (game.phase != TemporossPhase.DEPARTING) {
                pushHud(session, game, currentTick)
            }
        }
    }

    private fun reclaimEmpty(currentTick: Int) {
        for (session in instanceManager.serverOwnedSessions(TemporossConstants.INSTANCE_KEY)) {
            if (session.occupants.isEmpty()) {
                endGame(session, currentTick)
            }
        }
    }

    private fun beginCastOff(players: Set<Long>, currentTick: Int, solo: Boolean) {
        if (players.isEmpty()) return
        val departing = players.mapNotNull { id -> playerList.firstOrNull { it.uuid == id } }
            .filter { !isCastingOff(it) && instanceManager.sessionForPlayer(it) == null }
            .filter { TemporossConstants.isAtBoarding(it.coords) }
            .toSet()
        if (departing.isEmpty()) return
        for (player in departing) {
            leaveDeck(player, currentTick)
            player.spam("The Spirit Anglers weigh anchor and sail you out to face Tempoross.")
            // Fade transparency is inverted: 255 is clear, zero opaque.
            Cinematic.fadeOverlay(player, startColour = 0, startTransparency = 255, endColour = 0, endTransparency = 0, clientDuration = 50, eventBus = eventBus)
        }
        castingOff += CastOff(departing, currentTick + TemporossConstants.DEPART_FADE_TICKS, solo)
    }

    private fun createArena(departing: Set<Player>, solo: Boolean, currentTick: Int) {
        departing.forEach { Cinematic.closeFadeOverlay(it, eventBus) }
        val present = departing.filter { candidate -> playerList.any { it === candidate } }
            .filter { TemporossConstants.isAtBoarding(it.coords) }
            .filter { instanceManager.sessionForPlayer(it) == null }
        if (present.isEmpty()) return

        val session =
            instanceManager.createServerOwned(TemporossConstants.INSTANCE_KEY, spec, currentTick = currentTick)
                ?: run {
                    present.forEach { it.mes("The storm is too fierce right now - try again shortly.") }
                    return
                }

        var enter: CoordGrid? = null
        val joined = mutableListOf<Player>()
        for (player in present) {
            when (val result = instanceManager.joinServerOwned(player, session, currentTick)) {
                is InstanceManager.Result.Joined -> {
                    enter = result.enter
                    player.coords = result.enter
                    player.abortRoute()
                    player.clearInteraction()
                    instanceManager.finalizeEntry(player, result.session, currentTick)
                    joined += player
                }
                else -> Unit
            }
        }
        val enterCoord = enter ?: run { instanceManager.endServerOwned(session, currentTick); return }
        session.acceptingPlayers = false

        val game = TemporossEncounter(session.id)
        game.solo = solo
        game.createdCycle = currentTick
        game.startPlayers = joined.size
        game.energyMax = TemporossConstants.startingEnergy(joined.size)
        game.energy = game.energyMax
        game.essenceMax = TemporossConstants.startingEssence(joined.size)
        game.essence = game.essenceMax
        game.phase = TemporossPhase.ENERGY
        games[session.id] = game

        game.enter = enterCoord
        spawnArenaNpcs(game, enterCoord)
        environment.start(game, currentTick)

        for (player in joined) {
            clearEncounterFish(player)
            player.uuid?.let { game.points[it] = 0 }
            VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_POINTS, 0)
            openArenaHud(player)
        }
    }

    private fun spawnArenaNpcs(game: TemporossEncounter, enter: CoordGrid) {
        val dx = enter.x - TemporossConstants.ARENA_ENTER.x
        val dz = enter.z - TemporossConstants.ARENA_ENTER.z
        fun translate(source: CoordGrid) = CoordGrid(source.x + dx, source.z + dz, source.level)

        game.boss = spawnNpc(game, TemporossConstants.BOSS_SURFACED, translate(TemporossConstants.BOSS_SOURCE))

        for (source in TemporossConstants.SPIRIT_POOL_SOURCES) {
            spawnNpc(game, TemporossConstants.SPIRIT_POOL_INACTIVE, translate(source))?.let { game.spiritPools += it }
        }
        for (spawn in TemporossConstants.FISHING_SPOTS) {
            spawnNpc(game, spawn.npc, translate(spawn.source), spawn.facing)?.let { game.fishingSpots += it }
        }
        for (spawn in TemporossConstants.AMMO_CRATES) {
            spawnNpc(game, spawn.npc, translate(spawn.source))?.let { game.crateNpcs += it }
        }
        for (spawn in TemporossConstants.CANNONEERS) {
            spawnNpc(game, spawn.npc, translate(spawn.source), spawn.facing)
        }
        for (spawn in TemporossConstants.HOSTS) {
            spawnNpc(game, spawn.npc, translate(spawn.source), spawn.facing)?.let { game.hosts += it }
        }
    }

    private fun spawnNpc(game: TemporossEncounter, symbol: String, coord: CoordGrid, facing: Direction = Direction.South): Npc? {
        val type = ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) ?: return null
        return runCatching {
                val npc = Npc(type, coord)
                npc.mode = dev.openrune.types.NpcMode.None
                npc.moveRestrict = dev.openrune.types.MoveRestrict.NoMove
                npc.respawnDirection(facing)
                npcRepo.add(npc, Int.MAX_VALUE)
                instanceManager.attachNpc(game.instanceId, npc)
                npc
            }
            .getOrNull()
    }

    private fun tickEnergy(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        // Resolve ammunition before storm loss so preloading can trigger another vulnerable phase.
        if (game.energy <= 0) {
            enterVulnerable(session, game)
            return
        }
        if (game.phase == TemporossPhase.ENRAGED) game.intensity += TemporossConstants.ENRAGE_INTENSITY_PER_TICK
        else if (currentTick > game.phaseStarted && (currentTick - game.phaseStarted) % TemporossConstants.INTENSITY_RAMP_TICKS == 0) game.intensity++
        if (game.intensity >= TemporossConstants.INTENSITY_MAX) {
            game.intensity = TemporossConstants.INTENSITY_MAX
            loseByStorm(session, game, currentTick)
        }
    }

    private fun tickVulnerable(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        // Submerged energy-regeneration scaling is provisional.
        game.energyRegenerationRemainder += game.energyMax * maxOf(1, game.cycle - 2)
        val regenerated = game.energyRegenerationRemainder / TemporossConstants.VULNERABLE_TICKS
        game.energyRegenerationRemainder %= TemporossConstants.VULNERABLE_TICKS
        game.energy = (game.energy + regenerated).coerceAtMost(game.energyMax)

        if (game.essence <= 0) {
            win(session, game, currentTick)
            return
        }
        if (game.energy >= game.energyMax) {
            if (game.cycle >= TemporossConstants.MAX_CYCLES) {
                enrage(session, game, currentTick)
            } else {
                reEmerge(session, game)
            }
        }
    }

    private fun enterVulnerable(session: InstanceSession, game: TemporossEncounter) {
        game.phaseStarted = game.lastProcessedTick
        game.energyRegenerationRemainder = 0
        game.phase = TemporossPhase.VULNERABLE
        game.cycle += 1
        game.intensity = 0
        transmogBoss(game, TemporossConstants.BOSS_SUBMERGED)
        setSpiritPools(game, active = true)
        broadcast(session, "<col=98ffff>Tempoross is vulnerable!</col>")
    }

    private fun reEmerge(session: InstanceSession, game: TemporossEncounter) {
        game.phaseStarted = game.lastProcessedTick
        game.phase = TemporossPhase.ENERGY
        environment.start(game, game.lastProcessedTick)
        transmogBoss(game, TemporossConstants.BOSS_SURFACED)
        setSpiritPools(game, active = false)
        broadcast(session, "<col=ff9e7a>Tempoross emerges from the depths once more.</col>")
    }

    private fun win(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        if (!isVulnerable(game)) return
        environment.stop(game, occupants(session))
        game.phase = TemporossPhase.WON
        game.scheduledCycle = currentTick + TemporossConstants.WIN_RETURN_TICKS
        transmogBoss(game, TemporossConstants.BOSS_SUBMERGED)
        setSpiritPools(game, active = false)
        // TODO: show the native completion banner.
        for (player in occupants(session)) {
            val points = game.pointsFor(player.uuid ?: continue)
            val permits = permitsFor(points)
            TemporossStats.recordWin(player, points, currentTick - game.createdCycle)
            player.mes("<col=ff9fff>The skies clear as Tempoross retreats to the depths.</col>")
            player.mes("The Spirit Anglers will ferry you back to the Ruins of Unkah in 20 seconds.")
            player.mes("Points: <col=ef1020>$points</col>.")
            if (permits > 0) {
                val newTotal = (player.vars[TemporossConstants.VARBIT_PERMITS] + permits).coerceAtMost(8000)
                VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_PERMITS, newTotal)
                player.mes("Reward permits: <col=ef1020>$permits</col>. Total permits: <col=ef1020>$newTotal</col>.")
            }
        }
    }

    private fun enrage(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        game.phase = TemporossPhase.ENRAGED
        game.phaseStarted = currentTick
        transmogBoss(game, TemporossConstants.BOSS_ENRAGED)
        setSpiritPools(game, active = false)
        broadcast(
            session,
            "<col=ff9e7a>Tempoross has gathered too much energy. The storm is out of control!</col>",
        )
    }

    private fun loseByStorm(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        environment.stop(game, occupants(session))
        game.phase = TemporossPhase.STORM_LOSS
        game.scheduledCycle = currentTick + TemporossConstants.LOSS_RETURN_TICKS
        broadcast(session, "<col=ff9e7a>The storm becomes too intense and washes you away!</col>")
        clearPoints(session, game)
    }

    private fun tickReturn(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        if (currentTick >= game.scheduledCycle) {
            endGame(session, currentTick)
        }
    }

    fun gameForPlayer(player: Player): TemporossEncounter? {
        val session = instanceManager.sessionForPlayer(player) ?: return null
        if (session.key != TemporossConstants.INSTANCE_KEY) return null
        return games[session.id]
    }

    fun awardPoints(player: Player, game: TemporossEncounter, delta: Int) {
        if (gameForPlayer(player) !== game || !isPlaying(game)) return
        val id = player.uuid ?: return
        game.addPoints(id, delta)
        VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_POINTS, game.pointsFor(id))
    }

    fun depositEnergy(game: TemporossEncounter, amount: Int) {
        if (!isEnergyPhase(game) || amount <= 0) return
        game.energy = (game.energy - amount).coerceAtLeast(0)
    }

    fun drainEssence(game: TemporossEncounter, amount: Int) {
        if (!isVulnerable(game) || amount <= 0) return
        game.essence = (game.essence - amount).coerceAtLeast(0)
    }

    fun isEnergyPhase(game: TemporossEncounter): Boolean = game.phase == TemporossPhase.ENERGY || game.phase == TemporossPhase.ENRAGED

    fun isVulnerable(game: TemporossEncounter): Boolean = game.phase == TemporossPhase.VULNERABLE

    fun isPlaying(game: TemporossEncounter): Boolean = isEnergyPhase(game) || isVulnerable(game)

    fun canAct(player: Player, game: TemporossEncounter): Boolean =
        gameForPlayer(player) === game && isPlaying(game) && player !in game.tethers && !Stun.isActionBlocked(player)

    fun cannonIndex(game: TemporossEncounter, crate: Npc): Int? =
        game.crateNpcs.indexOfFirst { it === crate }.takeIf { it >= 0 }

    fun trackLoader(game: TemporossEncounter, player: Player, index: Int) {
        game.loaders[player] = TemporossLoader(index, player.currentMapClock, player.coords)
    }

    fun load(game: TemporossEncounter, index: Int, fish: TemporossAmmo) {
        check(isPlaying(game))
        game.cannons[index].ammunition.addLast(fish)
    }

    fun anchorIndex(game: TemporossEncounter, loc: BoundLocInfo) = environment.anchorIndex(game, loc)
    fun tether(game: TemporossEncounter, player: Player, index: Int) = environment.tether(game, player, index)
    fun repair(game: TemporossEncounter, index: Int) = environment.repair(game, index)
    fun hasFire(game: TemporossEncounter, npc: Npc) = ownsNpc(game, npc) && environment.hasFire(game, npc)
    fun douse(game: TemporossEncounter, npc: Npc) = environment.douse(game, npc)

    // Require both session and arena entity identity.
    fun ownsNpc(game: TemporossEncounter, npc: Npc): Boolean =
        npc.isSlotAssigned && instanceManager.instanceForNpc(npc) == game.instanceId

    fun forfeit(player: Player, currentTick: Int): CoordGrid {
        val session = instanceManager.sessionForPlayer(player)
        if (session == null || session.key != TemporossConstants.INSTANCE_KEY) {
            return TemporossConstants.RETURN_COORD
        }
        player.ifCloseOverlay(TemporossConstants.HUD_INTERFACE, eventBus)
        clearEncounterFish(player)
        games[session.id]?.let { environment.untether(it, player, false); it.loaders.remove(player); it.points.remove(player.uuid) }
        VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_POINTS, 0)
        return instanceManager.leave(player, session, currentTick)
    }

    private fun openArenaHud(player: Player) {
        Cinematic.closeFadeOverlay(player, eventBus)
        player.ifOpenOverlay(TemporossConstants.HUD_INTERFACE, TemporossConstants.HUD_OVERLAY_TARGET, eventBus)
        // Native 437:0 already runs 4071 with component handles; a second no-argument call corrupts them.
    }

    private fun openLobbyOverlay(player: Player) {
        player.ifOpenOverlay(
            TemporossConstants.LOBBY_HUD_INTERFACE,
            TemporossConstants.LOBBY_HUD_OVERLAY_TARGET,
            eventBus,
        )
    }

    private fun pushHud(session: InstanceSession, game: TemporossEncounter, currentTick: Int) {
        if ((currentTick - game.createdCycle) % TemporossConstants.HUD_CADENCE_TICKS != 0) return
        for (player in occupants(session)) {
            val points = game.pointsFor(player.uuid ?: continue)
            player.runClientScript(
                TemporossConstants.HUD_UPDATE_SCRIPT,
                *TemporossHud.arguments(game, points),
            )
        }
    }

    private fun refreshLobbyOverlay(currentTick: Int) {
        val count = deckPlayers.size
        val next =
            if (lobbyStartTick < 0) {
                "Awaiting players"
            } else {
                val remainingTicks =
                    (lobbyStartTick + TemporossConstants.LOBBY_DURATION_TICKS - currentTick).coerceAtLeast(0)
                val secondsLeft = remainingTicks * 3 / 5
                val bucket = ((secondsLeft + 4) / 5 * 5).coerceIn(5, 30)
                "$bucket seconds"
            }
        for (id in deckPlayers) {
            val player = playerList.firstOrNull { it.uuid == id } ?: continue
            player.ifSetText(
                TemporossConstants.LOBBY_HUD_PLAYERS,
                "Players Ready<br><col=ffffff>${if (count == 0) "None" else count.toString()}</col>",
            )
            player.ifSetText(
                TemporossConstants.LOBBY_HUD_NEXT,
                "Next Departure<br><col=ffffff>$next</col>",
            )
        }
    }

    private fun endGame(session: InstanceSession, currentTick: Int) {
        for (player in occupants(session)) {
            player.ifCloseOverlay(TemporossConstants.HUD_INTERFACE, eventBus)
            clearEncounterFish(player)
            player.abortRoute()
            player.clearInteraction()
            player.pendingSequence = EntitySeq.ZERO
        }
        games.remove(session.id)?.let { environment.stop(it, occupants(session)) }
        instanceManager.endServerOwned(session, currentTick)
    }

    fun clearEncounterFish(player: Player) {
        for (type in listOf(TemporossConstants.RAW_HARPOONFISH, TemporossConstants.COOKED_HARPOONFISH, TemporossConstants.CRYSTAL_HARPOONFISH)) {
            val id = type.asRSCM(RSCMType.OBJ)
            for (slot in player.inv.indices) {
                if (player.inv[slot]?.id == id) player.inv[slot] = null
            }
        }
    }

    fun onPlayerLeave(player: Player, instanceId: InstanceId) {
        clearEncounterFish(player)
        player.ifCloseOverlay(TemporossConstants.HUD_INTERFACE, eventBus)
        player.pendingSequence = EntitySeq.ZERO
        games[instanceId]?.let { environment.untether(it, player, false); it.loaders.remove(player); it.points.remove(player.uuid) }
    }

    private fun occupants(session: InstanceSession): List<Player> =
        session.occupants.mapNotNull { id -> playerList.firstOrNull { it.uuid == id } }

    private fun broadcast(session: InstanceSession, text: String) {
        for (player in occupants(session)) {
            player.mes(text, ChatType.Broadcast)
        }
    }

    private fun clearPoints(session: InstanceSession, game: TemporossEncounter) {
        for (player in occupants(session)) {
            val id = player.uuid ?: continue
            game.points[id] = 0
            VarPlayerIntMapSetter.set(player, TemporossConstants.VARBIT_POINTS, 0)
        }
    }

    private fun transmogBoss(game: TemporossEncounter, symbol: String) {
        val boss = game.boss ?: return
        val type = ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) ?: return
        boss.transmog(type, Int.MAX_VALUE)
    }

    private fun setSpiritPools(game: TemporossEncounter, active: Boolean) {
        val symbol =
            if (active) TemporossConstants.SPIRIT_POOL_ACTIVE else TemporossConstants.SPIRIT_POOL_INACTIVE
        val type = ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)) ?: return
        for (pool in game.spiritPools) {
            pool.transmog(type, Int.MAX_VALUE)
        }
    }

    private fun permitsFor(points: Int): Int {
        if (points < TemporossConstants.PERMIT_BASE_POINTS) return 0
        val over = points - TemporossConstants.PERMIT_BASE_POINTS
        val guaranteed = 1 + over / TemporossConstants.PERMIT_STEP_POINTS
        val remainder = over % TemporossConstants.PERMIT_STEP_POINTS
        val bonus = if (random.of(TemporossConstants.PERMIT_STEP_POINTS) < remainder) 1 else 0
        return guaranteed + bonus
    }
}
