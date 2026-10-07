package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.BirdhouseParams
import org.rsmod.game.entity.Player

// Clue rates are approximate; Hunter-level influence is unconfirmed.
internal object BirdhouseClues {
    enum class Tier(val weight: Int, val scroll: String) {
        ELITE(1, "obj.trail_clue_elite_music001"),
        HARD(2, "obj.trail_clue_hard_map001"),
        MEDIUM(3, "obj.trail_clue_medium_sextant001"),
        EASY(4, "obj.trail_clue_easy_simple001"),
        BEGINNER(30, "obj.trail_clue_beginner");

        val suffix: String get() = name.lowercase()
        val nest: String get() = "obj.wc_clue_nest_$suffix"
        val box: String get() = "obj.league_clue_box_$suffix"
    }

    // Scale by twenty to preserve the tier-matched 5% bonus without rounding.
    const val DENOMINATOR = 30_000

    fun roll(player: Player, random: GameRandom): List<Tier> = Tier.entries.filter { tier ->
        val boxed = QuestRequirements.hasCompleted(player, "quest_xmarksthespot")
        val eligible = boxed || !hasClue(player, tier)
        val ca = tier != Tier.BEGINNER && player.vars["varbit.ca_lamp_claimed_${tier.suffix}"] != 0
        eligible && random.of(DENOMINATOR) < tier.weight * if (ca) 21 else 20
    }

    fun hasClue(player: Player, tier: Tier, includeContainers: Boolean = true): Boolean {
        val names = buildSet {
            add("Clue scroll (${tier.suffix})")
            add("Challenge scroll (${tier.suffix})")
            add("Clue casket (${tier.suffix})")
            if (includeContainers) {
                add("Clue nest (${tier.suffix})")
                add("Clue geode (${tier.suffix})")
                add("Clue bottle (${tier.suffix})")
            }
        }
        return (sequenceOf(player.inv) + player.invMap.values.asSequence()).any { inv ->
            inv.objs.any { item -> item != null && item.count > 0 && ServerCacheManager.getItem(item.id)?.let { !it.isPlaceholder && it.name in names } == true }
        }
    }

    fun ProtectedAccess.awardClue(repo: ObjRepository, tier: Tier) {
        if (QuestRequirements.hasCompleted(player, "quest_xmarksthespot")) {
            invAddOrDrop(repo, tier.box, 1)
            repo.add(BirdhouseParams.EMPTY_NEST, coords, player.lootDropDuration ?: constants.lootdrop_duration, receiver = player)
        } else {
            invAddOrDrop(repo, tier.nest, 1)
        }
    }
}
