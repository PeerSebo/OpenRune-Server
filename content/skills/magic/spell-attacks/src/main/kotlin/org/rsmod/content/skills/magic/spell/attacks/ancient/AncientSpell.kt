package org.rsmod.content.skills.magic.spell.attacks.ancient

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column

internal enum class Element { Smoke, Shadow, Blood, Ice }

internal enum class Tier {
    Rush, Burst, Blitz, Barrage;

    val multiTarget: Boolean get() = this == Burst || this == Barrage
}

internal sealed class Travel {
    data object None : Travel()
    data class FromCaster(val spotanim: String, val endHeight: Int) : Travel()
    data class FromTarget(val spotanim: String, val endHeight: Int) : Travel()
}

internal enum class AncientSpell {
    SmokeRush, ShadowRush, BloodRush, IceRush,
    SmokeBurst, ShadowBurst, BloodBurst, IceBurst,
    SmokeBlitz, ShadowBlitz, BloodBlitz, IceBlitz,
    SmokeBarrage, ShadowBarrage, BloodBarrage, IceBarrage;

    private val row by lazy { DbHelper.row("dbrow.ancient_spell_${name.lowercase()}") }
    val obj: String by lazy {
        RSCM.getReverseMapping(
            RSCMType.OBJ,
            row.column("dbcol.ancient_spells:spell", ObjIdCodec),
        )
    }
    val element: Element by lazy { Element.valueOf(row.string("element")) }
    val tier: Tier by lazy { Tier.valueOf(row.string("tier")) }
    val maxHit: Int by lazy { row.int("max_hit") }
    val travel: Travel by lazy {
        when (val kind = row.string("travel_kind")) {
            "none" -> Travel.None
            "caster" -> Travel.FromCaster(row.string("travel_spot"), row.int("travel_height"))
            "target" -> Travel.FromTarget(row.string("travel_spot"), row.int("travel_height"))
            else -> error("Invalid ancient spell travel kind: $name $kind")
        }
    }
    val impact: String by lazy { row.string("impact") }
    val impactHeight: Int by lazy { row.int("impact_height") }
    val hitSound: String by lazy { row.string("hit_sound") }
    val launch: String? by lazy { row.string("launch").takeIf { it.isNotEmpty() } }
    val effectStrength: Int by lazy { row.int("effect_strength") }
    val castSound: String by lazy { row.string("cast_sound") }
    val castAnim: String by lazy { row.string("cast_anim") }

    private fun DbHelper.string(name: String): String =
        column("dbcol.ancient_spells:$name", StringCodec)

    private fun DbHelper.int(name: String): Int =
        column("dbcol.ancient_spells:$name", IntCodec)
}
