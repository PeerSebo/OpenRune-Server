package org.rsmod.api.inv.storage

public interface PlayerItemStorageHook {
    public val consumePolicy: VirtualItemConsumePolicy
        get() = VirtualItemConsumePolicy.InventoryFirst

    public fun shouldProcess(ctx: PlayerItemStorageContext): Boolean

    public fun contains(ctx: PlayerItemStorageContext): Int

    public fun remove(ctx: PlayerItemStorageContext, amount: Int): Int

    public fun add(ctx: PlayerItemStorageContext, amount: Int): Int
}
