package org.rsmod.content.bosses.hespori

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.hesporiRakeProgress by intVarBit("varbit.hespori_rake_progress")
internal var Player.hesporiDefeated by boolVarBit("varbit.hespori_defeated")
internal var Player.hesporiInArena by boolVarBit("varbit.hespori_in_arena")
internal var Player.hesporiReclaimPaid by boolVarBit("varbit.hespori_reclaim_paid")
private var Player.hesporiPlanted by boolVarBit("varbit.hespori_planted")
private var Player.hesporiPlantingMinute by intVarp("varp.hespori_planted_minute")
private var Player.hesporiStateMigrated by boolVarBit("varbit.hespori_state_migrated")

internal var Player.hesporiPlantedMinute: Int?
    get() = hesporiPlantingMinute.takeIf { hesporiPlanted }
    set(value) {
        hesporiPlantingMinute = value ?: 0
        hesporiPlanted = value != null
    }

private val LegacyRake = AttributeKey<Int>("hespori_rake")
private val LegacyPlanted = AttributeKey<Int>("hespori_planted_minute")
private val LegacyDefeated = AttributeKey<Boolean>("hespori_defeated")
private val LegacyInArena = AttributeKey<Boolean>("hespori_in_arena")
private val LegacyReclaimPaid = AttributeKey<Boolean>("hespori_reclaim_paid")

internal fun Player.migrateHesporiState() {
    if (!hesporiStateMigrated) {
        attr[LegacyRake]?.let { hesporiRakeProgress = it.coerceIn(0, 3) }
        attr[LegacyPlanted]?.let { hesporiPlantedMinute = it }
        attr[LegacyDefeated]?.let { hesporiDefeated = it }
        attr[LegacyInArena]?.let { hesporiInArena = it }
        attr[LegacyReclaimPaid]?.let { hesporiReclaimPaid = it }
        hesporiStateMigrated = true
    }
    attr.remove(LegacyRake)
    attr.remove(LegacyPlanted)
    attr.remove(LegacyDefeated)
    attr.remove(LegacyInArena)
    attr.remove(LegacyReclaimPaid)
}
