package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.skills.hunter.configs.HunterSackLoot
import org.rsmod.content.skills.hunter.configs.HunterSackReward
import org.rsmod.content.skills.hunter.configs.HunterSackTier
import org.rsmod.game.entity.Player

// Persist a sack's rolled reward until it fits; inventory failure must not reroll it.
@Singleton
internal class HunterGuildRewards @Inject constructor(private val random: GameRandom) {
    fun exchange(player: Player, obj: String): Boolean {
        if (obj !in EXCHANGEABLE || player.inv.count(obj) == 0) return false
        if (obj == KIT && player.invMap["inv.huntsmans_kit"]?.isNotEmpty() == true) {
            player.mes("Empty your huntsman's kit before exchanging it.")
            return false
        }
        if (obj in OUTFIT && ownedCount(player, obj) < 2) {
            player.mes("Verity exchanges spare outfit pieces. Keep this one for your outfit.")
            return false
        }
        val result = player.invTransaction(player.inv) {
            val inv = select(player.inv)
            delete(inv, obj.asRSCM(), 1)
            add(inv, HunterSackTier.SUPPLIES.obj.asRSCM(), 1)
        }
        if (result.success) player.mes("Verity exchanges your item for a supply loot sack.")
        return result.success
    }
    fun open(player: Player, tier: HunterSackTier): Boolean {
        if (player.inv.count(tier.obj) <= 0) return false
        val key = PENDING.getValue(tier)
        val stored = player.attr[key]
        val rolled = if (stored == null) roll(player, tier).also { rewards ->
            player.attr[key] = rewards.joinToString(";") { "${it.obj},${it.count},${it.noted}" }
        } else stored.split(';').map { entry ->
            val fields = entry.split(',')
            HunterSackReward(fields[0], fields[1].toInt(), fields[2].toBooleanStrict())
        }
        // Recheck lifetime ownership before delivery; it may change while this reward waits for space.
        val rewards = rolled.mapNotNull { reward -> when {
            reward.obj == KIT && owns(player, KIT) -> null
            reward.obj == PET && hasPet(player) -> null
            reward.obj == TORN_ENHANCED && enhancedSeen(player) -> null
            reward.obj == TORN_PERFECTED && perfectedSeen(player) -> null
            reward.obj in OUTFIT && owns(player, reward.obj) -> reward.copy(obj = OUTFIT.firstOrNull { !owns(player, it) } ?: reward.obj)
            else -> reward
        } }
        val result = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete(inventory, tier.obj.asRSCM(), 1)
            for (reward in rewards) add(inventory, reward.obj.asRSCM(), reward.count, cert = reward.noted)
        }
        if (!result.success) {
            player.mes("You need more inventory space to collect the contents of this sack.")
            return false
        }
        player.attr.remove(key)
        for (reward in rewards) {
            if (reward.obj == ENHANCED || reward.obj == TORN_ENHANCED) player.enhancedBlueprintSeen = true
            if (reward.obj == PERFECTED || reward.obj == TORN_PERFECTED) player.perfectedBlueprintSeen = true
            CollectionLog.grant(player, reward.obj, reward.count)
            val name = checkNotNull(ServerCacheManager.getItem(reward.obj.asRSCM())).name
            player.mes("Your reward is: ${reward.count} x $name.")
        }
        if (rewards.any { it.obj == PET }) player.mes("You feel something weird sneaking into your backpack.")
        return true
    }

    internal fun roll(player: Player, tier: HunterSackTier): List<HunterSackReward> = buildList {
        repeat(tier.rolls) { add(HunterSackLoot.rollResource(tier, player.statBase("stat.woodcutting"), player.statBase("stat.herblore"), random)) }
        if (tier != HunterSackTier.BASIC) {
            val completed = HunterRumours.completed(player)
            if (completed >= 100 && !enhancedSeen(player)) add(HunterSackReward(TORN_ENHANCED))
            if (completed >= 250 && !perfectedSeen(player)) add(HunterSackReward(TORN_PERFECTED))
        }
        if (!tier.uniques) return@buildList
        if (random.of(50) == 0) add(HunterSackReward(ENHANCED))
        if (tier.petDenominator != null && (enhancedSeen(player) || any { it.obj == ENHANCED || it.obj == TORN_ENHANCED }) && random.of(50) == 0) add(HunterSackReward(PERFECTED))
        if (!owns(player, KIT) && random.of(50) == 0) add(HunterSackReward(KIT))
        if (random.of(50) == 0) add(HunterSackReward(OUTFIT.firstOrNull { !owns(player, it) } ?: OUTFIT[random.of(OUTFIT.size)]))
        val petChance = tier.petDenominator
        val petRolls = if (player.statMap.getFineXP("stat.hunter") >= 2_000_000_000) 15 else 1
        if (petChance != null && !hasPet(player) && random.of(petChance) < petRolls) add(HunterSackReward(PET))
    }

    companion object {
        const val KIT = "obj.huntsmans_kit"
        const val PET = "obj.quetzalpet"
        const val ENHANCED = "obj.hg_whistle_blueprint_2"
        const val PERFECTED = "obj.hg_whistle_blueprint_3"
        const val TORN_ENHANCED = "obj.hg_whistle_blueprint_2_torn"
        const val TORN_PERFECTED = "obj.hg_whistle_blueprint_3_torn"
        val OUTFIT = listOf("obj.hg_hunter_hood", "obj.hg_hunter_top", "obj.hg_hunter_legs", "obj.hg_hunter_boots")
        val EXCHANGEABLE = OUTFIT + listOf(KIT, ENHANCED, PERFECTED)
        private val PENDING = HunterSackTier.entries.associateWith { AttributeKey<String>(persistenceKey = "hunter_sack_pending_${it.name.lowercase()}") }

        fun owns(player: Player, obj: String): Boolean {
            // Materialize the lazy inventory before iterating the map it can mutate.
            val collection = player.collectionTransmit
            return (sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence().filter { it !== collection }).any { it.count(obj) > 0 }
        }

        private fun ownedCount(player: Player, obj: String): Int {
            val collection = player.collectionTransmit
            return (sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence().filter { it !== collection })
                .distinct().sumOf { it.count(obj) }
        }

        fun enhancedSeen(player: Player): Boolean = player.enhancedBlueprintSeen ||
            listOf(ENHANCED, TORN_ENHANCED, "obj.hg_quetzalwhistle_enhanced", "obj.hg_quetzalwhistle_perfected", "obj.hg_quetzalwhistle_perfected_infinite").any { owns(player, it) }
        fun perfectedSeen(player: Player): Boolean = player.perfectedBlueprintSeen ||
            listOf(PERFECTED, TORN_PERFECTED, "obj.hg_quetzalwhistle_perfected", "obj.hg_quetzalwhistle_perfected_infinite").any { owns(player, it) }

        private fun hasPet(player: Player): Boolean = owns(player, PET) || player.collectionTransmit.count(PET) > 0
    }
}
