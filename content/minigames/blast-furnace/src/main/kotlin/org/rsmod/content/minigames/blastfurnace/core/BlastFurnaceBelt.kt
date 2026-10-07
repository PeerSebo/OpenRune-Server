package org.rsmod.content.minigames.blastfurnace.core

import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player
internal class BlastFurnaceBeltLoad(val entries: List<BeltEntry>) {
    internal class BeltEntry(val ore: String, val amount: Int)
}
private val BELT_IN_FLIGHT = AttributeKey<MutableMap<String, Int>>()

internal fun Player.beltInFlight(ore: String): Int =
    attr[BELT_IN_FLIGHT]?.get(ore) ?: 0

internal fun Player.addBeltInFlight(ore: String, delta: Int) {
    val map = attr[BELT_IN_FLIGHT] ?: mutableMapOf<String, Int>().also { attr[BELT_IN_FLIGHT] = it }
    val next = (map[ore] ?: 0) + delta
    if (next <= 0) {
        map.remove(ore)
    } else {
        map[ore] = next
    }
}
internal fun Player.takeBeltInFlight(): List<BlastFurnaceBeltLoad.BeltEntry> {
    val map = attr[BELT_IN_FLIGHT] ?: return emptyList()
    attr.remove(BELT_IN_FLIGHT)
    return map.entries.map { BlastFurnaceBeltLoad.BeltEntry(it.key, it.value) }
}
