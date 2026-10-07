package org.rsmod.api.player

import dev.openrune.types.InvScope
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.enabledPrayers
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.prayerDrainCounter
import org.rsmod.api.player.vars.usingQuickPrayers
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory

public fun Player.forceDisconnect() {
    forceDisconnect = true
}

public fun Player.clearInteractionRoute() {
    clearInteraction()
    abortRoute()
    clearMapFlag()
}

public fun Player.queueDeath() {
    strongQueue("queue.death", 1)
}

public fun Player.combatClearQueue() {
    clearQueue("queue.com_retaliate_npc")
    clearQueue("queue.com_retaliate_player")
    clearQueue("queue.hit")
    clearQueue("queue.impact_hit")
}

public fun Player.hasProtectItemPrayer(): Boolean = vars["varbit.prayer_protectitem"] == 1

private var Player.overheadLockExpiration by intVarp("varp.overhead_lock_expiration")
private var Player.protectFromMelee by intVarBit("varbit.prayer_protectfrommelee")
private var Player.protectFromMissiles by intVarBit("varbit.prayer_protectfrommissiles")
private var Player.protectFromMagic by intVarBit("varbit.prayer_protectfrommagic")
private var Player.quickPrayerActive by intVarBit("varbit.quickprayer_active")

public val overheadProtectionPrayerVarbits: List<String> = listOf(
    "varbit.prayer_protectfrommelee",
    "varbit.prayer_protectfrommissiles",
    "varbit.prayer_protectfrommagic",
)

public fun Player.lockOverheads(cycles: Int) {
    require(cycles > 0) { "`cycles` must be greater than 0. (cycles=$cycles)" }
    disableOverheadPrayers()
    overheadLockExpiration = maxOf(overheadLockExpiration, currentMapClock + cycles)
}

public fun Player.disableOverheadPrayers() {
    val hadProtection = protectFromMelee != 0 || protectFromMissiles != 0 || protectFromMagic != 0
    protectFromMelee = 0
    protectFromMissiles = 0
    protectFromMagic = 0
    if (hadProtection) {
        if (constants.isOverhead(appearance.overheadIcon)) appearance.overheadIcon = null
        if (enabledPrayers == 0) {
            quickPrayerActive = 0
            prayerDrainCounter = 0
            clearSoftTimer("timer.prayer_drain")
        }
    }
}

public val Player.overheadsLocked: Boolean
    get() = currentMapClock < overheadLockExpiration

public fun Player.disablePrayers() {
    enabledPrayers = 0
    prayerDrainCounter = 0

    if (usingQuickPrayers) {
        usingQuickPrayers = false
    }

    if (constants.isOverhead(appearance.overheadIcon)) {
        appearance.overheadIcon = null
    }

    clearQueue("queue.preserve_activation")
    clearSoftTimer("timer.prayer_drain")
    clearSoftTimer("timer.rapidrestore_regen")
    // Rapid Heal and Preserve speed up these timers while they are on; put them back to their
    // normal rates so running out of prayer points does not leave the boosts running.
    softTimer("timer.health_regen", constants.health_regen_interval)
    softTimer("timer.stat_boost_restore", constants.stat_boost_restore_interval)
}

public fun Player.deathResetTimers() {
    softTimer("timer.stat_regen", constants.stat_regen_interval)
    softTimer("timer.stat_boost_restore", constants.stat_boost_restore_interval)
    softTimer("timer.health_regen", constants.health_regen_interval)

    // Note: RL regeneration meter plugin does not reset on death. This can lead to de-sync, but
    // it is (currently) the official behavior.
    softTimer("timer.spec_regen", constants.spec_regen_interval)
}

public fun Player.isValidTarget(): Boolean {
    val isLoggingOut = pendingLogout || loggingOut
    if (isLoggingOut) {
        return false
    }
    return isSlotAssigned && isVisible && hitpoints > 0
}

public fun Player.isOutOfCombat(): Boolean = !isInCombat()

public fun Player.isInCombat(): Boolean = isInPvpCombat() || isInPvnCombat()

public fun Player.isInPvpCombat(): Boolean {
    return vars["varp.lastcombat_pvp"] + constants.combat_activecombat_delay >= currentMapClock
}

public fun Player.isInPvnCombat(): Boolean {
    return vars["varp.lastcombat"] + constants.combat_activecombat_delay >= currentMapClock
}

public fun Player.subjectPronoun(): String {
    appearance.pronoun = vars["varbit.settings_transmit_pronouns"]
    return appearance.subjectPronoun()
}

public fun Player.mapMultiway(checker: AreaChecker): Boolean {
    return checker.inArea("area.multiway", coords)
}

public fun Player.chatMesColorTag(opaque: String, transparent: String): String {
    return "<col=${chatMesColor(opaque, transparent)}>"
}

private fun Player.chatMesColor(opaque: String, transparent: String): String {
    require(opaque.length == 6 && transparent.length == 6) {
        "Color tags must be exactly 6 hexadecimal characters without the # symbol (e.g., 'FF0000')."
    }
    val transparentChatbox = ui.frameResizable && vars["varbit.chatbox_transparency"] == 1
    return if (transparentChatbox) {
        transparent
    } else {
        opaque
    }
}

public fun Player.startInvTransmit(inv: Inventory) {
    check(inv.type.scope != InvScope.Shared || !invMap.contains(inv.internalName)) {
        "`inv` should have previously been removed from cached inv map: $inv"
    }
    // Reordering changes later inventory transmission order, including inventories first
    // registered at login.
    transmittedInvs.remove(inv.type.id)
    transmittedInvAddQueue.add(inv.type.id)
    invMap[inv.internalName] = inv
}

public fun Player.stopInvTransmit(inv: Inventory) {
    if (inv.type.scope == InvScope.Shared) {
        val removed = invMap.remove(inv.internalName)
        check(removed == inv) { "Mismatch with cached value: (cached=$removed, inv=$inv)" }
    }
    transmittedInvs.remove(inv.type.id)
    transmittedInvAddQueue.remove(inv.type.id)
    UpdateInventory.updateInvStopTransmit(this, inv)
}

@OptIn(InternalApi::class)
public fun Player.hasAtLeast99s(requiredCount: Int): Boolean {
    return NamedEnums.skill_names.count { enum ->
        return statMap.getBaseLevel("stat.${enum.value?.lowercase()}") >= 99
    } >= requiredCount
}
