package org.rsmod.content.skills.magic.utility

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.NonInterruptingComponents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.farming.scripts.scheduleWateredSeedlings
import org.rsmod.content.skills.magic.utility.configs.UtilityMethod
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRule
import org.rsmod.content.skills.magic.utility.configs.UtilitySpellRules
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class InventoryUtilitySpellScript @Inject constructor(
    private val events: EventBus,
    private val launcher: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    private var Player.offeringReady by intVarp("varp.magic_offering_ready")
    private var Player.tanHardLeather by boolVarp("varp.magic_tan_hard")

    override fun ScriptContext.startup() {
        for (rule in UtilitySpellRules.all.filter { it.batch > 0 && it.obelisk == null }) {
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            if (rule.method in setOf(UtilityMethod.SinisterOffering, UtilityMethod.DemonicOffering)) {
                NonInterruptingComponents.register(RSCM.getReverseMapping(RSCMType.COMPONENT, spell.component.packed))
            }
            onIfOverlayButton(spell.component) {
                if (rule.method == UtilityMethod.TanLeather && it.op == IfButtonOp.Op2) {
                    player.tanHardLeather = !player.tanHardLeather
                    mes("Cowhides will now be tanned into ${if (player.tanHardLeather) "hard" else "soft"} leather.")
                } else {
                    cast(spell, rule, null, player.tanHardLeather)
                }
            }
            val key = EventBus.composeLongKey(spell.component.packed, "component.inventory:items".asRSCM())
            onEvent<IfOverlayButtonT>(key) { castOnItem(spell, rule) }
        }
        onPlayerQueueWithArgs<RepeatCast>(REPEAT_QUEUE) {
            val task = it.args
            val rule = UtilitySpellRules.all.single { row -> row.spell.id == task.spell }
            val spell = checkNotNull(spells.getObjSpell(rule.spell))
            cast(spell, rule, task.input, task.hardLeather, quiet = true)
        }
    }

    private fun IfOverlayButtonT.castOnItem(spell: MagicSpell, rule: UtilitySpellRule) {
        val target = targetObj ?: return
        if (targetSlot !in player.inv.indices || !player.inv[targetSlot].isType(target)) {
            resendSlot(player.inv, 0)
            return
        }
        if (player.isDelayed || player.isAccessProtected) return
        player.clearPendingAction(events)
        launcher.launch(player) { cast(spell, rule, target, false) }
    }

    internal fun ProtectedAccess.cast(
        spell: MagicSpell,
        rule: UtilitySpellRule,
        target: ItemServerType?,
        hardLeather: Boolean,
        quiet: Boolean = false,
    ) {
        val offering = rule.method in setOf(UtilityMethod.SinisterOffering, UtilityMethod.DemonicOffering)
        if ((!offering && actionDelay > mapClock) || (offering && player.offeringReady > mapClock)) return
        val recipes = UtilitySpellRules.conversions(rule.method, hardLeather)
            .filter { target == null || it.input.id == target.id }
            .associateBy { it.input.id }
        val candidates = inv.withIndex().mapNotNull { (slot, item) ->
            val recipe = item?.let { recipes[it.id] } ?: return@mapNotNull null
            slot to recipe
        }
        val selected = candidates.filter { (_, recipe) ->
            recipe.skill == null || player.stat(recipe.skill) >= recipe.level
        }.take(rule.batch)
        if (selected.isEmpty()) {
            if (!quiet) mes(if (candidates.isEmpty()) rule.missingMessage
                else "You don't have the required skill level to use this spell on those items.")
            return
        }
        val cost = selected.sumOf { it.second.coins }
        if (cost > 0 && inv.physicalCount("obj.coins") < cost) {
            if (!quiet) mes("You don't have enough coins to make that plank.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) return
        if (cost > 0) {
            check(player.invTransaction(inv) { delete(select(inv), "obj.coins".asRSCM(), cost) }.success)
        }
        for ((slot, recipe) in selected) {
            if (recipe.output == null) {
                check(invDel(inv, recipe.input.internalName, 1, slot = slot, ignoreVirtualStorage = true).success)
            } else {
                check(invReplaceSlot(inv, slot, 1, recipe.output).success)
            }
            if (recipe.skill != null && recipe.xp > 0.0) statAdvance(recipe.skill, recipe.xp)
        }
        val restored = selected.sumOf { it.second.prayerRestore }
        if (selected.any { it.second.seedling }) scheduleWateredSeedlings()
        if (restored > 0 && player.stat("stat.prayer") < player.statBase("stat.prayer")) {
            statHeal("stat.prayer", restored, 0)
        }
        anim(rule.sequence)
        spotanim(rule.spotanim, height = 0)
        rule.sound?.let { soundSynth(it) }
        statAdvance("stat.magic", spell.castXp)
        if (!offering) actionDelay = mapClock + rule.cooldown
        if (offering) player.offeringReady = mapClock + rule.cooldown
        publish(PlayerActionCompleteEvent(player, PlayerAction.CastUtility, spell.obj.internalName,
            count = selected.size))
        if (rule.repeatDelay > 0) {
            val input = target ?: selected.first().second.input
            weakQueue(REPEAT_QUEUE, rule.repeatDelay + 1, RepeatCast(rule.spell.id, input, hardLeather))
        }
    }

    private data class RepeatCast(val spell: Int, val input: ItemServerType, val hardLeather: Boolean)

    private companion object {
        const val REPEAT_QUEUE = "queue.utility_spell_repeat"
    }
}
