package org.rsmod.api.player.interact

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.events.interact.HeldUContentEvents
import org.rsmod.api.player.events.interact.HeldUDefaultEvents
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType

public class HeldUInteractions @Inject constructor(private val eventBus: EventBus) {
    private val logger = InlineLogger()

    public suspend fun interact(
        access: ProtectedAccess,
        inventory: Inventory,
        selectedItemServerType: ItemServerType,
        selectedSlot: Int,
        targetItemServerType: ItemServerType,
        targetSlot: Int,
    ) {
        if (selectedSlot == targetSlot) {
            resendSlot(inventory, 0)
            return
        }

        val selectedObj = inventory[selectedSlot]
        if (!objectVerify(inventory, selectedObj, selectedItemServerType)) {
            return
        }

        val targetObj = inventory[targetSlot]
        if (!objectVerify(inventory, targetObj, targetItemServerType)) {
            return
        }

        access.opHeldU(selectedItemServerType, selectedSlot, targetItemServerType, targetSlot)
    }

    private suspend fun ProtectedAccess.opHeldU(
        selectedItemServerType: ItemServerType,
        selectedSlot: Int,
        targetItemServerType: ItemServerType,
        targetSlot: Int,
    ) {
        // Try both operand orders at each specificity tier before falling back, so a wildcard
        // cannot beat a reversed exact pair.
        val combination =
            eitherOrientation(
                selectedItemServerType,
                selectedSlot,
                targetItemServerType,
                targetSlot,
                HeldUEvents::Type,
            )
                ?: eitherOrientation(
                    selectedItemServerType,
                    selectedSlot,
                    targetItemServerType,
                    targetSlot,
                    HeldUContentEvents::Type,
                )
                ?: eitherOrientation(
                    selectedItemServerType,
                    selectedSlot,
                    targetItemServerType,
                    targetSlot,
                    HeldUContentEvents::Content,
                )
                ?: eitherOrientation(
                    selectedItemServerType,
                    selectedSlot,
                    targetItemServerType,
                    targetSlot,
                    HeldUDefaultEvents::Type,
                )
                ?: eitherOrientation(
                    selectedItemServerType,
                    selectedSlot,
                    targetItemServerType,
                    targetSlot,
                    HeldUDefaultEvents::Content,
                )

        if (combination != null) {
            eventBus.publish(this, combination)
            return
        }

        mes(constants.dm_default, ChatType.Engine)
        logger.debug {
            "opHeldU for `${selectedItemServerType.name}` on `${targetItemServerType.name}` is not " +
                "implemented: selected=$selectedItemServerType, target=$targetItemServerType"
        }
    }

    /**
     * Resolves a single registration tier, trying `first on second` and then `second on first`.
     *
     * The event is always built in the orientation that actually matched, so the objs a script
     * receives still line up with the order it registered them in - the guarantee documented on
     * `ScriptContext.onOpHeldU`.
     */
    private fun eitherOrientation(
        first: ItemServerType,
        firstSlot: Int,
        second: ItemServerType,
        secondSlot: Int,
        event: (ItemServerType, Int, ItemServerType, Int) -> SuspendEvent<ProtectedAccess>,
    ): SuspendEvent<ProtectedAccess>? {
        val forward = event(first, firstSlot, second, secondSlot)
        if (eventBus.contains(forward::class.java, forward.id)) {
            return forward
        }

        val reversed = event(second, secondSlot, first, firstSlot)
        if (eventBus.contains(reversed::class.java, reversed.id)) {
            return reversed
        }

        return null
    }

    private fun objectVerify(inv: Inventory, obj: InvObj?, type: ItemServerType): Boolean {
        if (obj == null || !obj.isType(type)) {
            resendSlot(inv, 0)
            return false
        }
        return true
    }
}
