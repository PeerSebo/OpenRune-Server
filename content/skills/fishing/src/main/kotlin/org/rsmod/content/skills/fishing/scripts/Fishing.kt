package org.rsmod.content.skills.fishing.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.events.skilling.SkillingProduct
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.skilling.SkillingAwardResult
import org.rsmod.api.player.skilling.awardSkillingProduct
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpContentNpc1
import org.rsmod.api.script.onOpContentNpc3
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.cooking.CookingFoodsRow
import org.rsmod.content.skills.fishing.FishingHarpoonCharges
import org.rsmod.content.skills.fishing.configs.FishingEquipment
import org.rsmod.content.skills.fishing.configs.FishingEquipment.HarpoonTier
import org.rsmod.content.skills.fishing.configs.FishingParams
import org.rsmod.content.skills.fishing.configs.FishingStyle
import org.rsmod.content.skills.fishing.table.FishingCatch
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Fishing
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {

    private val cookingXpByRaw: Map<String, Int> by lazy {
        CookingFoodsRow.all().associate { it.input.internalName to it.xp }
    }

    override fun ScriptContext.startup() {
        onOpContentNpc1(FishingParams.SPOT_SMALLNET) { attempt(it.npc, FishingStyle.SmallNet) }
        onOpContentNpc3(FishingParams.SPOT_SMALLNET) { attempt(it.npc, FishingStyle.NetSpotBait) }
        onOpContentNpc1(FishingParams.SPOT_ROD) { attempt(it.npc, FishingStyle.Lure) }
        onOpContentNpc3(FishingParams.SPOT_ROD) { attempt(it.npc, FishingStyle.RodSpotBait) }
        onOpContentNpc1(FishingParams.SPOT_CAGE) { attempt(it.npc, FishingStyle.Cage) }
        onOpContentNpc3(FishingParams.SPOT_CAGE) { attempt(it.npc, FishingStyle.CageSpotHarpoon) }
        onOpContentNpc1(FishingParams.SPOT_BIGNET) { attempt(it.npc, FishingStyle.BigNet) }
        onOpContentNpc3(FishingParams.SPOT_BIGNET) {
            attempt(it.npc, FishingStyle.BigNetSpotHarpoon)
        }
        // TODO(quest): gate Piscatoris spots on Swan Song progress.
        onOpContentNpc1(FishingParams.SPOT_MONKFISH) { attempt(it.npc, FishingStyle.MonkfishNet) }
        onOpContentNpc3(FishingParams.SPOT_MONKFISH) {
            attempt(it.npc, FishingStyle.CageSpotHarpoon)
        }
        // TODO(favour): implement the intended Piscarilius access gate.
        onOpContentNpc1(FishingParams.SPOT_ANGLERFISH) {
            attempt(it.npc, FishingStyle.AnglerfishBait)
        }
        onOpContentNpc1(FishingParams.SPOT_BARBARIAN) { attempt(it.npc, FishingStyle.BarbarianRod) }

        onOpContentNpc1(FishingParams.SPOT_SLIMY_EEL) { attempt(it.npc, FishingStyle.SlimyEel) }
        onOpContentNpc3(FishingParams.SPOT_CAVEFISH) { attempt(it.npc, FishingStyle.CaveEel) }
        onOpContentNpc1(FishingParams.SPOT_LAVA_EEL) { attempt(it.npc, FishingStyle.LavaEel) }
        onOpContentNpc1(FishingParams.SPOT_INFERNAL_EEL) {
            attempt(it.npc, FishingStyle.InfernalEel)
        }
        onOpContentNpc1(FishingParams.SPOT_DARK_CRAB) { attempt(it.npc, FishingStyle.DarkCrab) }
        onOpContentNpc1(FishingParams.SPOT_KARAMBWANJI) {
            attempt(it.npc, FishingStyle.KarambwanjiNet)
        }
        onOpContentNpc1(FishingParams.SPOT_MINNOW) { attempt(it.npc, FishingStyle.MinnowNet) }
    }

    private fun ProtectedAccess.attempt(spot: Npc, style: FishingStyle) {
        if (
            style.requiresBarbFishingTraining &&
                vars[FishingParams.BARB_FISHING_VARBIT] < FishingParams.BARB_STAGE_TAUGHT
        ) {
            mes("You need to learn barbarian fishing from Otto Godblessed before fishing here.")
            bailReset()
            return
        }

        val minLevel = FishingCatch.minLevel(style.styleId)
        val gateLevel = if (style.useBaseLevelGate) player.baseFishingLvl else player.fishingLvl
        if (gateLevel < minLevel) {
            mes("You need a Fishing level of $minLevel to fish here.")
            bailReset()
            return
        }

        if (
            style.requiresIceGloves &&
                FishingParams.ICE_GLOVES !in player.worn &&
                FishingParams.SMITHS_GLOVES_I !in player.worn
        ) {
            mes("You need to be wearing ice gloves or Smith's gloves (i) to catch these fish.")
            bailReset()
            return
        }

        if (FishingCatch.cascade(style.styleId).none { meetsRowLevels(it) }) {
            val lowest = FishingCatch.cascade(style.styleId).minBy { it.level }
            mes(
                "You need an Agility level of ${lowest.agilityLevel} and a Strength level of " +
                    "${lowest.strengthLevel} to fish here."
            )
            bailReset()
            return
        }

        val tool = resolveTool(style)
        if (tool == null) {
            mes(style.missingToolMessage)
            bailReset()
            return
        }

        if (inv.isFull() && !canGrowStackableCatch(style)) {
            mesInvFull()
            bailReset()
            return
        }

        if (style.baits.isNotEmpty() && style.baits.none { it in inv }) {
            mes(checkNotNull(style.missingBaitMessage))
            bailReset()
            return
        }

        // actionDelay is shared with combat and other skills; pair it with the fishing-owned clock.
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            // Never shorten a foreign action delay.
            actionDelay = maxOf(actionDelay, mapClock + FishingParams.ROLL_TICKS)
            skillAnimDelay = mapClock + FishingParams.ROLL_TICKS
            anim(tool.anim)
            spam(style.castMessage)
            reinvoke(spot, style)
        } else {
            fish(spot, style, tool)
        }
    }

    private fun ProtectedAccess.fish(spot: Npc, style: FishingStyle, tool: ResolvedTool) {
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + FishingParams.ANIM_TICKS
            anim(tool.anim)
        }

        if (actionDelay < mapClock) {
            // A foreign action consumed the clock; re-arm without granting a catch.
            actionDelay = mapClock + FishingParams.ROLL_TICKS
        } else if (actionDelay == mapClock) {
            actionDelay = mapClock + FishingParams.ROLL_TICKS
            val keepFishing =
                if (style.independentRolls) {
                    rollBigNet(spot, style)
                } else {
                    rollCascade(spot, style, tool)
                }
            if (!keepFishing) {
                return
            }
        }

        reinvoke(spot, style)
    }

    private fun ProtectedAccess.rollCascade(
        spot: Npc,
        style: FishingStyle,
        tool: ResolvedTool,
    ): Boolean {
        for (catch in FishingCatch.cascade(style.styleId)) {
            if (!meetsRowLevels(catch)) {
                continue
            }
            val (low, high) = FishingEquipment.harpoonRates(catch, tool.tier)
            if (statRandom("stat.fishing", low, high, invisibleLvls)) {
                return award(spot, style, catch, tool)
            }
        }
        return true
    }

    private fun ProtectedAccess.meetsRowLevels(catch: FishingCatch): Boolean =
        player.fishingLvl >= catch.level &&
            player.agilityLvl >= (catch.agilityLevel ?: 0) &&
            player.strengthLvl >= (catch.strengthLevel ?: 0)

    private fun ProtectedAccess.rollBigNet(spot: Npc, style: FishingStyle): Boolean {
        for (catch in FishingCatch.tableOrder(style.styleId)) {
            if (player.fishingLvl < catch.level) {
                continue
            }
            repeat(catch.rolls) {
                val invisibleBoost = invisibleLvls.get(player, "stat.fishing")
                val success =
                    statRandom(
                        "stat.fishing",
                        catch.low,
                        catch.high,
                        invisibleBoost,
                        catch.bonus.toDouble(),
                    )
                if (success) {
                    if (!award(spot, style, catch, tool = null)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun ProtectedAccess.award(
        spot: Npc,
        style: FishingStyle,
        catch: FishingCatch,
        tool: ResolvedTool?,
    ): Boolean {
        val harpoonSelection = FishingHarpoonCharges.select(player, tool?.harpoon)
        val bait = style.baits.firstOrNull { it in inv }
        if (style.baits.isNotEmpty()) {
            if (bait == null) {
                // Recheck bait before commit; another action may have consumed it.
                return true
            }
            val delete = invDel(inv, bait, count = 1)
            if (!delete.success) {
                return true
            }
        }

        val isKarambwanjiRow =
            style == FishingStyle.KarambwanjiNet && catch.item.internalName == KARAMBWANJI_ITEM
        val count =
            when {
                style == FishingStyle.MinnowNet -> minnowQuantity(player.fishingLvl)
                isKarambwanjiRow -> karambwanjiQuantity(player.fishingLvl)
                catch.countMax > catch.countMin -> random.of(catch.countMin, catch.countMax)
                else -> catch.countMin
            }
        val xp = catch.xp * xpMods.get(player, "stat.fishing")
        val product =
            SkillingProduct(
                player = player,
                skill = "stat.fishing",
                item = RSCM.getReverseMapping(RSCMType.OBJ, catch.item.id),
                count = count,
                experience = xp,
                grantsExperience = catch.xpTenths > 0,
                source = SkillingProductSource.Fishing(spot, catch.item),
                depletes = false,
            )

        val previousBarrelCount = FishBarrelScript.storedCount(player, product.item)
        return when (awardSkillingProduct(product)) {
            SkillingAwardResult.Success -> {
                if (catch.secondaryXpTenths != null) {
                    statAdvance("stat.agility", catch.secondaryXp * xpMods.get(player, "stat.agility"))
                    statAdvance(
                        "stat.strength",
                        catch.secondaryXp * xpMods.get(player, "stat.strength"),
                    )
                    markBarbarianCatch()
                }
                spam(catch.catchMessage)
                val selected = FishingHarpoonCharges.refreshAfterTransaction(player, harpoonSelection)
                if (selected != null) {
                    repeat(product.count) {
                        val current = selected.inventory[selected.slot]
                        val live = FishingHarpoonCharges.select(player, current)
                        if (FishingHarpoonCharges.isCrystal(current)) {
                            FishingHarpoonCharges.consume(player, live, random)
                        } else if (FishingHarpoonCharges.isInfernal(current)) {
                            cookInfernalCatch(product.item, live, previousBarrelCount)
                        }
                    }
                }
                true
            }
            SkillingAwardResult.InventoryFull -> {
                if (bait != null) {
                    invAdd(inv, bait, count = 1)
                }
                mesInvFull()
                resetAnim()
                false
            }
            SkillingAwardResult.Cancelled -> {
                if (bait != null) {
                    invAdd(inv, bait, count = 1)
                }
                true
            }
        }
    }

    private fun ProtectedAccess.reinvoke(spot: Npc, style: FishingStyle) {
        if (style.op == 1) {
            opNpc1(spot)
        } else {
            opNpc3(spot)
        }
    }

    // Only clear an animation owned by an active gathering loop.
    private fun ProtectedAccess.bailReset() {
        if (skillAnimDelay >= mapClock) {
            resetAnim()
        }
    }

    private fun ProtectedAccess.markBarbarianCatch() {
        if (vars[FishingParams.BARB_FISHING_VARBIT] == FishingParams.BARB_STAGE_TAUGHT) {
            VarPlayerIntMapSetter.set(
                player,
                FishingParams.BARB_FISHING_VARBIT,
                FishingParams.BARB_STAGE_CAUGHT,
            )
        }
    }

    private fun ProtectedAccess.mesInvFull() {
        mes("You can't carry any more fish.")
        soundSynth("synth.PILLORY_WRONG")
    }

    private fun ProtectedAccess.cookInfernalCatch(item: String, selected: FishingHarpoonCharges.Selection?, previousBarrelCount: Int) {
        val cookingXp = cookingXpByRaw[item] ?: return
        if (random.of(3) != 0) {
            return
        }
        if (!FishBarrelScript.consumeNewlyStored(player, item, previousBarrelCount) && !invDel(inv, item, count = 1).success) return
        FishingHarpoonCharges.consume(player, FishingHarpoonCharges.refreshAfterTransaction(player, selected), random)
        statAdvance("stat.cooking", cookingXp / 2.0 * xpMods.get(player, "stat.cooking"))
    }

    private fun ProtectedAccess.resolveTool(style: FishingStyle): ResolvedTool? {
        if (!style.usesHarpoon) {
            val tool = checkNotNull(style.tool)
            if (tool in inv) {
                return ResolvedTool(checkNotNull(style.anim), HarpoonTier.Standard)
            }
            val pearl = FishingEquipment.PEARL_ROD_VARIANTS[tool]
            if (pearl != null && pearl.item in inv) {
                return ResolvedTool(pearl.anim, HarpoonTier.Standard)
            }
            return null
        }
        val harpoon = FishingEquipment.findHarpoon(player) ?: return null
        val type = getInvObj(harpoon)
        return ResolvedTool(
            anim = FishingEquipment.harpoonAnim(player, harpoon),
            tier = FishingEquipment.harpoonTier(type),
            // Empty infernal harpoons retain dragon rates but cannot auto-cook.
            harpoon = harpoon,
        )
    }

    private fun ProtectedAccess.canGrowStackableCatch(style: FishingStyle): Boolean =
        FishingCatch.cascade(style.styleId).any { catch ->
            catch.item.isStackable && catch.item.internalName in inv
        }

    private fun minnowQuantity(fishingLvl: Int): Int =
        when {
            fishingLvl >= 99 -> 14
            fishingLvl >= 95 -> 13
            fishingLvl >= 90 -> 12
            fishingLvl >= 85 -> 11
            else -> 10
        }

    private fun karambwanjiQuantity(fishingLvl: Int): Int = fishingLvl / 5 + 1

    private data class ResolvedTool(
        val anim: String,
        val tier: HarpoonTier,
        val harpoon: InvObj? = null,
    )

    private companion object {
        private const val KARAMBWANJI_ITEM = "obj.tbwt_raw_karambwanji"
    }
}
