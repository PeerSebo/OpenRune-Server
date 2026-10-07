package org.rsmod.content.minigames.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.*
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
class WintertodtGame @Inject constructor(
    private val collision: CollisionFlagMap, private val clock: MapClock, private val players: PlayerList, private val npcs: NpcList,
    private val locs: LocRepository, private val world: WorldRepository,
    private val events: EventBus, private val random: GameRandom,
) {
    internal val round by lazy { WintertodtRound(WintertodtData.rules) }
    private val members = mutableSetOf<Player>()
    private val attendants = mutableListOf<Npc>()
    private val hazards = mutableListOf<WinterHazard>()
    private var lastCycle = Int.MIN_VALUE
    private var nextAttack = 0
    fun initialise() {
        WintertodtData.actions
        WintertodtData.rewards
        WintertodtData.uniques
        WintertodtData.warmItems
        round.initialise(clock.cycle)
        attendants.addAll(npcs.filter { npc ->
            npc.id == "npc.wint_wizard".asRSCM(RSCMType.NPC) ||
                npc.id == "npc.wint_wizard_down".asRSCM(RSCMType.NPC) ||
                npc.id == "npc.wint_brewma".asRSCM(RSCMType.NPC)
        })
        synchroniseCamps()
    }
    fun tick() {
        val tick = clock.cycle
        if (lastCycle == tick) return
        lastCycle = tick
        for (player in members.toList()) {
            if (!WintertodtData.rules.contains(player.coords) || player.forceDisconnect ||
                player.clientDisconnected.get() || player.loggingOut || player.hitpoints <= 0) leave(player)
        }
        for (player in players) {
            if (player !in members && WintertodtData.rules.contains(player.coords) &&
                !player.forceDisconnect && !player.clientDisconnected.get() && !player.loggingOut &&
                player.hitpoints > 0) join(player)
        }
        for (npc in attendants) {
            npc.mode = NpcMode.None
            npc.moveRestrict = MoveRestrict.NoMove
            npc.abortRoute()
        }
        if (round.phase == WinterPhase.Waiting && tick >= round.deadline && members.isNotEmpty()) {
            round.begin(tick)
            nextAttack = tick + 12
            members.forEach { resetRound(it) }
            synchroniseCamps()
            members.forEach { it.mes("The Wintertodt awakens!") }
        }
        if (round.phase == WinterPhase.Fighting) {
            if (members.isEmpty()) {
                round.finish(tick)
                hazards.clear()
                synchroniseCamps()
            } else {
                resolveHazards(tick)
                if (round.advance()) victory(tick)
                else {
                    if (tick % WintertodtData.rules.coldTicks == 0) members.toList().forEach {
                        if (WintertodtData.rules.exposed(it.coords)) damage(it, WinterDamage.Cold)
                    }
                    if (tick >= nextAttack) attack(tick)
                }
            }
        }
        for (player in members.toList()) {
            if (tick >= player.vars[WintertodtState.RegenTick]) {
                restoreWarmth(player, 80 + warmItems(player) * 10)
                WintertodtState.set(player, WintertodtState.RegenTick,
                    tick + WintertodtData.rules.regenTicks)
            }
            hud(player)
        }
    }
    private fun join(player: Player) {
        if (player.statBase("stat.firemaking") < WintertodtData.rules.level) {
            PathingEntityCommon.telejump(player, collision, WintertodtData.rules.exit)
            player.mes("You need a Firemaking level of 50 to enter the Wintertodt's prison.")
            return
        }
        members.add(player)
        WintertodtState.clearSession(player)
        resetRound(player)
        WintertodtState.set(player, WintertodtState.Warmth, 1000)
        WintertodtState.set(player, WintertodtState.RegenTick, clock.cycle + WintertodtData.rules.regenTicks)
        player.ifOpenOverlay("interface.wint_status", "component.toplevel_osrs_stretch:overlay_atmosphere", events)
        if (warmItems(player) < 4) player.mes("You feel the cold wind strike you as you enter; perhaps you should find some warmer clothes.")
    }
    fun leave(player: Player) {
        if (!members.remove(player)) return
        WintertodtState.clearSession(player)
        player.ifCloseOverlay("interface.wint_status", events)
        player.resetAnim()
        cleanupItems(player)
    }
    fun login(player: Player) {
        WintertodtState.clearSession(player)
        cleanupItems(player)
    }
    fun shutdown() { members.toList().forEach(::leave); hazards.clear() }
    internal fun canAct(player: Player, id: Int = round.id): Boolean =
        player in members && id == round.id && round.phase == WinterPhase.Fighting &&
            WintertodtData.rules.contains(player.coords) && player.hitpoints > 0 &&
            player.vars[WintertodtState.Warmth] > 0 &&
            !player.forceDisconnect && !player.clientDisconnected.get() && !player.loggingOut
    internal fun award(player: Player, action: String) {
        if (!canAct(player)) return
        val data = WintertodtData.actions.getValue(action)
        WintertodtState.set(player, WintertodtState.Points,
            (player.vars[WintertodtState.Points].toLong() + data.points)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (data.xpTenths > 0 && (action != "repair" || player.vars["varbit.poh_house_location"] > 0)) {
            player.statAdvance(data.stat, player.stat(data.stat) * data.xpTenths / 10.0)
        }
    }
    internal fun warmItems(player: Player): Int =
        player.worn.objs.count { it != null && it.id in WintertodtData.warmItems }.coerceAtMost(4)
    internal fun restoreWarmth(player: Player, amount: Int) {
        if (player !in members) return
        WintertodtState.set(player, WintertodtState.Warmth,
            (player.vars[WintertodtState.Warmth] + amount).coerceIn(0, 1000))
    }
    internal fun food(player: Player, obj: String, heal: Int) {
        if (heal >= 4 && obj != "obj.triangle_sandwich" && !obj.contains("wine"))
            restoreWarmth(player, 350)
    }
    internal fun campAt(coords: CoordGrid): Int? =
        WintertodtData.camps.indexOfFirst { it.brazier == coords }.takeIf { it >= 0 }
    internal fun pyroIndex(npc: Npc): Int? =
        WintertodtData.camps.indexOfFirst { it.pyro == npc.spawnCoords }.takeIf { it >= 0 }
    internal fun synchroniseCamps() {
        locs.add(WintertodtData.rules.storm,
            if (round.phase == WinterPhase.Fighting) "loc.wint_snow_storm" else "loc.wint_snow_storm_idle",
            Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        WintertodtData.camps.forEachIndexed { index, data ->
            locs.add(data.brazier, round.camps[index].state.loc, Int.MAX_VALUE,
                LocAngle.West, LocShape.CentrepieceStraight)
            val npc = attendants.firstOrNull { it.spawnCoords == data.pyro &&
                it.id == "npc.wint_wizard".asRSCM(RSCMType.NPC) } ?: return@forEachIndexed
            if (round.camps[index].health == 0)
                npc.transmog(ServerCacheManager.getNpc("npc.wint_wizard_down".asRSCM())!!, Int.MAX_VALUE)
            else npc.resetTransmog()
        }
    }
    private fun resetRound(player: Player) {
        WintertodtState.set(player, WintertodtState.Round, round.id)
        WintertodtState.set(player, WintertodtState.Points, 0)
        WintertodtState.set(player, WintertodtState.ActionTick, 0)
    }
    private fun hud(player: Player) {
        WintertodtState.set(player, "varbit.wint_transmit_respawndelay",
            if (round.phase == WinterPhase.Waiting) (round.deadline - clock.cycle).coerceIn(0, 100) else 0)
        player.runClientScript("clientscript.[clientscript,wint_update]",
            *round.hud(player.vars[WintertodtState.Points]))
        if (round.phase == WinterPhase.Waiting) {
            player.runClientScript("clientscript.[clientscript,wint_timer_init]",
                "component.wint_status:energy_title".asRSCM(RSCMType.COMPONENT))
        }
    }
    private fun attack(tick: Int) {
        val exposed = members.filter { WintertodtData.rules.exposed(it.coords) }
        if (exposed.isEmpty()) { nextAttack = tick + 10; return }
        val roll = random.of(10)
        val kind = when { roll < 3 -> WinterHazardKind.Snow; roll < 6 -> WinterHazardKind.Extinguish
            roll < 8 -> WinterHazardKind.Shatter; else -> WinterHazardKind.Pyromancer }
        val index = random.of(4)
        val target = if (kind == WinterHazardKind.Snow) exposed[random.of(exposed.size)].coords
            else if (kind == WinterHazardKind.Pyromancer) WintertodtData.camps[index].pyro
            else WintertodtData.camps[index].brazier.translate(1, 1)
        val spot = SpotanimType("spotanim.wint_area_attack_spot".asRSCM(RSCMType.SPOTANIM))
        world.spotanimMap(spot, target, 0, 0)
        hazards += WinterHazard(kind, target, index, tick + WintertodtData.rules.hazardDelay, round.id)
        nextAttack = tick + random.of(10, 20)
    }
    private fun resolveHazards(tick: Int) {
        val due = hazards.filter { it.due <= tick }
        hazards.removeAll(due.toSet())
        for (hazard in due) {
            if (hazard.round != round.id) continue
            world.spotanimMap(SpotanimType("spotanim.wint_area_attack_impact".asRSCM(RSCMType.SPOTANIM)),
                hazard.coords, 0, 0)
            when (hazard.kind) {
                WinterHazardKind.Snow -> members.toList().filter {
                    distance(it.coords, hazard.coords) <= 1
                }.forEach { damage(it, WinterDamage.Snow) }
                WinterHazardKind.Shatter -> {
                    round.extinguish(hazard.camp, true)
                    members.toList().filter { distance(it.coords, hazard.coords) <= 2 }
                        .forEach { damage(it, WinterDamage.Brazier) }
                    synchroniseCamps()
                }
                WinterHazardKind.Extinguish -> {
                    if (round.camps[hazard.camp].state == BrazierState.Lit) {
                        round.extinguish(hazard.camp, false)
                        synchroniseCamps()
                    }
                }
                WinterHazardKind.Pyromancer -> {
                    round.hurtPyro(hazard.camp)
                    synchroniseCamps()
                }
            }
        }
    }
    private fun damage(player: Player, kind: WinterDamage) {
        if (!canAct(player)) return
        val lit = round.camps.count { it.state == BrazierState.Lit }
        val percent = damagePercent(kind, player.stat("stat.firemaking"), warmItems(player), lit)
        val remaining = (player.vars[WintertodtState.Warmth] - percent * 10).coerceAtLeast(0)
        WintertodtState.set(player, WintertodtState.Warmth, remaining)
        val group = hitmark_groups.wintertodt_drain
        player.showHitmark(Hitmark.fromNoSource(group.lit.asRSCM(RSCMType.HITMARK),
            group.tint!!.asRSCM(RSCMType.HITMARK), group.tint!!.asRSCM(RSCMType.HITMARK), percent, 0))
        if (clock.cycle >= player.vars[WintertodtState.InterruptTick]) {
            player.clearInteraction()
            player.resetAnim()
            WintertodtState.set(player, WintertodtState.InterruptTick, clock.cycle + 6)
        }
        if (remaining == 0) {
            player.mes("The cold of the Wintertodt overcomes you.")
            player.queueHit(0, HitType.Typeless, player.hitpoints, NoopPlayerHitModifier)
            leave(player)
        }
    }
    private fun victory(tick: Int) {
        for (player in members.toList()) {
            val points = player.vars[WintertodtState.Points]
            if (points >= WintertodtData.rules.minimumPoints && player.vars[WintertodtState.Round] == round.id) {
                val rolls = WintertodtRewards.rolls(points, random)
                val owed = player.vars[WintertodtState.RewardPool]
                val credited = minOf(rolls, WintertodtData.rules.rewardCap - owed)
                WintertodtState.set(player, WintertodtState.RewardPool, owed + credited)
                award(player, "victory")
                val kills = (player.vars["varp.total_wintertodt_kills"].toLong() + 1)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                WintertodtState.set(player, "varp.total_wintertodt_kills", kills)
                player.mes("Your Wintertodt kill count is: <col=ff0000>$kills</col>.")
                player.mes("You have earned $credited rewards. Search the reward cart to collect them.")
                if (credited < rolls) player.mes("Your reward cart is full. Collect rewards before playing another round.")
            } else player.mes("You did not earn enough points to be worthy of a gift from the citizens of Kourend this time.")
            cleanupItems(player)
            player.clearInteraction()
            player.resetAnim()
        }
        round.finish(tick)
        hazards.clear()
        synchroniseCamps()
    }
    private fun cleanupItems(player: Player) {
        player.inv.indices.forEach { slot ->
            val item = player.inv[slot] ?: return@forEach
            if (item.id in listOf("obj.wint_bruma_root", "obj.wint_bruma_kindling").map { it.asRSCM() })
                player.inv[slot] = null
        }
    }
    internal companion object {
        fun distance(a: CoordGrid, b: CoordGrid): Int =
            if (a.level != b.level) Int.MAX_VALUE else maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
        fun damagePercent(kind: WinterDamage, level: Int, warm: Int, lit: Int): Int = when (kind) {
            WinterDamage.Cold -> ((16 - warm.coerceIn(0, 4) - 2 * lit.coerceIn(0, 4)) * 100 /
                level.coerceAtLeast(1)).coerceAtLeast(1)
            WinterDamage.Brazier -> (2 * ((10 - warm.coerceIn(0, 4)) * 100 /
                level.coerceAtLeast(1))).coerceAtLeast(2)
            WinterDamage.Snow -> (3 * ((10 - warm.coerceIn(0, 4)) * 100 /
                level.coerceAtLeast(1))).coerceAtLeast(1)
        }
    }
}
internal enum class WinterDamage { Cold, Brazier, Snow }
private enum class WinterHazardKind { Snow, Shatter, Extinguish, Pyromancer }
private data class WinterHazard(val kind: WinterHazardKind, val coords: CoordGrid,
    val camp: Int, val due: Int, val round: Int)
