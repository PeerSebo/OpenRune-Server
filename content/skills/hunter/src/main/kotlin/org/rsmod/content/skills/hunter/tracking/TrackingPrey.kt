package org.rsmod.content.skills.hunter.tracking

internal enum class TrackingPrey(val level: Int, val xp: Double, val loot: String, val animation: String, val eventKey: String) {
    POLAR(1, 30.0, "obj.huntingbeast_polar_fur", "seq.hunting_noose_polar", "loc.hunting_trail_end_polar"),
    COMMON(3, 36.0, "obj.huntingbeast_woodland_fur", "seq.hunting_noose_wood", "loc.hunting_trail_end_bush35_55"),
    WEASEL(7, 48.0, "obj.huntingbeast_jungle_fur", "seq.hunting_noose_jungle", "loc.hunting_trail_end_jungle"),
    DESERT(13, 66.0, "obj.huntingbeast_desert_fur", "seq.hunting_noose_desert", "loc.hunting_trail_end_desert"),
    RAZOR(49, 348.5, "obj.huntingbeast_bigspike", "seq.hunting_noose_razorback", "loc.hunting_trail_end_bush29"),
    HERBIBOAR(80, 0.0, "obj.unidentified_guam", "seq.pruning_low", "npc.fossil_herbiboar_visible")
}
