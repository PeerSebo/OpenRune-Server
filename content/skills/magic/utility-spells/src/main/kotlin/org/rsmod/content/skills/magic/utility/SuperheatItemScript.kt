package org.rsmod.content.skills.magic.utility

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.baseCookingLvl
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.smelting.SmithingSmeltXp
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.content.skills.smithing.util.SmithingBonuses
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SuperheatItemScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private val bars by lazy {
        SmithingBar.all().filter { it.output.internalName != ElementalBar }
    }

    override fun ScriptContext.startup() {
        val spell = spells.resolveStandardSpell(SpellObj)
        if (spell == null) {
            logger.warn { "Superheat Item spell not resolvable; not binding." }
            return
        }
        val key = EventBus.composeLongKey(spell.component.packed, InventoryItemsComponent)
        onEvent<IfOverlayButtonT>(key) { opSuperheat(spell) }
        onPlayerQueueWithArgs<PendingSuperheat>(SuperheatQueue) { processQueuedSuperheat(it.args) }
    }

    private fun IfOverlayButtonT.opSuperheat(spell: MagicSpell) {
        val targetObj = targetObj ?: return resendSlot(player.inv, 0)
        player.opSuperheat(targetSlot, targetObj, spell)
    }

    private fun Player.opSuperheat(targetSlot: Int, targetObj: ItemServerType, spell: MagicSpell) {
        if (isDelayed) {
            resendSlot(inv, 0)
            return
        }
        clearPendingAction(eventBus)
        resetFaceEntity()
        if (isAccessProtected) {
            resendSlot(inv, 0)
            return
        }
        protectedAccess.launch(this) { castSuperheat(targetSlot, targetObj, spell) }
    }

    private suspend fun ProtectedAccess.castSuperheat(
        targetSlot: Int,
        targetObj: ItemServerType,
        spell: MagicSpell,
    ) {
        if (actionDelay > mapClock) {
            queueSuperheat(targetSlot, targetObj)
            return
        }

        if (targetSlot !in inv.indices) {
            resendSlot(inv, 0)
            return
        }

        val invObj = inv[targetSlot]
        if (!invObj.isType(targetObj)) {
            resendSlot(inv, 0)
            return
        }

        val targetInternal = RSCM.getReverseMapping(RSCMType.OBJ, targetObj.id)

        if (targetInternal == JogreBones) {
            castSuperheatJogreBones(targetSlot, spell)
            return
        }

        if (targetInternal == ElementalOre) {
            failCast("Even this spell is not hot enough to heat this item.")
            return
        }

        val bar = resolveBar(targetInternal)
        if (bar == null) {
            failCast("You need to cast superheat item on ore.")
            return
        }

        if (!SmithingUtils.requireSmithingLevel(this, bar.levelReq, "superheat that ore")) {
            return
        }

        val primary = bar.input.first()
        val primaryAmt = bar.inputAmount.first()
        val secondary = bar.input.getOrNull(1)
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0
        val secondaryAmt = if (requiresSecondary) {
            SmithingBonuses.effectiveCoalAmount(inv, bar)
        } else {
            0
        }

        if (inv.count(primary.internalName) < primaryAmt) {
            missingMaterialsMessage(bar, primaryAmt, secondaryAmt)
            return
        }
        if (requiresSecondary && inv.count(secondary.internalName) < secondaryAmt) {
            missingMaterialsMessage(bar, primaryAmt, secondaryAmt)
            return
        }

        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }

        val catalystBacked = SmithingBonuses.hasCatalystBoost(inv, bar)
        val inputs = buildList {
            add(primary.internalName to primaryAmt)
            if (requiresSecondary && secondaryAmt > 0) {
                add(secondary.internalName to secondaryAmt)
            }
        }
        if (!produceBar(inputs, bar.output.internalName)) {
            logger.warn {
                "Superheat produce failed after rune consumption: " +
                    "player=$player, target=$targetInternal, bar=${bar.output.internalName}"
            }
            resendSlot(inv, 0)
            return
        }

        if (catalystBacked) {
            invDel(inv, SmithingBonuses.SMITHING_CATALYST, 1)
        }

        val xpPerBar = SmithingSmeltXp.resolve(player, bar, isSuperHeat = true, xpMods)
        val smithXp = if (catalystBacked) xpPerBar * 2 else xpPerBar
        statAdvance("stat.smithing", smithXp)

        playCast(spell)
    }

    private fun ProtectedAccess.castSuperheatJogreBones(targetSlot: Int, spell: MagicSpell) {
        if (player.baseCookingLvl < JogreBonesCookingLevel) {
            mes("You need a Cooking level of $JogreBonesCookingLevel to burn the bones.")
            return
        }

        val burnt = ServerCacheManager.getItem(BurntJogreBones.asRSCM(RSCMType.OBJ))
        if (burnt == null) {
            logger.warn { "Burnt jogre bones missing from cache; aborting superheat." }
            return
        }

        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }

        if (invReplaceSlot(inv, targetSlot, count = 1, replacement = burnt).failure) {
            resendSlot(inv, 0)
            return
        }

        statAdvance("stat.cooking", JogreBonesCookingXp)
        playCast(spell)
    }

    private fun ProtectedAccess.playCast(spell: MagicSpell) {
        actionDelay = mapClock + SuperheatCastDelay
        anim(CastSeq)
        spotanim(CastSpotanim, height = CastSpotanimHeight)
        soundSynth(CastSound)
        runClientScript(ToplevelSidebuttonSwitch, SpellbookSideTab)
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.failCast(message: String) {
        mes(message)
        anim(CastSeq)
        spotanim(SplashSpotanim, height = CastSpotanimHeight)
        soundSynth(FailSound)
    }

    private suspend fun ProtectedAccess.missingMaterialsMessage(
        bar: SmithingBar,
        primaryAmt: Int,
        secondaryAmt: Int,
    ) {
        val primaryName = SmithingUtils.itemName(bar.input.first(), "ore")
        val secondary = bar.input.getOrNull(1)
        if (secondary == null || (bar.inputAmount.getOrNull(1) ?: 0) <= 0) {
            mesbox(
                "You don't have ${SmithingUtils.countLiteral(primaryAmt)} $primaryName to smelt."
            )
            return
        }
        val secondaryName = SmithingUtils.itemName(secondary, "materials")
        val barName = SmithingUtils.itemName(bar.output, "bar")
        mesbox(
            "You need ${SmithingUtils.countLiteral(primaryAmt)} $primaryName and " +
                "${SmithingUtils.countLiteral(secondaryAmt)} $secondaryName to make " +
                "${SmithingUtils.prefixAn(barName)}."
        )
    }

    private fun ProtectedAccess.resolveBar(targetInternal: String): SmithingBar? {
        if (targetInternal == Coal) {
            return null
        }
        val candidates = bars.filter { bar -> bar.input.any { it.internalName == targetInternal } }
        if (candidates.isEmpty()) {
            return null
        }

        if (targetInternal == IronOre) {
            val steel = bars.firstOrNull { it.output.internalName == SteelBar }
            if (steel != null && player.smithingLvl >= steel.levelReq) {
                val coalNeeded = SmithingBonuses.effectiveCoalAmount(inv, steel)
                if (inv.count(Coal) >= coalNeeded) {
                    return steel
                }
            }
            return candidates.firstOrNull { it.output.internalName == IronBar }
                ?: candidates.first()
        }

        return candidates.firstOrNull { hasAllInputs(it) } ?: candidates.first()
    }

    private fun ProtectedAccess.hasAllInputs(bar: SmithingBar): Boolean {
        val secondaryAmt = SmithingBonuses.effectiveCoalAmount(inv, bar)
        return bar.input.withIndex().all { (index, input) ->
            val amount = if (index == 0) bar.inputAmount.first() else secondaryAmt
            inv.count(input.internalName) >= amount
        }
    }

    private fun ProtectedAccess.produceBar(inputs: List<Pair<String, Int>>, output: String): Boolean {
        val removed = mutableListOf<Pair<String, Int>>()
        for ((item, amount) in inputs) {
            if (amount <= 0) {
                continue
            }
            if (invDel(inv, item, amount).success) {
                removed += item to amount
            } else {
                removed.forEach { (name, count) -> invAdd(inv, name, count) }
                return false
            }
        }
        if (invAdd(inv, output).failure) {
            removed.forEach { (name, count) -> invAdd(inv, name, count) }
            return false
        }
        return true
    }

    // Replace the queued follow-up; rapid clicks must not stack casts.
    private fun ProtectedAccess.queueSuperheat(targetSlot: Int, targetObj: ItemServerType) {
        val delay = (actionDelay - mapClock).coerceAtLeast(1)
        clearQueue(SuperheatQueue)
        queue(SuperheatQueue, delay, PendingSuperheat(targetSlot, targetObj.id))
    }

    private suspend fun ProtectedAccess.processQueuedSuperheat(task: PendingSuperheat) {
        val spell = spells.resolveStandardSpell(SpellObj) ?: return
        val targetObj = ServerCacheManager.getItem(task.targetObj) ?: return resendSlot(inv, 0)
        castSuperheat(task.targetSlot, targetObj, spell)
    }

    private data class PendingSuperheat(val targetSlot: Int, val targetObj: Int)

    private companion object {
        val logger = InlineLogger()
        val InventoryItemsComponent = "component.inventory:items".asRSCM(RSCMType.COMPONENT)

        const val SpellObj = "obj.43_superheat_item"

        const val SuperheatQueue = "queue.superheat"

        const val Coal = "obj.coal"
        const val IronOre = "obj.iron_ore"
        const val IronBar = "obj.iron_bar"
        const val SteelBar = "obj.steel_bar"
        const val ElementalOre = "obj.elemental_workshop_ore"
        const val ElementalBar = "obj.elemental_workshop_bar"

        const val JogreBones = "obj.tbwt_jogre_bones"
        const val BurntJogreBones = "obj.tbwt_burnt_jogre_bones"
        const val JogreBonesCookingLevel = 30
        const val JogreBonesCookingXp = 25.0

        const val CastSeq = "seq.human_castsuperheatitem"

        const val CastSpotanim = "spotanim.superheatitem_casting"

        const val SplashSpotanim = "spotanim.failedspell_impact"

        const val CastSpotanimHeight = 0

        const val CastSound = "synth.SUPERHEAT_ALL"

        const val FailSound = "synth.SUPERHEAT_FAIL"

        const val SuperheatCastDelay = 3

        const val ToplevelSidebuttonSwitch = 915
        const val SpellbookSideTab = 6
    }
}
