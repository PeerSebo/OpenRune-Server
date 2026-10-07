package org.rsmod.content.raids.theatreofblood

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.instances.*
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.deathResetTimers
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hasProtectItemPrayer
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.clearSafeDeathMark
import org.rsmod.api.player.ironman.isHardcoreIronman
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.ui.*
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

internal class TheatreRun(val session: InstanceSession, val controller: Npc) {
    val members = mutableListOf<Player>()
    val adds = mutableListOf<Npc>()
    var boss: Npc? = null
    val roomrow get() = TheatreData.roomsList[room]
    var room: Int
        get() = controller.vars["varn.theatre_room"]
        set(value) { controller.vars["varn.theatre_room"] = value }
    var stage: Int
        get() = controller.vars["varn.theatre_stage"]
        set(value) { controller.vars["varn.theatre_stage"] = value }
    var tick: Int
        get() = controller.vars["varn.theatre_tick"]
        set(value) { controller.vars["varn.theatre_tick"] = value }
    var phase: Int
        get() = controller.vars["varn.theatre_phase"]
        set(value) { controller.vars["varn.theatre_phase"] = value }
    var teamSize: Int
        get() = controller.vars["varn.theatre_hp_boost"]
        set(value) { controller.vars["varn.theatre_hp_boost"] = value }
}

@Singleton
internal class TheatreManager @Inject constructor(
    val deps: BossDeps,
    private val instances: InstanceManager,
    private val inventory: TheatreInventory,
    private val drops: PlayerDeathDrops,
    private val eventBus: EventBus,
    private val encounters: Provider<TheatreEncounters>,
    private val nylocas: Provider<TheatreNylocas>,
    private val presentation: TheatrePresentation,
    private val roomObjects: TheatreRoomObjects,
    internal val parties: TheatreParties,
) {
    private val runs = mutableMapOf<InstanceId, TheatreRun>()
    private val npcOwners = IdentityHashMap<Npc, TheatreRun>()

    fun run(player: Player): TheatreRun? =
        instances.sessionForPlayer(player)?.let { runs[it.id] }

    fun run(npc: Npc): TheatreRun? = npcOwners[npc]

    fun memberIndex(run: TheatreRun, player: Player): Int = run.members.indexOf(player)

    fun players(run: TheatreRun): List<Player> = run.members.filter {
        it.isSlotAssigned && !it.loggingOut && it.uuid in run.session.occupants &&
            it.vars[DEAD] == 0 && it.hitpoints > 0
    }

    fun combatants(run: TheatreRun): List<Player> = players(run).filter {
        val point = source(run, it.coords)
        val maze = TheatreData.mechanics.sotetsegMazeBounds
        run.roomrow.contains(point) || (run.room == 3 && run.phase == 1 &&
            point.x in maze[0]..maze[2] && point.z in maze[1]..maze[3] && point.level == maze[4])
    }

    fun active(run: TheatreRun): Boolean =
        runs[run.session.id] === run && run.stage == ACTIVE &&
            instances.sessionForId(run.session.id) === run.session

    fun coord(run: TheatreRun, source: CoordGrid): CoordGrid =
        checkNotNull(instances.resolveCoord(run.session, source))

    fun source(run: TheatreRun, actual: CoordGrid): CoordGrid {
        val anchor = run.roomrow.center
        val mapped = coord(run, anchor)
        return CoordGrid(anchor.x + actual.x - mapped.x, anchor.z + actual.z - mapped.z, actual.level)
    }

    suspend fun enter(access: ProtectedAccess) = with(access) {
        if (run(player) != null || instances.sessionForPlayer(player) != null) {
            mes("You are already inside an instance.")
            return@with
        }
        val party = parties.get(player)
        if (party == null) {
            mes("Use the notice board to form or join a Theatre party first.")
            return@with
        }
        if (!inventory.canEnter(player)) return@with
        val existing = party.session
        val result = if (existing != null) {
            if (runs[existing.id]?.stage?.let { it != LOBBY } == true) {
                mes("That Theatre party has already begun.")
                return@with
            }
            instances.join(player, existing, deps.mapClock.cycle, forceAccess = true)
        } else {
            val entry = TheatreData.rules.lobbyEntry
            val area = InstanceArea.copyRegions(
                TheatreData.rules.regions,
                enterCoord = RegionLocal(entry.level, entry.mx, entry.mz, entry.lx, entry.lz),
                exitCoord = TheatreData.rules.exit,
            )
            val spec = InstanceSettings(
                maxPlayers = TheatreData.rules.maxPlayers,
                destroyWhenEmpty = true,
                bossName = "Theatre of Blood",
                teamSize = TheatreData.rules.maxPlayers,
            ).withArea(area, TheatreData.rules.rowId)
            instances.create(player, KEY, spec, InstanceAccess.Private, deps.mapClock.cycle)
        }
        val session = when (result) {
            is InstanceManager.Result.Created -> result.session
            is InstanceManager.Result.Joined -> result.session
            is InstanceManager.Result.Failed -> { mes(result.reason); return@with }
        }
        party.session = session
        val entry = when (result) {
            is InstanceManager.Result.Created -> result.enter
            is InstanceManager.Result.Joined -> result.enter
            else -> error("Unreachable")
        }
        var entranceComplete = false
        try {
            presentation.beginEntrance(this, eventBus)
            if (instances.sessionForId(session.id) !== session || parties.get(player) !== party) {
                mes("That Theatre party is no longer available.")
                return@with
            }
            if (result is InstanceManager.Result.Joined) {
                if (runs[session.id]?.stage?.let { it != LOBBY } == true) {
                    mes("That Theatre party has already begun.")
                    return@with
                }
                val admission = instances.join(player, session, deps.mapClock.cycle, forceAccess = true)
                if (admission is InstanceManager.Result.Failed) {
                    mes(admission.reason)
                    return@with
                }
            }
            telejump(entry, TeleportType.Exempt)
            if (player.coords != entry) return@with
            instances.finalizeEntry(player, session, deps.mapClock.cycle)
            val raid = runs.getOrPut(session.id) {
                val source = CoordGrid(3138, 4420, 0)
                val controller = Npc("npc.tob_sotetseg_creeper", coordFor(session, source))
                controller.mode = NpcMode.None
                controller.hideAllOps()
                deps.npcRepo.add(controller, Int.MAX_VALUE)
                instances.attachNpc(session.id, controller)
                TheatreRun(session, controller)
            }
            party.run = raid
            raid.members.removeAll { it.uuid == player.uuid }
            raid.members += player
            if (raid.members.size == 1) roomObjects.enterRoom(raid)
            set(player, DEAD, 0)
            set(player, DAMAGE, 0)
            set(player, DEATHS, 0)
            set(player, ROOMS, 0)
            set(player, SUPPLIES, 0)
            presentation.open(player, raid, eventBus)
            updateHud(raid)
            loadDelay()
            if (run(player) !== raid) return@with
            presentation.revealEntrance(this, raid.roomrow.name)
            entranceComplete = true
            mes("Your Normal Theatre party is ready. Use the barrier to begin.")
        } finally {
            if (!entranceComplete) {
                presentation.cancelEntrance(player)
                if (run(player) == null) presentation.close(player, eventBus)
            }
            if (result is InstanceManager.Result.Created) {
                instances.cancelPendingEntry(player, deps.mapClock.cycle)
                if (instances.sessionForId(session.id) == null && party.session === session) {
                    party.session = null
                }
            }
        }
    }

    fun startEncounter(player: Player) {
        val raid = run(player) ?: return
        if (raid.stage != LOBBY && raid.stage != WAITING) return
        raid.teamSize = raid.session.occupants.size
        startRoom(raid)
    }

    fun advanceRoom(player: Player) {
        val raid = run(player) ?: return
        if (raid.stage == COMPLETED) {
            claimRewards(player)
            return
        }
        if (raid.stage != CLEARED) {
            player.mes("Defeat the inhabitants of this room first.")
            return
        }
        if (!isLeader(raid, player)) {
            player.mes("Your party leader must begin the next room.")
            return
        }
        raid.room++
        arriveRoom(raid)
    }

    private fun arriveRoom(raid: TheatreRun) {
        clearActors(raid)
        raid.stage = WAITING
        raid.tick = 0
        raid.phase = 0
        for (member in raid.members.filter { it.uuid in raid.session.occupants }) {
            restore(member)
            PathingEntityCommon.telejump(member, deps.collision, coord(raid, raid.roomrow.arrival))
            set(member, DISCONNECT, 0)
            val instruction = if (raid.room == 5) "Speak to the Vyre Orator" else "Pass the barrier"
            member.mes("$instruction when you are ready to face ${raid.roomrow.name}.")
        }
        roomObjects.enterRoom(raid)
        updateHud(raid)
    }

    private fun startRoom(raid: TheatreRun) {
        check(raid.room in TheatreData.roomsList.indices)
        clearActors(raid)
        raid.stage = ACTIVE
        raid.tick = 0
        raid.phase = 0
        for (member in raid.members.filter { it.uuid in raid.session.occupants }) {
            restore(member)
            set(member, "varp.theatre_room_damage", 0)
            set(member, "varp.theatre_room_deaths", 0)
            set(member, "varp.theatre_tornado_next", 0)
            set(member, DISCONNECT, 1)
            member.mes("${raid.roomrow.name} begins.")
        }
        if (raid.room == 2) nylocas.get().start(raid) else encounters.get().start(raid)
        roomObjects.enterRoom(raid)
        updateHud(raid)
        nextTick(raid)
    }

    private fun nextTick(raid: TheatreRun) {
        val room = raid.room
        deps.worldQueues.addDeferred(1) {
            if (!active(raid) || raid.room != room) return@addDeferred
            if (players(raid).isEmpty()) {
                fail(raid)
                return@addDeferred
            }
            raid.tick++
            if (raid.boss == null || checkNotNull(raid.boss).hitpoints > 0) {
                if (raid.room == 2) nylocas.get().tick(raid) else encounters.get().tick(raid)
            }
            updateHud(raid)
            if (active(raid)) nextTick(raid)
        }
    }

    fun spawn(raid: TheatreRun, type: String, source: CoordGrid, health: Int = 0): Npc {
        val npc = Npc(type, coord(raid, source))
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.respawns = false
        if (health > 0) {
            npc.baseHitpointsLvl = health
            npc.hitpoints = health
        }
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        instances.attachNpc(raid.session.id, npc)
        npcOwners[npc] = raid
        raid.adds += npc
        return npc
    }

    fun remove(raid: TheatreRun, npc: Npc) {
        npcOwners.remove(npc)
        raid.adds.remove(npc)
        if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
    }

    fun schedule(raid: TheatreRun, ticks: Int, action: () -> Unit) {
        val room = raid.room
        deps.worldQueues.addDeferred(ticks.coerceAtLeast(1)) {
            if (active(raid) && raid.room == room) action()
        }
    }

    fun gfx(raid: TheatreRun, sourceCoord: CoordGrid, symbol: String) {
        val effect = SpotanimType(symbol.asRSCM(RSCMType.SPOTANIM))
        deps.worldRepo.spotanimMap(effect, coord(raid, sourceCoord))
    }

    fun damage(
        raid: TheatreRun, target: Player, amount: Int, style: HitType = HitType.Typeless,
        source: Npc = checkNotNull(raid.boss),
        prayerPenetration: Int = 0,
    ) {
        if (!active(raid) || target !in combatants(raid)) return
        val room = raid.room
        target.queueImpactHit(source, 1, style, amount.coerceAtLeast(0),
            modifier = PlayerHitModifier { player ->
                if (!active(raid) || raid.room != room || player !in combatants(raid) ||
                    (source === raid.boss && source.hitpoints <= 0)) {
                    damage = 0
                } else {
                    deps.playerHitModifier.run { modify(player) }
                    player.queueCombatRetaliate(source, 1)
                    if (style != HitType.Magic) player.combatPlayDefendAnim()
                }
            }, prayerPenetration = prayerPenetration)
    }

    fun roomCleared(raid: TheatreRun) {
        if (!active(raid)) return
        raid.stage = CLEARED
        clearActors(raid)
        for (member in raid.members.filter { it.uuid in raid.session.occupants }) {
            set(member, ROOMS, member.vars[ROOMS] + 1)
            set(member, DISCONNECT, 0)
            restore(member)
            member.mes("${raid.roomrow.name} has been defeated.")
            if (raid.room == 1 || raid.room == 3) {
                val points = TheatreData.rules.supplyPoints
                set(member, SUPPLIES, member.vars[SUPPLIES] + points)
                member.mes("You have earned $points supply points. Use the supply chest before continuing.")
            }
        }
        if (raid.room == TheatreData.roomsList.lastIndex) complete(raid)
        else roomObjects.enterRoom(raid)
        updateHud(raid)
    }

    private fun isLeader(raid: TheatreRun, player: Player): Boolean =
        raid.members.firstOrNull { it.uuid in raid.session.occupants } === player

    private fun complete(raid: TheatreRun) {
        raid.stage = COMPLETED
        val eligible = raid.members.filter { it.uuid in raid.session.occupants && it.vars[DAMAGE] > 0 }
        val rules = TheatreData.rules
        fun penalize(value: Int, deaths: Int) = TheatreMechanics.applyRepeatedPenalty(
            value, deaths, rules.deathRetainedNumerator, rules.deathRetainedDenominator,
        )
        for (member in eligible) {
            set(member, "varp.theatre_reward_points", penalize(member.vars[DAMAGE], member.vars[DEATHS]).coerceAtLeast(1))
        }
        val teamDeaths = eligible.sumOf { it.vars[DEATHS].toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val uniqueChance = penalize(rules.uniqueNumerator * 10000, teamDeaths)
        val unique = if (eligible.isNotEmpty() && deps.random.of(rules.uniqueDenominator * 10000) < uniqueChance) {
            val total = eligible.sumOf { it.vars["varp.theatre_reward_points"].toLong() }
            val unit = (total / 1000000).coerceAtLeast(1)
            val weights = eligible.map { (it.vars["varp.theatre_reward_points"] / unit).coerceAtLeast(1).toInt() }
            var roll = deps.random.of(weights.sum())
            eligible[weights.indexOfFirst { weight -> roll -= weight; roll < 0 }]
        } else null
        for (member in raid.members.filter { it.uuid in raid.session.occupants }) {
            stripDawnbringer(member)
            val generated = inventory.generateRewards(
                member, unique = member === unique, contributed = member.vars[DAMAGE] > 0,
                quantityPercent = penalize(100, member.vars[DEATHS]),
            )
            check(generated) { "Theatre completion must commit a fresh reward chest" }
            set(member, KC, member.vars[KC] + 1)
            member.mes("Your Theatre of Blood completion count is: <col=ff0000>${member.vars[KC]}</col>.")
            member.mes("Your rewards are stored in the chest. They can also be collected outside.")
        }
        roomObjects.complete(raid)
        updateHud(raid)
    }

    suspend fun die(access: ProtectedAccess): Boolean = with(access) {
        val raid = run(player) ?: return false
        if (!active(raid)) return false
        if (player.vars[DEAD] != 0) return true
        captureDeathProtection(player)
        set(player, DEAD, 1)
        set(player, DEATHS, player.vars[DEATHS] + 1)
        set(player, "varp.theatre_room_deaths", player.vars["varp.theatre_room_deaths"] + 1)
        combatClearQueue()
        player.clearInteractionRoute()
        player.disablePrayers()
        revokeHardcoreStatus(player)
        anim("seq.human_death")
        soundSynth("synth.HUMAN_DEATH")
        delay(4)
        clearQueue("queue.death")
        resetAnim()
        player.cureAllToxins()
        player.deathResetTimers()
        statRestore("stat.hitpoints")
        if (active(raid)) {
            telejump(coord(raid, raid.roomrow.arrival), TeleportType.Exempt)
            mes("You will rejoin your party when this room is cleared.")
            if (players(raid).isEmpty()) fail(raid)
        }
        true
    }

    fun isWiping(player: Player): Boolean = player.vars[DEAD] == 2

    internal fun recoverLostGear(player: Player): Boolean {
        val chest = inventory.recovery(player)
        if (chest.isNotEmpty()) {
            player.mes("Your previous recovery items remain stored. Your carried items have been retained.")
            return false
        }
        revokeHardcoreStatus(player)
        stripDawnbringer(player)
        val context = PlayerDeathContext(
            player, TheatreData.rules.exit, false, -1, false, true, false,
            when (player.vars["varp.theatre_recovery_pending"]) {
                1 -> false
                2 -> true
                else -> player.hasProtectItemPrayer()
            }, false, player.gamemode, null,
        )
        val handling = PlayerDeathHandling(
            PlayerDeathDrops.standardKeepCount(context.hasProtectItem), null,
            PlayerDeathDrops.DROP_DURATION_STANDARD, 0, false, UntradeableHandling.DROP, false,
        )
        val selected = drops.selectDrops(player, context, handling)
        val lost = selected.supplyPile + selected.lostTradeable + selected.lostUntradeable
        if (lost.size > chest.size || selected.kept.size > player.inv.size) {
            player.mes("Your items could not fit safely into recovery storage and have been retained.")
            return false
        }
        set(player, DEAD, 2)
        try {
            drops.applyDrops(player, selected, handling, TheatreData.rules.exit)
        } finally { set(player, DEAD, 0) }
        set(player, DISCONNECT, 0)
        set(player, "varp.theatre_recovery_pending", 0)
        return true
    }

    internal fun captureDeathProtection(player: Player) {
        set(player, "varp.theatre_recovery_pending", if (player.hasProtectItemPrayer()) 2 else 1)
    }

    private fun revokeHardcoreStatus(player: Player) {
        player.clearSafeDeathMark()
        if (player.isHardcoreIronman) {
            player.setGamemode(PlayerGamemode.IRONMAN)
            player.mes("You have lost your Hardcore Ironman status.")
        }
    }

    fun fail(raid: TheatreRun) {
        if (!active(raid)) return
        raid.stage = FAILED
        clearActors(raid)
        for (member in raid.members.toList()) {
            if (member.uuid !in raid.session.occupants) continue
            if (!member.pendingShutdown && recoverLostGear(member)) {
                member.mes("Your party has fallen. Recover your lost items outside the Theatre.")
            }
            leave(member)
        }
    }

    fun leave(player: Player) {
        val raid = run(player) ?: return
        set(player, DISCONNECT, 0)
        set(player, DEAD, 0)
        stripDawnbringer(player)
        player.combatClearQueue()
        player.clearInteractionRoute()
        player.queueList.removeAll("queue.hit")
        player.queueList.removeAll("queue.impact_hit")
        presentation.close(player, eventBus)
        val exit = instances.leave(player, raid.session, deps.mapClock.cycle)
        parties.remove(player)
        PathingEntityCommon.telejump(player, deps.collision, exit)
    }

    fun restoreHud(player: Player) {
        val raid = run(player) ?: return
        presentation.open(player, raid, eventBus)
    }

    fun departed(player: Player, id: InstanceId) {
        val raid = runs[id] ?: return
        if (player.vars[DISCONNECT] != 0) {
            if (!player.pendingShutdown) recoverLostGear(player)
            set(player, DISCONNECT, 0)
        }
        stripDawnbringer(player)
        set(player, DEAD, 0)
        set(player, "varp.theatre_recovery_pending", 0)
        presentation.close(player, eventBus)
        if (raid.stage == LOBBY) raid.members.remove(player)
        updateHud(raid)
    }

    fun login(player: Player) {
        if (player.vars[DISCONNECT] == 0) return
        val recovered = recoverLostGear(player)
        set(player, DISCONNECT, 0)
        set(player, "varp.theatre_recovery_pending", 0)
        PathingEntityCommon.telejump(player, deps.collision, TheatreData.rules.exit)
        restore(player)
        player.mes(if (recovered) "Your interrupted Theatre attempt has ended. Lost items are stored outside."
            else "Your interrupted Theatre attempt has ended. Your carried items have been retained.")
    }

    fun ended(id: InstanceId) {
        val raid = runs.remove(id) ?: return
        parties.ended(raid.session)
        clearActors(raid)
        raid.members.clear()
        npcOwners.remove(raid.controller)
    }

    private fun clearActors(raid: TheatreRun) {
        roomObjects.clear(raid)
        encounters.get().clear(raid)
        raid.adds.toList().forEach { remove(raid, it) }
        raid.boss = null
    }

    private fun restore(player: Player) {
        set(player, "varp.theatre_recovery_pending", 0)
        set(player, DEAD, 0)
        player.combatClearQueue()
        player.clearInteractionRoute()
        player.statRestore("stat.hitpoints")
        player.statRestore("stat.prayer")
        player.runEnergy = 10000
        set(player, "varp.sa_energy", 1000)
    }

    private fun updateHud(raid: TheatreRun) {
        presentation.update(raid, raid.members.filter {
            it.isSlotAssigned && !it.loggingOut && it.uuid in raid.session.occupants
        })
    }

    private fun coordFor(session: InstanceSession, source: CoordGrid) =
        checkNotNull(instances.resolveCoord(session, source))

    internal fun stripDawnbringer(player: Player) {
        val id = "obj.verzik_special_weapon".asRSCM(RSCMType.OBJ)
        for (inv in listOf(player.inv, player.worn)) {
            for (slot in inv.objs.indices) if (inv[slot]?.id == id) inv[slot] = null
        }
        player.rebuildAppearance()
    }

    fun endDawnbringerPhase(raid: TheatreRun) {
        roomObjects.clearDawnbringer(raid)
        raid.members.forEach(::stripDawnbringer)
    }

    fun claimRewards(player: Player) {
        inventory.claimRewards(player)
        run(player)?.let { roomObjects.refresh(player, it) }
    }

    companion object {
        const val KEY = "theatre_of_blood"
        const val DEAD = "varp.theatre_dead"
        const val DAMAGE = "varp.theatre_damage"
        const val DEATHS = "varp.theatre_deaths"
        const val ROOMS = "varp.theatre_rooms"
        const val SUPPLIES = "varp.theatre_supply_points"
        const val DISCONNECT = "varp.theatre_disconnect"
        const val KC = "varp.theatre_kc"
        const val LOBBY = 0
        const val ACTIVE = 1
        const val CLEARED = 2
        const val COMPLETED = 3
        const val FAILED = 4
        const val WAITING = 5

        fun set(player: Player, symbol: String, value: Int) {
            VarPlayerIntMapSetter.set(player, symbol, value)
        }
    }
}
