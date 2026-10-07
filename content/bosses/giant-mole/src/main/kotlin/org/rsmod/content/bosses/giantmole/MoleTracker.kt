package org.rsmod.content.bosses.giantmole

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.isValidTarget
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

internal class MoleTracker(private val deps: BossDeps) {
    private val tracked = mutableMapOf<Player, CoordGrid>()
    private val moleId by lazy { "npc.mole_giant".asRSCM(RSCMType.NPC) }

    fun tick() {
        val eligible = deps.playerList.filter {
            inCave(it.coords) &&
                ("obj.falador_shield_hard" in it.inv || "obj.falador_shield_hard" in it.worn ||
                    "obj.falador_shield_elite" in it.inv || "obj.falador_shield_elite" in it.worn)
        }.toSet()
        for (player in tracked.keys.toList()) {
            if (player !in eligible) clear(player)
        }
        if (eligible.isEmpty()) return
        val mole = deps.npcRepo.findAll(ZoneKey.from(CoordGrid(1759, 5189)), zoneRadius = 10)
            .firstOrNull { it.id == moleId && it.isValidTarget() && inCave(it.coords) }
        if (mole == null) {
            eligible.forEach(::clear)
            return
        }
        val center = mole.coords.translate(mole.size / 2, mole.size / 2)
        for (player in eligible) {
            if (tracked[player] == center) continue
            tracked[player] = center
            player.client.write(HintArrow(HintArrow.TileHintArrow(center.x, center.z, 0, 2)))
        }
    }

    private fun clear(player: Player) {
        if (tracked.remove(player) != null && player.isSlotAssigned) {
            player.client.write(HintArrow(HintArrow.ResetHintArrow))
        }
    }

    private fun inCave(tile: CoordGrid): Boolean =
        tile.level == 0 && tile.x in 1728..1791 && tile.z in 5120..5247
}
