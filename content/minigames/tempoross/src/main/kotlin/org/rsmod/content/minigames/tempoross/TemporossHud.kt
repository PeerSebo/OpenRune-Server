package org.rsmod.content.minigames.tempoross

import org.rsmod.content.minigames.tempoross.configs.TemporossConstants as C

// Argument order must match native clientscript 4074.
internal object TemporossHud {
    // Native model order: north totem, west mast, east mast, south totem.
    private val anchorOrder = intArrayOf(2, 0, 1, 3)

    fun arguments(game: TemporossEncounter, points: Int): Array<Int> {
        val burningAreas = game.fires.keys.mapNotNull { fire ->
            C.ANCHORS.indices.minByOrNull { index ->
                val anchor = game.translate(C.ANCHORS[index].source)
                val dx = fire.x - anchor.x
                val dz = fire.z - anchor.z
                dx * dx + dz * dz
            }
        }.toSet()
        return arrayOf(
            0, points, game.energy, game.essence, game.energyMax, game.essenceMax,
            game.intensity.coerceIn(0, C.INTENSITY_MAX) * C.HUD_INTENSITY_MAX / C.INTENSITY_MAX,
            *anchorOrder.map { if (it in game.brokenAnchors) 0 else 1 }.toTypedArray(),
            *anchorOrder.map { if (it in burningAreas) 1 else 0 }.toTypedArray(),
        )
    }
}
