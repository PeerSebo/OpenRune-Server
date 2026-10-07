package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.events.interact.PlayerUEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.plugin.scripts.ScriptContext

internal class ShieldOfArravPartnerSwap(private val objRepo: ObjRepository) {

    fun ScriptContext.register() {
        register(SoaRefs.StoreKey, noun = "key")
        register(SoaRefs.Crossbow, noun = "crossbow")
        register(SoaRefs.CertificateLeft, noun = "certificate")
        register(SoaRefs.CertificateRight, noun = "certificate")
        register(SoaRefs.ShieldHalfLeft, noun = "shield half")
        register(SoaRefs.ShieldHalfRight, noun = "shield half")
    }

    private fun ScriptContext.register(obj: String, noun: String) {
        val type =
            checkNotNull(ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))) {
                "Shield of Arrav swap item does not resolve: $obj"
            }
        onOpPlayerU(type) { dropForPartner(it, obj, noun) }
    }

    private suspend fun ProtectedAccess.dropForPartner(
        op: PlayerUEvents.Op,
        obj: String,
        noun: String,
    ) {
        val target = op.target

        val confirm =
            choice2(
                "Yes",
                true,
                "No",
                false,
                title = "Drop your $noun for ${target.displayName} to take?",
            )
        if (!confirm) {
            return
        }

        if (invDel(inv, obj, 1, slot = op.invSlot).failure) {
            return
        }

        objRepo.add(
            obj,
            target.coords,
            duration = SwapDespawnTicks,
            receiver = target,
            reveal = SwapRevealTicks,
        )

        soundSynth(SoaRefs.PutDownSound)
        mes("${target.displayName} can now take the item from the ground.")

        target.mes("<col=ef1020>${player.displayName} has dropped something for you to take.</col>")
        target.soundSynth(SoaRefs.PutDownSound)
    }

    private companion object {
        private const val SwapDespawnTicks = 200

        private const val SwapRevealTicks = 100
    }
}
