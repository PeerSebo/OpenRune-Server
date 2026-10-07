package org.rsmod.api.player.interact

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import dev.openrune.types.varp.baseVar
import dev.openrune.types.varp.bits
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.ApEvent
import org.rsmod.api.player.events.interact.NpcContentEvents
import org.rsmod.api.player.events.interact.NpcDefaultEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.NpcUnimplementedEvents
import org.rsmod.api.player.events.interact.OpEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.game.vars.VarPlayerIntMap
import org.rsmod.utils.bits.getBits

public class NpcInteractions @Inject constructor(private val eventBus: EventBus) {
    public fun interact(player: Player, npc: Npc, op: InteractionOp) {
        val opTrigger = hasOpTrigger(player, npc, op)
        val apTrigger = hasApTrigger(player, npc, op)
        val interaction =
            InteractionNpcOp(
                target = npc,
                op = op,
                hasOpTrigger = opTrigger,
                hasApTrigger = apTrigger,
            )
        val routeRequest = RouteRequestPathingEntity(npc.avatar)
        player.faceNpc(npc)
        player.interaction = interaction
        player.routeRequest = routeRequest
    }

    public fun opTrigger(
        player: Player,
        npc: Npc,
        op: InteractionOp,
        type: NpcServerType = npc.visType,
    ): OpEvent? {
        val types = resolvedTypes(type, player.vars)
        explicitOp(npc, op, types)?.let { return it }
        val approach = apTrigger(player, npc, op, type)
        if (approach is NpcEvents.Ap || approach is NpcContentEvents.Ap) return null

        for (resolved in types) {
            val unimplEvent = npc.toUnimplementedOp(op, resolved.id)
            if (eventBus.contains(unimplEvent::class.java, resolved.id)) {
                return unimplEvent
            }
        }

        val defaultEvent = npc.toDefaultOp(op)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasOpTrigger(player: Player, npc: Npc, op: InteractionOp): Boolean =
        opTrigger(player, npc, op) != null

    public fun apTrigger(
        player: Player,
        npc: Npc,
        op: InteractionOp,
        type: NpcServerType = npc.visType,
    ): ApEvent? {
        val types = resolvedTypes(type, player.vars)
        for (resolved in types) {
            val typeEvent = npc.toAp(op, resolved.id)
            if (eventBus.contains(typeEvent::class.java, resolved.id)) {
                return typeEvent
            }
            val contentEvent = npc.toContentAp(resolved.contentGroup, op)
            if (eventBus.contains(contentEvent::class.java, resolved.contentGroup)) {
                return contentEvent
            }
        }

        for (resolved in types) {
            val unimplEvent = npc.toUnimplementedAp(op, resolved.id)
            if (eventBus.contains(unimplEvent::class.java, resolved.id)) {
                if (explicitOp(npc, op, types) != null) break
                return unimplEvent
            }
        }

        val defaultEvent = npc.toDefaultAp(op)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasApTrigger(player: Player, npc: Npc, op: InteractionOp): Boolean =
        apTrigger(player, npc, op) != null

    private fun explicitOp(npc: Npc, op: InteractionOp, types: List<NpcServerType>): OpEvent? {
        for (resolved in types) {
            val typeEvent = npc.toOp(op, resolved.id)
            if (eventBus.contains(typeEvent::class.java, resolved.id)) return typeEvent
            val contentEvent = npc.toContentOp(resolved.contentGroup, op)
            if (eventBus.contains(contentEvent::class.java, resolved.contentGroup)) return contentEvent
        }
        return null
    }

    private fun resolvedTypes(type: NpcServerType, vars: VarPlayerIntMap): List<NpcServerType> {
        val types = mutableListOf<NpcServerType>()
        var current = type
        while (types.none { it.id == current.id }) {
            types += current
            current = multiNpc(current, vars) ?: break
        }
        return types.asReversed()
    }

    public fun multiNpc(type: NpcServerType, vars: VarPlayerIntMap): NpcServerType? {
        if (type.multiNpc.isEmpty() && type.multiDefault <= 0) {
            return null
        }
        val varValue = type.multiVarValue(vars) ?: 0
        val multiNpc =
            if (varValue in type.multiNpc.indices) {
                type.multiNpc[varValue].toInt() and 0xFFFF
            } else {
                type.multiDefault
            }
        return if (!ServerCacheManager.getNpcs().containsKey(multiNpc)) {
            null
        } else {
            ServerCacheManager.getNpc(multiNpc)
        }
    }

    private fun Npc.toOp(op: InteractionOp, typeId: Int): NpcEvents.Op =
        when (op) {
            InteractionOp.Op1 -> NpcEvents.Op1(this, typeId)
            InteractionOp.Op2 -> NpcEvents.Op2(this, typeId)
            InteractionOp.Op3 -> NpcEvents.Op3(this, typeId)
            InteractionOp.Op4 -> NpcEvents.Op4(this, typeId)
            InteractionOp.Op5 -> NpcEvents.Op5(this, typeId)
        }

    private fun Npc.toContentOp(contentGroup: Int, op: InteractionOp): NpcContentEvents.Op =
        when (op) {
            InteractionOp.Op1 -> NpcContentEvents.Op1(this, contentGroup)
            InteractionOp.Op2 -> NpcContentEvents.Op2(this, contentGroup)
            InteractionOp.Op3 -> NpcContentEvents.Op3(this, contentGroup)
            InteractionOp.Op4 -> NpcContentEvents.Op4(this, contentGroup)
            InteractionOp.Op5 -> NpcContentEvents.Op5(this, contentGroup)
        }

    private fun Npc.toUnimplementedOp(op: InteractionOp, typeId: Int): NpcUnimplementedEvents.Op =
        when (op) {
            InteractionOp.Op1 -> NpcUnimplementedEvents.Op1(this, typeId)
            InteractionOp.Op2 -> NpcUnimplementedEvents.Op2(this, typeId)
            InteractionOp.Op3 -> NpcUnimplementedEvents.Op3(this, typeId)
            InteractionOp.Op4 -> NpcUnimplementedEvents.Op4(this, typeId)
            InteractionOp.Op5 -> NpcUnimplementedEvents.Op5(this, typeId)
        }

    private fun Npc.toDefaultOp(op: InteractionOp): NpcDefaultEvents.Op =
        when (op) {
            InteractionOp.Op1 -> NpcDefaultEvents.Op1(this)
            InteractionOp.Op2 -> NpcDefaultEvents.Op2(this)
            InteractionOp.Op3 -> NpcDefaultEvents.Op3(this)
            InteractionOp.Op4 -> NpcDefaultEvents.Op4(this)
            InteractionOp.Op5 -> NpcDefaultEvents.Op5(this)
        }

    private fun Npc.toAp(op: InteractionOp, typeId: Int): NpcEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> NpcEvents.Ap1(this, typeId)
            InteractionOp.Op2 -> NpcEvents.Ap2(this, typeId)
            InteractionOp.Op3 -> NpcEvents.Ap3(this, typeId)
            InteractionOp.Op4 -> NpcEvents.Ap4(this, typeId)
            InteractionOp.Op5 -> NpcEvents.Ap5(this, typeId)
        }

    private fun Npc.toContentAp(contentGroup: Int, op: InteractionOp): NpcContentEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> NpcContentEvents.Ap1(this, contentGroup)
            InteractionOp.Op2 -> NpcContentEvents.Ap2(this, contentGroup)
            InteractionOp.Op3 -> NpcContentEvents.Ap3(this, contentGroup)
            InteractionOp.Op4 -> NpcContentEvents.Ap4(this, contentGroup)
            InteractionOp.Op5 -> NpcContentEvents.Ap5(this, contentGroup)
        }

    private fun Npc.toDefaultAp(op: InteractionOp): NpcDefaultEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> NpcDefaultEvents.Ap1(this)
            InteractionOp.Op2 -> NpcDefaultEvents.Ap2(this)
            InteractionOp.Op3 -> NpcDefaultEvents.Ap3(this)
            InteractionOp.Op4 -> NpcDefaultEvents.Ap4(this)
            InteractionOp.Op5 -> NpcDefaultEvents.Ap5(this)
        }

    private fun Npc.toUnimplementedAp(op: InteractionOp, typeId: Int): NpcUnimplementedEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> NpcUnimplementedEvents.Ap1(this, typeId)
            InteractionOp.Op2 -> NpcUnimplementedEvents.Ap2(this, typeId)
            InteractionOp.Op3 -> NpcUnimplementedEvents.Ap3(this, typeId)
            InteractionOp.Op4 -> NpcUnimplementedEvents.Ap4(this, typeId)
            InteractionOp.Op5 -> NpcUnimplementedEvents.Ap5(this, typeId)
        }

    private fun NpcServerType.multiVarValue(vars: VarPlayerIntMap): Int? {
        if (multiVarp > 0) {
            val varp = ServerCacheManager.getVarp(multiVarp) ?: return null
            return vars[varp]
        } else if (multiVarBit > 0) {
            val varBit = ServerCacheManager.getVarbit(multiVarBit) ?: return null
            val packed = vars[varBit.baseVar]
            return packed.getBits(varBit.bits)
        }
        return null
    }

    public fun hasOp(npc: Npc, vars: VarPlayerIntMap, op: InteractionOp): Boolean {
        val multiNpc = multiNpc(npc.visType, vars)
        if (multiNpc != null) {
            val multiNpcType =
                ServerCacheManager.getNpc(multiNpc.id)
                    ?: error("Unable to find multi npc: ${multiNpc}")
            return multiNpcType.hasOp(op.slot)
        }
        return npc.visType.hasOp(op.slot)
    }
}
