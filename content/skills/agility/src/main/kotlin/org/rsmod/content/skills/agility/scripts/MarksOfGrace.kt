package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.skills.agility.configs.AgilityCourse
import org.rsmod.content.skills.agility.configs.LapReward
import org.rsmod.content.skills.agility.configs.MarkSpawnConfig
import org.rsmod.game.entity.Player

@Singleton
class MarksOfGrace @Inject constructor(private val objRepo: ObjRepository) {
    private var Player.marksLastSpawnMinute by intVarp("varp.agility_marks_last_spawn")

    fun lapReward(spawns: MarkSpawnConfig): LapReward =
        object : LapReward {
            override fun onLapComplete(access: ProtectedAccess, course: AgilityCourse) {
                access.rollMark(course, spawns)
            }
        }

    private fun ProtectedAccess.rollMark(course: AgilityCourse, spawns: MarkSpawnConfig) {
        val now = currentGameMinute()
        val last = player.marksLastSpawnMinute
        if (last in 1..now && now - last < SPAWN_COOLDOWN_MINUTES) {
            return
        }
        val overleveled =
            spawns.overlevelPenaltyApplies && player.baseAgilityLvl >= course.levelReq + OVERLEVEL_MARGIN
        val denominator =
            if (overleveled) spawns.chanceDenominator * OVERLEVEL_DENOMINATOR_MULT
            else spawns.chanceDenominator
        if (random.of(1, denominator) > spawns.chanceNumerator) {
            return
        }
        val tile = random.pick(spawns.tiles)
        objRepo.add(
            type = MARK_OBJ,
            coords = tile,
            duration = MARK_DURATION_TICKS,
            receiver = player,
            reveal = MARK_DURATION_TICKS + 1,
        )
        // Start the cooldown on a successful spawn, not a failed roll.
        player.marksLastSpawnMinute = now
    }

    private fun currentGameMinute(): Int =
        (System.currentTimeMillis() / MILLIS_PER_GAME_MINUTE).toInt()

    private companion object {
        const val MARK_OBJ = "obj.grace"

        const val SPAWN_COOLDOWN_MINUTES = 3

        const val MARK_DURATION_TICKS = 980

        const val OVERLEVEL_MARGIN = 20

        const val OVERLEVEL_DENOMINATOR_MULT = 5

        const val MILLIS_PER_GAME_MINUTE = 60_000L
    }
}
