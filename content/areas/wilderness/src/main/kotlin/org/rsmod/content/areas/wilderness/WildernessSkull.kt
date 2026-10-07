package org.rsmod.content.areas.wilderness

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.constants
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

enum class SkullSource(val code: Int, val timerTicks: Int) {
    COMBAT(1, 3000),
    ABYSS(2, 1000),
    VOLUNTARY(3, 3000),
    POST_EQUIP(4, 2000),
    CAPE(5, 2000),
    FORINTHRY_SURGE(6, 3000),
    EQUIPMENT(7, Int.MAX_VALUE),
}

internal const val EMBLEM_EXTEND_TICKS = 2000

private var Player.forinthrySurgeExpiration by intVarp("varp.forinthry_surge_expiration")
private var Player.skullPreventionEnabled by boolVarBit("varbit.skull_prevent_enabled")

internal fun Player.isSkulled(): Boolean = wildernessSkulled

internal fun Player.isSkullPreventionEnabled(): Boolean = skullPreventionEnabled

internal fun Player.isSkullEquipLocked(): Boolean = wildernessSkullEquipLocked

internal fun Player.applySkull(
    source: SkullSource = SkullSource.COMBAT,
    skullType: Int = constants.skullicon_default,
) {
    wildernessSkulled = true
    wildernessSkullIcon = skullType
    wildernessSkullSource = source
    wildernessSkullEquipLocked = false
    if (source != SkullSource.FORINTHRY_SURGE) {
        forinthrySurgeExpiration = 0
    }
    if (source.timerTicks != Int.MAX_VALUE) {
        softTimer("timer.skull_timer", source.timerTicks)
    } else {
        clearSoftTimer("timer.skull_timer")
    }
    refreshSkullIcon()
}

internal fun Player.applyEquipmentSkull() {
    wildernessSkulled = true
    wildernessSkullIcon = constants.skullicon_default
    wildernessSkullSource = SkullSource.EQUIPMENT
    wildernessSkullEquipLocked = true
    forinthrySurgeExpiration = 0
    clearSoftTimer("timer.skull_timer")
    refreshSkullIcon()
}

internal fun Player.applyPostEquipSkull() {
    wildernessSkullEquipLocked = false
    applySkull(SkullSource.POST_EQUIP)
}

internal fun Player.applyVoluntarySkull() {
    if (isSkullEquipLocked()) {
        return
    }
    applySkull(SkullSource.VOLUNTARY)
}

internal fun Player.extendEmblemTraderSkull(): Boolean {
    if (isSkullEquipLocked() || !isSkulled()) {
        return false
    }
    if (skullTimerRemaining() >= EMBLEM_EXTEND_TICKS) {
        return false
    }
    wildernessSkullSource = SkullSource.VOLUNTARY
    softTimer("timer.skull_timer", EMBLEM_EXTEND_TICKS)
    return true
}

internal fun Player.applyForinthrySurge() {
    if (worn[Wearpos.Front.slot]?.isType("obj.wild_cave_amulet") != true) {
        return
    }
    wildernessSkulled = true
    wildernessSkullIcon = constants.skullicon_forinthry_surge
    wildernessSkullSource = SkullSource.FORINTHRY_SURGE
    forinthrySurgeExpiration = currentMapClock + SkullSource.FORINTHRY_SURGE.timerTicks
    softTimer("timer.skull_timer", SkullSource.FORINTHRY_SURGE.timerTicks)
    refreshSkullIcon()
}

internal fun Player.clearForinthrySurge() {
    if (wildernessSkullIcon != constants.skullicon_forinthry_surge) {
        return
    }
    forinthrySurgeExpiration = 0
    wildernessSkullIcon = constants.skullicon_default
    refreshSkullIcon()
}

internal fun Player.unlockEquipmentSkull() {
    wildernessSkullEquipLocked = false
    clearForinthrySurge()
}

internal fun Player.clearSkull(force: Boolean = false) {
    if (!force && isSkullEquipLocked()) {
        return
    }
    wildernessSkulled = false
    wildernessSkullSource = null
    wildernessSkullEquipLocked = false
    forinthrySurgeExpiration = 0
    clearSoftTimer("timer.skull_timer")
    skullIcon = null
}

internal fun Player.setSkull(skulled: Boolean) {
    if (skulled) applySkull() else clearSkull()
}

@OptIn(InternalApi::class)
internal fun Player.skullTimerRemaining(): Int {
    val timerId = "timer.skull_timer".asRSCM(RSCMType.TIMER).toShort()
    val packed = softTimerMap[timerId] ?: return 0
    val expiry = softTimerMap.extractExpiry(packed)
    return (expiry - currentMapClock).coerceAtLeast(0)
}

internal fun Player.expireForinthrySurgeIfNeeded() {
    if (forinthrySurgeExpiration <= 0) {
        return
    }
    if (currentMapClock >= forinthrySurgeExpiration) {
        clearForinthrySurge()
    }
}

internal fun Player.refreshSkullIcon() {
    expireForinthrySurgeIfNeeded()
    val keyCount = countWildyLootKeys()
    wildernessLootKeyCount = keyCount
    if (!wildernessSkulled) {
        skullIcon = null
        return
    }
    val baseIcon = wildernessSkullIcon
    val cappedKeyCount = keyCount.coerceIn(0, 5)
    skullIcon = when {
        cappedKeyCount > 0 && baseIcon == constants.skullicon_forinthry_surge -> constants.skullicon_forinthry_surge_keys_1 + (cappedKeyCount - 1)
        cappedKeyCount > 0 -> constants.skullicon_loot_key_1 + (cappedKeyCount - 1)
        else -> baseIcon
    }
}

internal fun Player.refreshSkullIconIfLootKeysChanged() {
    val keyCount = countWildyLootKeys()
    if (keyCount == wildernessLootKeyCount) {
        return
    }
    refreshSkullIcon()
}

private fun Player.countWildyLootKeys(): Int {
    var total = 0
    for (i in 0..4) {
        total += inv.physicalCount("obj.wildy_loot_key$i")
    }
    return total
}
