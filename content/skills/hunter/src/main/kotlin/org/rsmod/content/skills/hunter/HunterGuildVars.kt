package org.rsmod.content.skills.hunter

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.skills.hunter.configs.HunterRumourPrey
import org.rsmod.content.skills.hunter.configs.RumourMaster
import org.rsmod.game.entity.Player

internal var Player.rumoursUnlocked by boolVarBit("varbit.hunter_rumours_unlocked")
internal var Player.rumoursBackToBack by boolVarBit("varbit.hunter_rumours_back_to_back")
internal var Player.rumoursResetLock by boolVarBit("varbit.hunter_rumours_reset_lock")
internal var Player.basicBlueprintClaimed by boolVarBit("varbit.hunter_basic_blueprint_claimed")
internal var Player.perfectedWhistleCrafted by boolVarBit("varbit.hunter_perfected_whistle_crafted")
internal var Player.infiniteWhistleUnlocked by boolVarBit("varbit.hunter_infinite_whistle_unlocked")
internal var Player.enhancedBlueprintSeen by boolVarBit("varbit.hunter_enhanced_blueprint_seen")
internal var Player.perfectedBlueprintSeen by boolVarBit("varbit.hunter_perfected_blueprint_seen")
internal var Player.rumoursCompleted by intVarp("varp.hunter_rumours_completed")
internal var Player.rumourActiveMaster by intVarp("varp.hunter_rumours_active")
internal var Player.rumourLastPrey by intVarp("varp.hunter_rumours_last")
internal var Player.rumourRerollMaster by intVarp("varp.hunter_rumours_reroll_after")
internal var Player.quetzalFeedCredit by intVarp("varp.hunter_quetzal_feed_credit")
private var Player.hunterGuildStateMigrated by boolVarBit("varbit.hunter_guild_state_migrated")

internal fun Player.rumourPrey(master: RumourMaster, saved: Boolean = false): HunterRumourPrey? =
    HunterRumourPrey.entries.firstOrNull { it.saveCode == vars[master.preyVar(saved)] }

internal fun Player.setRumourPrey(master: RumourMaster, prey: HunterRumourPrey?, saved: Boolean = false) {
    VarPlayerIntMapSetter.set(this, master.preyVar(saved), prey?.saveCode ?: 0)
}

internal fun Player.rumourCatchCount(master: RumourMaster, saved: Boolean = false): Int =
    vars[master.countVar(saved)]

internal fun Player.setRumourCatchCount(master: RumourMaster, count: Int, saved: Boolean = false) {
    VarPlayerIntMapSetter.set(this, master.countVar(saved), count.coerceAtLeast(0))
}

private fun RumourMaster.preyVar(saved: Boolean) =
    "varp.hunter_rumour_${saveName}_${if (saved) "original" else "prey"}"

private fun RumourMaster.countVar(saved: Boolean) =
    "varp.hunter_rumour_${saveName}_${if (saved) "original_count" else "count"}"

internal fun Player.migrateHunterGuildState() {
    val shouldImport = !hunterGuildStateMigrated
    val shouldImportFlags = shouldImport && "varp.hunter_guild_flags" !in vars
    for (name in listOf("hunter_rumours_unlocked", "hunter_rumours_back_to_back", "hunter_rumours_reset_lock",
        "hunter_basic_blueprint_claimed", "hunter_perfected_whistle_crafted", "hunter_infinite_whistle_unlocked",
        "hunter_enhanced_blueprint_seen", "hunter_perfected_blueprint_seen")) {
        val key = AttributeKey<Boolean>(name)
        if (shouldImportFlags && attr[key] == true) VarPlayerIntMapSetter.set(this, "varbit.$name", 1)
        if (attr.has(key)) attr.remove(key)
    }
    fun importCount(name: String, maximum: Int = Int.MAX_VALUE) {
        val key = AttributeKey<Int>(name)
        val value = attr[key]
        if (shouldImport && value != null && "varp.$name" !in vars) {
            VarPlayerIntMapSetter.set(this, "varp.$name", value.coerceIn(0, maximum))
        }
        if (attr.has(key)) attr.remove(key)
    }
    fun importCode(name: String, resolve: (String) -> Int?) {
        val key = AttributeKey<String>(name)
        val code = attr[key]?.let(resolve)
        if (shouldImport && code != null && "varp.$name" !in vars) {
            VarPlayerIntMapSetter.set(this, "varp.$name", code)
        }
        if (attr.has(key)) attr.remove(key)
    }
    importCount("hunter_rumours_completed")
    importCount("hunter_quetzal_feed_credit", 10_000)
    importCode("hunter_rumours_active") { value -> RumourMaster.entries.firstOrNull { it.name == value }?.saveCode }
    importCode("hunter_rumours_reroll_after") { value -> RumourMaster.entries.firstOrNull { it.name == value }?.saveCode }
    importCode("hunter_rumours_last") { value -> HunterRumourPrey.entries.firstOrNull { it.name == value }?.saveCode }
    for (master in RumourMaster.entries) {
        for (saved in listOf(false, true)) {
            importCode(master.preyVar(saved).removePrefix("varp.")) { value -> HunterRumourPrey.entries.firstOrNull { it.name == value }?.saveCode }
            importCount(master.countVar(saved).removePrefix("varp."))
        }
    }
    hunterGuildStateMigrated = true
}
