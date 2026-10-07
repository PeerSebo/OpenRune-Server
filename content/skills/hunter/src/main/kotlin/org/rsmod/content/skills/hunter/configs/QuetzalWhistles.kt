package org.rsmod.content.skills.hunter.configs

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

internal enum class QuetzalWhistle(val obj: String, val capacity: Int, val blueprint: String?, val logs: String?, val previous: String?) {
    BASIC("obj.hg_quetzalwhistle_basic", 5, "obj.hg_whistle_blueprint_1", "obj.willow_logs", null),
    ENHANCED("obj.hg_quetzalwhistle_enhanced", 20, "obj.hg_whistle_blueprint_2", "obj.yew_logs", BASIC.obj),
    PERFECTED("obj.hg_quetzalwhistle_perfected", 50, "obj.hg_whistle_blueprint_3", "obj.redwood_logs", ENHANCED.obj),
    INFINITE("obj.hg_quetzalwhistle_perfected_infinite", Int.MAX_VALUE, null, null, null);

    fun charges(item: InvObj): Int = if (this == INFINITE) Int.MAX_VALUE else item.vars.coerceIn(0, capacity)
    companion object { fun from(item: InvObj?): QuetzalWhistle? = entries.firstOrNull { it.obj.asRSCM() == item?.id } }
}

// Native table 64: column 0 = ID, 2 = arrival, 6 = built by default; script 5015 reads the unlock bit.
internal enum class QuetzalDestination(val nativeId: Int, val displayName: String, val coords: CoordGrid, val built: Boolean, val suffix: String) {
    FORTIS(1, "Civitas illa Fortis", CoordGrid(1697, 3140), true, "fortis"),
    TEOMAT(2, "The Teomat", CoordGrid(1437, 3171), true, "teomat"),
    SUNSET(3, "Sunset Coast", CoordGrid(1548, 2995), true, "sunsetcoast"),
    GUILD(4, "Hunter Guild", CoordGrid(1585, 3053), true, "hunterguild"),
    CAM_TORUM(5, "Cam Torum Entrance", CoordGrid(1446, 3108), false, "camtorum"),
    WYRM(6, "Colossal Wyrm Remains", CoordGrid(1670, 2933), false, "colossalwyrm"),
    OUTER_FORTIS(7, "Outer Fortis", CoordGrid(1700, 3037), false, "outerfortis"),
    COLOSSEUM(8, "Fortis Colosseum", CoordGrid(1779, 3111), false, "colosseum"),
    ALDARIN(9, "Aldarin", CoordGrid(1389, 2901), true, "aldarin"),
    GORGE(10, "Quetzacalli Gorge", CoordGrid(1510, 3222), true, "quetzacalligorge"),
    SALVAGER(11, "Salvager Overlook", CoordGrid(1613, 3300), false, "salvageroverlook"),
    TAL_TEKLAN(12, "Tal Teklan", CoordGrid(1226, 3091), true, "talteklan"),
    AUBURNVALE(13, "Auburnvale", CoordGrid(1411, 3361), true, "auburnvalley"),
    KASTORI(14, "Kastori", CoordGrid(1344, 3022), false, "kastori");

    val varbit: String get() = "varbit.quetzal_$suffix"
    val parent: String get() = "loc.quetzal_landing_site_$suffix"
    fun unlocked(player: Player): Boolean = built || player.vars["varp.quetzals_unlocked"] and (1 shl nativeId) != 0
    companion object {
        fun fromButton(sub: Int): QuetzalDestination? = entries.getOrNull(sub)
        fun fromNative(id: Int): QuetzalDestination? = entries.firstOrNull { it.nativeId == id }
    }
}
