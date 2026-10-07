package org.rsmod.content.other.transformrings

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TransformRingsScript @Inject constructor(private val unequipOp: WornUnequipOp) :
    PluginScript() {
    private var Player.transformRingId by nullableIntVarp("varp.transform_ring_active")
    private var Player.transformOrigin by nullableIntVarp("varp.transform_ring_origin")

    private val rings: Map<Int, TransformRing> =
        TransformRing.entries.associateBy { it.obj.asRSCM(RSCMType.OBJ) }

    override fun ScriptContext.startup() {
        onEvent<HeldEquipEvents.WearposChange> {
            if (wearpos == Wearpos.Ring) {
                onRingChange(player)
            }
        }
        onPlayerCoordsChanged { player.revertIfMoved() }
        onPlayerLogout { player.clearTransform() }
    }

    private fun onRingChange(player: Player) {
        val worn = player.worn[Wearpos.Ring.slot]
        val ring = worn?.let { rings[it.id] }
        if (ring == null) {
            player.clearTransform()
            return
        }
        player.transform(ring)
    }

    private fun Player.transform(ring: TransformRing) {
        val npcType = ring.npcType()
        transmog = npcType
        transformRingId = ring.ordinal
        transformOrigin = coords.packed
        mes(ring.message)
    }

    // The coordinate event fires even without movement; compare against the original transformation tile.
    private fun Player.revertIfMoved() {
        val ring = transformRingId?.let { TransformRing.entries.getOrNull(it) } ?: return
        if (transformOrigin == coords.packed) {
            return
        }
        clearTransform()
        if (ring.unequipOnRevert) {
            unequipRing(ring)
        }
    }

    private fun Player.clearTransform() {
        if (transformRingId == null) {
            return
        }
        transformRingId = null
        transformOrigin = null
        transmog = null
    }

    private fun Player.unequipRing(ring: TransformRing) {
        val worn = worn[Wearpos.Ring.slot] ?: return
        if (rings[worn.id] != ring) {
            return
        }
        val result = unequipOp.unequip(this, Wearpos.Ring.slot, this.worn, inv)
        if (result is WornUnequipResult.Fail.NotEnoughInvSpace) {
            mes("Your ring stays on as you have no free inventory space.")
        }
    }

    private enum class TransformRing(
        val obj: String,
        val npc: String,
        val message: String,
        val unequipOnRevert: Boolean,
    ) {
        Stone(
            obj = "obj.enchanted_onyx_ring",
            npc = "npc.tzhaar_morph_rock",
            message = "You turn into a rock. Moving will turn you back.",
            unequipOnRevert = true,
        ),
        Coins(
            obj = "obj.ring_of_coins",
            npc = "npc.coins_transmog",
            message = "You turn into a pile of coins. Moving will turn you back.",
            unequipOnRevert = false,
        ),
        Nature(
            obj = "obj.ring_of_nature",
            npc = "npc.bush_transmog",
            message = "You turn into a bush. Moving will turn you back.",
            unequipOnRevert = false,
        );

        fun npcType(): NpcServerType =
            ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $npc")
    }
}
