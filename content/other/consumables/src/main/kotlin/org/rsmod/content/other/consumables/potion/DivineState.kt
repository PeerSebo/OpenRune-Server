package org.rsmod.content.other.consumables.potion

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.game.entity.Player

internal enum class DivineEffectState(val bit: Int, val key: String, val hudVarbits: List<String>) {
    ATTACK(1, "divine_super_attack_boost", listOf("varbit.divineattack_potion_time")),
    STRENGTH(2, "divine_super_strength_boost", listOf("varbit.divinestrength_potion_time")),
    DEFENCE(4, "divine_super_defence_boost", listOf("varbit.divinedefence_potion_time")),
    RANGED(8, "divine_ranging_boost", listOf("varbit.divinerange_potion_time")),
    MAGIC(16, "divine_magic_boost", listOf("varbit.divinemagic_potion_time")),
    COMBAT(32,
        "divine_super_combat_boost",
        listOf("varbit.divineattack_potion_time", "varbit.divinestrength_potion_time",
            "varbit.divinedefence_potion_time", "varbit.divinecombat_potion_time"),
    ),
    BASTION(64,
        "divine_bastion_boost",
        listOf("varbit.divinerange_potion_time", "varbit.divinedefence_potion_time", "varbit.divinebastion_potion_time"),
    ),
    BATTLEMAGE(128,
        "divine_battlemage_boost",
        listOf("varbit.divinemagic_potion_time", "varbit.divinedefence_potion_time", "varbit.divinebattlemage_potion_time"),
    );

    val expiryVarp = "varp.consumables_${key.removeSuffix("_boost")}_expires_at"
    val remainingVarp = "varp.consumables_${key.removeSuffix("_boost")}_remaining"
    val effect: PotionEffectRow get() = PotionEffectRow.getRow("dbrow.effect_$key")
    val potionName: String get() = key.removeSuffix("_boost").replace('_', ' ') + " potion"

    fun expiresAt(player: Player): Int = player.vars[expiryVarp]
    fun remaining(player: Player): Int = player.vars[remainingVarp]
    fun warned(player: Player): Boolean = player.vars[DivineState.WARNED] and bit != 0

    fun setExpiresAt(player: Player, cycle: Int) = VarPlayerIntMapSetter.set(player, expiryVarp, cycle)
    fun setRemaining(player: Player, ticks: Int) = VarPlayerIntMapSetter.set(player, remainingVarp, ticks.coerceAtLeast(0))
    fun setWarned(player: Player, warned: Boolean) {
        val flags = player.vars[DivineState.WARNED]
        VarPlayerIntMapSetter.set(player, DivineState.WARNED, if (warned) flags or bit else flags and bit.inv())
    }
}

internal object DivineState {
    const val WARNED = "varp.consumables_divine_warned"
    const val MIGRATED = "varp.consumables_divine_migrated"
    val savedDivineTicks = AttributeKey<MutableMap<String, Int>>("consumables.potion.divine_remaining")

    fun active(player: Player): Map<Int, Int> = DivineEffectState.entries
        .filter { it.expiresAt(player) != 0 }
        .associate { it.effect.rowId to it.expiresAt(player) }

    fun saved(player: Player): Map<String, Int> = DivineEffectState.entries
        .filter { it.remaining(player) > 0 }
        .associate { it.key to it.remaining(player) }

    fun migrate(player: Player) {
        val legacy = player.attr[savedDivineTicks].orEmpty()
        var migrated = player.vars[MIGRATED]
        DivineEffectState.entries.forEach { state ->
            if (migrated and state.bit == 0 && state.remainingVarp !in player.vars) {
                legacy[state.key]?.let { state.setRemaining(player, it) }
            }
            migrated = migrated or state.bit
        }
        VarPlayerIntMapSetter.set(player, MIGRATED, migrated)
        // Retain unknown historical keys until their content has a migration of its own.
        val unknown = legacy.filterKeys { key -> DivineEffectState.entries.none { it.key == key } }
        if (unknown.isEmpty()) {
            if (player.attr.has(savedDivineTicks)) player.attr.remove(savedDivineTicks)
        } else if (unknown != legacy) {
            player.attr[savedDivineTicks] = unknown.toMutableMap()
        }
    }

    fun clear(player: Player) {
        DivineEffectState.entries.forEach {
            it.setExpiresAt(player, 0)
            it.setRemaining(player, 0)
        }
        VarPlayerIntMapSetter.set(player, WARNED, 0)
        VarPlayerIntMapSetter.set(player, MIGRATED, DivineEffectState.entries.fold(0) { mask, state -> mask or state.bit })
        if (player.attr.has(savedDivineTicks)) player.attr.remove(savedDivineTicks)
    }
}
