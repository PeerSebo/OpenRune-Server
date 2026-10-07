package org.rsmod.api.player.worn

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.aliases.ParamInt
import org.rsmod.api.config.aliases.ParamStat
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.invtx.swap
import org.rsmod.api.invtx.transfer
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.utils.format.addArticle
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.stat.StatRequirement
import org.rsmod.game.type.getInvObj
import org.rsmod.objtx.TransactionResult
import org.rsmod.objtx.isErr

public class HeldEquipOp
@Inject
constructor(private val eventBus: EventBus, private val restrictions: PlayerRestrictions) {
    public fun equip(player: Player, invSlot: Int, inventory: Inventory): HeldEquipResult {
        val obj = inventory[invSlot] ?: return HeldEquipResult.Fail.InvalidObj
        val objType = getInvObj(obj)

        val restriction = restrictions.check(player, RestrictedAction.Equip(objType))
        if (restriction != null) {
            return HeldEquipResult.Fail.Restricted(restriction)
        }

        val result = equip(player, objType)

        if (result is HeldEquipResult.Success) {
            val (unequipWearpos, primaryWearpos) = result
            val allWearpos = unequipWearpos + primaryWearpos
            val into = player.worn

            val unequipObjs =
                allWearpos.associateWith { pos ->
                    into[pos.slot]?.let { id ->
                        ServerCacheManager.getItems().values.firstOrNull { it.id == id.id }
                    }
                }
            val unequipPrimary = into[primaryWearpos.slot] != null

            val transaction =
                player.invTransaction(inventory, into) {
                    val inv = select(inventory)
                    val worn = select(into)
                    swap(
                        from = inv,
                        fromSlot = invSlot,
                        intoSlot = primaryWearpos.slot,
                        into = worn,
                        transform = true,
                        mergeStacks = true,
                    )
                    for (unequip in unequipWearpos) {
                        val wornObj = into[unequip.slot] ?: continue
                        transfer(
                            from = worn,
                            fromSlot = unequip.slot,
                            count = wornObj.count,
                            into = inv,
                            intoSlot = if (unequipPrimary) null else invSlot,
                            untransform = true,
                        )
                    }
                }

            val equipTransaction = transaction[0]
            if (equipTransaction.isErr()) {
                check(equipTransaction is TransactionResult.NotEnoughSpace) {
                    "Transaction error is expected to only be of " +
                        "`NotEnoughSpace` type: found=$equipTransaction"
                }
                val message = "You don't have enough free space to do that."
                return HeldEquipResult.Fail.NotEnoughWornSpace(message)
            }

            val unequipTransactionErr = transaction.err
            if (unequipTransactionErr != null) {
                check(unequipTransactionErr is TransactionResult.NotEnoughSpace) {
                    "Transaction error is expected to only be of " +
                        "`NotEnoughSpace` type: found=$unequipTransactionErr"
                }
                val message = "You don't have enough free inventory space to do that."
                return HeldEquipResult.Fail.NotEnoughInvSpace(message)
            }

            for (wearpos in allWearpos) {
                val wornType = if (wearpos == primaryWearpos) objType else unequipObjs[wearpos]
                if (wornType != null) {
                    val change = HeldEquipEvents.WearposChange(player, wearpos, wornType)
                    eventBus.publish(change)
                }
            }

            for ((wearpos, type) in unequipObjs) {
                val unequipType = type ?: continue
                val unequip = HeldEquipEvents.Unequip(player, wearpos, unequipType)
                eventBus.publish(unequip)
            }

            val equip = HeldEquipEvents.Equip(player, invSlot, primaryWearpos, objType)
            eventBus.publish(equip)

            player.rebuildAppearance()
        }

        return result
    }

    private fun equip(player: Player, type: ItemServerType): HeldEquipResult {
        val statRequirements =
            type.statRequirements().filter {
                player.statBase(RSCM.getReverseMapping(RSCMType.STAT, it.stat.id)) < it.level
            }
        if (statRequirements.isNotEmpty()) {
            val (header, detail) = type.toMessages(statRequirements)
            val messages = listOf(header, detail).filter(String::isNotBlank)
            return HeldEquipResult.Fail.StatRequirements(messages)
        }

        val wearpos1 = Wearpos[type.wearpos1] ?: return HeldEquipResult.Fail.InvalidObj
        val wearpos2 = Wearpos[type.wearpos2]?.takeUnless { it.isClientOnly }
        val wearpos3 = Wearpos[type.wearpos3]?.takeUnless { it.isClientOnly }

        val unequipWearpos = mutableListOf<Wearpos>()
        wearpos2?.let(unequipWearpos::add)
        wearpos3?.let(unequipWearpos::add)
        if (wearpos1 == Wearpos.LeftHand) {
            val twoHanded = player.righthand?.takeIf { getInvObj(it).isTwoHanded() }
            if (twoHanded != null && Wearpos.RightHand !in unequipWearpos) {
                unequipWearpos += Wearpos.RightHand
            }
        }

        return HeldEquipResult.Success(unequipWearpos, wearpos1)
    }

    private fun ItemServerType.statRequirements(): List<StatRequirement> {
        var requirements: MutableList<StatRequirement>? = null
        for ((skillParam, levelParam) in STAT_REQUIREMENT_PARAMS) {
            val stat = paramOrNull(skillParam) ?: continue
            val level = paramOrNull(levelParam) ?: 0
            val list = requirements ?: ArrayList<StatRequirement>(2).also { requirements = it }
            list += StatRequirement(stat, level)
        }
        return requirements ?: emptyList()
    }

    private fun ItemServerType.toMessages(reqs: List<StatRequirement>): Pair<String, String> {
        val message1 = param(BaseParams.statreq_failmessage1)
        val message2 = paramOrNull(BaseParams.statreq_failmessage2)
        val replace =
            when {
                reqs.isEmpty() -> ""
                message2 != null -> message2.replacePlaceholders(reqs)
                reqs.size == 1 -> DEFAULT_STAT_MESSAGE1.replacePlaceholders(reqs)
                reqs.size == 2 -> DEFAULT_STAT_MESSAGE2.replacePlaceholders(reqs)
                else -> "You need to have ${reqs.joinToPhrase()}."
            }
        return message1 to replace
    }

    private fun String.replacePlaceholders(reqs: List<StatRequirement>): String {
        var text = this
        for ((index, req) in reqs.withIndex()) {
            val ordinal = index + 1
            text =
                text
                    .replace("{skill$ordinal}", req.statPhrase())
                    .replace("{level$ordinal}", req.level.toString())
        }
        return text
    }

    private fun StatRequirement.statPhrase(): String =
        stat.displayName.replaceFirstChar(Char::uppercaseChar).addArticle()

    private fun List<StatRequirement>.joinToPhrase(): String {
        val fragments = map { "${it.statPhrase()} level of ${it.level}" }
        if (fragments.size == 1) {
            return fragments[0]
        }
        return fragments.dropLast(1).joinToString(", ") + " and " + fragments.last()
    }

    private fun ItemServerType.isTwoHanded(): Boolean =
        wearpos2 == Wearpos.LeftHand.slot || wearpos3 == Wearpos.LeftHand.slot

    private companion object {
        private const val DEFAULT_STAT_MESSAGE1 = "You need to have {skill1} level of {level1}."

        private const val DEFAULT_STAT_MESSAGE2 =
            "You need to have {skill1} level of {level1} and {skill2} level of {level2}."

        /**
         * Check all seven statreq pairs in tier order; requirements beyond tier two are sparse
         * but still binding.
         */
        private val STAT_REQUIREMENT_PARAMS: List<Pair<ParamStat, ParamInt>> by lazy {
            listOf(
                BaseParams.statreq1_skill to BaseParams.statreq1_level,
                BaseParams.statreq2_skill to BaseParams.statreq2_level,
                BaseParams.statreq3_skill to BaseParams.statreq3_level,
                BaseParams.statreq4_skill to BaseParams.statreq4_level,
                BaseParams.statreq5_skill to BaseParams.statreq5_level,
                BaseParams.statreq6_skill to BaseParams.statreq6_level,
                BaseParams.statreq7_skill to BaseParams.statreq7_level,
            )
        }
    }
}
