package org.rsmod.content.skills.agility.configs

import org.rsmod.api.player.protect.ProtectedAccess

data class AgilityCourse(
    val id: Int,
    val displayName: String,
    val levelReq: Int,
    val obstacleCount: Int,
    val lapBonusXp: Double,
    val restoresRunEnergy: Boolean,
    val lapCountVarp: String,
    val lapReward: LapReward = LapReward.XpOnly,
    // Combined obstacle/lap XP requires a valid lap; its partial-lap base share is unknown.
    val finalObstacleXpRequiresLap: Boolean = false,
) {
    // Client helper enum 3507 uses different keys from saved lap progress; both Wyrm routes share one key.
    val helperCourseId: Int
        get() = when (id) {
            1 -> 1
            2 -> 3
            3 -> 4
            4 -> 7
            5 -> 10
            6 -> 12
            7 -> 15
            8 -> 17
            9 -> 19
            10 -> 20
            11 -> 21
            12, 13 -> 22
            14 -> 6
            15 -> 13
            else -> 0
        }
}

interface LapReward {
    fun onLapComplete(access: ProtectedAccess, course: AgilityCourse)

    object XpOnly : LapReward {
        override fun onLapComplete(access: ProtectedAccess, course: AgilityCourse) = Unit
    }
}
