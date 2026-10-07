package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.shops.Shops
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ShopkeeperScript @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        for (merchant in listOf("npc.deepfin_dwarf_durrik", "npc.deepfin_dwarf_durrik_1op")) {
            onProtectedEvent<NpcUnimplementedEvents.Op1>(merchant.asRSCM()) {
                startDialogue(it.npc) {
                    chatNpc(neutral, "Can I help you at all?")
                    if (choice2("What are you selling?", true, "Nothing, thanks.", false)) {
                        vars["varbit.deepfin_dwarf_durrik_met"] = 1
                        shops.openConfigured(player, it.npc, "inv.deepfin_dwarf_durrik_shop")
                    }
                }
            }
        }
        for ((type, binding) in resolveBindings()) {
            if ((0..4).none { type.actions.getOpOrNull(it).equals("Trade", ignoreCase = true) }) {
                continue
            }
            val counter = ShopkeeperCounters.requiresApproach(binding)
            for (slot in 0..4) {
                when (type.actions.getOpOrNull(slot)?.lowercase()) {
                    "trade" -> bind(type.id, slot, counter) { npc ->
                        openShop(npc, binding)
                    }
                    "talk-to" -> bind(type.id, slot, counter) { npc -> browse(npc, binding) }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.browse(npc: Npc, binding: ShopkeeperBinding) {
        if (!canTrade(npc, binding)) return
        startDialogue(npc) {
            chatNpc(happy, "Can I help you at all?")
            if (choice2("Yes please. What are you selling?", true, "No thanks.", false)) {
                openShop(npc, binding)
            }
        }
    }

    private fun ProtectedAccess.openShop(npc: Npc, binding: ShopkeeperBinding) {
        openShopBinding(shops, npc, binding)
    }

    private fun ScriptContext.bind(
        id: Int,
        slot: Int,
        counter: Boolean,
        action: suspend ProtectedAccess.(Npc) -> Unit,
    ) {
        when (slot) {
            0 -> onProtectedEvent<NpcUnimplementedEvents.Op1>(id) { action(it.npc) }
            1 -> onProtectedEvent<NpcUnimplementedEvents.Op2>(id) { action(it.npc) }
            2 -> onProtectedEvent<NpcUnimplementedEvents.Op3>(id) { action(it.npc) }
            3 -> onProtectedEvent<NpcUnimplementedEvents.Op4>(id) { action(it.npc) }
            4 -> onProtectedEvent<NpcUnimplementedEvents.Op5>(id) { action(it.npc) }
        }
        if (!counter) return
        val approach: suspend ProtectedAccess.(Npc) -> Unit = { npc ->
            if (isWithinApRange(npc, 2)) action(npc)
        }
        when (slot) {
            0 -> onProtectedEvent<NpcUnimplementedEvents.Ap1>(id) { approach(it.npc) }
            1 -> onProtectedEvent<NpcUnimplementedEvents.Ap2>(id) { approach(it.npc) }
            2 -> onProtectedEvent<NpcUnimplementedEvents.Ap3>(id) { approach(it.npc) }
            3 -> onProtectedEvent<NpcUnimplementedEvents.Ap4>(id) { approach(it.npc) }
            4 -> onProtectedEvent<NpcUnimplementedEvents.Ap5>(id) { approach(it.npc) }
        }
    }

    internal fun resolveBindings(): List<Pair<NpcServerType, ShopkeeperBinding>> {
        val bindings = linkedMapOf<Int, Pair<NpcServerType, ShopkeeperBinding>>()
        for (binding in ShopkeeperCatalog.shops) {
            checkNotNull(ServerCacheManager.getInventory(binding.inventory.asRSCM())) {
                "Missing inventory for shopkeeper binding: ${binding.inventory}"
            }
            for (merchant in binding.merchants) {
                val type = checkNotNull(ServerCacheManager.getNpc(merchant.asRSCM()))
                addBinding(type, binding, bindings)
            }
        }
        return bindings.values.toList()
    }

    private fun addBinding(
        type: NpcServerType,
        binding: ShopkeeperBinding,
        bindings: MutableMap<Int, Pair<NpcServerType, ShopkeeperBinding>>,
    ) {
        val previous = bindings.putIfAbsent(type.id, type to binding)
        if (previous != null) {
            check(previous.second.key == binding.key) {
                "NPC ${type.id} maps to multiple shop inventories"
            }
            return
        }
        val transforms = type.multiNpc.map { it.toInt() and 0xffff } + type.multiDefault
        for (id in transforms.distinct()) {
            val child = ServerCacheManager.getNpc(id) ?: continue
            addBinding(child, binding, bindings)
        }
    }
}
