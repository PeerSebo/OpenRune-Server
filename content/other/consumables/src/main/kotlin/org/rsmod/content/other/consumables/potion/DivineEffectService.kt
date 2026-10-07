package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.StatBoostDecayPrevention
import org.rsmod.api.player.stat.clearPositiveStatBoost
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class DivineEffectService @Inject constructor(private val worldClock: MapClock) {
    fun apply(access: ProtectedAccess, effect: PotionEffectRow) {
        if (effect.duration <= 0) return
        val state = checkNotNull(DivineEffectState.entries.find { it.key == effect.key }) {
            "Divine potion '${effect.key}' requires registered state variables."
        }
        val player = access.player
        DivineState.migrate(player)
        state.setExpiresAt(player, access.mapClock + effect.duration)
        state.setWarned(player, false)
        addDecayPrevention(player, state)
        syncHud(player, restart = true)
        snapshot(player)
        schedule(player, access.mapClock)
    }

    internal fun onLogin(player: Player) {
        DivineState.migrate(player)
        val clock = worldClock.cycle
        DivineEffectState.entries.forEach { state ->
            removeDecayPrevention(player, state)
            val remaining = state.remaining(player)
            state.setExpiresAt(player, if (remaining > 0) clock + remaining else 0)
            state.setWarned(player, false)
            if (remaining > 0) addDecayPrevention(player, state)
        }
        syncHud(player, restart = false, clock = clock)
        schedule(player, clock)
    }

    internal fun onLogout(player: Player) {
        snapshot(player)
        syncHud(player, restart = false)
    }

    internal fun snapshot(player: Player) {
        DivineState.migrate(player)
        DivineEffectState.entries.forEach { state ->
            val expiry = state.expiresAt(player)
            state.setRemaining(player, if (expiry == 0) 0 else expiry - player.currentMapClock)
        }
    }

    internal fun onDeath(player: Player) = clear(player)

    fun process(access: ProtectedAccess) {
        val player = access.player
        var expired = false
        DivineEffectState.entries.forEach { state ->
            val expiry = state.expiresAt(player)
            if (expiry == 0) return@forEach
            val remaining = expiry - access.mapClock
            if (remaining <= 0) {
                removeDecayPrevention(player, state)
                boostStats(state.effect).forEach { stat ->
                    if (!StatBoostDecayPrevention.prevents(player, stat)) {
                        player.clearPositiveStatBoost(stat)
                    }
                }
                state.setExpiresAt(player, 0)
                state.setRemaining(player, 0)
                state.setWarned(player, false)
                expired = true
                access.mes("Your ${state.potionName} has expired.")
            } else if (remaining <= WARNING_LEAD && !state.warned(player)) {
                state.setWarned(player, true)
                access.mes("Your ${state.potionName} is about to expire.")
            }
        }
        if (expired) {
            snapshot(player)
            syncHud(player, restart = true)
        }
        schedule(player, access.mapClock)
    }

    fun cancelMaintenance(player: Player) = clear(player)

    private fun clear(player: Player) {
        DivineEffectState.entries.forEach { removeDecayPrevention(player, it) }
        DivineState.clear(player)
        player.clearTimer(DIVINE_TIMER)
        syncHud(player, restart = false)
    }

    private fun schedule(player: Player, clock: Int) {
        player.clearTimer(DIVINE_TIMER)
        val delay = DivineEffectState.entries.mapNotNull { state ->
            val expiry = state.expiresAt(player)
            if (expiry == 0) return@mapNotNull null
            val remaining = (expiry - clock).coerceAtLeast(1)
            val untilWarning = remaining - WARNING_LEAD
            if (!state.warned(player) && untilWarning > 0) untilWarning else remaining
        }.minOrNull() ?: return
        player.timerAt(DIVINE_TIMER, clock, delay)
    }

    private fun collectBoostStats(effect: PotionEffectRow, destination: MutableSet<String>) {
        when (effect.kind) {
            "stat_boost", "flat_stat_boost" -> effect.skills.forEach { destination += it.internalName }
            "compound" -> effect.effects.forEach { collectBoostStats(it, destination) }
            "divine" -> effect.baseEffect?.let { collectBoostStats(it, destination) }
        }
    }

    private fun boostStats(effect: PotionEffectRow): Set<String> = linkedSetOf<String>().also {
        collectBoostStats(effect, it)
    }

    private fun addDecayPrevention(player: Player, state: DivineEffectState) {
        val effect = state.effect
        StatBoostDecayPrevention.add(player, boostStats(effect), "consumable.divine.${effect.rowId}")
    }

    private fun removeDecayPrevention(player: Player, state: DivineEffectState) {
        val effect = state.effect
        StatBoostDecayPrevention.remove(player, boostStats(effect), "consumable.divine.${effect.rowId}")
    }

    private fun syncHud(player: Player, restart: Boolean, clock: Int = player.currentMapClock) {
        val values = HUD_VARBITS.associateWith { 0 }.toMutableMap()
        DivineEffectState.entries.forEach { state ->
            val expiry = state.expiresAt(player)
            val remaining = if (expiry == 0) 0 else (expiry - clock).coerceAtLeast(0)
            state.hudVarbits.forEach { varbit ->
                values[varbit] = maxOf(values.getValue(varbit), remaining)
            }
        }
        if (restart) HUD_VARBITS.forEach { VarPlayerIntMapSetter.set(player, it, 0) }
        values.forEach { (varbit, value) -> VarPlayerIntMapSetter.set(player, varbit, value) }
    }

    companion object {
        const val DIVINE_TIMER = "timer.potion_divine"
        private const val WARNING_LEAD = 25
        private val HUD_VARBITS = DivineEffectState.entries.flatMap { it.hudVarbits }.distinct()
    }
}
