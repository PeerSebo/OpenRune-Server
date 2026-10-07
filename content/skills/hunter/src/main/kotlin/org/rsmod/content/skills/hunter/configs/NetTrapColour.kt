package org.rsmod.content.skills.hunter.configs

enum class NetTrapColour(
    val setSuffix: String,
    val catchSuffix: String,
    val prey: String,
) {
    SWAMP(setSuffix = "swamp", catchSuffix = "green", prey = "npc.salamander_green"),
    ORANGE(setSuffix = "orange", catchSuffix = "orange", prey = "npc.salamander_orange"),
    RED(setSuffix = "red", catchSuffix = "red", prey = "npc.salamander_red"),
    BLACK(setSuffix = "black", catchSuffix = "black", prey = "npc.salamander_black"),
    MOUNTAIN(setSuffix = "mountain", catchSuffix = "mountain", prey = "npc.salamander_mountain"),
}
