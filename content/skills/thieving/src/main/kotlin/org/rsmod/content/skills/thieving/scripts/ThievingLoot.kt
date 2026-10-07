package org.rsmod.content.skills.thieving.scripts

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import dtx.rs.RSTable
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj

internal object ThievingLoot {
    internal data class ThievingDrop(val obj: String, val count: Int)

    fun ProtectedAccess.awardLoot(
        registry: DropTableRegistry,
        objRepo: ObjRepository,
        loc: String,
    ): List<String> {
        val table = registry.forLoc(loc) ?: return emptyList()
        return awardLoot(objRepo, table)
    }

    fun ProtectedAccess.awardLoot(
        objRepo: ObjRepository,
        table: RSTable<Player, DropRollItem>,
    ): List<String> = rollLoot(table).mapNotNull { drop -> giveDrop(objRepo, drop) }

    fun ProtectedAccess.rollLoot(table: RSTable<Player, DropRollItem>): List<ThievingDrop> {
        val rolled = mutableListOf<ThievingDrop>()
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> resolve(result.result)?.let(rolled::add)
            is RollResult.ListOf ->
                result.results.forEach { drop -> resolve(drop)?.let(rolled::add) }
        }
        return rolled
    }

    fun ProtectedAccess.giveDrop(objRepo: ObjRepository, drop: ThievingDrop): String {
        invAddOrDrop(objRepo, drop.obj, drop.count)
        return getInvObj(InvObj(drop.obj)).name.lowercase()
    }

    private fun ProtectedAccess.resolve(drop: DropRollItem): ThievingDrop? {
        if (drop.isNothing || !drop.condition(player)) {
            return null
        }
        val obj = drop.transformObj(player) ?: drop.obj
        if (obj.isEmpty()) {
            return null
        }
        return ThievingDrop(obj, drop.rollCount(random))
    }
}

internal fun rollPrifddinasLoot(doubleLoot: Boolean, roll: (Int) -> Int): List<ThievingLoot.ThievingDrop> = buildList {
    val count = if (doubleLoot) 2 else 1
    if (roll(35) == 0) add(ThievingLoot.ThievingDrop("obj.prif_crystal_shard", count))
    if (roll(1024) == 0) add(ThievingLoot.ThievingDrop("obj.prif_teleport_seed", count))
}
