package org.rsmod.content.skills.magic.spell.attacks.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun ancientSpellTable() = dbTable("dbtable.ancient_spells", serverOnly = true) {
    column("spell", 0, VarType.OBJ)
    column("element", 1, VarType.STRING)
    column("tier", 2, VarType.STRING)
    column("max_hit", 3, VarType.INT)
    column("travel_kind", 4, VarType.STRING)
    column("travel_spot", 5, VarType.STRING)
    column("travel_height", 6, VarType.INT)
    column("impact", 7, VarType.STRING)
    column("impact_height", 8, VarType.INT)
    column("hit_sound", 9, VarType.STRING)
    column("launch", 10, VarType.STRING)
    column("effect_strength", 11, VarType.INT)
    column("cast_sound", 12, VarType.STRING)
    column("cast_anim", 13, VarType.STRING)
    for (spell in AncientSpell.entries) {
        row("dbrow.ancient_spell_${spell.name.lowercase()}") {
            columnRSCM(0, spell.obj)
            column(1, spell.element.name)
            column(2, spell.tier.name)
            column(3, spell.maxHit)
            val kind: String
            val spot: String
            val height: Int
            when (val travel = spell.travel) {
                Travel.None -> {
                    kind = "none"
                    spot = ""
                    height = 0
                }
                is Travel.FromCaster -> {
                    kind = "caster"
                    spot = travel.spotanim
                    height = travel.endHeight
                }
                is Travel.FromTarget -> {
                    kind = "target"
                    spot = travel.spotanim
                    height = travel.endHeight
                }
            }
            column(4, kind)
            column(5, spot)
            column(6, height)
            column(7, spell.impact)
            column(8, spell.impactHeight)
            column(9, spell.hitSound)
            column(10, spell.launch ?: "")
            column(11, spell.effectStrength)
            column(12, spell.castSound)
            column(13, spell.tier.anim)
        }
    }
}
