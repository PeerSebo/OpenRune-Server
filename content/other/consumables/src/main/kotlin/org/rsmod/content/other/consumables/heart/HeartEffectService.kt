package org.rsmod.content.other.consumables.heart

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.clearPositiveStatBoost
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class HeartEffectService @Inject constructor(private val worldClock: MapClock) {
    internal fun activate(access: ProtectedAccess, type: HeartType) {
        with(access) {
            HeartState.migrate(player)
            val remaining = (player.heartCooldownExpiresAt - mapClock).coerceAtLeast(0)
            if (remaining > 0) {
                sendCooldownMessage(remaining)
                return
            }

            anim(HEART_ANIMATION)
            spotanim(spot = type.spotanim, delay = 0, height = 0, slot = 0)
            statBoost(stat = MAGIC, constant = type.constant, percent = type.percent)
            val expiresAt = mapClock + type.cooldown
            player.heartCooldownExpiresAt = expiresAt
            player.savedHeartCooldownTicks = type.cooldown
            updateMaintainedBoost(player, type.maintainsBoost, expiresAt)
            VarPlayerIntMapSetter.set(player, HEART_TIMER_VARBIT, 0)
            syncDisplay(player, type.cooldown)
            runClientScript(BUFF_BAR_START_CLIENTSCRIPT, HEART_BUFF_STRUCT, mapClock.coerceAtLeast(1))
            player.clearTimer(TIMER)
            player.timer(TIMER, type.cooldown)
        }
    }

    internal fun process(access: ProtectedAccess) {
        val player = access.player
        val expiresAt = player.heartCooldownExpiresAt
        val remaining = expiresAt - access.mapClock
        if (expiresAt == 0 || remaining <= 0) {
            if (player.saturatedHeartExpiresAt != 0) player.clearPositiveStatBoost(MAGIC)
            clearStoredState(player)
            return
        }
        player.clearTimer(TIMER)
        player.timer(TIMER, remaining)
    }

    internal fun cancelMaintainedBoost(player: Player) {
        HeartState.migrate(player)
        StatBoostDecayPrevention.removeSource(player, SATURATED_SOURCE)
        player.saturatedHeartExpiresAt = 0
        player.savedSaturatedHeartActive = false
    }

    internal fun onLogin(player: Player) {
        HeartState.migrate(player)
        val remaining = player.savedHeartCooldownTicks
        if (remaining <= 0) {
            clearStoredState(player)
            return
        }
        val expiresAt = worldClock.cycle + remaining
        player.heartCooldownExpiresAt = expiresAt
        updateMaintainedBoost(player, player.savedSaturatedHeartActive, expiresAt)
        syncDisplay(player, remaining)
        player.clearTimer(TIMER)
        player.timerAt(TIMER, worldClock.cycle, remaining.coerceAtLeast(1))
    }

    internal fun onLogout(player: Player) {
        HeartState.migrate(player)
        snapshot(player)
        syncDisplay(player, player.savedHeartCooldownTicks)
    }

    internal fun snapshot(player: Player) {
        val expiresAt = player.heartCooldownExpiresAt
        val remaining = if (expiresAt == 0) 0 else (expiresAt - player.currentMapClock).coerceAtLeast(0)
        player.savedHeartCooldownTicks = remaining
        player.savedSaturatedHeartActive = remaining > 0 &&
            player.saturatedHeartExpiresAt > player.currentMapClock
    }

    private fun updateMaintainedBoost(player: Player, active: Boolean, expiresAt: Int) {
        if (active) {
            player.saturatedHeartExpiresAt = expiresAt
            player.savedSaturatedHeartActive = true
            StatBoostDecayPrevention.add(player, MAGIC, SATURATED_SOURCE)
        } else {
            cancelMaintainedBoost(player)
        }
    }

    private fun ProtectedAccess.sendCooldownMessage(remainingTicks: Int) {
        val totalSeconds = (remainingTicks * 3 + 4) / 5
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minuteUnit = if (minutes == 1) "minute" else "minutes"
        val secondUnit = if (seconds == 1) "second" else "seconds"
        mes("The heart is still drained of its power.")
        mes(
            "Judging by how it feels, it will be ready in around " +
                "$minutes $minuteUnit and $seconds $secondUnit.",
        )
    }

    private fun clearStoredState(player: Player) {
        StatBoostDecayPrevention.removeSource(player, SATURATED_SOURCE)
        HeartState.clear(player)
        syncDisplay(player, 0)
        player.clearTimer(TIMER)
    }

    private fun syncDisplay(player: Player, remaining: Int) {
        VarPlayerIntMapSetter.set(player, HEART_TIMER_VARBIT, timerUnits(remaining))
    }

    companion object {
        internal const val TIMER = "timer.heart_cooldown"
        private const val MAGIC = "stat.magic"
        private const val SATURATED_SOURCE = "consumable.saturated_heart"
        private const val HEART_ANIMATION = "seq.human_cast_selfimbue"
        private const val HEART_TIMER_VARBIT = "varbit.imbued_heart_timer"
        private const val BUFF_BAR_START_CLIENTSCRIPT = "clientscript.[clientscript,buff_bar_start]"
        private const val HEART_BUFF_STRUCT = 3083
        private const val TIMER_UNIT_TICKS = 10

        private fun timerUnits(ticks: Int): Int =
            if (ticks <= 0) 0 else (ticks + TIMER_UNIT_TICKS - 1) / TIMER_UNIT_TICKS
    }
}

internal enum class HeartType(
    val item: String,
    val cooldown: Int,
    val constant: Int,
    val percent: Int,
    val spotanim: String,
    val maintainsBoost: Boolean,
) {
    Imbued(
        item = "obj.imbued_heart",
        cooldown = 700,
        constant = 1,
        percent = 10,
        spotanim = "spotanim.imbued_heart_impact",
        maintainsBoost = false,
    ),
    Saturated(
        item = "obj.saturated_heart",
        cooldown = 500,
        constant = 4,
        percent = 10,
        spotanim = "spotanim.imbued_heart_impact03",
        maintainsBoost = true,
    ),
}
