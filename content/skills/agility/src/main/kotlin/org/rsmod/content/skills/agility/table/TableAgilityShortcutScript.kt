package org.rsmod.content.skills.agility.table

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.rangedLvl
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.agility.AgilityShortcutLinkRow
import org.rsmod.api.table.agility.AgilityShortcutRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.skillSuccess
import org.rsmod.events.EventBus
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

private const val GEAR_GRAPPLE = 1
private const val GEAR_CLIMBING_BOOTS = 2

data class Shortcut(
    val locs: List<String>,
    val level: Int,
    val xp: Double,
    val option: String,
    val anim: String,
    val ticks: Int = 2,
    val links: Map<CoordGrid, ShortcutLink> = emptyMap(),
    val fail: ShortcutFail? = null,
)

data class ShortcutLink(val dest: CoordGrid, val level: Int, val reqs: ShortcutReqs)

data class ShortcutFail(val low: Int, val high: Int, val xp: Double, val damage: IntRange?)

data class ShortcutReqs(
    val ranged: Int = 0,
    val strength: Int = 0,
    val gear: Gear? = null,
    val quest: String? = null,
    val varSymbol: String? = null,
    val varValue: Int = 0,
    val varExact: Boolean = false,
    val bareLevel: Int = 0,
) {
    enum class Gear {
        Grapple,
        ClimbingBoots,
    }

    companion object {
        val NONE: ShortcutReqs = ShortcutReqs()
    }
}

object AgilityShortcutTable {
    val rows: List<Shortcut> by lazy {
        val links =
            AgilityShortcutLinkRow.all().groupBy(
                { it.shortcut.rowId },
                { it.origin to ShortcutLink(it.dest, it.level, it.reqs()) },
            )
        AgilityShortcutRow.all().map { row ->
            Shortcut(
                locs = listOf(RSCM.getReverseMapping(RSCMType.LOC, row.loc.id)),
                level = row.level,
                xp = row.xp / 10.0,
                option = row.option,
                anim = RSCM.getReverseMapping(RSCMType.SEQ, row.anim.id),
                ticks = row.ticks,
                links = links[row.rowId].orEmpty().toMap(),
                fail = row.toFail(),
            )
        }
    }

    private fun AgilityShortcutRow.toFail(): ShortcutFail? {
        val slots = fail
        if (slots.size < 5) {
            return null
        }
        val damage = if (slots[3] == 0 && slots[4] == 0) null else slots[3]..slots[4]
        return ShortcutFail(slots[0], slots[1], slots[2] / 10.0, damage)
    }

    private fun AgilityShortcutLinkRow.reqs(): ShortcutReqs {
        val gate = varGate.firstOrNull()
        return ShortcutReqs(
            ranged = ranged ?: 0,
            strength = strength ?: 0,
            gear =
                when (gear) {
                    GEAR_GRAPPLE -> ShortcutReqs.Gear.Grapple
                    GEAR_CLIMBING_BOOTS -> ShortcutReqs.Gear.ClimbingBoots
                    else -> null
                },
            quest = quest,
            varSymbol = gate?.t0,
            varValue = gate?.t1 ?: 0,
            varExact = gate?.t2 == 1,
            bareLevel = bareLevel ?: 0,
        )
    }
}

class TableAgilityShortcutScript
@Inject
constructor(private val events: EventBus, private val collision: CollisionFlagMap, private val xpMods: XpModifiers) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { registerUnboundObstacles() }
    }

    private fun ScriptContext.registerUnboundObstacles() {
        for (shortcut in AgilityShortcutTable.rows) {
            for (loc in shortcut.locs) {
                val type = ServerCacheManager.getObject(locId(loc) ?: continue) ?: continue
                val slot =
                    (1..5).firstOrNull {
                        type.actions.getOpOrNull(it - 1).equals(shortcut.option, ignoreCase = true)
                    } ?: continue
                if (hasLocHandler(events, type, slot)) continue
                when (slot) {
                    1 -> onOpLoc1(loc) { cross(it.loc, shortcut) }
                    2 -> onOpLoc2(loc) { cross(it.loc, shortcut) }
                    3 -> onOpLoc3(loc) { cross(it.loc, shortcut) }
                    4 -> onOpLoc4(loc) { cross(it.loc, shortcut) }
                    else -> onOpLoc5(loc) { cross(it.loc, shortcut) }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo, shortcut: Shortcut) {
        val origin = shortcut.links.keys
            .filter { it.level == coords.level && it.chebyshevDistance(coords) <= 16 }
            .minByOrNull { it.chebyshevDistance(coords) }
        if (shortcut.links.isNotEmpty() && origin == null) {
            mes("You can't use this shortcut from here.")
            return
        }
        val link = shortcut.links[origin]
        val level = link?.level ?: shortcut.level
        if (player.agilityLvl < level) {
            mes("You need an Agility level of $level to use this shortcut.")
            return
        }

        if (!meets(link?.reqs ?: ShortcutReqs.NONE)) {
            return
        }

        val dest = link?.dest ?: farSide(loc)
        if (dest == null) {
            mes("You can't find a way through from here.")
            return
        }

        if (origin != null && !approachMount(origin)) return
        faceSquare(loc.coords)
        anim(shortcut.anim)
        delay(shortcut.ticks)

        val failed = shortcut.fail?.let { !skillSuccess(it.low, it.high, player.agilityLvl) } == true
        if (failed) {
            slip(shortcut.fail!!)
            return
        }

        teleport(dest, TeleportType.Exempt)
        resetAnim()
        publish(PlayerActionCompleteEvent(
            player, PlayerAction.CrossShortcut, loc.internalName,
        ))

        if (shortcut.xp > 0) {
            statAdvance(STAT_AGILITY, shortcut.xp * xpMods.get(player, STAT_AGILITY))
        }
    }

    private fun ProtectedAccess.slip(fail: ShortcutFail) {
        resetAnim()
        mes("You lose your footing and fail to make it across.")
        val damage = fail.damage
        if (damage != null) {
            queueHit(delay = 0, type = HitType.Typeless, damage = random.of(damage.first, damage.last))
        }
        if (fail.xp > 0) {
            statAdvance(STAT_AGILITY, fail.xp * xpMods.get(player, STAT_AGILITY))
        }
    }

    private fun ProtectedAccess.meets(reqs: ShortcutReqs): Boolean {
        val barehanded = reqs.bareLevel > 0 && player.agilityLvl >= reqs.bareLevel
        if (!barehanded) {
            if (reqs.ranged > 0 && player.rangedLvl < reqs.ranged) {
                mes("You need a Ranged level of ${reqs.ranged} to use this shortcut.")
                return false
            }
            if (reqs.strength > 0 && player.strengthLvl < reqs.strength) {
                mes("You need a Strength level of ${reqs.strength} to use this shortcut.")
                return false
            }
            when (reqs.gear) {
                ShortcutReqs.Gear.Grapple ->
                    if (!wearingGrapple()) {
                        mes("You need a crossbow and a mith grapple to use this shortcut.")
                        return false
                    }
                ShortcutReqs.Gear.ClimbingBoots ->
                    if (player.worn[Wearpos.Feet.slot]?.isType(CLIMBING_BOOTS) != true) {
                        mes("You need climbing boots to use this shortcut.")
                        return false
                    }
                null -> Unit
            }
        }
        val quest = reqs.quest
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            mes("You need to have completed a quest to use this shortcut.")
            return false
        }
        val symbol = reqs.varSymbol
        if (symbol != null) {
            val current = player.vars[symbol]
            val satisfied = if (reqs.varExact) current == reqs.varValue else current >= reqs.varValue
            if (!satisfied) {
                mes("You can't use this shortcut yet.")
                return false
            }
        }
        return true
    }

    private fun ProtectedAccess.farSide(loc: BoundLocInfo): CoordGrid? {
        val candidates =
            crossingCandidates(
                from = player.coords,
                loc = loc.coords,
                width = loc.adjustedWidth,
                length = loc.adjustedLength,
                angle = loc.angle,
                depth = SEARCH_DEPTH,
            )
        return candidates.firstOrNull { candidate ->
            val flags = collision[candidate.x, candidate.z, candidate.level]
            flags and CollisionFlag.BLOCK_WALK == 0
        }
    }

    private companion object {
        const val SEARCH_DEPTH = 2
        const val CLIMBING_BOOTS = "obj.death_climbingboots"
    }
}

private const val MITH_GRAPPLE = "obj.xbows_grapple_tip_bolt_mithril_rope"

internal fun locId(loc: String): Int? {
    val id = runCatching { loc.asRSCM(RSCMType.LOC) }.getOrNull()
    if (id == null) {
        logger.warn { "Agility: no such loc '$loc', skipping it." }
    }
    return id
}

private val logger = InlineLogger()

internal fun ProtectedAccess.wearingGrapple(): Boolean {
    if (player.worn[Wearpos.Quiver.slot]?.isType(MITH_GRAPPLE) != true) {
        return false
    }
    val weapon = player.worn[Wearpos.RightHand.slot] ?: return false
    val name = ServerCacheManager.getItem(weapon.id)?.name ?: return false
    return name.contains("crossbow", ignoreCase = true)
}

internal fun crossingCandidates(
    from: CoordGrid,
    loc: CoordGrid,
    width: Int,
    length: Int,
    angle: LocAngle,
    depth: Int,
): List<CoordGrid> {
    val centreX = loc.x + (width - 1) / 2.0
    val centreZ = loc.z + (length - 1) / 2.0
    val dx = centreX - from.x
    val dz = centreZ - from.z

    // A player on the wall tile has no approach bearing; use the wall's angle.
    val (stepX, stepZ) =
        if (dx == 0.0 && dz == 0.0) {
            when (angle) {
                LocAngle.West -> -1 to 0
                LocAngle.East -> 1 to 0
                LocAngle.North -> 0 to 1
                LocAngle.South -> 0 to -1
            }
        } else if (abs(dx) >= abs(dz)) {
            sign(dx).toInt() to 0
        } else {
            0 to sign(dz).toInt()
        }
    val past =
        when {
            stepX > 0 -> loc.x + width - from.x
            stepX < 0 -> from.x - loc.x + 1
            stepZ > 0 -> loc.z + length - from.z
            else -> from.z - loc.z + 1
        }

    val first = maxOf(past, 1)
    return (first..first + depth).map { from.translate(stepX * it, stepZ * it) }
}
