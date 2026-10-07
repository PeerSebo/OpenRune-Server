package org.rsmod.content.skills.agility.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object AgilityCourseTables {
    const val COURSE_NAME = 0
    const val COURSE_LEVEL = 1
    const val COURSE_LAP_XP = 2
    const val COURSE_MARK_ODDS = 3
    const val COURSE_MARK_PENALTY = 4
    const val COURSE_PET_BASE = 5
    const val COURSE_MARK_SPAWNS = 6
    const val COURSE_QUEST = 7
    const val COURSE_WORN = 8
    const val COURSE_WORN_MESSAGE = 9
    const val COURSE_GRAPPLE = 10

    const val OBSTACLE_COURSE = 0
    const val OBSTACLE_ORDINAL = 1
    const val OBSTACLE_LOCS = 2
    const val OBSTACLE_LANDING = 3
    const val OBSTACLE_XP = 4
    const val OBSTACLE_ANIM = 5
    const val OBSTACLE_TICKS = 6
    const val OBSTACLE_SLIDE = 7
    const val OBSTACLE_REPEATS = 8
    const val OBSTACLE_FAIL = 9

    const val STAGE_OBSTACLE = 0
    const val STAGE_ORDINAL = 1
    const val STAGE_ANIM = 2
    const val STAGE_LANDING = 3
    const val STAGE_TICKS = 4
    const val STAGE_SLIDE = 5
    const val STAGE_MOVE_TICKS = 6
    const val STAGE_PER_TILE = 7
    const val STAGE_SOUND = 8
    const val STAGE_SOUND_LOOPS = 9
    const val STAGE_SOUND_DELAY = 10
    const val STAGE_ANIM_DELAY = 11

    const val LANDING_UNCHANGED = -1

    private fun courseRow(course: Course): String =
        "dbrow.agility_course_" +
            course.name
                .lowercase()
                .map { if (it.isLetterOrDigit()) it else '_' }
                .joinToString("")
                .replace(Regex("_+"), "_")
                .trim('_')

    private fun obstacleRow(course: Course, ordinal: Int): String =
        courseRow(course).replace("dbrow.agility_course_", "dbrow.agility_obstacle_") + "_$ordinal"

    private fun stageRow(course: Course, ordinal: Int, stage: Int): String =
        obstacleRow(course, ordinal).replace("dbrow.agility_obstacle_", "dbrow.agility_stage_") +
            "_$stage"

    private fun landingValues(landing: Landing?): Array<Any>? =
        landing?.let {
            arrayOf(
                it.x ?: LANDING_UNCHANGED,
                it.z ?: LANDING_UNCHANGED,
                it.level ?: LANDING_UNCHANGED,
                it.dx,
                it.dz,
                it.dLevel,
            )
        }

    fun courses() =
        dbTable("dbtable.agility_course", serverOnly = true) {
            column("name", COURSE_NAME, VarType.STRING)
            column("level", COURSE_LEVEL, VarType.INT)
            column("lap_xp", COURSE_LAP_XP, VarType.INT)
            // numerator, denominator: the fraction the wiki quotes, not a rounded decimal.
            column("mark_odds", COURSE_MARK_ODDS, VarType.INT, VarType.INT)
            column("mark_penalty", COURSE_MARK_PENALTY, VarType.BOOLEAN)
            column("pet_base", COURSE_PET_BASE, VarType.INT)
            column("mark_spawns", COURSE_MARK_SPAWNS, VarType.COORDGRID)
            column("quest", COURSE_QUEST, VarType.STRING)
            column("worn", COURSE_WORN, VarType.OBJ)
            column("worn_message", COURSE_WORN_MESSAGE, VarType.STRING)
            column("grapple", COURSE_GRAPPLE, VarType.BOOLEAN)

            for (course in AgilityCourseData.courses) {
                row(courseRow(course)) {
                    column(COURSE_NAME, course.name)
                    column(COURSE_LEVEL, course.level)
                    column(COURSE_LAP_XP, (course.lapXp * 10).toInt())
                    column(COURSE_MARK_ODDS, course.markNumerator, course.markDenominator)
                    column(COURSE_MARK_PENALTY, course.markPenalty)
                    column(COURSE_PET_BASE, course.petBase)
                    if (course.markSpawns.isNotEmpty()) {
                        column(
                            COURSE_MARK_SPAWNS,
                            *course.markSpawns.map { it.packed }.toTypedArray<Any>(),
                        )
                    }
                    course.reqs.quest?.let { column(COURSE_QUEST, it) }
                    if (course.reqs.worn.isNotEmpty()) {
                        columnRSCM(COURSE_WORN, *course.reqs.worn.toTypedArray())
                    }
                    if (course.reqs.wornMessage.isNotEmpty()) {
                        column(COURSE_WORN_MESSAGE, course.reqs.wornMessage)
                    }
                    column(COURSE_GRAPPLE, course.reqs.grapple)
                }
            }
        }

    fun obstacles() =
        dbTable("dbtable.agility_obstacle", serverOnly = true) {
            column("course", OBSTACLE_COURSE, VarType.DBROW)
            column("ordinal", OBSTACLE_ORDINAL, VarType.INT)
            column("locs", OBSTACLE_LOCS, VarType.LOC)
            column(
                "landing",
                OBSTACLE_LANDING,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
            )
            column("xp", OBSTACLE_XP, VarType.INT)
            column("anim", OBSTACLE_ANIM, VarType.SEQ)
            column("ticks", OBSTACLE_TICKS, VarType.INT)
            column("slide", OBSTACLE_SLIDE, VarType.BOOLEAN)
            column("repeats", OBSTACLE_REPEATS, VarType.INT)
            // low, high, the divisor and the addend of the share of hitpoints a slip takes.
            column(
                "fail",
                OBSTACLE_FAIL,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
            )

            for (course in AgilityCourseData.courses) {
                course.obstacles.forEachIndexed { ordinal, obstacle ->
                    row(obstacleRow(course, ordinal)) {
                        columnRSCM(OBSTACLE_COURSE, courseRow(course))
                        column(OBSTACLE_ORDINAL, ordinal)
                        columnRSCM(OBSTACLE_LOCS, *obstacle.locs.toTypedArray())
                        column(OBSTACLE_LANDING, *landingValues(obstacle.landing)!!)
                        column(OBSTACLE_XP, (obstacle.xp * 10).toInt())
                        columnRSCM(OBSTACLE_ANIM, obstacle.anim)
                        column(OBSTACLE_TICKS, obstacle.ticks)
                        column(OBSTACLE_SLIDE, obstacle.slide)
                        column(OBSTACLE_REPEATS, obstacle.repeats)
                        obstacle.fail?.let {
                            column(
                                OBSTACLE_FAIL,
                                it.low,
                                it.high,
                                it.damageDivisor,
                                it.damageBase,
                            )
                        }
                    }
                }
            }
        }

    fun stages() =
        dbTable("dbtable.agility_stage", serverOnly = true) {
            column("obstacle", STAGE_OBSTACLE, VarType.DBROW)
            column("ordinal", STAGE_ORDINAL, VarType.INT)
            column("anim", STAGE_ANIM, VarType.SEQ)
            column(
                "landing",
                STAGE_LANDING,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
                VarType.INT,
            )
            column("ticks", STAGE_TICKS, VarType.INT)
            column("slide", STAGE_SLIDE, VarType.BOOLEAN)
            column("move_ticks", STAGE_MOVE_TICKS, VarType.INT)
            column("per_tile", STAGE_PER_TILE, VarType.INT)
            column("sound", STAGE_SOUND, VarType.SYNTH)
            column("sound_loops", STAGE_SOUND_LOOPS, VarType.INT)
            column("sound_delay", STAGE_SOUND_DELAY, VarType.INT)
            column("anim_delay", STAGE_ANIM_DELAY, VarType.INT)

            for (course in AgilityCourseData.courses) {
                course.obstacles.forEachIndexed { ordinal, obstacle ->
                    obstacle.stages.forEachIndexed { index, stage ->
                        row(stageRow(course, ordinal, index)) {
                            columnRSCM(STAGE_OBSTACLE, obstacleRow(course, ordinal))
                            column(STAGE_ORDINAL, index)
                            columnRSCM(STAGE_ANIM, stage.anim)
                            landingValues(stage.landing)?.let { column(STAGE_LANDING, *it) }
                            column(STAGE_TICKS, stage.ticks)
                            column(STAGE_SLIDE, stage.slide)
                            column(STAGE_MOVE_TICKS, stage.moveTicks)
                            column(STAGE_PER_TILE, stage.perTile)
                            stage.sound?.let { columnRSCM(STAGE_SOUND, it) }
                            column(STAGE_SOUND_LOOPS, stage.soundLoops)
                            column(STAGE_SOUND_DELAY, stage.soundDelay)
                            column(STAGE_ANIM_DELAY, stage.animDelay)
                        }
                    }
                }
            }
        }
}
