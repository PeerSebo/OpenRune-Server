package org.rsmod.content.skills.farming.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingPatchDef
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchType
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.content.skills.farming.configs.FarmingMessages
import org.rsmod.content.skills.farming.configs.FarmingServiceData
import org.rsmod.content.skills.farming.configs.ProtectionPayment
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.cert
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FarmingGardenerScript @Inject constructor(private val manager: FarmingManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (gardener in FarmingServiceData.gardeners) {
            if (gardener.choice) {
                onOpNpc3(gardener.npc) {
                    val patch = choice2(
                        "Northern allotment", gardener.patches[0],
                        "Southern allotment", gardener.patches[1],
                    )
                    payGardener(it.npc, patch)
                }
            } else {
                onOpNpc3(gardener.npc) { payGardener(it.npc, gardener.patches[0]) }
                if (gardener.patches.size == 2) {
                    onOpNpc4(gardener.npc) { payGardener(it.npc, gardener.patches[1]) }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.payGardener(npc: Npc, patchKey: String) {
        val def = manager.patchByKey(FarmingPatchKey(patchKey)) ?: return
        val state = manager.state(player, def)
        val cropId = state.cropId
        if (cropId == null) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_NOTHING_PLANTED) }
            return
        }
        val crop = manager.crop(cropId) ?: return
        if ((def.type == FarmingPatchType.TREE || def.type == FarmingPatchType.FRUIT_TREE) &&
            !state.dead && state.stage >= crop.grownStage
        ) {
            removeTree(npc, def, cropId)
            return
        }
        val payment = FarmingCrops.byId(cropId)?.protection
        if (payment == null) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_CANNOT_PROTECT) }
            return
        }
        if (state.dead || state.stage >= crop.grownStage) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_CANNOT_PROTECT) }
            return
        }
        if (manager.isPaidProtected(player, def)) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_ALREADY_PAID) }
            return
        }
        if (state.protected) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_UNNECESSARY) }
            return
        }
        val itemName = itemDisplayName(payment.obj)
        startDialogue(npc) {
            chatNpc(quiz, FarmingMessages.gardenerOffer(itemName, payment.count))
            val accept = choice2("Yes.", true, "No.", false)
            if (!accept) {
                chatPlayer(neutral, "No.")
                chatNpc(neutral, FarmingMessages.GARDENER_DECLINED)
                return@startDialogue
            }
            chatPlayer(happy, "Yes.")
            // Revalidate after dialogue: the crop may have died, matured or changed before payment.
            val current = manager.state(player, def)
            if (current.cropId != cropId || current.dead || current.stump ||
                current.stage >= crop.grownStage || current.protected ||
                manager.isPaidProtected(player, def)
            ) {
                chatNpc(neutral, FarmingMessages.GARDENER_CANNOT_PROTECT)
                return@startDialogue
            }
            if (!access.payGardenerFee(payment)) {
                chatNpc(sad, FarmingMessages.gardenerNotEnoughPayment(payment.count, itemName))
                return@startDialogue
            }
            manager.setPaidProtected(player, def, true)
            mesbox(FarmingMessages.GARDENER_PAYMENT)
            chatNpc(happy, FarmingMessages.GARDENER_PAID)
        }
    }

    private suspend fun ProtectedAccess.removeTree(npc: Npc, def: FarmingPatchDef, cropId: String) {
        if (!manager.state(player, def).checkHealthClaimed) {
            startDialogue(npc) { chatNpc(neutral, FarmingMessages.GARDENER_CHECK_HEALTH) }
            return
        }
        startDialogue(npc) {
            chatNpc(quiz, FarmingMessages.GARDENER_REMOVE_OFFER)
            if (!choice2("Yes, please.", true, "No, thanks.", false)) return@startDialogue
            val current = manager.state(player, def)
            val crop = manager.crop(cropId) ?: return@startDialogue
            if (current.cropId != cropId || current.dead || current.diseased ||
                current.stage < crop.grownStage || !current.checkHealthClaimed
            ) {
                chatNpc(neutral, FarmingMessages.GARDENER_CANNOT_REMOVE)
                return@startDialogue
            }
            if (!access.payTreeRemovalFee()) {
                chatNpc(neutral, FarmingMessages.GARDENER_REMOVE_NO_COINS)
                return@startDialogue
            }
            manager.clear(player, def)
            access.mes(FarmingMessages.GARDENER_REMOVED_TREE)
        }
    }

    private fun ProtectedAccess.payTreeRemovalFee(): Boolean = player.invTransaction(inv) {
        val inventory = select(inv)
        delete {
            from = inventory
            obj = "obj.coins".asRSCM(RSCMType.OBJ)
            strictCount = TreeRemovalCoins
        }
    }.success

    private fun ProtectedAccess.payGardenerFee(payment: ProtectionPayment): Boolean {
        val unnoted = payment.obj.asRSCM(RSCMType.OBJ)
        val noted = cert(payment.obj).id
        val unnotedCount = minOf(invTotal(inv, payment.obj), payment.count)
        return player.invTransaction(inv) {
            takeGardenerPayment(select(inv), unnoted, noted, unnotedCount, payment.count)
        }.success
    }

    private companion object {
        const val TreeRemovalCoins = 200
    }
}

private fun itemDisplayName(obj: String): String =
    ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name ?: obj
