package org.rsmod.content.minigames.sorceressgarden.configs

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

var Player.osmanApprenticeUnlocked: Boolean by boolVarBit("varbit.osman_apprentice")

var Player.osmanWantsPomes: Boolean by boolVarBit("varbit.osman_want_pomes")
