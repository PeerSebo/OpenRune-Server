package org.rsmod.api.player.cinematic

import org.rsmod.api.utils.vars.VarEnumDelegate

public enum class CameraMode(override val varValue: Int) : VarEnumDelegate {
    Normal(0),
    Close(1),
    Far(2),
    Fixed(3),
}
