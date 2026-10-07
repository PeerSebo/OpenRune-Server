package org.rsmod.content.skills.agility.table

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.agility.AgilityCourseRow
import org.rsmod.api.table.agility.AgilityObstacleRow
import org.rsmod.api.table.agility.AgilityStageRow
import org.rsmod.map.CoordGrid

internal const val STAT_AGILITY: String = "stat.agility"
internal const val MARK_OF_GRACE: String = "obj.grace"
internal const val VARP_COURSE: String = "varp.agility_table_course"
internal const val VARP_PROGRESS: String = "varp.agility_table_progress"
internal const val VARP_MARK_CLOCK: String = "varp.agility_marks_last_spawn"

data class Landing(
    val x: Int? = null,
    val z: Int? = null,
    val level: Int? = null,
    val dx: Int = 0,
    val dz: Int = 0,
    val dLevel: Int = 0,
) {
    fun resolve(from: CoordGrid): CoordGrid =
        CoordGrid(
            x = (x ?: from.x) + dx,
            z = (z ?: from.z) + dz,
            level = (level ?: from.level) + dLevel,
        )
}

data class Stage(
    val anim: String,
    val landing: Landing? = null,
    val ticks: Int = 0,
    val slide: Boolean = true,
    val moveTicks: Int = 0,
    val perTile: Int = 1,
    val sound: Int? = null,
    val soundLoops: Int = 1,
    val soundDelay: Int = 0,
    val animDelay: Int = 0,
)

data class ObstacleFail(val low: Int, val high: Int, val damageDivisor: Int, val damageBase: Int) {
    fun damage(hitpoints: Int): Int = hitpoints / damageDivisor + damageBase
}

data class Obstacle(
    val locs: List<String>,
    val landing: Landing,
    val xp: Double,
    val anim: String,
    val ticks: Int = 2,
    val slide: Boolean = false,
    val repeats: Int = 1,
    val fail: ObstacleFail? = null,
    val stages: List<Stage> = emptyList(),
)

data class CourseReqs(
    val quest: String? = null,
    val worn: List<String> = emptyList(),
    val wornMessage: String = "",
    val grapple: Boolean = false,
) {
    companion object {
        val NONE: CourseReqs = CourseReqs()
    }
}

data class Course(
    val name: String,
    val level: Int,
    val lapXp: Double,
    val obstacles: List<Obstacle>,
    val markSpawns: List<CoordGrid> = emptyList(),
    val markOdds: Double = 0.0,
    val markPenalty: Boolean = true,
    val petBase: Int = 0,
    val reqs: CourseReqs = CourseReqs.NONE,
) {
    val steps: List<Int> = obstacles.flatMapIndexed { index, obstacle ->
        // Ordinary ladders keep their global handler and award no course XP.
        if (obstacle.locs.all { it == SHARED_LADDER }) emptyList()
        else List(obstacle.repeats) { index }
    }
}

private const val LANDING_UNCHANGED = -1

private fun toLanding(slots: List<Int>): Landing? {
    if (slots.size < 6) {
        return null
    }
    return Landing(
        x = slots[0].takeIf { it != LANDING_UNCHANGED },
        z = slots[1].takeIf { it != LANDING_UNCHANGED },
        level = slots[2].takeIf { it != LANDING_UNCHANGED },
        dx = slots[3],
        dz = slots[4],
        dLevel = slots[5],
    )
}

private fun seqSymbol(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)

private fun List<Int>.toOdds(): Double =
    if (size < 2 || this[1] == 0) 0.0 else this[0].toDouble() / this[1]

private fun AgilityStageRow.toStage(): Stage =
    Stage(
        anim = seqSymbol(anim.id),
        landing = toLanding(landing),
        ticks = ticks,
        slide = slide,
        moveTicks = moveTicks,
        perTile = perTile,
        sound = sound,
        soundLoops = soundLoops,
        soundDelay = soundDelay,
        animDelay = animDelay,
    )

private fun AgilityObstacleRow.toObstacle(stages: List<Stage>): Obstacle =
    Obstacle(
        locs = locs.map { RSCM.getReverseMapping(RSCMType.LOC, it.id) },
        landing = toLanding(landing) ?: Landing(),
        xp = xp / 10.0,
        anim = seqSymbol(anim.id),
        ticks = ticks,
        slide = slide,
        repeats = repeats,
        fail = fail.takeIf { it.size >= 4 }?.let { ObstacleFail(it[0], it[1], it[2], it[3]) },
        stages = stages,
    )

private fun AgilityCourseRow.toCourse(obstacles: List<Obstacle>): Course =
    Course(
        name = name,
        level = level,
        lapXp = lapXp / 10.0,
        obstacles = obstacles,
        markSpawns = markSpawns,
        markOdds = markOdds.toOdds(),
        markPenalty = markPenalty,
        petBase = petBase,
        reqs =
            CourseReqs(
                quest = quest,
                worn = worn.map { it.internalName },
                wornMessage = wornMessage.orEmpty(),
                grapple = grapple,
            ),
    )

object AgilityCourses {
    val courses: List<Course> by lazy {
        val stages =
            AgilityStageRow.all()
                .sortedBy(AgilityStageRow::ordinal)
                .groupBy({ it.obstacle.rowId }, AgilityStageRow::toStage)
        val obstacles =
            AgilityObstacleRow.all().sortedBy(AgilityObstacleRow::ordinal).groupBy(
                { it.course.rowId },
                { it.toObstacle(stages[it.rowId].orEmpty()) },
            )
        AgilityCourseRow.all().map { row -> row.toCourse(obstacles[row.rowId].orEmpty()) }
    }
}
