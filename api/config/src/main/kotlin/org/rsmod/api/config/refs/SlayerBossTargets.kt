package org.rsmod.api.config.refs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType

/** Native parent task 98 is disambiguated by varbit.slayer_target_bossid. */
public object SlayerBossTargets {
    public const val TaskId: Int = 98
    public val supportedBossIds: Set<Int> = setOf(18, 19, 25)
    private val symbols = mapOf(
        18 to listOf("npc.slayer_kraken_boss", "npc.slayer_kraken_boss_whirlpool"),
        19 to listOf("npc.smoke_devil_boss"),
        25 to listOf("npc.sarachnis"),
    )
    public fun matches(type: NpcServerType, bossId: Int): Boolean =
        symbols[bossId]?.any { type.id == it.asRSCM() } == true

    /** Skotizo is listed in both native demon categories; a single NPC param cannot encode both. */
    public fun additionalCategory(type: NpcServerType, taskId: Int): Boolean =
        type.id == "npc.cata_boss".asRSCM() && taskId in setOf(29, 30)
}
