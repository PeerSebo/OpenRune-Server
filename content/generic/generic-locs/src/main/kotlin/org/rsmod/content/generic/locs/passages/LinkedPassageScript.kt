package org.rsmod.content.generic.locs.passages

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

data class PassageEnd(val loc: String, val coords: CoordGrid, val landing: CoordGrid? = null)

data class LinkedPassage(val name: String, val a: PassageEnd, val b: PassageEnd)

object LinkedPassages {
    val all: List<LinkedPassage> =
        listOf(
            LinkedPassage(
                name = "Wilderness Slayer Cave (south)",
                a = PassageEnd("loc.wild_slayer_cave_south_entrance", CoordGrid(3259, 3664)),
                b =
                    PassageEnd(
                        "loc.wild_slayer_cave_south_exit",
                        CoordGrid(3384, 10050),
                        landing = CoordGrid(3385, 10052),
                    ),
            ),
            LinkedPassage(
                name = "Wilderness Slayer Cave (north)",
                a = PassageEnd("loc.wild_slayer_cave_north_entrance", CoordGrid(3293, 3746)),
                b =
                    PassageEnd(
                        "loc.wild_slayer_cave_north_exit",
                        CoordGrid(3405, 10146),
                        landing = CoordGrid(3407, 10145),
                    ),
            ),
        )
}

class LinkedPassageScript @Inject constructor(private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (passage in LinkedPassages.all) {
            onOpLoc1(passage.a.loc) { travel(passage.a, passage.b) }
            onOpLoc1(passage.b.loc) { travel(passage.b, passage.a) }
        }
    }

    private suspend fun ProtectedAccess.travel(start: PassageEnd, end: PassageEnd) {
        arriveDelay()
        if (coords.level != start.coords.level || coords.chebyshevDistance(start.coords) > 16) return
        val dest =
            end.landing
                ?: Passages.landingCandidates(end.coords).firstOrNull {
                    collision.isZoneValid(it) && !collision.isWalkBlocked(it)
                }
        if (dest == null || !collision.isZoneValid(dest) || collision.isWalkBlocked(dest)) {
            mes("You cannot see a way through.")
            return
        }
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }
}
