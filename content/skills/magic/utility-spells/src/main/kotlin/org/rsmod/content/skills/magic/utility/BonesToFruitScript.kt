package org.rsmod.content.skills.magic.utility

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO(MTA): support animal bones yielding 1-4 fruit. TODO(ToA): enforce the fruit-spell restriction.
class BonesToFruitScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (fruit in BonesToFruit.entries) {
            val spell = spells.resolveStandardSpell(fruit.spellObj)
            if (spell == null) {
                logger.warn { "Bones spell not resolvable; not binding: ${fruit.spellObj}" }
                continue
            }
            onIfOverlayButton(spell.component) { castBonesToFruit(spell, fruit) }
        }
    }

    private fun ProtectedAccess.castBonesToFruit(spell: MagicSpell, fruit: BonesToFruit) {
        val convertible = inv.withIndex().filter { (_, obj) ->
            obj != null && obj.id in convertibleBoneIds
        }

        if (convertible.isEmpty()) {
            val holdingHigherBones = inv.any { obj ->
                obj != null && obj.id in buryableBoneIds && obj.id !in convertibleBoneIds
            }
            val message = if (holdingHigherBones && fruit == BonesToFruit.Peaches) {
                "The bones you're holding aren't suitable for peachifying."
            } else {
                "You aren't holding any bones!"
            }
            mes(message)
            return
        }

        // Reject missing output data before attemptCast consumes runes.
        val product = ServerCacheManager.getItem(fruit.product.asRSCM(RSCMType.OBJ))
        if (product == null) {
            logger.warn { "Bones-to-fruit product missing from cache: ${fruit.product}" }
            return
        }

        // TODO(MTA): require the Bones to Peaches unlock.
        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }

        var converted = 0
        for ((slot, _) in convertible) {
            if (invReplaceSlot(inv, slot, count = 1, replacement = product).failure) {
                logger.warn { "Bones-to-fruit replacement failed: player=$player, slot=$slot" }
            } else {
                converted++
            }
        }

        anim(CastSeq)
        spotanim(CastSpotanim, height = CastSpotanimHeight)
        soundSynth(CastSound)
        statAdvance("stat.magic", spell.castXp)
        if (converted > 0) {
            publish(PlayerActionCompleteEvent(player, PlayerAction.CastUtility,
                fruit.spellObj, count = converted))
        }
    }

    private val convertibleBoneIds: Set<Int> by lazy {
        setOf(
                "obj.bones",
                "obj.bones_burnt",
                "obj.bat_bones",
                "obj.shade_bleached_bones",
                "obj.tbwt_jogre_bones",
                "obj.wolf_bones",
                "obj.mm_normal_monkey_bones",
                "obj.mm_small_ninja_monkey_bones",
                "obj.mm_medium_ninja_monkey_bones",
                "obj.mm_normal_gorilla_monkey_bones",
                "obj.mm_bearded_gorilla_monkey_bones",
                "obj.mm_small_zombie_monkey_bones",
                "obj.mm_large_zombie_monkey_bones",
                "obj.alan_bones",
                "obj.big_bones",
            )
            .map { it.asRSCM(RSCMType.OBJ) }
            .toSet()
    }

    private val buryableBoneIds: Set<Int> by lazy {
        SkillPrayerRow.all().filterNot { it.ashes }.map { it.item.id }.toSet()
    }

    private enum class BonesToFruit(val spellObj: String, val product: String) {
        Bananas("obj.15_bones_bananas", "obj.banana"),

        Peaches("obj.60_bones_peaches", "obj.peach"),
    }

    private companion object {
        val logger = InlineLogger()

        const val CastSeq = "seq.human_castbonestobananas"

        const val CastSpotanim = "spotanim.bonestobananas_casting"

        const val CastSpotanimHeight = 92

        const val CastSound = "synth.BONES_TO_BANANAS_ALL"
    }
}
