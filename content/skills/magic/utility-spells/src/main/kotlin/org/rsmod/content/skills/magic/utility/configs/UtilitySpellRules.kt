package org.rsmod.content.skills.magic.utility.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbColumnCodec.SpotCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.SeqIdCodec
import dev.openrune.types.dbcol.SynthIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.columnOptional
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.objOptional
import dev.openrune.types.dbcol.string
import org.rsmod.api.table.cooking.CookingFoodsRow
import org.rsmod.api.table.crafting.CraftingTanningRow
import org.rsmod.api.table.herblore.HerbloreCleaningRow
import org.rsmod.api.table.prayer.SkillPrayerRow

internal enum class UtilityMethod {
    Humidify, SpinFlax, StringJewellery, BakePie, TanLeather, PlankMake, Degrime,
    SinisterOffering, DemonicOffering, CureMe, VileVigour, ShadowVeil, MarkOfDarkness, Charge, ChargeOrb,
}

internal data class UtilitySpellRule(
    val spell: ItemServerType,
    val method: UtilityMethod,
    val batch: Int,
    val cooldown: Int,
    val sequence: String,
    val spotanim: String,
    val sound: String?,
    val repeatDelay: Int,
    val missingMessage: String,
    val obelisk: String? = null,
    val orb: ItemServerType? = null,
)

internal data class UtilityConversion(
    val input: ItemServerType,
    val output: ItemServerType?,
    val skill: String? = null,
    val xp: Double = 0.0,
    val level: Int = 1,
    val coins: Int = 0,
    val prayerRestore: Int = 0,
    val seedling: Boolean = false,
)

internal object UtilitySpellRules {
    val all by lazy {
        DbHelper.table("dbtable.utility_spell_rules").map { row ->
            UtilitySpellRule(
                row.obj("dbcol.utility_spell_rules:spell"),
                UtilityMethod.valueOf(row.string("dbcol.utility_spell_rules:method")),
                row.int("dbcol.utility_spell_rules:batch"),
                row.int("dbcol.utility_spell_rules:cooldown"),
                RSCM.getReverseMapping(RSCMType.SEQ, row.column("dbcol.utility_spell_rules:sequence", SeqIdCodec)),
                RSCM.getReverseMapping(RSCMType.SPOTANIM, row.column("dbcol.utility_spell_rules:spotanim", SpotCodec).id),
                row.columnOptional("dbcol.utility_spell_rules:sound", SynthIdCodec)?.let {
                    RSCM.getReverseMapping(RSCMType.SYNTH, it)
                },
                row.int("dbcol.utility_spell_rules:repeat_delay"),
                row.string("dbcol.utility_spell_rules:missing_message"),
                row.columnOptional("dbcol.utility_spell_rules:obelisk", LocIdCodec)?.let {
                    RSCM.getReverseMapping(RSCMType.LOC, it)
                },
                row.objOptional("dbcol.utility_spell_rules:orb"),
            )
        }.also { rules ->
            require(rules.distinctBy { it.spell.id }.size == rules.size)
            require(rules.all { it.batch in 0..28 && it.cooldown > 0 && it.repeatDelay >= 0 })
        }
    }

    private val conversions by lazy {
        DbHelper.table("dbtable.utility_spell_conversions").groupBy {
            UtilityMethod.valueOf(it.string("dbcol.utility_spell_conversions:method"))
        }.mapValues { (_, rows) -> rows.map {
            UtilityConversion(
                it.obj("dbcol.utility_spell_conversions:input"),
                it.obj("dbcol.utility_spell_conversions:output"),
                "stat.crafting", it.int("dbcol.utility_spell_conversions:crafting_xp_tenths") / 10.0,
            )
        } }
    }

    private val normalRecipes by lazy {
        UtilityMethod.entries.associateWith { loadConversions(it, false) }
    }
    private val hardTanningRecipes by lazy { loadConversions(UtilityMethod.TanLeather, true) }
    private val offeringBonuses by lazy {
        DbHelper.table("dbtable.utility_offering_bonuses").associate {
            it.obj("dbcol.utility_offering_bonuses:item").id to
                (it.int("dbcol.utility_offering_bonuses:prayer_level") to
                    it.int("dbcol.utility_offering_bonuses:prayer_restore"))
        }
    }

    fun conversions(method: UtilityMethod, hardLeather: Boolean): List<UtilityConversion> =
        if (method == UtilityMethod.TanLeather && hardLeather) hardTanningRecipes
        else normalRecipes.getValue(method)

    private fun loadConversions(method: UtilityMethod, hardLeather: Boolean): List<UtilityConversion> = when (method) {
        UtilityMethod.BakePie -> CookingFoodsRow.all().filter { it.category == "Pie" }.map {
            UtilityConversion(it.input, it.output, "stat.cooking", it.xpTenths / 10.0,
                it.statReq.single { req -> req.t0.internalName == "stat.cooking" }.t1)
        }
        UtilityMethod.TanLeather -> CraftingTanningRow.all().filter {
            if (it.input.internalName == "obj.cow_hide") {
                (it.output.internalName == "obj.hard_leather") == hardLeather
            } else true
        }.map { UtilityConversion(it.input, it.output) }
        UtilityMethod.Degrime -> HerbloreCleaningRow.all().map {
            UtilityConversion(it.input, it.output, "stat.herblore", it.xpTenths / 20.0,
                it.statReq.single { req -> req.t0.internalName == "stat.herblore" }.t1)
        }
        UtilityMethod.PlankMake -> DbHelper.table("dbtable.sawmill_planks").map {
            UtilityConversion(it.obj("dbcol.sawmill_planks:logs"),
                it.obj("dbcol.sawmill_planks:plank"), coins = it.int("dbcol.sawmill_planks:coins") * 7 / 10)
        }
        UtilityMethod.SinisterOffering, UtilityMethod.DemonicOffering -> SkillPrayerRow.all().filter {
            it.ashes == (method == UtilityMethod.DemonicOffering) &&
                it.item.internalName !in setOf("obj.bone_long", "obj.bone_curved")
        }.map {
            UtilityConversion(it.item, null, "stat.prayer", it.xpTenths * 3 / 10.0,
                level = offeringBonuses[it.item.id]?.first ?: 1,
                prayerRestore = offeringBonuses[it.item.id]?.second ?: 1)
        }
        UtilityMethod.Humidify -> conversions[method].orEmpty() +
            DbHelper.table("dbtable.farming_saplings").map {
                UtilityConversion(it.obj("dbcol.farming_saplings:seedling"),
                    it.obj("dbcol.farming_saplings:watered"), seedling = true)
            }
        else -> conversions[method].orEmpty()
    }
}
