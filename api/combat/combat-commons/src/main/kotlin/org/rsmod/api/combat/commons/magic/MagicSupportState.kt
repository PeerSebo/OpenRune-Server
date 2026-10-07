package org.rsmod.api.combat.commons.magic

import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public var Player.magicChargeUntil: Int by intVarp("varp.magic_charge_until")
public var Player.markOfDarknessUntil: Int by intVarp("varp.magic_mark_until")

public val Player.hasMagicCharge: Boolean
    get() = magicChargeUntil > currentMapClock

public val Player.hasMarkOfDarkness: Boolean
    get() = markOfDarknessUntil > currentMapClock
