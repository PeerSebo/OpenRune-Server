package org.rsmod.api.instances

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

public object InstanceAttributes {
    internal val KILL_BEST_TICKS: AttributeKey<MutableMap<String, Int>> =
        AttributeKey(temp = true)
}

public fun Player.currentInstanceId(): InstanceId? =
    if (instanceIdPresent) {
        InstanceId((instanceIdHigh.toLong() shl 32) or (instanceIdLow.toLong() and 0xffffffffL))
    } else {
        null
    }

public fun Player.assignInstance(id: InstanceId) {
    instanceIdLow = id.value.toInt()
    instanceIdHigh = (id.value ushr 32).toInt()
    instanceIdPresent = true
    VarPlayerIntMapSetter.set(this, "varbit.player_in_instance", 1)
}

public fun Player.clearInstance() {
    instanceIdPresent = false
    instanceIdLow = 0
    instanceIdHigh = 0
    VarPlayerIntMapSetter.set(this, "varbit.player_in_instance", 0)
}

internal fun Player.instanceKillBest(instanceKey: String): Int? =
    attr[InstanceAttributes.KILL_BEST_TICKS]?.get(instanceKey)

internal fun Player.recordInstanceKillBest(instanceKey: String, ticks: Int): Boolean {
    val bests = attr.getOrPut(InstanceAttributes.KILL_BEST_TICKS) { mutableMapOf() }
    val previous = bests[instanceKey]

    if (previous != null && ticks >= previous) {
        return false
    }

    bests[instanceKey] = ticks
    return true
}

public fun Player.inInstance(id: InstanceId): Boolean = currentInstanceId() == id

private var Player.instanceIdLow by intVarp("varp.player_instance_id_low")
private var Player.instanceIdHigh by intVarp("varp.player_instance_id_high")
private var Player.instanceIdPresent by boolVarp("varp.player_instance_id_present")
