@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.minigames.gnomeball

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.rangedLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Singleton
internal class GnomeballGame @Inject constructor(
    private val clock: MapClock,
    private val npcs: NpcList,
    private val world: WorldRepository,
    private val ray: RayCastValidator,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val hits: StandardPlayerHitModifier,
    private val events: EventBus,
) {
    internal sealed interface Destination {
        data class Goal(val scored: Boolean) : Destination
        data class Winger(val npc: Npc, val uid: NpcUid) : Destination
        data class Receiver(val player: Player) : Destination
    }
    internal data class Flight(val destination: Destination, val arrives: Int)
    private data class Holder(val npc: Npc, val owner: Player, val winger: Boolean)
    private val sessions = mutableSetOf<Player>()
    private val flights = mutableMapOf<Player, Flight>()
    private val holders = mutableMapOf<NpcUid, Holder>()
    private val actors = mutableMapOf<NpcUid, Npc>()
    private var initialised = false

    fun enter(player: Player): Boolean {
        if (!GnomeballData.contains(player.coords)) return false
        if (GnomeballState.active(player)) return true
        removeLoans(player)
        claim(player)
        val equipment = listOf(3, 5).mapNotNull { player.worn[it] }
        if (player.inv.freeSpace() < equipment.size) {
            player.mes("Please make room to remove your weapon and shield before playing Gnomeball.")
            return false
        }
        for (slot in listOf(3, 5)) {
            val obj = player.worn[slot] ?: continue
            check(player.invAdd(player.inv, obj.id, obj.count, vars = obj.vars).success)
            player.worn[slot] = null
        }
        val deposit = player.invMap.getOrPut(GnomeballState.Deposit)
        val brought = player.inv.count(GnomeballState.Ball)
        val balls = player.inv.objs.withIndex().filter { it.value?.id == GnomeballState.Ball.asRSCM() }
        if (deposit.freeSpace() < balls.size) {
            player.rebuildAppearance()
            player.mes("Collect your stored Gnomeballs from the referee before starting another game.")
            return false
        }
        for ((slot, ball) in balls) {
            deposit[deposit.objs.indexOfFirst { it == null }] = ball
            player.inv[slot] = null
        }
        GnomeballState.clear(player)
        GnomeballState.set(player, GnomeballState.Session, 1)
        GnomeballState.set(player, GnomeballState.Brought, if (brought > 0) 1 else 0)
        sessions += player
        player.rebuildAppearance()
        player.ifOpenOverlay("interface.gnomeball", "component.toplevel_osrs_stretch:side4", events)
        MiscOutput.setPlayerOp(player, 5, "Pass")
        player.mes("Speak to the referee for a ball. Score five goals to win!")
        return true
    }

    fun requestBall(player: Player, referee: Npc): Boolean {
        if (!GnomeballState.active(player) && !enter(player)) return false
        if (player.vars[GnomeballState.Phase] == GnomeballState.Won) {
            player.mes("You've won! Leave the field to collect your Gnomeball.")
            return false
        }
        if (player.vars[GnomeballState.Phase] != GnomeballState.Empty ||
            !ready(player) || referee.coords.chebyshevDistance(player.coords) > 2) {
            player.mes("Retrieve your ball or wait for the throw to finish first.")
            return false
        }
        referee.facePlayer(player)
        referee.anim("seq.gnome_throw")
        player.faceNpc(referee)
        player.mes("The ref throws the ball into the air.")
        player.soundSynth("synth.WHISTLE")
        player.soundSynth("synth.GNOMEBALL_REFEREE_GIVES_BALL")
        val effect = GnomeballData.projectile
        projectile(referee.coords, player.coords, referee.slotId + 1, -(player.slotId + 1),
            effect[4], effect[5], effect[2], effect[3], effect[8], effect[9])
        GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Flying)
        flights[player] = Flight(Destination.Receiver(player), clock.cycle + 2)
        return true
    }

    fun canThrow(player: Player, target: CoordGrid, range: Int): Boolean =
        GnomeballData.contains(target) && player.coords.level == target.level &&
            player.coords.chebyshevDistance(target) <= range && ray.hasLineOfSight(player.coords, target)

    fun shoot(player: Player): Boolean {
        if (!checkBall(player) || !ready(player)) return false
        if (!canThrow(player, GnomeballData.goal, GnomeballData.shotRange)) {
            player.mes("Move closer to the net before throwing the ball.")
            return false
        }
        val distance = player.coords.chebyshevDistance(GnomeballData.goal)
        val scored = random.of(100) < GnomeballData.shotChance(player.rangedLvl, distance)
        player.mes("You throw the ball at the goal...")
        val ticks = throwBall(player, GnomeballData.goal)
        player.soundSynth("synth.THROW", delay = 10)
        flights[player] = Flight(Destination.Goal(scored), clock.cycle + ticks)
        return true
    }

    fun pass(player: Player, winger: Npc): Boolean {
        val type = actor(winger) ?: return false
        if (!type.winger || !checkBall(player) || !ready(player)) return false
        if (winger.uid in holders) { player.mes("That winger already has a ball."); return false }
        if (!canThrow(player, winger.coords, GnomeballData.passRange)) return false
        val ticks = throwBall(player, winger.coords, winger.slotId + 1)
        holders[winger.uid] = Holder(winger, player, true)
        flights[player] = Flight(Destination.Winger(winger, winger.uid), clock.cycle + ticks)
        return true
    }

    fun pass(player: Player, receiver: Player): Boolean {
        if (player === receiver || !checkBall(player) || !ready(player)) return false
        if (!GnomeballState.active(receiver) || receiver.vars[GnomeballState.Phase] != GnomeballState.Empty) {
            player.mes("That player cannot receive a Gnomeball right now.")
            return false
        }
        if (!canThrow(player, receiver.coords, GnomeballData.passRange)) return false
        val ticks = throwBall(player, receiver.coords, -(receiver.slotId + 1))
        GnomeballState.set(receiver, GnomeballState.Phase, GnomeballState.Flying)
        flights[player] = Flight(Destination.Receiver(receiver), clock.cycle + ticks)
        return true
    }

    fun tackle(player: Player, npc: Npc): Boolean {
        val type = actor(npc) ?: return false
        if (type.winger || !GnomeballState.active(player) || !ready(player) ||
            player.coords.chebyshevDistance(npc.coords) > 1 ||
            !ray.hasLineOfSight(player.coords, npc.coords)) return false
        val holder = holders[npc.uid]
        if (holder != null && holder.owner !== player) {
            player.mes("That gnome is holding another player's ball.")
            return false
        }
        if (player.vars[GnomeballState.Phase] == GnomeballState.Flying ||
            player.vars[GnomeballState.Phase] == GnomeballState.Won ||
            clock.cycle < npc.vars[GnomeballState.NpcCooldown]) return false
        cooldown(player, GnomeballData.actionTicks)
        player.faceNpc(npc)
        npc.facePlayer(player)
        val success = random.of(100) < GnomeballData.tackleChance(player.agilityLvl)
        if (success) {
            player.anim("seq.human_gnome_tackle")
            player.soundSynth("synth.GNOMEBALL_TACKLED_GNOME")
            npc.abortRoute()
            npc.transmog(type.floor, GnomeballData.recoveryTicks)
            npc.anim("seq.gnome_tackled")
            npc.vars[GnomeballState.NpcCooldown] = clock.cycle + GnomeballData.recoveryTicks
            if (holder != null) { holders.remove(npc.uid); giveBall(player) }
        } else {
            player.anim("seq.human_gnome_tackle_fail")
            npc.anim("seq.gnome_dodge_tackle")
            player.soundSynth("synth.GNOMEBALL_TACKLED_GNOME_MISS")
        }
        return true
    }

    fun tick() {
        if (!initialised) {
            for (npc in npcs) spawned(npc)
            initialised = true
        }
        for (player in sessions.toList()) {
            if (!player.isSlotAssigned || !GnomeballData.contains(player.coords) ||
                player.statMap.getCurrentLevel("stat.hitpoints") <= 0) { leave(player); continue }
            if (player.vars[GnomeballState.Phase] == GnomeballState.Holding &&
                !GnomeballState.loan(player.worn[3])) {
                removeLoans(player)
                GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
            }
            flights[player]?.takeIf { clock.cycle >= it.arrives }?.let { finishFlight(player, it) }
        }
        for ((uid, holder) in holders.toMap()) {
            if (!holder.npc.isValidTarget() || holder.npc.uid != uid || !GnomeballState.active(holder.owner)) {
                release(uid)
                if (GnomeballState.active(holder.owner)) GnomeballState.set(holder.owner, GnomeballState.Phase, GnomeballState.Empty)
                continue
            }
            if (!holder.winger && holder.npc.routeDestination.isEmpty() && random.of(3) == 0) {
                val destination = holder.npc.spawnCoords.translate(random.of(-2, 2), random.of(-2, 2))
                if (GnomeballData.contains(destination)) holder.npc.walk(destination)
            }
            if (holder.owner in flights || clock.cycle < holder.npc.vars[GnomeballState.NpcDeadline]) continue
            if (holder.winger) {
                release(uid)
                holder.npc.facePlayer(holder.owner)
                holder.npc.anim("seq.gnome_throw")
                val ticks = flightTicks(holder.npc.coords, holder.owner.coords)
                projectile(holder.npc.coords, holder.owner.coords, holder.npc.slotId + 1,
                    -(holder.owner.slotId + 1), 110, 163, 10, ticks * 30 - 1, 25, 11)
                flights[holder.owner] = Flight(Destination.Receiver(holder.owner), clock.cycle + ticks)
            } else {
                release(uid)
                GnomeballState.set(holder.owner, GnomeballState.Phase, GnomeballState.Empty)
                holder.owner.mes("The gnome returns the ball to the referee.")
            }
        }
        moveDefence()
    }

    internal fun finishFlight(player: Player, flight: Flight) {
        if (flights[player] !== flight || !GnomeballState.active(player)) return
        flights.remove(player)
        when (val target = flight.destination) {
            is Destination.Goal -> {
                GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
                if (target.scored) goal(player) else player.mes("... and miss.")
            }
            is Destination.Winger -> {
                if (target.npc.uid != target.uid || !target.npc.isValidTarget()) {
                    release(target.uid)
                    GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
                } else {
                    target.npc.abortRoute()
                    target.npc.anim("seq.gnome_catch")
                    target.npc.transmog(checkNotNull(actor(target.npc)).ball, Int.MAX_VALUE)
                    target.npc.vars[GnomeballState.NpcDeadline] = clock.cycle + GnomeballData.wingerTicks
                }
            }
            is Destination.Receiver -> {
                if (target.player !== player) GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
                if (GnomeballState.active(target.player) && GnomeballData.contains(target.player.coords) &&
                    target.player.vars[GnomeballState.Phase] == GnomeballState.Flying) {
                    giveBall(target.player)
                    target.player.mes("You jump up and catch it.")
                }
            }
        }
    }

    private fun goal(player: Player) {
        val score = player.vars[GnomeballState.Score] + 1
        if (score !in 1..GnomeballData.goalXp.size) return
        GnomeballState.set(player, GnomeballState.Score, score)
        xp(player, GnomeballData.goalXp[score - 1])
        player.mes("You score a goal!")
        for (npc in npcs) if (npc.type.id == "npc.gnomecheerleader".asRSCM() &&
            npc.coords.chebyshevDistance(player.coords) <= 20) npc.anim("seq.gnome_cl1")
        if (score == GnomeballData.goalXp.size) {
            xp(player, GnomeballData.winXp)
            GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Won)
            GnomeballState.set(player, GnomeballState.Score, 0)
            if (player.vars[GnomeballState.Brought] == 0) {
                val deposit = player.invMap.getOrPut(GnomeballState.Deposit)
                deposit[deposit.objs.indexOfFirst { it == null }] = InvObj(GnomeballState.Ball)
            }
            player.mes("You've won the game! Leave the field to collect your Gnomeball.")
        }
    }

    private fun moveDefence() {
        for ((uid, npc) in actors.toMap()) {
            if (!npc.isValidTarget() || npc.uid != uid) { actors.remove(uid); release(uid); continue }
            if (uid in holders || clock.cycle < npc.vars[GnomeballState.NpcCooldown]) continue
            val type = actor(npc) ?: continue
            val target = if (type.winger) null else sessions.filter { holding(it) &&
                npc.coords.chebyshevDistance(it.coords) <= GnomeballData.pursuitRange }
                .minByOrNull { npc.coords.chebyshevDistance(it.coords) }
            if (target != null) {
                val distance = npc.coords.chebyshevDistance(target.coords)
                if (distance <= 1 && ray.hasLineOfSight(npc.coords, target.coords)) steal(npc, target)
                else npc.walk(target.coords)
            } else if (npc.routeDestination.isEmpty() && random.of(8) == 0) {
                val destination = npc.spawnCoords.translate(random.of(-2, 2), random.of(-2, 2))
                if (GnomeballData.contains(destination)) npc.walk(destination)
            }
        }
    }

    private fun steal(npc: Npc, player: Player) {
        npc.vars[GnomeballState.NpcCooldown] = clock.cycle + GnomeballData.tackleTicks
        if (!ready(player)) return
        npc.abortRoute()
        npc.facePlayer(player)
        if (random.of(100) < GnomeballData.stealChance(player.agilityLvl)) {
            npc.anim("seq.gnome_human_tackle")
            player.anim("seq.human_tackled")
            player.soundSynth("synth.GNOMEBALL_TACKLED_PLAYER")
            removeLoans(player)
            GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Stolen)
            cooldown(player, GnomeballData.actionTicks)
            holders[npc.uid] = Holder(npc, player, false)
            npc.transmog(checkNotNull(actor(npc)).ball, Int.MAX_VALUE)
            npc.vars[GnomeballState.NpcDeadline] = clock.cycle + GnomeballData.stolenTicks
            player.queueHit(npc, 1, HitType.Typeless, random.of(GnomeballData.tackleMinDamage, GnomeballData.tackleMaxDamage), hits)
            player.mes("The gnome tackles you and takes the ball!")
        } else {
            npc.anim("seq.gnome_human_tackle_fail")
            player.soundSynth("synth.GNOMEBALL_TACKLED_MISS")
        }
    }

    fun spawned(npc: Npc) {
        if (actor(npc) != null && GnomeballData.contains(npc.spawnCoords)) {
            npc.noneMode()
            actors[npc.uid] = npc
        }
    }

    fun deleted(npc: Npc) {
        actors.remove(npc.uid)
        val holder = holders[npc.uid] ?: return
        release(npc.uid)
        if (GnomeballState.active(holder.owner)) GnomeballState.set(holder.owner, GnomeballState.Phase, GnomeballState.Empty)
    }

    private fun actor(npc: Npc) = GnomeballData.players.firstOrNull { it.base.id == npc.type.id }
    fun holding(player: Player) = GnomeballState.active(player) &&
        player.vars[GnomeballState.Phase] == GnomeballState.Holding && GnomeballState.loan(player.worn[3])
    private fun checkBall(player: Player): Boolean {
        if (holding(player)) return true
        player.mes("You need a ball in your hand to throw.")
        return false
    }
    private fun ready(player: Player) = clock.cycle >= player.vars[GnomeballState.Cooldown]
    private fun cooldown(player: Player, ticks: Int) = GnomeballState.set(player, GnomeballState.Cooldown, clock.cycle + ticks)
    private fun giveBall(player: Player) {
        if (player.worn[3] != null || player.worn[5] != null) {
            GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
            player.mes("Remove your weapon and shield before taking the ball.")
            return
        }
        removeLoans(player)
        player.worn[3] = GnomeballState.loan()
        GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Holding)
        cooldown(player, 1)
        player.rebuildAppearance()
    }

    private fun throwBall(player: Player, destination: CoordGrid, target: Int = 0): Int {
        val ticks = flightTicks(player.coords, destination)
        player.faceSquare(destination)
        player.anim("seq.human_throw_gnomeball")
        removeLoans(player)
        GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Flying)
        cooldown(player, GnomeballData.actionTicks)
        val effect = GnomeballData.projectile
        projectile(player.coords, destination, 0, target,
            effect[5], if (target == 0) effect[6] else 110, effect[0],
            GnomeballData.throwEnd(player.coords.chebyshevDistance(destination)), effect[7], effect[9])
        return ticks
    }

    private fun flightTicks(from: CoordGrid, to: CoordGrid) =
        1 + GnomeballData.throwEnd(from.chebyshevDistance(to)) / 30
    private fun projectile(from: CoordGrid, to: CoordGrid, source: Int, target: Int,
        startHeight: Int, endHeight: Int, start: Int, end: Int, angle: Int, progress: Int) {
        world.projAnim(ProjAnim("spotanim.gnomeball_travel".asRSCM(), startHeight, endHeight,
            start, end, angle, progress, source, target, from, to))
    }
    private fun xp(player: Player, amount: Double) {
        for (stat in listOf("stat.agility", "stat.ranged")) player.statAdvance(stat, amount * xpMods.get(player, stat))
    }
    private fun release(uid: NpcUid) {
        val holder = holders.remove(uid) ?: return
        if (holder.npc.uid == uid && holder.npc.isSlotAssigned) holder.npc.resetTransmog()
    }

    fun equipmentChanged(player: Player) {
        if (!GnomeballState.active(player) || GnomeballState.loan(player.worn[3])) return
        removeLoans(player)
        if (player.vars[GnomeballState.Phase] == GnomeballState.Holding)
            GnomeballState.set(player, GnomeballState.Phase, GnomeballState.Empty)
    }

    fun removeLoans(player: Player) {
        for (inv in (player.invMap.values + listOf(player.inv, player.worn)).distinct()) {
            for (slot in inv.objs.indices) if (GnomeballState.loan(inv[slot])) inv[slot] = null
        }
        player.rebuildAppearance()
    }

    fun leave(player: Player) {
        flights.remove(player)?.let { flight ->
            val receiver = (flight.destination as? Destination.Receiver)?.player
            if (receiver != null && receiver !== player && GnomeballState.active(receiver) &&
                receiver.vars[GnomeballState.Phase] == GnomeballState.Flying)
                GnomeballState.set(receiver, GnomeballState.Phase, GnomeballState.Empty)
        }
        for ((uid, holder) in holders.toMap()) if (holder.owner === player) release(uid)
        sessions.remove(player)
        removeLoans(player)
        GnomeballState.clear(player)
        player.ifCloseOverlay("interface.gnomeball", events)
        MiscOutput.clearPlayerOp(player, 5, "Pass")
        claim(player)
    }

    fun claim(player: Player) {
        val deposit = player.invMap[GnomeballState.Deposit] ?: return
        for (slot in deposit.objs.indices) {
            val obj = deposit[slot] ?: continue
            if (player.invAdd(player.inv, obj.id, obj.count, vars = obj.vars).success) deposit[slot] = null
        }
        if (deposit.objs.any { it != null }) player.mes("The referee is keeping your Gnomeballs safe. Make room and speak to him to collect them.")
    }

    internal fun flight(player: Player) = flights[player]
    internal fun sessionCount() = sessions.size
    internal fun holderCount() = holders.size
}
