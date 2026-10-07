package org.rsmod.content.other.achievementdiaries

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.VarbitIdCodec
import dev.openrune.types.dbcol.VarpIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object DiaryData {
    val tasks: List<DiaryTask> by lazy {
        DbHelper.table("dbtable.achievement_diary_tasks").sortedBy { it.int("order") }.map { row ->
            DiaryTask(
                row.int("bit"),
                row.string("description"),
                row.string("action"),
                row.multiColumn("dbcol.achievement_diary_tasks:targets", StringCodec).toSet(),
                row.string("area"),
                row.multiColumn("dbcol.achievement_diary_tasks:bounds", IntCodec),
                row.int("required_stage"),
                row.int("set_stage"),
                row.int("tier"),
                RSCM.getReverseMapping(RSCMType.VARP,
                    row.column("dbcol.achievement_diary_tasks:progress_varp", VarpIdCodec)),
                row.int("minimum_count"),
                row.string("stage_varp"),
                row.string("unavailable"),
            )
        }.also { tasks ->
            require(tasks.filter { it.bit > 0 && it.difficulty == 1 }.map { it.bit } == (1..12).toList())
            require(tasks.filter { it.difficulty == 2 }.map { it.bit } == (13..24).toList())
            require(tasks.filter { it.difficulty == 3 && it.bit >= 0 }.map { it.bit } ==
                (25..31).toList() + (0..3).toList())
            val completedTasks = tasks.filter { it.bit >= 0 }
            require(completedTasks.distinctBy { it.progressVarp to it.bit }.size == completedTasks.size)
            tasks.forEach { task ->
                require(task.bounds.size == 5 && task.minimumCount > 0)
                require(task.bit in -1..31)
                require(task.setStage == 0 || (task.bit < 0 && task.stageVarp.isNotEmpty()))
                require(task.requiredStage == 0 || task.stageVarp.isNotEmpty())
                RSCM.getRSCM(task.progressVarp)
                if (task.stageVarp.isNotEmpty()) RSCM.getRSCM(task.stageVarp)
                require(task.targets.isNotEmpty())
                task.targets.forEach { RSCM.getRSCM(it) }
                if (task.area.isNotEmpty()) RSCM.getRSCM(task.area)
            }
        }
    }

    val tiers: List<DiaryTier> by lazy {
        DbHelper.table("dbtable.achievement_diary_tiers").map(::readTier).sortedBy { it.difficulty }
            .also { require(it.map { tier -> tier.difficulty } == listOf(1, 2, 3)) }
    }

    val easy: DiaryTier get() = tiers.first()
    val medium: DiaryTier get() = tiers[1]
    val hard: DiaryTier get() = tiers[2]
    fun forDifficulty(difficulty: Int): DiaryTier = tiers.single { it.difficulty == difficulty }
    fun tasks(tier: DiaryTier): List<DiaryTask> = tasks.filter { it.difficulty == tier.difficulty }

    private fun readTier(row: DbHelper): DiaryTier {
        val fields = row.multiColumn("dbcol.achievement_diary_tiers:fields", VarbitIdCodec)
            .map { RSCM.getReverseMapping(RSCMType.VARBIT, it) }
        val rewards = row.multiColumn("dbcol.achievement_diary_tiers:rewards", ObjIdCodec)
            .map { RSCM.getReverseMapping(RSCMType.OBJ, it) }
        require(fields.size == 4 && rewards.size == 2)
        return DiaryTier(
            row.column("dbcol.achievement_diary_tiers:region", IntCodec),
            row.column("dbcol.achievement_diary_tiers:name", StringCodec),
            RSCM.getReverseMapping(
                RSCMType.VARP,
                row.column("dbcol.achievement_diary_tiers:tasks", VarpIdCodec),
            ),
            fields[0], fields[1], fields[2], fields[3], rewards[0], rewards[1],
            row.column("dbcol.achievement_diary_tiers:lamp_xp", IntCodec),
            row.column("dbcol.achievement_diary_tiers:lamp_min_level", IntCodec),
            row.column("dbcol.achievement_diary_tiers:energy_uses", IntCodec),
            row.column("dbcol.achievement_diary_tiers:energy_percent", IntCodec),
            row.column("dbcol.achievement_diary_tiers:alchemy_uses", IntCodec),
            row.column("dbcol.achievement_diary_tiers:difficulty", IntCodec),
            row.column("dbcol.achievement_diary_tiers:label", StringCodec),
            row.column("dbcol.achievement_diary_tiers:cabbage_uses", IntCodec),
            row.column("dbcol.achievement_diary_tiers:cabbage_destination", CoordGridCodec),
        )
    }

    private fun DbHelper.int(name: String) = column("dbcol.achievement_diary_tasks:$name", IntCodec)
    private fun DbHelper.string(name: String) =
        column("dbcol.achievement_diary_tasks:$name", StringCodec)
}

internal data class DiaryTask(
    val bit: Int,
    val description: String,
    val action: String,
    val targets: Set<String>,
    val area: String,
    val bounds: List<Int>,
    val requiredStage: Int,
    val setStage: Int,
    val difficulty: Int,
    val progressVarp: String,
    val minimumCount: Int,
    val stageVarp: String,
    val unavailable: String,
) {
    fun matches(
        action: String,
        target: String,
        coords: CoordGrid,
        inArea: (String, CoordGrid) -> Boolean,
    ): Boolean =
        this.action == action && target in targets &&
            (area.isEmpty() || inArea(area, coords)) &&
            (bounds[4] == -1 || coords.level == bounds[4]) &&
            coords.x in bounds[0]..bounds[2] && coords.z in bounds[1]..bounds[3]
}

internal data class DiaryTier(
    val region: Int,
    val name: String,
    val taskVarp: String,
    val startedVar: String,
    val countVar: String,
    val completeVar: String,
    val rewardVar: String,
    val ring: String,
    val lamp: String,
    val lampXp: Int,
    val lampMinLevel: Int,
    val energyUses: Int,
    val energyPercent: Int,
    val alchemyUses: Int,
    val difficulty: Int,
    val label: String,
    val cabbageUses: Int,
    val cabbageDestination: CoordGrid,
)
