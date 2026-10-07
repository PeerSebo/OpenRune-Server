package org.rsmod.content.skills.fishing.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.fishing.table.FishingCatch
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj

object FishingEquipment {

    private const val SONG_OF_THE_ELVES = "quest_songoftheelves"

    val ItemServerType.harpoonLevelReq: Int by objParam(params.levelrequire)
    val ItemServerType.harpoonAnim: SequenceServerType by objParam(params.skill_anim)

    enum class HarpoonTier {
        Standard,
        Dragon,
        Crystal,
    }

    fun harpoonTier(type: ItemServerType): HarpoonTier =
        when (type.internalName) {
            "obj.crystal_harpoon" -> HarpoonTier.Crystal
            "obj.dragon_harpoon",
            "obj.infernal_harpoon",
            "obj.infernal_harpoon_empty",
            "obj.crystal_harpoon_inactive",
            "obj.trailblazer_harpoon",
            "obj.trailblazer_harpoon_empty",
            "obj.trailblazer_harpoon_no_infernal",
            "obj.trailblazer_reloaded_harpoon",
            "obj.trailblazer_reloaded_harpoon_empty",
            "obj.trailblazer_reloaded_harpoon_no_infernal" -> HarpoonTier.Dragon
            else -> HarpoonTier.Standard
        }

    fun harpoonRates(catch: FishingCatch, tier: HarpoonTier): Pair<Int, Int> =
        when (tier) {
            HarpoonTier.Crystal ->
                (catch.crystalLow ?: catch.low) to (catch.crystalHigh ?: catch.high)
            HarpoonTier.Dragon -> (catch.dragonLow ?: catch.low) to (catch.dragonHigh ?: catch.high)
            HarpoonTier.Standard -> catch.low to catch.high
        }

    fun findHarpoon(player: Player): InvObj? {
        val worn = player.wornHarpoon()
        val carried = player.carriedHarpoons()
        val candidates = buildList {
            if (worn != null) {
                add(worn)
            }
            addAll(carried)
        }
        return candidates.maxWithOrNull(compareBy { harpoonTier(getInvObj(it)).ordinal })
    }

    fun isWielding(player: Player, harpoon: InvObj): Boolean = player.righthand === harpoon

    fun harpoonAnim(player: Player, harpoon: InvObj): String {
        if (!isWielding(player, harpoon)) {
            return FishingParams.HARPOON_ANIM_CARRIED
        }
        when (getInvObj(harpoon).internalName) {
            "obj.trailblazer_harpoon", "obj.trailblazer_harpoon_empty" -> return "seq.human_harpoon_trailblazer"
            "obj.trailblazer_harpoon_no_infernal" -> return "seq.human_harpoon_trailblazer_no_infernal"
            "obj.trailblazer_reloaded_harpoon", "obj.trailblazer_reloaded_harpoon_empty" -> return "seq.human_harpoon_trailblazer_reloaded"
            "obj.trailblazer_reloaded_harpoon_no_infernal" -> return "seq.human_harpoon_trailblazer_reloaded_no_infernal"
        }
        return RSCM.getReverseMapping(RSCMType.SEQ, getInvObj(harpoon).harpoonAnim.id)
    }

    private fun Player.wornHarpoon(): InvObj? {
        val righthand = righthand ?: return null
        return righthand.takeIf { getInvObj(it).isUsableHarpoon(this) }
    }

    private fun Player.carriedHarpoons(): List<InvObj> =
        inv.filterNotNull { getInvObj(it).isUsableHarpoon(this) }

    private fun ItemServerType.isUsableHarpoon(player: Player): Boolean {
        // Reloaded shares infernal/dragon behavior but lacks the older style's server params.
        val reloaded = internalName in setOf("obj.trailblazer_reloaded_harpoon", "obj.trailblazer_reloaded_harpoon_empty", "obj.trailblazer_reloaded_harpoon_no_infernal")
        if (reloaded) return player.fishingLvl >= if (internalName.endsWith("no_infernal")) 61 else 75
        if (!isContentType(FishingParams.HARPOON_CONTENT) || player.fishingLvl < harpoonLevelReq) {
            return false
        }
        return when (internalName) {
            "obj.crystal_harpoon",
            "obj.crystal_harpoon_inactive" ->
                QuestRequirements.hasCompleted(player, SONG_OF_THE_ELVES)
            else -> true
        }
    }

    data class PearlRodVariant(val item: String, val anim: String)

    val PEARL_ROD_VARIANTS: Map<String, PearlRodVariant> =
        mapOf(
            FishingParams.FISHING_ROD to
                PearlRodVariant("obj.fishingrod_pearl", "seq.human_fishing_casting_pearl"),
            FishingParams.FLY_FISHING_ROD to
                PearlRodVariant("obj.fishingrod_pearl_fly", "seq.human_fishing_casting_pearl_fly"),
            FishingParams.BARBARIAN_ROD to
                PearlRodVariant(
                    "obj.fishingrod_pearl_brut",
                    "seq.human_fishing_casting_pearl_brut",
                ),
            FishingParams.OILY_ROD to
                PearlRodVariant(
                    "obj.fishingrod_pearl_oily",
                    "seq.human_fishing_casting_pearl_oily",
                ),
        )
}
