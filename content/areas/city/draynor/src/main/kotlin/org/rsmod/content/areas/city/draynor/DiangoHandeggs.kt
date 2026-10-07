package org.rsmod.content.areas.city.draynor

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.dbcol.DbColumnCodec.SpotCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.SeqIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.obj
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.ironman.IronmanRestrictions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpPlayerU
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoHandeggs @Inject constructor(
    private val equipment: HeldEquipOp,
    private val world: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (egg in eggs) onOpPlayerU(egg.item) { pass(egg, it.invSlot, it.target) }
    }

    private fun ProtectedAccess.pass(egg: Egg, slot: Int, target: Player) {
        if (actionDelay > mapClock || inv[slot]?.id != egg.item.id) return
        if (
            player === target || !player.isValidTarget() || !target.isValidTarget() ||
                player.staffInvisible || target.staffInvisible || !isWithinDistance(target, 1)
        ) return
        if (IronmanRestrictions.blockTrade(player, target)) return
        if (
            target.isAccessProtected || target.worn[Wearpos.RightHand.slot] != null ||
                target.worn[Wearpos.LeftHand.slot] != null
        ) {
            mes("That player needs both hands free to catch the handegg.")
            return
        }
        val result = equipment.equip(target, slot, inv)
        if (result !is HeldEquipResult.Success) {
            mes("That player cannot catch the handegg right now.")
            return
        }
        actionDelay = mapClock + 3
        player.abortRoute()
        player.facePathingEntitySquare(target)
        target.facePathingEntitySquare(player)
        anim(egg.throwAnimation)
        val projectile = ProjAnim.fromPlayerToPlayer(
            player, target, egg.projectile.asRSCM(), "projanim.thrown",
        )
        world.projAnim(projectile)
        target.anim(egg.catchAnimation, delay = projectile.clientCycles)
        mes("You pass the handegg to ${target.displayName}.")
        target.mes("${player.displayName} passes you a handegg.")
    }

    internal data class Egg(
        val item: ItemServerType,
        val throwAnimation: String,
        val catchAnimation: String,
        val projectile: String,
    )

    internal companion object {
        val eggs by lazy {
            DbHelper.table("dbtable.diango_handeggs").map { row ->
                val prefix = "dbcol.diango_handeggs:"
                fun sequence(column: String) = RSCM.getReverseMapping(
                    RSCMType.SEQ, row.column(prefix + column, SeqIdCodec),
                )
                Egg(
                    row.obj(prefix + "item"),
                    sequence("throw"),
                    sequence("catch"),
                    RSCM.getReverseMapping(
                        RSCMType.SPOTANIM, row.column(prefix + "projectile", SpotCodec).id,
                    ),
                )
            }.also { require(it.size == 3 && it.map { egg -> egg.item.id }.distinct().size == 3) }
        }
    }
}
