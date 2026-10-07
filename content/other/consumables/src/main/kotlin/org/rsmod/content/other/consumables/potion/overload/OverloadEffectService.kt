package org.rsmod.content.other.consumables.potion.overload

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.consumables.potion.PotionStatBoost
import org.rsmod.content.other.consumables.potion.applyStatBoost
import org.rsmod.content.other.consumables.potion.durationUnits
import org.rsmod.content.other.consumables.potion.nextTimedEffectDelay
import org.rsmod.content.other.consumables.potion.playPotionVisual
import org.rsmod.content.other.consumables.potion.restartTimer
import org.rsmod.content.other.consumables.potion.restoreHitpointsIfDrained
import org.rsmod.content.other.consumables.potion.restoreStatsToBase
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

@Singleton
class OverloadEffectService {
    internal fun canApply(access: ProtectedAccess, type: OverloadType): Boolean {
        val damage = OverloadRegistry[type].totalDamage
        if (access.player.hitpoints > damage) return true
        access.mes("You need more than $damage Hitpoints to drink this potion.")
        return false
    }

    internal fun apply(
        access: ProtectedAccess,
        type: OverloadType,
        boost: PotionStatBoost? = null,
        clientTier: Int = 0,
    ) {
        val definition = OverloadRegistry[type]
        val resolvedBoost = boost ?: requireNotNull(definition.defaultBoost)
        val buff = if (type == OverloadType.NIGHTMARE_ZONE) 3088 else when (clientTier) {
            1 -> 4152
            2 -> 3089
            3 -> 4153
            else -> error("Invalid overload tier: $clientTier")
        }
        with(access) {
            applyStatBoost(definition.boostedStats, resolvedBoost)
            val state = OverloadState(
                expiresAt = mapClock + definition.duration,
                boost = resolvedBoost,
                damageHitsRemaining = definition.damageHits,
                warned = false,
            )
            type.state.write(player, state)
            startDisplay(player, definition, clientTier)
            runClientScript("clientscript.[clientscript,buff_bar_start]", buff, mapClock.coerceAtLeast(1))
            player.restartTimer(definition.damageTimer, definition.damageInterval)
            player.restartTimer(definition.effectTimer, definition.refreshInterval)
        }
    }

    internal fun processDamage(access: ProtectedAccess, type: OverloadType) {
        val definition = OverloadRegistry[type]
        with(access) {
            val state = type.state.read(player) ?: return
            if (state.damageHitsRemaining <= 0) return
            val hits = state.damageHitsRemaining - 1
            type.state.write(player, state.copy(damageHitsRemaining = hits))
            playPotionVisual(definition.animation, definition.spotAnimation)
            takeInstantHit(HitType.Typeless, definition.damagePerHit)
            if (hits > 0 && type.state.read(player) != null) {
                player.timer(definition.damageTimer, definition.damageInterval)
            }
        }
    }

    internal fun process(access: ProtectedAccess, type: OverloadType) {
        val definition = OverloadRegistry[type]
        with(access) {
            var state = type.state.read(player) ?: return
            val remaining = state.expiresAt - mapClock
            if (remaining <= 0) {
                clear(player, type)
                restoreHitpointsIfDrained(definition.totalDamage)
                mes(definition.expiryMessage)
                return
            }
            if (!state.warned && remaining <= definition.warningLead) {
                mes(definition.warningMessage)
                state = state.copy(warned = true)
                type.state.write(player, state)
            }
            applyStatBoost(definition.boostedStats, state.boost)
            val units = durationUnits(remaining, definition.refreshInterval)
            VarPlayerIntMapSetter.set(player, definition.timerVarbit, units)
            val delay = nextTimedEffectDelay(
                remaining, definition.refreshInterval, definition.warningLead, state.warned,
            )
            player.timer(definition.effectTimer, delay)
        }
    }

    internal fun clear(player: Player, type: OverloadType) {
        val definition = OverloadRegistry[type]
        val active = type.state.read(player) != null
        type.state.clear(player)
        player.clearTimer(definition.effectTimer)
        player.clearTimer(definition.damageTimer)
        VarPlayerIntMapSetter.set(player, definition.timerVarbit, 0)
        definition.tierVarbit?.let { VarPlayerIntMapSetter.set(player, it, 0) }
        if (active) player.restoreStatsToBase(definition.boostedStats)
    }

    private fun startDisplay(player: Player, definition: OverloadDefinition, clientTier: Int) {
        VarPlayerIntMapSetter.set(player, definition.timerVarbit, 0)
        definition.tierVarbit?.let { VarPlayerIntMapSetter.set(player, it, clientTier) }
        val units = durationUnits(definition.duration, definition.refreshInterval)
        VarPlayerIntMapSetter.set(player, definition.timerVarbit, units)
    }
}
