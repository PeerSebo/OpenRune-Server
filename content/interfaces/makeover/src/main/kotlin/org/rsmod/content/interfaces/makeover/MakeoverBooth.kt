package org.rsmod.content.interfaces.makeover

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Player

internal object MakeoverBooth {
    const val Timer: String = "timer.makeover_changing_booth"

    const val Spotanim: String = "spotanim.makeover_changing_booth_idle"
}

// Arm after modal-open cleanup so the close event does not immediately clear the timer.
internal fun ProtectedAccess.startMakeoverBooth() {
    player.softTimer(MakeoverBooth.Timer, 1)
}

internal fun stopMakeoverBooth(player: Player) {
    player.clearSoftTimer(MakeoverBooth.Timer)
}

internal fun Player.makeoverBoothLoop(worldRepo: WorldRepository) {
    if (MakeoverSessions.current(this) == null) {
        clearSoftTimer(MakeoverBooth.Timer)
        return
    }
    // A world map-animation otherwise broadcasts the concealed staff member's exact tile.
    if (staffInvisible) return
    // Resolve gamevals when firing; top-level initialization runs before the tables exist.
    val spotanim = SpotanimType(MakeoverBooth.Spotanim.asRSCM(RSCMType.SPOTANIM))
    worldRepo.spotanimMap(spotanim, coords)
}
