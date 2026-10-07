package org.rsmod.content.minigames.blastfurnace.core

import org.rsmod.game.entity.Player
internal val Player.blastFurnaceIronArmedForSteel: Boolean
    get() = blastFurnaceCoal > 0
