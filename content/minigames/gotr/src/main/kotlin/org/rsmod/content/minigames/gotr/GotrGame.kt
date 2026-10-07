package org.rsmod.content.minigames.gotr

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Locale
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.content.minigames.gotr.scripts.GotrMiningScript
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

public enum class GotrPhase {
    LOBBY,

    ACTIVE,

    GRACE,
}

public sealed class GotrEnterResult {
    public data object Allowed : GotrEnterResult()
    public data class Denied(val message: String) : GotrEnterResult()
}

// Tracked altar trips retain membership; unrelated teleports and logout clear it.
@Singleton
public class GotrGame
@Inject
constructor(
    private val locRepo: LocRepository,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val defence: GotrDefence,
    private val hud: GotrHud,
    private val portalPresentation: GotrPortalPresentation,
) {
    public var phase: GotrPhase = GotrPhase.LOBBY
        private set

    public var power: Double = 0.0
        private set

    private val members = mutableSetOf<String>()

    private val joinedThisRound = mutableSetOf<String>()
    private var currentElemental = ELEMENTAL_RUNES.first()
    private var currentCatalytic = CATALYTIC_RUNES.first()
    private var nextPortalRotationCycle = 0
    private var portalRotationCount = 0
    private var hugeRemainsLoc: LocInfo? = null
    private var hugeRemainsDirection = 0
    private var hugeRemainsCloseCycle = 0
    private var hugeRemainsNextSpawnCycle = 0
    private var graceStartCycle = -1
    private var graceBeatIndex = 0
    private var graceBeats: List<Pair<Int, () -> Unit>> = emptyList()
    private var settings: GotrSettings = GotrSettings()
    private var activeStartCycle = 0
    private var roundPlayers = 1
    private var started = false
    private val altarVisits = mutableMapOf<String, CoordGrid>()

    public fun activeElapsedTicks(): Int = (mapClock.cycle - activeStartCycle).coerceAtLeast(0)

    public fun guardianCount(): Int = defence.guardianCount()

    public fun guardianLimit(): Int = defence.guardianLimit()

    public fun portalTicksRemaining(): Int =
        if (activeElapsedTicks() < PREPARATION_TICKS) (PREPARATION_TICKS - 1 - activeElapsedTicks()).coerceAtLeast(0)
        else (nextPortalRotationCycle - mapClock.cycle).coerceAtLeast(0)

    public fun nextRoundTicksRemaining(): Int =
        if (phase == GotrPhase.ACTIVE) 0 else (graceStartCycle + 136 - mapClock.cycle).coerceAtLeast(0)

    public fun visitAltar(player: Player, destination: CoordGrid) {
        if (isMember(player)) altarVisits[player.displayName] = destination
    }

    public fun bindSettings(settings: GotrSettings) {
        this.settings = settings
    }

    public fun isMember(player: Player): Boolean = player.displayName in members

    public fun memberCount(): Int = members.size

    public fun target(): Int {
        val n = roundPlayers
        return TARGET_PER_PLAYER * minOf(n, 20) + TARGET_PER_PLAYER_OVERFLOW * maxOf(n - 20, 0)
    }

    public fun powerPercent(): Int {
        val target = target()
        if (target <= 0) return 0
        return ((power / target) * 100).toInt().coerceIn(0, 100)
    }

    public fun currentElementalRune(): String = currentElemental

    public fun currentCatalyticRune(): String = currentCatalytic

    public fun isPortalOpen(rune: String): Boolean {
        if (!portalsAvailable()) return false
        val key = if (rune.startsWith("obj.")) rune else "obj.${rune}rune"
        return key == currentElemental || key == currentCatalytic
    }

    public fun portalsAvailable(): Boolean =
        when (phase) {
            GotrPhase.ACTIVE -> activeElapsedTicks() >= PREPARATION_TICKS
            GotrPhase.GRACE -> true
            GotrPhase.LOBBY -> graceStartCycle >= 0 && mapClock.cycle - graceStartCycle < 83
        }

    public fun isHugeRemainsPortalOpen(): Boolean = hugeRemainsLoc != null

    public fun tryEnter(player: Player): GotrEnterResult {
        if (isMember(player)) return GotrEnterResult.Allowed
        return when (phase) {
            GotrPhase.ACTIVE -> {
                val firstPlayer = members.isEmpty()
                members += player.displayName
                if (firstPlayer) {
                    defence.endRound()
                    beginActive()
                } else if (members.size > roundPlayers) {
                    val fraction = power / target()
                    roundPlayers = members.size
                    power = fraction * target()
                }
                VarPlayerIntMapSetter.set(player, VARBIT_IS_PLAYING, 1)
                VarPlayerIntMapSetter.set(player, VARBIT_PLAYER_HAS_FINISHED_GAME, 0)
                if (joinedThisRound.add(player.displayName)) {
                    // Re-entry must not reset energy already earned during this round.
                    VarPlayerIntMapSetter.set(player, VARBIT_MINING_COUNT, 0)
                    VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, 0)
                    VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, 0)
                }
                GotrEnterResult.Allowed
            }
            GotrPhase.GRACE,
            GotrPhase.LOBBY ->
                GotrEnterResult.Denied(
                    "It looks like the adventurers within are just finishing up. You must wait " +
                        "until they are done to join.",
                )
        }
    }

    public fun statusMessage(): String =
        "The rift is wide open. Creatures are flooding through from the Abyss! There were " +
            "${members.size} adventurers inside when it opened. Great Guardian power: " +
            "${powerPercent()}%"

    public fun leave(player: Player) {
        clearRoundItems(player)
        altarVisits.remove(player.displayName)
        if (members.remove(player.displayName) || player.vars[VARBIT_IS_PLAYING] != 0) {
            VarPlayerIntMapSetter.set(player, VARBIT_IS_PLAYING, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_MINING_COUNT, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_PLAYER_HAS_FINISHED_GAME, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_PORTAL_BOTTOM_VISIBLE, 0)
        }
        hud.hide(player)
    }

    public fun applyGuardianStone(player: Player, stoneObj: String): Boolean {
        if (phase == GotrPhase.LOBBY || !isMember(player)) return false
        val elemental = stoneObj == STONE_ELEMENTAL || stoneObj == STONE_POLYELEMENTAL
        val catalytic = stoneObj == STONE_CATALYTIC || stoneObj == STONE_POLYCATALYTIC
        if (!elemental && !catalytic) return false
        val poly = stoneObj == STONE_POLYELEMENTAL || stoneObj == STONE_POLYCATALYTIC

        player.statAdvance("stat.runecrafting", RC_XP_PER_STONE)

        val runeEnergyGain = constants.run_max_energy / 100
        player.runEnergy = minOf(constants.run_max_energy, player.runEnergy + runeEnergyGain)

        val baseEnergy = if (poly) POLY_ENERGY_PER_STONE else ENERGY_PER_STONE
        awardEnergy(player, if (elemental) baseEnergy else 0, if (catalytic) baseEnergy else 0)

        power = minOf(target().toDouble(), power + 1.0)

        return true
    }

    public fun finishGuardianFeed(player: Player) {
        val specPercent = (player.vars[VARP_SA_ENERGY] / 10).coerceIn(0, 100)
        val bonusPercent = (1 + specPercent / 10).coerceIn(1, 11)
        player.runEnergy = minOf(constants.run_max_energy,
            player.runEnergy + constants.run_max_energy * bonusPercent / 100)
    }

    public fun rewardCell(player: Player, elemental: Int, catalytic: Int, xp: Double) {
        if (!isMember(player) || phase != GotrPhase.ACTIVE) return
        awardEnergy(player, elemental, catalytic)
        if (xp > 0.0) player.statAdvance("stat.runecrafting", xp)
    }

    private fun awardEnergy(player: Player, elemental: Int, catalytic: Int) {
        val oldElemental = player.vars[VARBIT_ELEMENTAL_EARNED]
        val oldCatalytic = player.vars[VARBIT_CATALYTIC_EARNED]
        val cap = when {
            player.worn.contains("obj.abyssal_lantern_yew") -> 1320
            player.worn.contains("obj.abyssal_lantern_oak") || player.worn.contains("obj.abyssal_lantern_redwood") -> 1260
            else -> 1200
        }
        val remaining = (cap - oldElemental - oldCatalytic).coerceAtLeast(0)
        val desiredElemental = elemental.coerceIn(0, (PER_TYPE_ENERGY_CAP - oldElemental).coerceAtLeast(0))
        val desiredCatalytic = catalytic.coerceIn(0, (PER_TYPE_ENERGY_CAP - oldCatalytic).coerceAtLeast(0))
        val elementalGain = minOf(desiredElemental, if (desiredCatalytic > 0) remaining / 2 else remaining)
        val catalyticGain = minOf(desiredCatalytic, remaining - elementalGain)
        VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, oldElemental + elementalGain)
        VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, oldCatalytic + catalyticGain)
        if (remaining > 0 && elementalGain + catalyticGain == remaining) {
            player.mes("You are fully attuned with runic energy and cannot earn anymore until the rift has been closed.")
        }
    }

    public fun addPoints(player: Player, elemental: Int, catalytic: Int) {
        player.migrateGotrRewards()
        player.gotrElementalPoints = (player.gotrElementalPoints + elemental).coerceIn(0, MAX_SEARCHES)
        player.gotrCatalyticPoints = (player.gotrCatalyticPoints + catalytic).coerceIn(0, MAX_SEARCHES)
    }

    public fun tick() {
        if (!started) start()
        reconcileMembers()
        when (phase) {
            GotrPhase.ACTIVE -> tickActive()
            GotrPhase.GRACE, GotrPhase.LOBBY -> tickGraceOrLobby()
        }
        val open = portalsAvailable()
        portalPresentation.sync(currentElemental.takeIf { open }, currentCatalytic.takeIf { open }, mapClock.cycle)
        if (mapClock.cycle % 2 == 0) syncDisplays()
    }

    private fun syncDisplays() {
        val open = portalsAvailable()
        val status = when {
            phase == GotrPhase.GRACE -> "The Great Guardian is closing the rift."
            phase == GotrPhase.LOBBY -> "The rift will become active in ${(nextRoundTicksRemaining() * 0.6).toInt()} seconds."
            activeElapsedTicks() < PREPARATION_TICKS -> "Creatures from the Abyss will attack soon."
            else -> "Protect the Great Guardian!"
        }
        val state = GotrHudState(
            power = power.toInt(), maxPower = target(),
            elementalRune = currentElemental.takeIf { open },
            catalyticRune = currentCatalytic.takeIf { open },
            guardianCount = guardianCount(), guardianLimit = guardianLimit(),
            portalTicksRemaining = if (phase == GotrPhase.ACTIVE) portalTicksRemaining() else nextRoundTicksRemaining(),
            hugePortalDirection = hugeRemainsDirection,
            hugePortalTicksRemaining = if (hugeRemainsLoc != null) (hugeRemainsCloseCycle - mapClock.cycle).coerceAtLeast(0) else -1,
            status = status,
        )
        for (player in playerList) {
            val coords = player.coords
            val temple = coords.level == 0 && coords.x in TEMPLE_MIN_X..TEMPLE_MAX_X &&
                coords.z in TEMPLE_MIN_Z..TEMPLE_MAX_Z
            hud.syncAtmosphere(player, temple)
            if (isMember(player)) hud.sync(player, state) else hud.hideHud(player)
        }
    }

    private fun tickActive() {
        val cycle = mapClock.cycle
        val elapsed = activeElapsedTicks()

        // Commit victory before the later NPC attack phase can drain power.
        if (powerPercent() >= 100) {
            beginGrace()
            return
        }

        val damage = defence.tick(elapsed, powerPercent(), memberCount(), ::broadcast)
        power = (power - damage).coerceAtLeast(0.0)
        if (members.isNotEmpty() && power <= 0.0 && elapsed >= PREPARATION_TICKS) {
            failRound()
            return
        }

        if (elapsed >= PREPARATION_TICKS && cycle >= nextPortalRotationCycle) {
            rotatePortals()
            nextPortalRotationCycle = cycle + settings.portalRotationTicks
        }

        if (hugeRemainsLoc != null && cycle >= hugeRemainsCloseCycle) {
            hugeRemainsLoc = null
            hugeRemainsDirection = 0
        }
        if (hugeRemainsLoc == null && cycle >= hugeRemainsNextSpawnCycle) {
            spawnHugeRemainsPortal()
            hugeRemainsNextSpawnCycle = cycle + settings.hugeRemainsCadenceTicks
        }

        if (powerPercent() >= 100) {
            beginGrace()
        }
    }

    private fun tickGraceOrLobby() {
        if (graceStartCycle < 0) return
        val elapsed = mapClock.cycle - graceStartCycle
        while (graceBeatIndex < graceBeats.size && elapsed >= graceBeats[graceBeatIndex].first) {
            graceBeats[graceBeatIndex].second()
            graceBeatIndex++
        }
    }

    public fun start() {
        if (started) return
        started = true
        beginActive()
    }

    private fun beginActive() {
        phase = GotrPhase.ACTIVE
        activeStartCycle = mapClock.cycle
        roundPlayers = members.size.coerceAtLeast(1)
        power = target() / 10.0
        defence.beginRound()
        joinedThisRound.clear()
        for (name in members) {
            val player = playerList.firstOrNull { it.displayName == name } ?: continue
            joinedThisRound += name
            VarPlayerIntMapSetter.set(player, VARBIT_IS_PLAYING, 1)
            VarPlayerIntMapSetter.set(player, VARBIT_PLAYER_HAS_FINISHED_GAME, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_MINING_COUNT, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, 0)
        }
        portalRotationCount = 0
        nextPortalRotationCycle = mapClock.cycle + PREPARATION_TICKS
        despawnHugeRemainsPortal()
        hugeRemainsNextSpawnCycle = mapClock.cycle + settings.hugeRemainsFirstOpenTicks
        graceStartCycle = -1
        broadcast("<col=ef1020>The rift becomes active!")
    }

    private fun beginGrace() {
        phase = GotrPhase.GRACE
        defence.stopCreatures()
        graceStartCycle = mapClock.cycle
        graceBeatIndex = 0
        broadcast("@mes_hl_gre@The Great Guardian is fully charged.</col>")

        graceBeats =
            listOf(
                32 to { closeRifts() },
                // TODO: determine the independent delivery gates for these two announcements.
                83 to { broadcast("The Portal Guardians close their rifts.") },
                86 to { broadcast("The rift will become active in 30 seconds.") },
                120 to { broadcast("The rift will become active in 10 seconds.") },
                128 to { broadcast("The rift will become active in 5 seconds.") },
                133 to { broadcast("3...") },
                134 to { broadcast("2...") },
                135 to { broadcast("1...") },
                136 to { beginActive() },
            )
    }

    private fun closeRifts() {
        defence.endRound()
        phase = GotrPhase.LOBBY
        despawnHugeRemainsPortal()
        broadcast("@mes_hl_gre@The Great Guardian successfully closed the rift!</col>")
        for (name in members.toList()) {
            val player = playerList.firstOrNull { it.displayName == name } ?: continue
            payout(player)
            clearRoundItems(player)
            VarPlayerIntMapSetter.set(player, VARBIT_PLAYER_HAS_FINISHED_GAME, 1)
        }
        // is_playing spans successive rounds for players who stay inside.
        broadcast("The Portal Guardians will keep their rifts open for another 30 seconds.")
    }

    private fun failRound() {
        defence.endRound()
        despawnHugeRemainsPortal()
        phase = GotrPhase.LOBBY
        graceStartCycle = mapClock.cycle - 86
        graceBeatIndex = 0
        broadcast("<col=ef1020>The Great Guardian has been defeated!</col>")
        broadcast("The rift will become active in 30 seconds.")
        for (player in playerList) if (isMember(player)) {
            clearRoundItems(player)
            VarPlayerIntMapSetter.set(player, VARBIT_PLAYER_HAS_FINISHED_GAME, 1)
        }
        graceBeats = listOf(
            120 to { broadcast("The rift will become active in 10 seconds.") },
            128 to { broadcast("The rift will become active in 5 seconds.") },
            133 to { broadcast("3...") },
            134 to { broadcast("2...") },
            135 to { broadcast("1...") },
            136 to { beginActive() },
        )
    }

    private fun reconcileMembers() {
        val online = playerList.associateBy { it.displayName }
        for (name in members.toList()) {
            val player = online[name]
            if (player == null) {
                members.remove(name)
                altarVisits.remove(name)
                continue
            }
            if (isArena(player.coords)) {
                altarVisits.remove(name)
                continue
            }
            val altar = altarVisits[name]
            if (altar != null && player.coords.x / 64 == altar.x / 64 &&
                player.coords.z / 64 == altar.z / 64 && player.coords.level in 0..3) continue
            leave(player)
        }
        if (phase == GotrPhase.ACTIVE) {
            for (player in playerList) if (isArena(player.coords) && !isMember(player)) tryEnter(player)
        }
    }

    private fun isArena(c: CoordGrid): Boolean =
        c.level == 0 && c.x in TEMPLE_MIN_X..TEMPLE_MAX_X && c.z in 9484..9527

    private fun clearRoundItems(player: Player) {
        GotrMiningScript.stopMining(player)
        val ids = (ELEMENTAL_RUNES + CATALYTIC_RUNES).mapTo(hashSetOf()) {
            "obj.gotr_portal_talisman_${it.removePrefix("obj.").removeSuffix("rune")}".asRSCM()
        }
        ids += "obj.gotr_guardian_fragment".asRSCM()
        ids += EssencePouch.GUARDIAN_ESSENCE.asRSCM()
        for (slot in player.inv.indices) if (player.inv[slot]?.id in ids) player.inv[slot] = null
        EssencePouch.removeStoredForType(player, EssencePouch.GUARDIAN_ESSENCE, Int.MAX_VALUE)
    }

    private fun payout(player: Player) {
        val elementalEnergy = player.vars[VARBIT_ELEMENTAL_EARNED]
        val catalyticEnergy = player.vars[VARBIT_CATALYTIC_EARNED]
        val total = elementalEnergy + catalyticEnergy
        if (total < PAYOUT_MIN_ENERGY) {
            player.mes(
                "You did not contribute enough to attune any elemental or catalytic energy.",
            )
            VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, 0)
            VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, 0)
            return
        }

        player.migrateGotrRewards()
        val riftsClosed = player.gotrRiftsClosed + 1
        player.gotrRiftsClosed = riftsClosed

        val elementalPoints = rollPointsWithRemainder(elementalEnergy)
        val catalyticPoints = rollPointsWithRemainder(catalyticEnergy)
        addPoints(player, elementalPoints, catalyticPoints)
        val totalElemental = player.gotrElementalPoints
        val totalCatalytic = player.gotrCatalyticPoints

        val xp = player.baseRunecraftingLvl * XP_PER_LEVEL_ON_CLOSE
        player.statAdvance("stat.runecrafting", xp.toDouble())

        player.mes("Amount of rifts you have closed: <col=ff0000>$riftsClosed</col>.")
        player.mes(
            "Elemental energy attuned: <col=ef1020>$elementalPoints</col>. Catalytic energy " +
                "attuned: <col=ef1020>$catalyticPoints</col>.",
        )
        player.mes(
            "Total elemental energy: <col=ef1020>$totalElemental</col>. Total catalytic energy: " +
                "<col=ef1020>$totalCatalytic</col>.",
        )
        player.mes(
            "You've been awarded ${"%,d".format(Locale.UK, xp)} bonus Runecraft XP for closing " +
                "the rift.",
        )

        VarPlayerIntMapSetter.set(player, VARBIT_ELEMENTAL_EARNED, 0)
        VarPlayerIntMapSetter.set(player, VARBIT_CATALYTIC_EARNED, 0)
    }

    private fun rollPointsWithRemainder(energy: Int): Int {
        val whole = energy / 100
        val remainder = energy % 100
        val roundUp = remainder > 0 && random.of(100) < remainder
        return whole + if (roundUp) 1 else 0
    }

    private fun rotatePortals() {
        val previousElemental = currentElemental
        currentElemental = random.pick(ELEMENTAL_RUNES.filter { it != currentElemental })
        currentCatalytic = random.pick(CATALYTIC_RUNES.filter { it != currentCatalytic })
        if (portalRotationCount < 2 && currentElemental == "obj.airrune" &&
            currentCatalytic in setOf("obj.mindrune", "obj.bodyrune")) {
            currentElemental = random.pick(ELEMENTAL_RUNES.filter {
                it != "obj.airrune" && it != previousElemental
            })
        }
        portalRotationCount++
    }

    private fun spawnHugeRemainsPortal() {
        val (coords, direction) = random.pick(HUGE_REMAINS_PORTALS)
        hugeRemainsDirection = when (direction) { "east" -> 1; "south east" -> 2; "south west" -> 3; else -> 4 }
        val duration = settings.hugeRemainsOpenTicks
        hugeRemainsLoc =
            locRepo.add(coords, HUGE_REMAINS_PORTAL_LOC, duration, LocAngle.West, LocShape.CentrepieceStraight)
        hugeRemainsCloseCycle = mapClock.cycle + duration
        broadcast(
            "<col=ef1020>A portal to the huge guardian fragment mine has opened to the " +
                "$direction!</col>",
        )
    }

    private fun despawnHugeRemainsPortal() {
        hugeRemainsLoc?.let { locRepo.del(it, Int.MAX_VALUE) }
        hugeRemainsLoc = null
        hugeRemainsDirection = 0
    }

    private fun broadcast(text: String) {
        for (player in playerList) {
            val c = player.coords
            if (c.level != 0) continue
            if (c.x < TEMPLE_MIN_X || c.x > TEMPLE_MAX_X) continue
            if (c.z < TEMPLE_MIN_Z || c.z > TEMPLE_MAX_Z) continue
            player.mes(text)
        }
    }

    public companion object {
        public const val TARGET_PER_PLAYER: Int = 250
        public const val TARGET_PER_PLAYER_OVERFLOW: Int = 200
        public const val PREPARATION_TICKS: Int = 201
        public const val MAX_SEARCHES: Int = 65535
        private const val RC_XP_PER_STONE = 5.0
        private const val ENERGY_PER_STONE = 2
        private const val POLY_ENERGY_PER_STONE = 3
        private const val PER_TYPE_ENERGY_CAP = 1000
        private const val PAYOUT_MIN_ENERGY = 300
        private const val XP_PER_LEVEL_ON_CLOSE = 45

        public val ELEMENTAL_RUNES: List<String> =
            listOf("obj.airrune", "obj.waterrune", "obj.earthrune", "obj.firerune")
        public val CATALYTIC_RUNES: List<String> =
            listOf(
                "obj.mindrune",
                "obj.chaosrune",
                "obj.deathrune",
                "obj.bloodrune",
                "obj.bodyrune",
                "obj.cosmicrune",
                "obj.naturerune",
                "obj.lawrune",
            )

        public const val STONE_ELEMENTAL: String = "obj.gotr_guardian_stone_elemental"
        public const val STONE_CATALYTIC: String = "obj.gotr_guardian_stone_catalytic"
        public const val STONE_POLYELEMENTAL: String = "obj.gotr_guardian_stone_polyelemental"
        public const val STONE_POLYCATALYTIC: String = "obj.gotr_guardian_stone_polycatalytic"

        public val BARRIER_COORDS: CoordGrid = CoordGrid(3613, 9483, 0)

        private const val HUGE_REMAINS_PORTAL_LOC = "loc.gotr_agility_portal_top"

        private val HUGE_REMAINS_PORTALS =
            listOf(
                CoordGrid(3627, 9503, 0) to "east",
                CoordGrid(3627, 9493, 0) to "south east",
                CoordGrid(3615, 9487, 0) to "south",
                CoordGrid(3603, 9493, 0) to "south west",
            )

        private const val TEMPLE_MIN_X = 3584
        private const val TEMPLE_MAX_X = 3647
        private const val TEMPLE_MIN_Z = 9408
        private const val TEMPLE_MAX_Z = 9535

        private const val VARBIT_IS_PLAYING = "varbit.gotr_is_playing"
        private const val VARBIT_MINING_COUNT = "varbit.gotr_mining_count"
        private const val VARBIT_ELEMENTAL_EARNED = "varbit.gotr_elemental_earned_this_game"
        private const val VARBIT_CATALYTIC_EARNED = "varbit.gotr_catalytic_earned_this_game"
        private const val VARBIT_PLAYER_HAS_FINISHED_GAME = "varbit.gotr_player_has_finished_game"
        private const val VARP_SA_ENERGY = "varp.sa_energy"

        public const val VARBIT_PORTAL_BOTTOM_VISIBLE: String = "varbit.gotr_portal_bottom_visible"
    }
}
