package dev.openrune.tables.skills.runecrafting

import dev.openrune.definition.util.VarType
import dev.openrune.tables.production.productionTable

enum class Rune(
    val id: String,
    val essence: List<String>,
    val level: Int,
    /**
     * XP in tenths (e.g. 55 = 5.5xp). Nine of eighteen runes have a fractional wiki XP value
     * that a plain Int `xp` column would silently floor - runecrafting-gaps.md gap 1. Written to
     * the shared `xp_tenths` production column; the base production `xp` column is derived from it
     * (floored) for table consistency and must NOT be read by content.
     */
    val xpTenths: Int,
    val dbId: String,
    val extract: String,
) {
    AIR(
        id = "obj.airrune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 1,
        xpTenths = 50,
        dbId = "dbrow.runecrafting_rune_air",
        extract = "obj.scar_extract_warped",
    ),
    MIND(
        id = "obj.mindrune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 2,
        xpTenths = 55,
        dbId = "dbrow.runecrafting_rune_mind",
        extract = "obj.scar_extract_warped",
    ),
    WATER(
        id = "obj.waterrune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 5,
        xpTenths = 60,
        dbId = "dbrow.runecrafting_rune_water",
        extract = "obj.scar_extract_warped",
    ),
    EARTH(
        id = "obj.earthrune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 9,
        xpTenths = 65,
        dbId = "dbrow.runecrafting_rune_earth",
        extract = "obj.scar_extract_warped",
    ),
    FIRE(
        id = "obj.firerune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 14,
        xpTenths = 70,
        dbId = "dbrow.runecrafting_rune_fire",
        extract = "obj.scar_extract_warped",
    ),
    BODY(
        id = "obj.bodyrune",
        essence = listOf("obj.blankrune", "obj.blankrune_high"),
        level = 20,
        xpTenths = 75,
        dbId = "dbrow.runecrafting_rune_body",
        extract = "obj.scar_extract_warped",
    ),
    COSMIC(
        id = "obj.cosmicrune",
        essence = listOf("obj.blankrune_high"),
        level = 27,
        xpTenths = 80,
        dbId = "dbrow.runecrafting_rune_cosmic",
        extract = "obj.scar_extract_twisted",
    ),
    CHAOS(
        id = "obj.chaosrune",
        essence = listOf("obj.blankrune_high"),
        level = 35,
        xpTenths = 85,
        dbId = "dbrow.runecrafting_rune_chaos",
        extract = "obj.scar_extract_twisted",
    ),
    SUNFIRE(
        id = "obj.sunfirerune",
        essence = listOf("obj.blankrune_high"),
        level = 33,
        xpTenths = 90,
        dbId = "dbrow.runecrafting_rune_sunfire",
        extract = "obj.scar_extract_twisted",
    ),
    ASTRAL(
        id = "obj.astralrune",
        essence = listOf("obj.blankrune_high"),
        level = 40,
        xpTenths = 87,
        dbId = "dbrow.runecrafting_rune_astral",
        extract = "obj.scar_extract_mangled",
    ),
    NATURE(
        id = "obj.naturerune",
        essence = listOf("obj.blankrune_high"),
        level = 44,
        xpTenths = 90,
        dbId = "dbrow.runecrafting_rune_nature",
        extract = "obj.scar_extract_mangled",
    ),
    LAW(
        id = "obj.lawrune",
        essence = listOf("obj.blankrune_high"),
        level = 54,
        xpTenths = 95,
        dbId = "dbrow.runecrafting_rune_law",
        extract = "obj.scar_extract_mangled",
    ),
    DEATH(
        id = "obj.deathrune",
        essence = listOf("obj.blankrune_high"),
        level = 65,
        xpTenths = 100,
        dbId = "dbrow.runecrafting_rune_death",
        extract = "obj.scar_extract_mangled",
    ),
    BLOOD(
        id = "obj.bloodrune",
        essence = listOf("obj.blankrune_high"),
        level = 77,
        xpTenths = 105,
        dbId = "dbrow.runecrafting_rune_blood",
        extract = "obj.scar_extract_scarred",
    ),
    BLOOD_DARK(
        id = "obj.bloodrune",
        essence = listOf("obj.bigblankrune"),
        level = 77,
        xpTenths = 238,
        dbId = "dbrow.runecrafting_rune_blood_dark",
        extract = "obj.scar_extract_scarred",
    ),
    SOUL(
        id = "obj.soulrune",
        essence = listOf("obj.bigblankrune"),
        level = 90,
        xpTenths = 297,
        dbId = "dbrow.runecrafting_rune_soul",
        extract = "obj.scar_extract_scarred",
    ),
    AETHER(
        id = "obj.aetherrune",
        essence = listOf("obj.gotr_guardian_essence"),
        level = 90,
        xpTenths = 200,
        dbId = "dbrow.runecrafting_rune_aether",
        extract = "obj.scar_extract_scarred",
    ),
    WRATH(
        id = "obj.wrathrune",
        essence = listOf("obj.blankrune_high"),
        level = 95,
        xpTenths = 80,
        dbId = "dbrow.runecrafting_rune_wrath",
        extract = "obj.scar_extract_scarred",
    );

    companion object {
        val values = enumValues<Rune>()
    }
}

object RunecraftRune {

    const val COL_EXTRACT = 7

    // Column 8 is the shared `xp_tenths` column: the production builder allocates the first index
    // the extras below leave free, which is 8.

    fun runecraftRune() =
        productionTable(
            "dbtable.runecrafting_runes",
            serverOnly = true,
            defaultCategory = "Runecraft",
            extraColumns = { column("extract", COL_EXTRACT, VarType.OBJ) },
        ) {
            Rune.entries.forEach { rune ->
                row(rune.dbId) {
                    production {
                        input(rune.essence)
                        statReq("stat.runecrafting", rune.level)
                        xpTenths(rune.xpTenths)
                        output(rune.id)
                    }
                    columnRSCM(COL_EXTRACT, rune.extract)
                }
            }
        }
}
