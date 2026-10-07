package org.rsmod.content.areas.wilderness

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.wildernessSkulled by boolVarBit("varbit.wilderness_is_skulled")
internal var Player.wildernessSkullEquipLocked by boolVarBit("varbit.wilderness_skull_equip_locked")
internal var Player.wildernessSkullIcon by intVarp("varp.wilderness_skull_icon")
internal var Player.wildernessOptOut by boolVarBit("varbit.wilderness_opt_out")
internal var Player.wildernessCanPvp by boolVarBit("varbit.wilderness_can_pvp")
internal var Player.wildernessDueling by boolVarBit("varbit.wilderness_dueling")
internal var Player.wildernessLootKeyCount by intVarp("varp.wilderness_loot_key_count")
private var Player.wildernessSkullSourceCode by intVarp("varp.wilderness_skull_source")
private var Player.wildernessLastSpecTickEncoded by intVarp("varp.wilderness_last_pvp_spec_tick")
private var Player.wildernessVarsMigrated by boolVarBit("varbit.wilderness_vars_migrated")

internal var Player.wildernessSkullSource: SkullSource?
    get() = SkullSource.entries.firstOrNull { it.code == wildernessSkullSourceCode }
    set(value) { wildernessSkullSourceCode = value?.code ?: 0 }

internal var Player.wildernessLastSpecTick: Int?
    get() = wildernessLastSpecTickEncoded.takeIf { it != 0 }?.minus(1)
    set(value) { wildernessLastSpecTickEncoded = value?.plus(1) ?: 0 }

// Retain legacy keys only to read pre-var character saves.
private val LegacySkulled = AttributeKey<Boolean>(persistenceKey = "is_skulled")
private val LegacySkullIcon = AttributeKey<Int>(persistenceKey = "skull_icon_type")
private val LegacyEquipLocked = AttributeKey<Boolean>(persistenceKey = "skull_equip_locked")
private val LegacyOptOut = AttributeKey<Boolean>(persistenceKey = "wilderness_opt_out")

internal fun Player.migrateWildernessVars() {
    if (!wildernessVarsMigrated) {
        attr[LegacySkulled]?.let { wildernessSkulled = it }
        attr[LegacySkullIcon]?.let { wildernessSkullIcon = it }
        attr[LegacyEquipLocked]?.let { wildernessSkullEquipLocked = it }
        attr[LegacyOptOut]?.let { wildernessOptOut = it }
        wildernessVarsMigrated = true
    }
    if (attr.has(LegacySkulled)) attr.remove(LegacySkulled)
    if (attr.has(LegacySkullIcon)) attr.remove(LegacySkullIcon)
    if (attr.has(LegacyEquipLocked)) attr.remove(LegacyEquipLocked)
    if (attr.has(LegacyOptOut)) attr.remove(LegacyOptOut)
}
