package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.ImplingDropTables
import org.rsmod.game.entity.Player

internal object ImplingLoot {
    data class Reward(val obj: String, val count: Int, val noted: Boolean = false)

    fun roll(index: Int, random: GameRandom): List<Reward> = buildList {
        if (index == 11) {
            addAll(LuckyImplingRewards.roll(random))
            return@buildList
        }
        val table = checkNotNull(ImplingDropTables.main[index]) { "Unknown impling: $index" }
        // Gourmet's key replaces its main reward, not its independent clue roll.
        if (index == 3 && random.of(500) == 0) {
            add(Reward("obj.hosdun_grubby_key", 1))
        } else {
            var roll = random.of(table.sumOf { it.weight })
            val entry = table.first { entry -> roll -= entry.weight; roll < 0 }
            if (!entry.item.isNothing) add(Reward(entry.item.obj, entry.item.rollCount(random), entry.noted))
        }
        for (bonus in ImplingDropTables.tertiary[index].orEmpty()) {
            if (random.of(bonus.denominator) == 0) add(Reward(bonus.obj, 1))
        }
    }

    fun ProtectedAccess.award(repo: ObjRepository, rewards: List<Reward>) {
        for (reward in rewards) {
            val type = checkNotNull(ServerCacheManager.getItem(reward.obj.asRSCM(RSCMType.OBJ)))
            val clue = type.name.startsWith("Clue scroll (")
            val boxed = clue && QuestRequirements.hasCompleted(player, "quest_xmarksthespot")
            if (clue && !boxed && hasClue(player, type.name)) continue
            if (boxed) {
                val tier = type.name.substringAfter('(').substringBefore(')').lowercase()
                invAddOrDrop(repo, "obj.league_clue_box_$tier", reward.count)
                continue
            }
            val output = if (reward.noted) {
                check(type.canCert) { "Impling reward ${reward.obj} cannot be noted" }
                checkNotNull(ServerCacheManager.getItem(type.certlink))
            } else type
            invAddOrDropType(repo, output, reward.count)
            CollectionLog.grant(player, type.id, reward.count)
        }
    }

    internal fun hasClue(player: Player, name: String): Boolean {
        val tier = BirdhouseClues.Tier.entries.firstOrNull { name == "Clue scroll (${it.suffix})" }
        if (tier != null) return BirdhouseClues.hasClue(player, tier)
        val inventories = sequenceOf(player.inv) + player.invMap.values.asSequence()
        return inventories.any { inv -> inv.objs.any { item ->
            item != null && item.count > 0 && ServerCacheManager.getItem(item.id)?.let { it.name == name && !it.isPlaceholder } == true
        } }
    }
}
