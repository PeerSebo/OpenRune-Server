package org.rsmod.content.travel.jewellery

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldSubOp
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.script.onOpWorn4
import org.rsmod.api.script.onOpWorn5
import org.rsmod.api.script.onOpWorn6
import org.rsmod.api.script.onOpWorn7
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class JewelleryTeleportScript
@Inject
constructor(
    private val teleportValidator: PlayerTeleportValidator,
    private val areaChecker: AreaChecker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerQueueWithArgs<CoordGrid>("queue.jewellery_teleport_land") {
            telejump(it.args, TeleportType.Exempt)
            anim(TELEPORT_END_ANIM)
        }
        for (item in JewelleryTeleports.all) {
            for (obj in item.objs) {
                register(item, obj)
            }
        }
    }

    private fun ScriptContext.register(item: Jewellery, obj: String) {
        val type =
            ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))
                ?: error("Missing teleport jewellery obj: $obj")
        val ops = type.interfaceOptions

        // Items whose `Wear` op sits in slot 1 (slayer rings) lose the engine's default equip, so
        // give it back.
        if (ops.getOrNull(0) == "Wear") {
            onOpHeld1(obj) { invEquip(it.slot) }
        }
        val teleportOpIndex = ops.indexOf("Teleport").takeIf { it != -1 } ?: ops.indexOf("Rub")
        if (teleportOpIndex == -1) {
            logger.warn { "No rub or teleport op on $obj" }
            return
        }
        onHeldOp(teleportOpIndex, obj) { inv, slot -> rub(item, inv, slot) }
        val rubIndex = ops.indexOf("Rub")
        if (rubIndex != -1 && rubIndex != teleportOpIndex) {
            onHeldOp(rubIndex, obj) { _, _ -> mes("Contacting your Slayer master is not available yet.") }
        }
        onOpHeldSubOp(obj) { event ->
            if (event.op.slot != teleportOpIndex + 1) {
                mes("That option is not available yet.")
                return@onOpHeldSubOp
            }
            val destination = item.destinations.getOrNull(subopIndex(event.subop))
            if (destination == null) {
                logger.warn {
                    "Unknown sub-op ${event.subop} for $obj (${item.destinations.size} destinations)"
                }
                return@onOpHeldSubOp
            }
            teleport(item, event.inventory, event.slot, destination)
        }

        val menuOp = item.wornMenuOp
        if (menuOp != null) {
            onWornOp(menuOp, obj) { slot -> rub(item, worn, slot) }
            return
        }
        for ((index, destination) in item.destinations.withIndex()) {
            onWornOp(index + FIRST_WORN_DESTINATION_OP, obj) { slot ->
                teleport(item, worn, slot, destination)
            }
        }
    }

    private fun ScriptContext.onHeldOp(
        index: Int,
        obj: String,
        action: suspend ProtectedAccess.(inv: Inventory, slot: Int) -> Unit,
    ) {
        when (index) {
            0 -> onOpHeld1(obj) { action(it.inventory, it.slot) }
            1 -> onOpHeld2(obj) { action(it.inventory, it.slot) }
            2 -> onOpHeld3(obj) { action(it.inventory, it.slot) }
            3 -> onOpHeld4(obj) { action(it.inventory, it.slot) }
            else -> logger.warn { "Unsupported held op slot $index for $obj" }
        }
    }

    private fun ScriptContext.onWornOp(
        op: Int,
        obj: String,
        action: suspend ProtectedAccess.(slot: Int) -> Unit,
    ) {
        when (op) {
            2 -> onOpWorn2(obj) { action(it.slot) }
            3 -> onOpWorn3(obj) { action(it.slot) }
            4 -> onOpWorn4(obj) { action(it.slot) }
            5 -> onOpWorn5(obj) { action(it.slot) }
            6 -> onOpWorn6(obj) { action(it.slot) }
            7 -> onOpWorn7(obj) { action(it.slot) }
            else -> logger.warn { "Unsupported worn op $op for $obj" }
        }
    }

    // Client submenu entries are one-based; zero is tolerated as the first entry.
    private fun subopIndex(subop: Int): Int = if (subop <= 0) 0 else subop - 1

    private suspend fun ProtectedAccess.rub(item: Jewellery, inv: Inventory, slot: Int) {
        val names = item.destinations.map { it.name }
        val choice =
            when (names.size) {
                1 -> choice2(names[0], 0, NOWHERE, -1, title = MENU_TITLE)
                2 -> choice3(names[0], 0, names[1], 1, NOWHERE, -1, title = MENU_TITLE)
                3 ->
                    choice4(
                        names[0],
                        0,
                        names[1],
                        1,
                        names[2],
                        2,
                        NOWHERE,
                        -1,
                        title = MENU_TITLE,
                    )
                4 ->
                    choice5(
                        names[0],
                        0,
                        names[1],
                        1,
                        names[2],
                        2,
                        names[3],
                        3,
                        NOWHERE,
                        -1,
                        title = MENU_TITLE,
                    )
                else -> {
                    val picked = menu(MENU_TITLE, hotkeys = true, choices = names + NOWHERE)
                    if (picked in names.indices) picked else -1
                }
            }
        val destination = item.destinations.getOrNull(choice) ?: return
        teleport(item, inv, slot, destination)
    }

    private suspend fun ProtectedAccess.teleport(
        item: Jewellery,
        inv: Inventory,
        slot: Int,
        destination: JewelleryDestination,
    ) {
        val coords = destination.coords
        if (coords == null) {
            mes("${destination.name} has not been added to this server yet.")
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        if (destination.wilderness) {
            val confirm =
                choice2(
                    "Yes, teleport me into the Wilderness.",
                    true,
                    "No, I will stay here.",
                    false,
                    title = "${destination.name} is in the Wilderness. Teleport anyway?",
                )
            if (!confirm) {
                return
            }
        }
        val denial = teleportValidator.validate(player, item.teleportType, areaChecker)
        if (denial != null) {
            mes(denial, ChatType.Engine)
            return
        }
        if (!spendCharge(item, inv, slot)) {
            return
        }
        actionDelay = mapClock + ACTION_DELAY
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_SPOTANIM_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        // A consumed charge commits the landing. Player queues survive unrelated UI clicks;
        // a coroutine delay would lose the teleport after charging the item.
        player.abortRoute()
        clearQueue("queue.jewellery_teleport_land")
        queue("queue.jewellery_teleport_land", TELEPORT_DELAY + 1, coords)
    }

    private fun ProtectedAccess.spendCharge(item: Jewellery, inv: Inventory, slot: Int): Boolean {
        val obj = inv[slot] ?: return false
        val name = RSCM.getReverseMapping(RSCMType.OBJ, obj.id)
        if (name !in item.objs) {
            return false
        }
        if (name !in item.charged) {
            return true
        }
        val next = item.afterCharge(name)
        val success =
            if (next == null) {
                invDel(inv, name, count = 1, slot = slot).success
            } else {
                invReplaceSlot(inv, slot, count = 1, replacement = next.type()).success
            }
        if (!success) {
            return false
        }
        if (inv === worn) {
            rebuildAppearance()
        }
        val left = item.chargesAfter(name) ?: 0
        when {
            next == null -> mes("Your ${item.label} crumbles to dust.")
            left == 0 -> mes("Your ${item.label} has run out of charges.")
            left == 1 -> mes("Your ${item.label} has one charge left.")
            else -> mes("Your ${item.label} has $left charges left.")
        }
        return true
    }

    private fun String.type(): ItemServerType =
        ServerCacheManager.getItem(asRSCM(RSCMType.OBJ)) ?: error("Missing obj: $this")

    private companion object {
        private val logger = InlineLogger()

        private const val MENU_TITLE = "Where would you like to teleport to?"
        private const val NOWHERE = "Nowhere"

        /** Worn op 1 is `Remove`; the item's `wear_op1` param is worn op 2. */
        private const val FIRST_WORN_DESTINATION_OP = 2

        private const val TELEPORT_ANIM = "seq.human_castteleport"
        private const val TELEPORT_END_ANIM = "seq.human_castteleport_reverse"
        private const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        private const val TELEPORT_SPOTANIM_HEIGHT = 92
        private const val TELEPORT_SOUND = "synth.TELEPORT_ALL"
        private const val TELEPORT_DELAY = 3
        private const val ACTION_DELAY = 4
    }
}
