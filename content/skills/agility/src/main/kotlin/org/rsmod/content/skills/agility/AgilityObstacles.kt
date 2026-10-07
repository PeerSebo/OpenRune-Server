package org.rsmod.content.skills.agility

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import kotlin.math.sign
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

object AgilityAnims {
    const val CLIMB = "seq.human_climbing"

    const val WALL_CLIMB = "seq.human_climbing"
    const val CLIMB_LADDER = "seq.human_reachforladder"
    const val JUMP_UP = "seq.agility_shortcut_wall_jump2"

    const val JUMP = "seq.human_longjump"

    const val JUMP_DOWN = "seq.agility_shortcut_wall_jumpdown2"
    const val WALL_CLIMB_OVER = "seq.human_walk_style"
    const val ROPE_SWING = "seq.human_ropeswing_long"
    const val ZIPLINE_GRAB = "seq.zipline_bite"
    const val ZIPLINE_SLIDE = "seq.zipline_slide"
    const val POLE_VAULT = "seq.rooftops_pole_vault"
    const val BALANCE_WALK = "seq.human_walk_logbalance"
    const val BALANCE_READY = "seq.human_walk_logbalance_ready"
    const val BALANCE_STUMBLE = "seq.human_walk_logbalance_stumble"
    const val MONKEYBARS_ON = "seq.human_monkeybars_on"
    const val MONKEYBARS_OFF = "seq.human_monkeybars_off"
    const val MONKEYBARS_WALK = "seq.human_monkeybars_walk"
    const val MONKEYBARS_READY = "seq.human_monkeybars_ready"
    const val HANDHOLDS = "seq.agilityarena_handholds_middle"
    const val PIPE_SQUEEZE = "seq.human_pipesqueeze"
    const val PIPE_UNSQUEEZE = "seq.human_pipeunsqueeze"
    const val CRACK_ENTER = "seq.agility_shortcut_crack_enter"
    const val CRACK_LEAVE = "seq.agility_shortcut_crack_leave"

    const val TUNNEL_ENTER = "seq.agilty_shortcut_enter_hole"
    const val TUNNEL_WALK = "seq.agilty_shortcut_tunnel_walk"
    const val TUNNEL_EXIT = "seq.agilty_shortcut_exit_hole"
    const val STEPPING_STONE = "seq.human_steppingstonejump"
}

// Use the balance sequence for every walk direction; normal movement otherwise replaces the pose.
enum class BalanceStyle(
    val ready: String,
    val walk: String,
    val mount: String? = null,
    val dismount: String? = null,
) {
    Tightrope(AgilityAnims.BALANCE_READY, AgilityAnims.BALANCE_WALK),
    Monkeybars(
        AgilityAnims.MONKEYBARS_READY,
        AgilityAnims.MONKEYBARS_WALK,
        AgilityAnims.MONKEYBARS_ON,
        AgilityAnims.MONKEYBARS_OFF,
    ),
    Handholds(AgilityAnims.HANDHOLDS, AgilityAnims.HANDHOLDS),
}

// exactMove delays use 20ms client cycles; one server tick is 30 cycles.
internal const val CLIENT_CYCLES_PER_TICK = 30

fun successChance(level: Int, requiredLevel: Int, noFailLevel: Int, baseChance: Int = 60): Int {
    if (level >= noFailLevel) {
        return 100
    }
    val span = (noFailLevel - requiredLevel).coerceAtLeast(1)
    val progress = (level - requiredLevel).coerceIn(0, span)
    return baseChance + (100 - baseChance) * progress / span
}

fun emFaceTowards(from: CoordGrid, to: CoordGrid): Int {
    val dx = (to.x - from.x).sign
    val dz = (to.z - from.z).sign
    return when {
        dx == 0 && dz > 0 -> constants.em_face_north
        dx == 0 && dz < 0 -> constants.em_face_south
        dx > 0 && dz == 0 -> constants.em_face_east
        dx < 0 && dz == 0 -> constants.em_face_west
        dx > 0 && dz > 0 -> constants.em_face_northeast
        dx < 0 && dz > 0 -> constants.em_face_northwest
        dx > 0 && dz < 0 -> constants.em_face_southeast
        dx < 0 && dz < 0 -> constants.em_face_southwest
        else -> constants.em_face_south
    }
}

fun line(from: CoordGrid, to: CoordGrid): List<CoordGrid> {
    require(from.level == to.level) { "Balance paths cannot change level: from=$from, to=$to" }
    val tiles = mutableListOf<CoordGrid>()
    var x = from.x
    var z = from.z
    while (x != to.x || z != to.z) {
        x += (to.x - x).sign
        z += (to.z - z).sign
        tiles += CoordGrid(x, z, from.level)
    }
    return tiles
}

// Round up to avoid clipping the final animation frame.
internal fun seqTicks(seq: String, fallback: Int): Int {
    val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))
    val ticks = type?.tickDuration ?: 0
    return if (ticks > 0) ticks else fallback
}

// Round glide duration down so the closing animation frames cover the landing.
internal fun seqGlideTicks(seq: String, fallback: Int): Int {
    val type = ServerCacheManager.getAnim(seq.asRSCM(RSCMType.SEQ))
    val cycles = type?.totalDelay ?: 0
    return if (cycles > 0) maxOf(1, cycles / CLIENT_CYCLES_PER_TICK) else fallback
}

internal suspend fun ProtectedAccess.stepOnto(start: CoordGrid) {
    if (coords == start) {
        return
    }
    if (coords.level == start.level && coords.chebyshevDistance(start) <= 2) {
        teleport(start, TeleportType.Exempt)
    } else {
        telejump(start, TeleportType.Exempt)
    }
    delay(1)
}

internal suspend fun ProtectedAccess.climbTo(dest: CoordGrid, seq: String, ticks: Int) {
    faceSquare(dest)
    anim(seq)
    delay(ticks)
    telejump(dest, TeleportType.Exempt)
}

// The client cannot interpolate across levels: glide on glideLevel, then change level on landing.
internal suspend fun ProtectedAccess.leapTo(
    dest: CoordGrid,
    seq: String,
    ticks: Int,
    glideLevel: Int = coords.level,
) {
    val start = coords
    val glideStart = CoordGrid(start.x, start.z, glideLevel)
    val glideEnd = CoordGrid(dest.x, dest.z, glideLevel)
    anim(seq)
    exactMove(
        start = glideStart,
        end = glideEnd,
        delay1 = 0,
        delay2 = ticks * CLIENT_CYCLES_PER_TICK,
        dir = emFaceTowards(start, dest),
        teleportType = TeleportType.Exempt,
    )
    try {
        delay(ticks)
    } finally {
        // Cancellation must still restore the destination plane.
        if (glideEnd != dest && player.coords != dest) {
            telejump(dest, TeleportType.Exempt)
        }
    }
}

internal suspend fun ProtectedAccess.zipTo(dest: CoordGrid, ticks: Int) {
    faceSquare(dest)
    anim(AgilityAnims.ZIPLINE_GRAB)
    delay(seqTicks(AgilityAnims.ZIPLINE_GRAB, fallback = 2))
    leapTo(dest, AgilityAnims.ZIPLINE_SLIDE, ticks, glideLevel = dest.level)
}

internal fun ProtectedAccess.setBalanceStyle(style: BalanceStyle) {
    val ready = style.ready.asRSCM(RSCMType.SEQ)
    val walk = style.walk.asRSCM(RSCMType.SEQ)
    player.bas =
        BasType(
            id = -1,
            readyAnim = ready,
            turnOnSpot = ready,
            walkForward = walk,
            walkBack = walk,
            walkLeft = walk,
            walkRight = walk,
            running = walk,
        )
    rebuildAppearance()
}

internal fun ProtectedAccess.clearBalanceStyle() {
    if (player.bas != null) {
        player.bas = null
        rebuildAppearance()
    }
}

internal suspend fun ProtectedAccess.balanceAlong(
    path: List<CoordGrid>,
    style: BalanceStyle,
    stopAt: Int = -1,
): Boolean {
    setBalanceStyle(style)
    var finished = false
    try {
        style.mount?.let {
            anim(it)
            delay(1)
        }
        for ((index, tile) in path.withIndex()) {
            if (index == stopAt) {
                finished = true
                return false
            }
            teleport(tile, TeleportType.Exempt)
            delay(1)
        }
        style.dismount?.let { anim(it) }
        finished = true
        return true
    } finally {
        clearBalanceStyle()
        if (!finished && player.coords != path.last()) {
            logger.warn {
                "Balance walk interrupted at ${player.coords}; placing $player on ${path.last()}."
            }
            telejump(path.last(), TeleportType.Exempt)
        }
    }
}

private val logger = InlineLogger()

internal suspend fun ProtectedAccess.fallTo(
    landing: CoordGrid,
    seq: String,
    minDamage: Int,
    maxDamage: Int,
) {
    anim(seq)
    delay(1)
    telejump(landing, TeleportType.Exempt)
    val damage = random.of(minDamage, maxDamage)
    queueHit(delay = 1, type = HitType.Typeless, damage = damage)
    mes("You lose your footing and fall to the ground below.")
    delay(1)
}
