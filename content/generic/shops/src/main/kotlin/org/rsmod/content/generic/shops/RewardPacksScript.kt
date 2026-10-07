package org.rsmod.content.generic.shops

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RewardPacksScript : PluginScript() {
    override fun ScriptContext.startup() {
        RewardPackData.validate()
        for (pack in RewardPackData.packs) {
            onOpHeld1(pack.pack) {
                val contents = when (pack.kind) {
                    RewardPackKind.Fixed -> mapOf(requireNotNull(pack.item) to pack.count)
                    RewardPackKind.Gems -> buildMap {
                        repeat(pack.count) {
                            val gem = rollGem(random)
                            put(gem, getOrDefault(gem, 0) + 1)
                        }
                    }
                }
                open(it.inventory, it.slot, it.obj, contents)
            }
        }
    }

    private fun ProtectedAccess.open(
        inventory: Inventory,
        slot: Int,
        pack: InvObj,
        contents: Map<String, Int>,
    ) {
        if (inventory !== inv || inventory[slot]?.id != pack.id) return
        val transaction = player.invTransaction(inventory) {
            val target = select(inventory)
            delete(target, pack.id, 1, slot = slot)
            for ((item, count) in contents) add(target, item.asRSCM(), count)
        }
        if (transaction.failure) {
            mes("You don't have enough inventory space to open this pack.")
            return
        }
        for (gem in RewardPackData.gems) {
            val collectionItem = gem.collectionItem ?: continue
            contents[gem.item]?.let { CollectionLog.grant(player, collectionItem, it) }
        }
    }

    internal companion object {
        val fixedPacks get() = RewardPackData.fixedPacks
        fun rollGem(random: GameRandom): String = RewardPackData.rollGem(random)
    }
}
