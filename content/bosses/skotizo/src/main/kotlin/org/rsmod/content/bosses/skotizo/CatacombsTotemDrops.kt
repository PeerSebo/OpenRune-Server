package org.rsmod.content.bosses.skotizo

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.skills.slayer.superior.SlayerSuperiorManager
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class CatacombsTotemDrops @Inject constructor(
    private val areas: AreaChecker,
    private val superiors: SlayerSuperiorManager,
    private val random: GameRandom,
    private val objs: ObjRepository,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        if (!CatacombsTotemRules.eligible(areas.inArea("area.catacombs_of_kourend", npc.coords), npc)) return
        val denominator = CatacombsTotemRules.denominator(npc.visType.hitpoints, superiors.isSuperior(npc))
        if (random.of(denominator) != 0) return
        val part = CatacombsTotemRules.nextPart(CatacombsTotemRules.counts(context.hero))
        objs.add(part, npc.coords, constants.lootdrop_duration, receiver = context.hero)
    }
}

internal object CatacombsTotemRules {
    val Parts = listOf("obj.cata_totem1", "obj.cata_totem2", "obj.cata_totem3")
    fun eligible(inCatacombs: Boolean, npc: Npc): Boolean = inCatacombs && npc.visType.combatLevel > 0 &&
        !npc.visType.name.equals("Ghost", ignoreCase = true) && npc.visType.id !in 7286..7296
    fun denominator(hitpoints: Int, superior: Boolean): Int = if (superior) 1 else (500 - hitpoints).coerceAtLeast(1)
    fun counts(player: Player): List<Int> = Parts.map { part ->
        player.inv.count(part) + (player.invMap["inv.bank"]?.count(part) ?: 0)
    }
    fun nextPart(counts: List<Int>): String {
        require(counts.size == 3 && counts.all { it >= 0 })
        return Parts[counts.indices.minBy { counts[it] }]
    }
}
