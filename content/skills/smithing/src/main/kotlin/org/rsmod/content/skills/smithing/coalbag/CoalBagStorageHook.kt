package org.rsmod.content.skills.smithing.coalbag

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.inv.storage.PlayerItemStorageContext
import org.rsmod.api.inv.storage.PlayerItemStorageHook
import org.rsmod.api.inv.storage.VirtualItemConsumePolicy

@Singleton
internal class CoalBagStorageHook @Inject constructor() : PlayerItemStorageHook {
    override val consumePolicy: VirtualItemConsumePolicy = VirtualItemConsumePolicy.InventoryFirst

    override fun contains(ctx: PlayerItemStorageContext): Int = CoalBag.storedAmount(ctx.player)

    override fun remove(ctx: PlayerItemStorageContext, amount: Int): Int =
        CoalBag.removeStored(ctx.player, amount)

    /** Only an *open* bag auto-collects incoming coal (mined coal etc.). */
    override fun add(ctx: PlayerItemStorageContext, amount: Int): Int =
        if (CoalBag.isOpenInInventory(ctx.player)) {
            CoalBag.depositUpTo(ctx.player, amount)
        } else {
            0
        }

    // Closed bags still supply coal; open/closed affects only incoming auto-collection.
    override fun shouldProcess(ctx: PlayerItemStorageContext): Boolean =
        CoalBag.isCoal(ctx.itemInternal) && CoalBag.hasBagInInventory(ctx.player)
}
