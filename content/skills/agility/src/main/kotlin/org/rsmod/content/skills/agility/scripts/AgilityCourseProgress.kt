package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.skills.agility.configs.AgilityCourse
import org.rsmod.game.entity.Player

// Saved layout: bits 0-4 are course ID (zero = no lap); bits 5-9 are the next expected obstacle index.
internal object AgilityCourseProgress {
    private var Player.progress by intVarp("varp.agility_course_progress")

    private const val COURSE_ID_BITS = 5
    private const val COURSE_ID_MASK = (1 shl COURSE_ID_BITS) - 1

    private const val MAX_OBSTACLES = 32

    fun completeObstacle(player: Player, course: AgilityCourse, obstacleIndex: Int): Boolean {
        val next = nextProgress(player.progress, course, obstacleIndex) ?: return false
        player.progress = next
        return next == 0
    }

    fun canCompleteObstacle(player: Player, course: AgilityCourse, obstacleIndex: Int): Boolean =
        nextProgress(player.progress, course, obstacleIndex) != null

    fun switchCourse(player: Player, from: AgilityCourse, to: AgilityCourse, expectedIndex: Int): Boolean {
        val next = switchedProgress(player.progress, from, to, expectedIndex) ?: return false
        player.progress = next
        return true
    }

    internal fun switchedProgress(packed: Int, from: AgilityCourse, to: AgilityCourse, expectedIndex: Int): Int? {
        require(expectedIndex > 0) { "Switching courses requires a completed shared prefix." }
        if (nextProgress(packed, from, expectedIndex) == null) {
            return null
        }
        val switched = (packed and COURSE_ID_MASK.inv()) or to.id
        return switched.takeIf { nextProgress(it, to, expectedIndex) != null }
    }

    fun rewindCourse(player: Player, course: AgilityCourse, expectedIndex: Int): Boolean {
        val next = rewoundProgress(player.progress, course, expectedIndex) ?: return false
        player.progress = next
        return true
    }

    internal fun rewoundProgress(packed: Int, course: AgilityCourse, expectedIndex: Int): Int? {
        require(course.id in 1..COURSE_ID_MASK)
        require(course.obstacleCount in 1..MAX_OBSTACLES)
        require(expectedIndex in 1 until course.obstacleCount)
        val storedCourse = packed and COURSE_ID_MASK
        val storedExpected = packed ushr COURSE_ID_BITS
        if (storedCourse != course.id || storedExpected !in 1 until course.obstacleCount || expectedIndex > storedExpected) {
            return null
        }
        return (expectedIndex shl COURSE_ID_BITS) or course.id
    }

    internal fun nextProgress(packed: Int, course: AgilityCourse, obstacleIndex: Int): Int? {
        require(course.id in 1..COURSE_ID_MASK) { "Course id out of range: ${course.id}" }
        require(course.obstacleCount in 1..MAX_OBSTACLES) {
            "Obstacle count out of range: ${course.obstacleCount}"
        }
        require(obstacleIndex in 0 until course.obstacleCount) {
            "Obstacle index out of range: $obstacleIndex (course=${course.displayName})"
        }
        val storedCourse = packed and COURSE_ID_MASK
        val expected = if (storedCourse == course.id) packed ushr COURSE_ID_BITS else 0
        if (obstacleIndex != 0 && obstacleIndex != expected) {
            return null
        }
        val lapComplete = obstacleIndex == course.obstacleCount - 1
        return if (lapComplete) 0 else ((obstacleIndex + 1) shl COURSE_ID_BITS) or course.id
    }

    fun incrementLaps(player: Player, course: AgilityCourse): Int {
        val next = player.vars[course.lapCountVarp].let { if (it == Int.MAX_VALUE) it else it + 1 }
        VarPlayerIntMapSetter.set(player, course.lapCountVarp, next)
        return next
    }

    fun reset(player: Player) {
        player.progress = 0
    }

    fun currentCourseId(player: Player): Int = player.progress and COURSE_ID_MASK
}
