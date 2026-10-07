package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants as C
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.canStep
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal class TemporossEnvironment(
    private val locs: LocRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val npcs: NpcRepository,
    private val instances: InstanceManager,
    private val random: GameRandom,
) {
    fun start(game: TemporossEncounter, tick: Int) {
        game.phaseStarted = tick
        game.nextLightning = tick + C.FIRST_LIGHTNING_TICKS
        game.nextWave = tick + C.FIRST_WAVE_TICKS
        game.nextTorrent = tick + C.FIRST_TORRENT_TICKS
        game.nextSpotChange = tick + 25
        for ((index, anchor) in C.ANCHORS.withIndex()) {
            val coord = game.translate(anchor.source)
            locs.findAll(coord).firstOrNull { it.id == anchor.normal.asRSCM(RSCMType.LOC) }
                ?.let { game.anchors[index] = it }
        }
    }

    fun tick(game: TemporossEncounter, players: List<Player>, tick: Int, award: (Player, Int) -> Unit) {
        for (player in game.tethers.keys.toList()) {
            if (player !in players) { untether(game, player, false); continue }
            if (player.routeRequest != null || !player.routeDestination.isEmpty()) {
                player.abortRoute()
                player.clearMapFlag()
                player.mes("You must untether yourself to move away.")
            }
        }
        game.loaders.entries.removeIf { (player, load) -> player !in players || load.tick < tick - 1 || player.coords != load.coords || !isLoading(game, player, load.cannon) }
        tickLightning(game, tick)
        tickFires(game, players, tick)
        if (game.waveVisualStart >= 0 && tick >= game.waveVisualStart) {
            game.waveVisualStart = -1
            game.waveVisualActive = true
            players.forEach { it.runClientScript(C.WAVE_ADVANCE_SCRIPT, C.WAVE_ADVANCE_CLIENT_CYCLES) }
        }
        if (game.waveVisualRecede >= 0 && tick >= game.waveVisualRecede) {
            game.waveVisualRecede = -1
            game.waveVisualActive = false
            players.forEach { it.runClientScript(C.WAVE_RECEDE_SCRIPT, C.WAVE_RECEDE_CLIENT_CYCLES) }
        }
        if (game.waveImpact >= 0 && tick >= game.waveImpact) {
            game.waveImpact = -1
            wave(game, players, award)
            game.waveVisualRecede = tick + C.WAVE_VISUAL_DELAY_TICKS
        }
        if (game.torrentImpact >= 0 && tick >= game.torrentImpact) {
            game.torrentImpact = -1
            val index = game.torrentCannon
            game.cannons[index].disabledUntil = tick + C.CANNON_DISABLE_TICKS
            for ((player, load) in game.loaders.toMap()) {
                if (load.cannon != index || load.tick < tick - 1 || load.coords != player.coords || !isLoading(game, player, index)) continue
                player.clearInteraction()
                player.pendingSequence = EntitySeq.ZERO
                Stun.apply(player, 5, message = "The cannon's electricity stuns you!")
                game.loaders.remove(player)
            }
            players.forEach { it.mes("<col=ff9e7a>A cannon is stunned by lightning...</col>") }
        }
        tickCannons(game, tick)
        val surfaced = game.phase == TemporossPhase.ENERGY || game.phase == TemporossPhase.ENRAGED
        if (!surfaced || game.energy * 10 <= game.energyMax) return
        if (tick >= game.nextSpotChange && game.fishingSpots.isNotEmpty()) {
            game.nextSpotChange = tick + 35
            game.specialSpot = if (game.specialSpot < 0) random.of(game.fishingSpots.size) else -1
            for ((index, spot) in game.fishingSpots.withIndex()) {
                val symbol = if (game.specialSpot == index) C.SPECIAL_FISHING_SPOT else C.FISHING_SPOTS[index].npc
                ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC))?.let { spot.transmog(it, Int.MAX_VALUE) }
            }
        }
        if (tick >= game.nextWave && game.waveImpact < 0) {
            game.nextWave = tick + C.SPECIAL_INTERVAL_TICKS
            game.waveImpact = tick + C.WAVE_WARNING_TICKS
            game.waveVisualStart = tick + C.WAVE_VISUAL_DELAY_TICKS
            players.forEach { it.mes("<col=ff9e7a>A colossal wave closes in...</col>") }
        }
        if (tick >= game.nextLightning) {
            game.nextLightning = tick + C.SPECIAL_INTERVAL_TICKS
            players.forEach { it.mes("<col=ff9e7a>A strong wind blows as clouds roll in...</col>") }
            for (player in players.shuffledWith(random).take((game.startPlayers / 5 + 1).coerceAtMost(8))) {
                val coord = fireOrigin(game, player.coords) ?: continue
                val cloud = spawn(game, "npc.tempoross_lightning_cloud", coord) ?: continue
                cloud.anim("seq.tempoross_lightning_cloud_growing")
                val shadow = LocInfo(2, coord, LocEntity("loc.tempoross_lightning_shadow".asRSCM(RSCMType.LOC), 10, 0))
                if (!locs.add(shadow, Int.MAX_VALUE)) { removeNpc(cloud); continue }
                game.lightning += TemporossLightning(cloud, shadow, tick + C.LIGHTNING_WARNING_TICKS)
            }
        }
        if (tick >= game.nextTorrent && game.torrentImpact < 0) {
            game.nextTorrent = tick + C.SPECIAL_INTERVAL_TICKS
            game.torrentCannon = random.of(4)
            game.torrentImpact = tick + C.PROJECTILE_TICKS
            game.boss?.let { boss ->
                boss.anim("seq.tempoross_torrent_cast")
                boss.spotanim("spotanim.tempoross_torrent_cast")
                projectile("spotanim.tempoross_torrent_projectile", boss.coords, game.translate(C.CANNON_SOURCES[game.torrentCannon]))
            }
            players.forEach { it.mes("<col=ff9e7a>A charged torrent of water surges towards a nearby cannon...</col>") }
        }
    }

    private fun isLoading(game: TemporossEncounter, player: Player, index: Int): Boolean {
        val interaction = player.interaction as? InteractionNpcOp ?: return false
        return interaction.op == InteractionOp.Op1 && interaction.target === game.crateNpcs.getOrNull(index)
    }

    private fun tickCannons(game: TemporossEncounter, tick: Int) {
        val surfaced = game.phase == TemporossPhase.ENERGY || game.phase == TemporossPhase.ENRAGED
        for (shot in game.shots.filter { tick >= it.impact }) {
            game.shots.remove(shot)
            if (surfaced && game.energy > 0) {
                game.energy = (game.energy - shot.fish.damage).coerceAtLeast(0)
                game.boss?.spotanim("spotanim.tempoross_harpoonfish_projectile_impact")
            }
        }
        for ((index, cannon) in game.cannons.withIndex()) {
            val disabled = tick < cannon.disabledUntil
            game.cannonFlags[index] = if (disabled) 0 else 1
            val coord = game.translate(C.CANNON_SOURCES[index])
            val cannonLoc = locs.findAll(coord).firstOrNull { it.shape.id == 10 }
            if (cannon.displayedDisabled != disabled) {
                cannon.displayedDisabled = disabled
                cannonLoc?.let { replace(it, "loc.tempoross_cannon_${index + 1}" + if (disabled) "_disabled" else "") }
            }
            val fish = cannon.takeShot(tick, surfaced && game.energy > 0)
            if (fish != null) {
                cannonLoc?.let { world.locAnim(it, if (index < 2) "seq.tempoross_cannon_firing" else "seq.tempoross_cannon_firing_mirror") }
                game.boss?.let { projectile(fish.projectile, coord, it.coords, it.slotId + 1) }
                game.shots += TemporossShot(fish, tick + C.PROJECTILE_TICKS)
            }
            val fill = when { cannon.ammunition.size >= 15 -> 100; cannon.ammunition.size >= 8 -> 50; else -> 0 }
            if (fill != cannon.displayedFill) {
                cannon.displayedFill = fill
                val crateCoord = game.translate(C.AMMO_CRATES[index].source)
                locs.findAll(crateCoord).firstOrNull { it.shape.id == 10 }?.let {
                    replace(it, "loc.tempoross_crate_ammunition_${if (fill == 0) "empty" else fill}_${index + 1}")
                }
            }
        }
    }

    private fun tickLightning(game: TemporossEncounter, tick: Int) {
        for (strike in game.lightning.filter { tick >= it.impact }) {
            game.lightning.remove(strike)
            val coord = strike.shadow.coords
            locs.del(strike.shadow, Int.MAX_VALUE)
            removeNpc(strike.cloud)
            spawn(game, "npc.tempoross_lightning_strike", coord, 3)
            spawnFire(game, coord, tick)
        }
    }

    private fun tickFires(game: TemporossEncounter, players: List<Player>, tick: Int) {
        if (tick % 2 == 0) for (player in players) {
            if (game.fires.keys.none { player.coords.level == it.level && player.coords.x in it.x..it.x + 1 && player.coords.z in it.z..it.z + 1 }) continue
            if (loseSupplies(player, 1) > 0) player.mes("The fire destroys some of your supplies!")
            player.clearInteraction()
            player.pendingSequence = EntitySeq.ZERO
        }
        for ((coord, fire) in game.fires.toMap()) {
            if (fire.spreads >= 2 || tick < fire.nextSpread) continue
            fire.nextSpread = tick + C.FIRE_SPREAD_TICKS
            fire.spreads++
            val options = listOf(CoordGrid(coord.x + 2, coord.z, coord.level), CoordGrid(coord.x - 2, coord.z, coord.level),
                CoordGrid(coord.x, coord.z + 2, coord.level), CoordGrid(coord.x, coord.z - 2, coord.level)).shuffledWith(random)
            for (next in options) {
                if (spawnFire(game, next, tick)) { fire.npc.spotanim("spotanim.tempoross_fire_spread"); break }
            }
        }
    }

    internal fun spawnFire(game: TemporossEncounter, coord: CoordGrid, tick: Int): Boolean {
        if (game.fires.size >= C.MAX_FIRES || game.fires.keys.any { kotlin.math.abs(it.x - coord.x) < 2 && kotlin.math.abs(it.z - coord.z) < 2 }) return false
        if (!isFireTile(game, coord)) return false
        val npc = spawn(game, C.FIRE_NPC, coord) ?: return false
        val loc = LocInfo(2, coord, LocEntity(C.FIRE_LOC.asRSCM(RSCMType.LOC), 10, 0))
        if (!locs.add(loc, Int.MAX_VALUE)) { removeNpc(npc); return false }
        npc.spotanim("spotanim.tempoross_fire_spawn")
        game.fires[coord] = TemporossFire(npc, loc, tick + C.FIRE_SPREAD_TICKS)
        return true
    }

    fun douse(game: TemporossEncounter, npc: Npc): Boolean {
        val fire = game.fires[npc.coords]?.takeIf { it.npc === npc } ?: return false
        removeFire(game, fire)
        return true
    }

    fun hasFire(game: TemporossEncounter, npc: Npc) = game.fires[npc.coords]?.npc === npc

    private fun isFireTile(game: TemporossEncounter, coord: CoordGrid): Boolean {
        val sourceX = coord.x - game.enter.x + C.ARENA_ENTER.x
        val sourceZ = coord.z - game.enter.z + C.ARENA_ENTER.z
        if (coord.level != 0 || sourceX !in 3030..3063 || sourceZ !in 2825..2875) return false
        for (x in coord.x..coord.x + 1) for (z in coord.z..coord.z + 1) {
            val tile = CoordGrid(x, z, coord.level)
            if (!collision.isZoneValid(tile) || collision.isWalkBlocked(tile) || locs.findAll(tile).any()) return false
        }
        return true
    }

    private fun fireOrigin(game: TemporossEncounter, near: CoordGrid): CoordGrid? {
        for (dx in 0..2) for (dz in 0..2) {
            val coord = CoordGrid(near.x - dx, near.z - dz, near.level)
            if (isFireTile(game, coord)) return coord
        }
        return null
    }

    internal fun wave(game: TemporossEncounter, players: List<Player>, award: (Player, Int) -> Unit) {
        for (player in players) {
            if (player in game.tethers) {
                award(player, 10)
                player.mes("...the rope keeps you securely upright as the wave washes over you.")
            } else {
                loseSupplies(player, random.of(1, 6))
                player.clearInteraction()
                player.pendingSequence = EntitySeq.ZERO
                // Resolve wave movement against collision to avoid water/furniture landings.
                val direction = if (player.coords.z < game.enter.z) Direction.North else Direction.South
                repeat(3) {
                    if (collision.canStep(player.coords, direction)) {
                        val dest = CoordGrid(player.coords.x + direction.xOff, player.coords.z + direction.zOff, player.coords.level)
                        PathingEntityCommon.teleport(player, collision, dest)
                    }
                }
                Stun.apply(player, 5, message = "The wave washes you away, scattering your supplies!")
            }
            player.spotanim("spotanim.tempoross_watersplash")
        }
        for (fire in game.fires.values.toList()) removeFire(game, fire)
        for ((index, loc) in game.anchors.toMap()) {
            if (index in game.brokenAnchors || random.of(100) >= 15 + game.intensity / 10) continue
            game.brokenAnchors += index
            game.anchors[index] = replace(loc, C.ANCHORS[index].broken)
            for ((player, tether) in game.tethers.toMap()) if (tether.anchor == index) untether(game, player, false)
        }
    }

    internal fun loseSupplies(player: Player, count: Int): Int {
        val allowed = listOf(C.HAMMER, C.ROPE, C.BUCKET, C.WATER_BUCKET, C.RAW_HARPOONFISH, C.COOKED_HARPOONFISH, C.CRYSTAL_HARPOONFISH)
            .map { it.asRSCM(RSCMType.OBJ) }.toSet()
        val slots = player.inv.indices.filter { player.inv[it]?.id in allowed }.shuffledWith(random).take(count)
        for (slot in slots) player.inv[slot] = null
        return slots.size
    }

    fun anchorIndex(game: TemporossEncounter, loc: BoundLocInfo): Int? =
        game.anchors.entries.firstOrNull { it.value.coords == loc.coords }?.key

    fun tether(game: TemporossEncounter, player: Player, index: Int): Boolean {
        if (index in game.brokenAnchors || index !in game.anchors) return false
        if (player in game.tethers) { untether(game, player, true); return true }
        // Do not reuse a combat freeze whose expiry could clear the tether.
        if (player.frozen) { player.mes("You cannot tether yourself while frozen."); return false }
        if (C.ROPE !in player.inv && !C.SPIRIT_ANGLER.all { it in player.worn }) {
            player.mes("You need a rope to tether yourself.")
            return false
        }
        game.tethers[player] = TemporossTether(index, player.frozen)
        player.frozen = true
        player.abortRoute()
        player.clearInteraction()
        player.clearMapFlag()
        VarPlayerIntMapSetter.set(player, C.TETHER_VARBIT, C.ANCHORS[index].tetherValue)
        player.anim("seq.tempoross_tether_loop")
        player.spotanim("spotanim.tempoross_tether_loop")
        player.mes("You securely tether yourself to the ${if (C.ANCHORS[index].mast) "mast" else "totem pole"}.")
        return true
    }

    fun untether(game: TemporossEncounter, player: Player, message: Boolean) {
        val old = game.tethers.remove(player) ?: return
        player.abortRoute()
        player.clearMapFlag()
        player.frozen = old.wasFrozen
        VarPlayerIntMapSetter.set(player, C.TETHER_VARBIT, 0)
        player.pendingSequence = EntitySeq.ZERO
        player.resetSpotanim()
        player.spotanim("spotanim.tempoross_untether")
        if (message) player.mes("You untether yourself from the ${if (C.ANCHORS[old.anchor].mast) "mast" else "totem pole"}.")
    }

    fun repair(game: TemporossEncounter, index: Int): Boolean {
        if (index !in game.brokenAnchors) return false
        val loc = game.anchors[index] ?: return false
        val repaired = replace(loc, C.ANCHORS[index].normal)
        if (repaired.id != C.ANCHORS[index].normal.asRSCM(RSCMType.LOC)) return false
        game.brokenAnchors.remove(index)
        game.anchors[index] = repaired
        return true
    }

    fun stop(game: TemporossEncounter, players: List<Player> = emptyList()) {
        if (game.waveVisualActive) {
            players.forEach { player ->
                player.runClientScript(C.WAVE_RECEDE_SCRIPT, C.WAVE_RECEDE_CLIENT_CYCLES)
            }
        }
        for (player in game.tethers.keys.toList()) untether(game, player, false)
        for (fire in game.fires.values.toList()) removeFire(game, fire)
        for (strike in game.lightning) { locs.del(strike.shadow, Int.MAX_VALUE); removeNpc(strike.cloud) }
        game.lightning.clear()
        game.shots.clear()
        game.loaders.clear()
        game.waveImpact = -1
        game.waveVisualStart = -1
        game.waveVisualRecede = -1
        game.waveVisualActive = false
        game.torrentImpact = -1
    }

    private fun removeFire(game: TemporossEncounter, fire: TemporossFire) {
        game.fires.remove(fire.loc.coords)
        locs.del(fire.loc, Int.MAX_VALUE)
        removeNpc(fire.npc)
    }

    private fun replace(loc: LocInfo, type: String): LocInfo {
        val replacement = LocInfo(loc.layer, loc.coords, LocEntity(type.asRSCM(RSCMType.LOC), loc.shape.id, loc.angle.id))
        return if (locs.add(replacement, Int.MAX_VALUE)) replacement else loc
    }

    private fun spawn(game: TemporossEncounter, type: String, coords: CoordGrid, duration: Int = Int.MAX_VALUE): Npc? {
        val definition = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: return null
        val npc = Npc(definition, coords)
        npc.mode = dev.openrune.types.NpcMode.None
        npc.moveRestrict = dev.openrune.types.MoveRestrict.NoMove
        npcs.add(npc, duration)
        instances.attachNpc(game.instanceId, npc)
        return npc
    }

    private fun removeNpc(npc: Npc) { if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE) }

    private fun projectile(symbol: String, from: CoordGrid, to: CoordGrid, target: Int = 0) {
        world.projAnim(ProjAnim(symbol.asRSCM(RSCMType.SPOTANIM), 40, 40, 0, C.PROJECTILE_TICKS * 30 - 1,
            15, 0, 0, target, from, to))
    }

    private fun <T> List<T>.shuffledWith(random: GameRandom): List<T> {
        val result = toMutableList()
        for (i in result.lastIndex downTo 1) { val j = random.of(i + 1); val old = result[i]; result[i] = result[j]; result[j] = old }
        return result
    }
}
