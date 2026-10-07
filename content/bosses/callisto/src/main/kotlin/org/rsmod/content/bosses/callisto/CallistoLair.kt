package org.rsmod.content.bosses.callisto

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.areas.wilderness.checkWildernessBossFee
import org.rsmod.content.areas.wilderness.tryPayWildernessBossFee
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CallistoLair @Inject constructor(
    private val players: PlayerList,
    private val random: GameRandom,
    private val clock: MapClock,
) : PluginScript() {
    internal data class Arena(
        val variant: BearVariant,
        val entrance: String,
        val surface: CoordGrid,
        val arrival: CoordGrid,
        val bossSpawn: CoordGrid,
        val exit: CoordGrid,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
    ) {
        fun contains(tile: CoordGrid): Boolean = tile.level == 0 && tile.x in minX..maxX && tile.z in minZ..maxZ
    }

    private var lastExitTick = Int.MIN_VALUE
    private var lastExit = EscapeLandings.first()

    override fun ScriptContext.startup() {
        for (arena in Arenas) {
            onOpLoc1(arena.entrance) {
                arriveDelay()
                tryPayWildernessBossFee(50_000) { source ->
                    mes("<col=ef1020>You enter the cave and 50,000 coins are taken from $source to pay the entry fee.")
                    anim("seq.godwars_human_crawling")
                    delay(1)
                    telejump(arena.arrival, TeleportType.Exempt)
                    anim("seq.human_falling_end")
                }
            }
            onOpLoc2(arena.entrance) {
                if (player.vars[arena.variant.killcount] < 20) {
                    mes("You need to have defeated this boss 20 times before you can peek inside.")
                } else {
                    val count = players.count { !it.staffInvisible && arena.contains(it.coords) }
                    mes(if (count == 0) "The lair is currently empty." else "There ${if (count == 1) "is 1 player" else "are $count players"} inside the lair.")
                }
            }
            onOpLoc3(arena.entrance) { checkWildernessBossFee(50_000) }
        }
        onOpLoc1("loc.wild_callisto_exit01") { event ->
            val arena = Arenas.firstOrNull { it.exit == event.loc.coords } ?: return@onOpLoc1
            arriveDelay()
            telejump(if (arena.variant == BearVariant.ARTIO) arena.surface else escapeLanding(), TeleportType.Exempt)
        }
        for (loc in EscapeExits.keys) {
            onOpLoc1(loc) {
                val dest = EscapeExits.getValue(loc)[it.loc.coords] ?: return@onOpLoc1
                arriveDelay()
                telejump(dest, TeleportType.Exempt)
            }
        }
    }

    private fun escapeLanding(): CoordGrid {
        if (lastExitTick == Int.MIN_VALUE || clock.cycle - lastExitTick > 16) {
            lastExit = EscapeLandings[random.of(EscapeLandings.size)]
        }
        lastExitTick = clock.cycle
        return lastExit
    }

    internal companion object {
        val Arenas = listOf(
            Arena(BearVariant.CALLISTO, "loc.wild_callisto_entrance01", CoordGrid(3289, 3851),
                CoordGrid(3359, 10336), CoordGrid(3357, 10320), CoordGrid(3358, 10315), 3338, 3380, 10314, 10355),
            Arena(BearVariant.ARTIO, "loc.wild_callisto_singles_entrance01", CoordGrid(3116, 3675),
                CoordGrid(1759, 11552), CoordGrid(1758, 11536), CoordGrid(1758, 11531), 1738, 1780, 11530, 11571),
        )
        fun arena(tile: CoordGrid): Arena? = Arenas.firstOrNull { it.contains(tile) }

        val EscapeLandings = listOf(CoordGrid(3337, 10279), CoordGrid(3359, 10269), CoordGrid(3380, 10279))
        val EscapeExits = mapOf(
            "loc.wild_boss_escape_cave_exit01" to mapOf(CoordGrid(3358, 10244) to CoordGrid(3359, 3843)),
            "loc.wild_boss_escape_cave_exit02" to mapOf(
                CoordGrid(3336, 10287) to CoordGrid(3334, 3887),
                CoordGrid(3382, 10287) to CoordGrid(3384, 3887),
            ),
            "loc.wild_boss_escape_cave_exit03" to mapOf(CoordGrid(3361, 10273) to CoordGrid(3362, 3873)),
        )
    }
}
