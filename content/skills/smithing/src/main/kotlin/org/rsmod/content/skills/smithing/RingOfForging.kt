package org.rsmod.content.skills.smithing

import dev.openrune.util.Wearpos
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

// Charges belong to the player and survive ring swaps.
object RingOfForging {

    const val ITEM = "obj.ring_of_forging"

    const val MAX_CHARGES = 140

    private var Player.chargesUsed by intVarp("varp.smithing_ring_of_forging_used")

    fun isWearing(player: Player): Boolean = player.worn[Wearpos.Ring.slot]?.isType(ITEM) == true

    fun chargesRemaining(player: Player): Int = (MAX_CHARGES - player.chargesUsed).coerceAtLeast(0)

    // Call only after confirming that a ring is worn.
    fun consumeCharges(player: Player, charges: Int) {
        val used = (player.chargesUsed + charges).coerceAtMost(MAX_CHARGES)
        player.chargesUsed = used
        if (used < MAX_CHARGES) {
            return
        }
        crumbleWornRing(player)
        player.chargesUsed = 0
        player.mes("Your Ring of Forging has melted.")
    }

    fun checkCharges(player: Player) {
        player.mes(
            "You can smelt ${chargesRemaining(player)} more pieces of iron ore before a ring melts."
        )
    }

    fun prepareBreak(player: Player) {
        player.chargesUsed = 0
    }

    private fun crumbleWornRing(player: Player) {
        val ringSlot = Wearpos.Ring.slot
        if (player.worn[ringSlot]?.isType(ITEM) != true) {
            return
        }
        player.invDel(player.worn, ITEM, count = 1, slot = ringSlot)
    }
}
