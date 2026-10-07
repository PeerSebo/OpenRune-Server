package org.rsmod.content.skills.hunter.configs

enum class TrapFamily(
    val levelReq: Int,
    val blockedByPlayerOnTile: Boolean,
    val singleTrapOnly: Boolean,
) {
    BIRD_SNARE(levelReq = 1, blockedByPlayerOnTile = true, singleTrapOnly = false),

    BOX_TRAP(levelReq = 27, blockedByPlayerOnTile = true, singleTrapOnly = false),

    NET_TRAP(levelReq = 29, blockedByPlayerOnTile = true, singleTrapOnly = false),

    DEADFALL(levelReq = 23, blockedByPlayerOnTile = false, singleTrapOnly = true),
}
