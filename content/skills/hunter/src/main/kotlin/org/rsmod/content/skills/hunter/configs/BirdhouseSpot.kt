package org.rsmod.content.skills.hunter.configs

enum class BirdhouseSpot(val baseLoc: String, val stateVarp: String, val seededMinuteVarp: String) {
    ONE("loc.birdhouse_1", "varp.birdhouse_transmit_a", "varp.hunter_birdhouse_seeded_1"),

    TWO("loc.birdhouse_2", "varp.birdhouse_transmit_b", "varp.hunter_birdhouse_seeded_2"),

    THREE("loc.birdhouse_3", "varp.birdhouse_transmit_c", "varp.hunter_birdhouse_seeded_3"),

    FOUR("loc.birdhouse_4", "varp.birdhouse_transmit_d", "varp.hunter_birdhouse_seeded_4"),
}
