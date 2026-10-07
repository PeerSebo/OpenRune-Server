package org.rsmod.content.minigames.mahoganyhomes.configs

public enum class MahoganyHomesHouse {
    BOB,
    JEFF,
    SARAH,
    TAU,
    LARRY,
    NORMAN,
    NOELLA,
    ROSS,
    JESS,
    MARIAH,
    LEELA,
    BARBARA;

    private val definition get() = MahoganyHomesData.houses.getValue(name)
    public val clientNpc: String get() = definition.client
    public val hotspots: List<String> get() = definition.hotspots

    public companion object {
        public fun ofOrdinal(ordinal: Int): MahoganyHomesHouse = entries[ordinal]
    }
}
