package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

// Clientscript 5980 requires eleven arguments.
public data class GotrHudState(
    val power: Int,
    val maxPower: Int,
    val elementalRune: String?,
    val catalyticRune: String?,
    val guardianCount: Int,
    val guardianLimit: Int,
    val portalTicksRemaining: Int,
    val hugePortalDirection: Int = 0,
    val hugePortalTicksRemaining: Int = -1,
    val status: String = "",
) {
    internal fun arguments(elementalEnergy: Int, catalyticEnergy: Int): Array<Int> =
        arrayOf(
            elementalEnergy.coerceAtLeast(0),
            catalyticEnergy.coerceAtLeast(0),
            power.coerceIn(0, maxPower.coerceAtLeast(1)),
            maxPower.coerceAtLeast(1),
            hugePortalDirection.takeIf { it in 1..4 } ?: 0,
            ELEMENTAL_INDEX[normalize(elementalRune)] ?: 0,
            CATALYTIC_INDEX[normalize(catalyticRune)] ?: 0,
            guardianCount.coerceAtLeast(0),
            guardianLimit.coerceAtLeast(0),
            portalTicksRemaining.coerceAtLeast(-1),
            hugePortalTicksRemaining.coerceAtLeast(-1),
        )

    public companion object {
        // Native enums 4270/4271 map rune indices; zero is the inactive icon.
        internal val ELEMENTAL_INDEX = mapOf("air" to 1, "water" to 2, "earth" to 3, "fire" to 4)
        internal val CATALYTIC_INDEX =
            mapOf("mind" to 1, "body" to 2, "cosmic" to 3, "chaos" to 4,
                "nature" to 5, "law" to 6, "death" to 7, "blood" to 8)

        internal fun normalize(rune: String?): String? = rune?.removePrefix("obj.")?.removeSuffix("rune")
    }
}

@Singleton
public class GotrHud @Inject constructor(private val eventBus: EventBus) {
    public fun sync(player: Player, state: GotrHudState) {
        if (!player.ui.containsOverlay(HUD)) {
            player.ifOpenOverlay(HUD, HUD_TARGET, eventBus)
        }
        val arguments = state.arguments(
            player.vars["varbit.gotr_elemental_earned_this_game"],
            player.vars["varbit.gotr_catalytic_earned_this_game"],
        )
        player.runClientScript(HUD_UPDATE, *arguments)
        if (state.status.isNotEmpty()) player.ifSetText("component.gotr_hud:status", state.status)
    }

    // Do not overwrite an active teleport fade with lobby atmosphere.
    public fun syncAtmosphere(player: Player, insideTemple: Boolean) {
        if (!insideTemple) {
            player.ifCloseOverlay(ATMOSPHERE, eventBus)
            return
        }
        val target = ServerCacheManager.fromComponent(ATMOSPHERE_TARGET)
        if (player.ui.getOverlayOrNull(target) == null) {
            player.ifOpenOverlay(ATMOSPHERE, ATMOSPHERE_TARGET, eventBus)
        }
    }

    public fun hideHud(player: Player) {
        player.ifCloseOverlay(HUD, eventBus)
    }

    public fun hide(player: Player) {
        hideHud(player)
        player.ifCloseOverlay(ATMOSPHERE, eventBus)
    }

    public companion object {
        public const val HUD: String = "interface.gotr_hud"
        public const val ATMOSPHERE: String = "interface.gotr_overlay"
        public const val HUD_TARGET: String = "component.toplevel_osrs_stretch:overlay_hud"
        public const val ATMOSPHERE_TARGET: String = "component.toplevel_osrs_stretch:overlay_atmosphere"
        public val HUD_UPDATE: Int get() = "clientscript.[clientscript,gotr_hud_update]".asRSCM()
    }
}
