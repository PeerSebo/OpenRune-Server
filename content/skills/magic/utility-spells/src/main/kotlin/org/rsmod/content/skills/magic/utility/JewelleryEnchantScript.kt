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
import org.rsmod.api.config.Constants
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.skills.magic.utility.configs.EnchantJewelleryShape
import org.rsmod.content.skills.magic.utility.configs.EnchantTier
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO(interface): implement the enchanting chooser and automatic repeat casting.
class JewelleryEnchantScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in EnchantTier.entries) {
            val spell = spells.resolveStandardSpell(tier.spellObj)
            if (spell == null) {
                logger.warn { "Enchant spell not resolvable; not binding: ${tier.spellObj}" }
                continue
            }
            val key = EventBus.composeLongKey(spell.component.packed, InventoryItemsComponent)
            onEvent<IfOverlayButtonT>(key) { opEnchant(spell, tier) }
        }
        onPlayerQueueWithArgs<PendingEnchant>(EnchantQueue) { processQueuedEnchant(it.args) }
    }

    private fun IfOverlayButtonT.opEnchant(spell: MagicSpell, tier: EnchantTier) {
        val targetObj = targetObj ?: return resendSlot(player.inv, 0)
        player.opEnchant(targetSlot, targetObj, spell, tier)
    }

    private fun Player.opEnchant(
        targetSlot: Int,
        targetObj: ItemServerType,
        spell: MagicSpell,
        tier: EnchantTier,
    ) {
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
        protectedAccess.launch(this) { castEnchant(targetSlot, targetObj, spell, tier) }
    }

    private fun ProtectedAccess.castEnchant(
        targetSlot: Int,
        targetObj: ItemServerType,
        spell: MagicSpell,
        tier: EnchantTier,
    ) {
        if (actionDelay > mapClock) {
            queueEnchant(targetSlot, targetObj, tier)
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
        val enchantRow = tier.findRow(targetInternal)
        if (enchantRow == null) {
            mes(Constants.dm_default)
            return
        }

        val product = ServerCacheManager.getItem(enchantRow.product.asRSCM(RSCMType.OBJ))
        if (product == null) {
            logger.warn { "Enchant product missing from cache: ${enchantRow.product}" }
            mes(Constants.dm_default)
            return
        }

        val result = runes.attemptCast(player, spell)
        if (result.isFailure()) {
            return
        }

        if (invReplaceSlot(inv, targetSlot, count = 1, replacement = product).failure) {
            logger.warn {
                "Enchant replacement failed after rune consumption: " +
                    "player=$player, target=$targetInternal, slot=$targetSlot"
            }
            resendSlot(inv, 0)
            return
        }

        actionDelay = mapClock + EnchantCastDelay

        val ring = enchantRow.shape == EnchantJewelleryShape.Ring
        anim(if (ring) RingSeq else tier.amuletSeq)
        spotanim(
            if (ring) RingSpotanim else tier.amuletSpotanim,
            height = EnchantSpotanimHeight,
        )
        soundSynth(if (ring) tier.ringSound else tier.amuletSound)
        runClientScript(ToplevelSidebuttonSwitch, SpellbookSideTab)
        statAdvance("stat.magic", spell.castXp)
        publish(PlayerActionCompleteEvent(player, PlayerAction.EnchantItem, enchantRow.product))
    }

    // Replace the queued follow-up; rapid clicks must not stack casts.
    private fun ProtectedAccess.queueEnchant(
        targetSlot: Int,
        targetObj: ItemServerType,
        tier: EnchantTier,
    ) {
        val delay = (actionDelay - mapClock).coerceAtLeast(1)
        clearQueue(EnchantQueue)
        queue(EnchantQueue, delay, PendingEnchant(tier, targetSlot, targetObj.id))
    }

    private fun ProtectedAccess.processQueuedEnchant(task: PendingEnchant) {
        val spell = spells.resolveStandardSpell(task.tier.spellObj) ?: return
        val targetObj = ServerCacheManager.getItem(task.targetObj) ?: return resendSlot(inv, 0)
        castEnchant(task.targetSlot, targetObj, spell, task.tier)
    }

    private data class PendingEnchant(
        val tier: EnchantTier,
        val targetSlot: Int,
        val targetObj: Int,
    )

    private companion object {
        val logger = InlineLogger()
        val InventoryItemsComponent = "component.inventory:items".asRSCM(RSCMType.COMPONENT)
        const val EnchantQueue = "queue.jewellery_enchant"

        const val RingSeq = "seq.human_cast_enchantring"

        const val RingSpotanim = "spotanim.enchant_ring"

        const val EnchantSpotanimHeight = 92

        const val EnchantCastDelay = 3

        const val ToplevelSidebuttonSwitch = 915
        const val SpellbookSideTab = 6
    }
}
