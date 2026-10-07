package org.rsmod.content.areas.city.draynor

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Chronicle @Inject constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(Book, Card) { charge(player) }
        onOpHeld3(Book) { teleport() }
        onOpHeld4(Book) { checkCharges(player) }
        onOpWorn2(Book) { checkCharges(player) }
        onOpWorn3(Book) { teleport() }
        onPlayerQueue(Landing) {
            telejump(Destination, TeleportType.Exempt)
            resetAnim()
            mes("<col=ef1020>Your book has ${player.charges} charges left.</col>")
        }
    }

    private fun ProtectedAccess.teleport() {
        if (actionDelay > mapClock) return
        if (Book !in inv && Book !in worn) return
        val denial = teleportValidator.validate(player, TeleportType.Chronicle, areaChecker)
        if (denial != null) {
            mes(denial)
            return
        }
        if (player.charges <= 0) {
            mes("Your Chronicle has no teleport charges. You can buy teleport cards from Diango in Draynor Village.")
            return
        }
        player.charges--
        actionDelay = mapClock + 5
        player.abortRoute()
        anim("seq.human_castteleport")
        spotanim("spotanim.teleport_casting", height = 92)
        clearQueue(Landing)
        queue(Landing, 4)
    }

    internal companion object {
        const val Book = "obj.chronicle"
        const val Card = "obj.chronicle_tele_card"
        const val ChargeVar = "varp.chronicle_charges"
        const val Landing = "queue.chronicle_teleport_land"
        const val Capacity = 1000
        val Destination = CoordGrid(3200, 3354)
        var Player.charges by intVarp(ChargeVar)

        fun charge(player: Player): Int {
            if (Book !in player.inv && Book !in player.worn) return 0
            val available = player.inv.physicalCount(Card)
            val count = minOf(available, (Capacity - player.charges).coerceAtLeast(0))
            if (count == 0) {
                if (player.charges >= Capacity) player.mes("Your Chronicle is already fully charged.")
                return 0
            }
            val transaction = player.invTransaction(player.inv) {
                delete(select(player.inv), Card.asRSCM(), count)
            }
            if (transaction.failure) return 0
            player.charges += count
            val added = if (count == 1) "a single charge" else "$count charges"
            player.mes("You add $added to your book. It now has ${player.charges} charges.")
            return count
        }

        fun checkCharges(player: Player) {
            player.mes("Your book has ${player.charges} charges left.")
        }
    }
}
