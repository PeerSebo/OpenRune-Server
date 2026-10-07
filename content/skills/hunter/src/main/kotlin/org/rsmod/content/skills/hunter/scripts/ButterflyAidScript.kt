package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.mapMultiway
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.content.skills.hunter.configs.NetPrey
import org.rsmod.content.skills.hunter.configs.NetPreyKind
import org.rsmod.content.skills.hunter.configs.NetPreyTable
import org.rsmod.content.skills.hunter.configs.NettingParams
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ButterflyAidScript @Inject constructor(private val players: PlayerRepository, private val areas: AreaChecker) : PluginScript() {
    override fun ScriptContext.startup() {
        for (prey in NetPreyTable.ALL.filter { it.kind == NetPreyKind.BUTTERFLY }) {
            val type = checkNotNull(ServerCacheManager.getItem(prey.filledJar.asRSCM()))
            onOpPlayerU(type) { share(prey, it.invSlot, it.target) }
        }
    }

    internal fun ProtectedAccess.share(prey: NetPrey, slot: Int, target: Player) {
        if (prey.kind != NetPreyKind.BUTTERFLY || !inv[slot].isType(prey.filledJar)) return
        if (player === target || !player.isValidTarget() || !isWithinDistance(target, 1)) return
        if (!player.mapMultiway(areas) || !target.mapMultiway(areas)) {
            mes("You can only share butterflies in a multi-combat area.")
            return
        }
        // TODO: allow aid within GIM groups when group identity is available.
        if (player.isAnyIronman || target.isAnyIronman) {
            mes("Ironmen do not accept aid.")
            return
        }
        if (!canReceive(target)) {
            mes("That player cannot accept aid right now.")
            return
        }
        val recipients = listOf(target) + players.findAll(ZoneKey.from(target.coords), 1)
            .filter { it !== target && it.coords.level == target.coords.level && it.coords.chebyshevDistance(target.coords) <= 2 && canReceive(it) }
            .distinctBy { it.slotId }.sortedBy { it.slotId }.take(3).toList()
        val empty = checkNotNull(ServerCacheManager.getItem(NettingParams.BUTTERFLY_JAR.asRSCM()))
        if (!invReplaceSlot(inv, slot, 1, empty).success) return
        for (recipient in recipients) ButterflyEffects.apply(recipient, prey)
        mes("You release the creature to aid the nearby players.")
    }

    private fun canReceive(player: Player): Boolean = player.isValidTarget() && !player.isAccessProtected &&
        !player.isAnyIronman && player.vars["varbit.option_acceptaid"] != 0 && player.mapMultiway(areas)
}
