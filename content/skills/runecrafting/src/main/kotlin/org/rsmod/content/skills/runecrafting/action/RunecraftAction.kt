package org.rsmod.content.skills.runecrafting.action

import dev.openrune.types.ItemServerType
import kotlin.math.floor
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseRunecraftingLvl
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ComboruneRecipeRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.skills.runecrafting.configs.OuraniaRates
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.canChargeGotrCell
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.canFitGotrCraft
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.chargeGotrCell
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.grantGotrStonesForAether
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.grantGotrStonesForCombo
import org.rsmod.content.skills.runecrafting.gotr.GotrImbueHook.grantGotrStonesForRune
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.consumeChargeAfterCombo
import org.rsmod.content.skills.runecrafting.items.BindingNecklace.isWearing
import org.rsmod.content.skills.runecrafting.items.BloodEssence
import org.rsmod.content.skills.runecrafting.items.BloodEssence.applyBloodRuneBonus
import org.rsmod.content.skills.runecrafting.items.RaimentsOfTheEye.applyBonus
import org.rsmod.content.skills.runecrafting.magic.MagicImbue.isActive

object RunecraftAction {
    private const val RUNE_ESSENCE = EssencePouch.RUNE_ESSENCE
    private const val PURE_ESSENCE = EssencePouch.PURE_ESSENCE
    private const val DAEYALT_ESSENCE = EssencePouch.DAEYALT_ESSENCE
    private const val GUARDIAN_ESSENCE = EssencePouch.GUARDIAN_ESSENCE
    private const val DARK_ESSENCE = EssencePouch.DARK_ESSENCE_FRAGMENT
    private const val DAEYALT_XP_MULTIPLIER = 1.5
    private const val OURANIA_XP_MULTIPLIER = 1.7

    private const val OURANIA_SOUL_XP_TENTHS = 110
    private const val CORE_RUNE_MULTIPLIER = 11
    private const val CORE_XP_MULTIPLIER = 10
    private const val FIRE_RUNE = "obj.firerune"
    private const val SUNFIRE_SPLINTER = "obj.sunfiresplinter"

    private val runecraftingExtract =
        mapOf(
            "obj.scar_extract_warped" to 250,
            "obj.scar_extract_twisted" to 60,
            "obj.scar_extract_mangled" to 60,
            "obj.scar_extract_scarred" to 60,
        )

    suspend fun ProtectedAccess.preCraft() {
        anim("seq.human_runecraft")
        spotanim("spotanim.runecrafting", height = 92)
        soundSynth("synth.BIND_RUNES")
    }

    suspend fun ProtectedAccess.craftRune(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean = false,
    ) {
        if (!canCraftRune(rune)) {
            return
        }

        val validEssence = rune.input.map { it.internalName }.toSet()
        val acceptsDaeyalt = PURE_ESSENCE in validEssence
        val daeyaltEssCount =
            if (acceptsDaeyalt) {
                inv.count(DAEYALT_ESSENCE)
            } else {
                0
            }

        val consumedInputs = (validEssence + if (acceptsDaeyalt) setOf(DAEYALT_ESSENCE, GUARDIAN_ESSENCE) else emptySet())
            .associateWith { inv.count(it) }.toMutableMap()
        val extract = rune.extract.internalName
        if (extract in runecraftingExtract) consumedInputs[extract] = minOf(inv.count(extract), consumedInputs.values.sum())
        if (!canFitGotrCraft(rune.output.internalName, consumedInputs[GUARDIAN_ESSENCE] ?: 0, consumedInputs)) return

        preCraft()

        val daeyalt = if (daeyaltEssCount > 0) {
            craftDaeyaltEssence(rune, xpMods, daeyaltEssCount, ouraniaAltar)
        } else EssenceCraftResult()
        val standard = craftStandardEssence(rune, xpMods, validEssence, ouraniaAltar)
        val outputCount = daeyalt.ordinaryRunes + standard.ordinaryRunes
        if (!ouraniaAltar && outputCount > 0 && !daeyalt.extractUsed &&
            !standard.extractUsed && !standard.guardianUsed) {
            publish(PlayerActionCompleteEvent(player, PlayerAction.CraftRuneBatch,
                rune.output.internalName, count = outputCount))
        }
        if (!ouraniaAltar) chargeGotrCell(rune.output.internalName)
    }

    private suspend fun ProtectedAccess.craftDaeyaltEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        daeyaltEssCount: Int,
        ouraniaAltar: Boolean,
    ): EssenceCraftResult {
        if (invDel(inv, DAEYALT_ESSENCE, daeyaltEssCount).failure) {
            return EssenceCraftResult()
        }

        val level = player.baseRunecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val baseProduced = daeyaltEssCount * baseMultiplier
        val produced = applyBonus(baseProduced)

        val xpMultiplier =
            if (ouraniaAltar) {
                DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
            } else {
                DAEYALT_XP_MULTIPLIER
            }

        return finishEssenceCraft(
            rune,
            daeyaltEssCount,
            // Use xpTenths; the legacy integer column loses fractional XP.
            baseProduced * (rune.xpTenths / 10.0) * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
            ordinaryEssenceConsumed = daeyaltEssCount,
        )
    }

    private suspend fun ProtectedAccess.craftStandardEssence(
        rune: RunecraftingRunesRow,
        xpMods: XpModifiers,
        validEssence: Set<String>,
        ouraniaAltar: Boolean,
    ): EssenceCraftResult {
        val runeEssCount = if (RUNE_ESSENCE in validEssence) inv.count(RUNE_ESSENCE) else 0
        val pureEssCount = if (PURE_ESSENCE in validEssence) inv.count(PURE_ESSENCE) else 0
        val guardianEssCount =
            if (PURE_ESSENCE in validEssence) {
                inv.count(GUARDIAN_ESSENCE)
            } else {
                0
            }
        val darkEssCount =
            if (DARK_ESSENCE in validEssence) {
                inv.count(DARK_ESSENCE)
            } else {
                0
            }
        val totalEssence = runeEssCount + pureEssCount + guardianEssCount + darkEssCount
        if (totalEssence <= 0) {
            return EssenceCraftResult()
        }

        if (pureEssCount > 0 && invDel(inv, PURE_ESSENCE, pureEssCount).failure) {
            return EssenceCraftResult()
        }
        if (guardianEssCount > 0 && invDel(inv, GUARDIAN_ESSENCE, guardianEssCount).failure) {
            return EssenceCraftResult()
        }
        if (darkEssCount > 0 && invDel(inv, DARK_ESSENCE, darkEssCount).failure) {
            return EssenceCraftResult()
        }
        if (runeEssCount > 0 && invDel(inv, RUNE_ESSENCE, runeEssCount).failure) {
            return EssenceCraftResult()
        }

        val level = player.baseRunecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()

        val basePure = pureEssCount * baseMultiplier
        val baseGuardian = guardianEssCount * baseMultiplier
        val baseDark = darkEssCount * baseMultiplier
        val baseRune = runeEssCount * baseMultiplier
        val totalXp = (basePure + baseGuardian + baseDark + baseRune) * (rune.xpTenths / 10.0)
        val xpMultiplier = if (ouraniaAltar) OURANIA_XP_MULTIPLIER else 1.0
        val produced =
            applyBonus(basePure) +
                applyBonus(baseGuardian) +
                applyBonus(baseDark) +
                applyBonus(baseRune)

        return finishEssenceCraft(
            rune,
            totalEssence,
            totalXp * xpMultiplier,
            xpMods,
            ouraniaAltar,
            produced,
            guardianEssenceConsumed = guardianEssCount,
            ordinaryEssenceConsumed = runeEssCount + pureEssCount,
        )
    }

    private suspend fun ProtectedAccess.finishEssenceCraft(
        rune: RunecraftingRunesRow,
        essenceConsumed: Int,
        xp: Double,
        xpMods: XpModifiers,
        ouraniaAltar: Boolean,
        producedRunes: Int? = null,
        guardianEssenceConsumed: Int = 0,
        ordinaryEssenceConsumed: Int = 0,
    ): EssenceCraftResult {
        val level = player.baseRunecraftingLvl
        val baseMultiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        var totalRunes = producedRunes ?: applyBonus(essenceConsumed * baseMultiplier)

        val extractName = rune.extract.internalName
        val extractBonusPerEssence = runecraftingExtract[extractName] ?: 0
        var usedExtract = false
        if (extractBonusPerEssence > 0) {
            val extractCount = minOf(inv.count(extractName), essenceConsumed)
            if (extractCount > 0 && invDel(inv, extractName, extractCount).success) {
                usedExtract = true
                totalRunes += extractCount * extractBonusPerEssence
            }
        }

        if (!ouraniaAltar && rune.output.internalName == BloodEssence.BLOOD_RUNE) {
            applyBloodRuneBonus(essenceConsumed)?.let { bonus -> totalRunes += bonus }
        }

        val added = invAdd(inv, rune.output.internalName, totalRunes)
        if (added.success && !ouraniaAltar && ordinaryEssenceConsumed > 0) {
            publish(PlayerActionCompleteEvent(
                player, PlayerAction.CraftRune,
                rune.output.internalName, count = ordinaryEssenceConsumed,
            ))
        }
        advanceRunecraftingXp(xp, xpMods)

        grantGotrStonesForRune(rune.output.internalName, guardianEssenceConsumed)
        return EssenceCraftResult(
            ordinaryRunes = if (added.success && ordinaryEssenceConsumed > 0 &&
                guardianEssenceConsumed == 0) totalRunes else 0,
            extractUsed = usedExtract,
            guardianUsed = guardianEssenceConsumed > 0,
        )
    }
    private data class EssenceCraftResult(
        val ordinaryRunes: Int = 0,
        val extractUsed: Boolean = false,
        val guardianUsed: Boolean = false,
    )

    private suspend fun ProtectedAccess.canCraftRune(rune: RunecraftingRunesRow): Boolean {
        val level = player.baseRunecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s."
            )
            return false
        }

        val validEssenceIds = rune.input.map { it.internalName }.toSet()
        val acceptsPureSubstitutes = PURE_ESSENCE in validEssenceIds
        val checkIds =
            when {
                acceptsPureSubstitutes -> validEssenceIds + DAEYALT_ESSENCE + GUARDIAN_ESSENCE
                DARK_ESSENCE in validEssenceIds -> validEssenceIds
                else -> validEssenceIds
            }
        val hasEssence = checkIds.any(inv::contains)
        if (!hasEssence) {
            if (canChargeGotrCell(rune.output.internalName)) {
                preCraft()
                chargeGotrCell(rune.output.internalName)
                return false
            }

            val essenceName = rune.input.first().name.lowercase()
            if (acceptsPureSubstitutes) {
                mesbox(
                    "You do not have any $essenceName, Daeyalt essence, or guardian essence to bind."
                )
            } else {
                mesbox("You do not have any $essenceName to bind.")
            }
            return false
        }

        return true
    }

    suspend fun ProtectedAccess.craftOurania(xpMods: XpModifiers) {
        val daeyaltCount = inv.count(DAEYALT_ESSENCE)
        val pureLikeCount = countPureLikeEssence()
        if (daeyaltCount <= 0 && pureLikeCount <= 0) {
            mesbox("You do not have any pure essence to bind.")
            return
        }

        preCraft()

        if (daeyaltCount > 0 && invDel(inv, DAEYALT_ESSENCE, daeyaltCount).success) {
            val xpMultiplier = DAEYALT_XP_MULTIPLIER * OURANIA_XP_MULTIPLIER
            craftOuraniaBatch(daeyaltCount, xpMultiplier = xpMultiplier, xpMods = xpMods)
        }

        if (pureLikeCount > 0 && removePureLikeEssence(pureLikeCount)) {
            craftOuraniaBatch(pureLikeCount, xpMultiplier = OURANIA_XP_MULTIPLIER, xpMods = xpMods)
        }
    }

    private suspend fun ProtectedAccess.craftOuraniaBatch(
        essenceCount: Int,
        xpMultiplier: Double,
        xpMods: XpModifiers,
    ) {
        val level = player.baseRunecraftingLvl
        var totalXp = 0.0

        repeat(essenceCount) {
            val rune = rollOuraniaRune(level)
            val produced = applyBonus(1)
            val xpTenths =
                if (rune.output.internalName == "obj.soulrune") {
                    OURANIA_SOUL_XP_TENTHS
                } else {
                    rune.xpTenths
                }
            totalXp += (xpTenths / 10.0) * xpMultiplier
            invAdd(inv, rune.output.internalName, produced)
        }

        advanceRunecraftingXp(totalXp, xpMods)
    }

    suspend fun ProtectedAccess.craftCore(
        rune: RunecraftingRunesRow,
        coreItem: String,
        xpMods: XpModifiers,
    ) {
        if (!inv.contains(coreItem)) {
            return
        }

        val level = player.baseRunecraftingLvl
        if (level < rune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${rune.statReq.first().t1} to craft ${rune.output.name.lowercase()}s."
            )
            return
        }

        preCraft()

        if (invDel(inv, coreItem, 1).failure) {
            return
        }

        val multiplier = getBonusMultiplier(rune.output.internalName, level).toInt()
        val produced = applyBonus(CORE_RUNE_MULTIPLIER * multiplier)
        invAdd(inv, rune.output.internalName, produced)
        advanceRunecraftingXp((rune.xpTenths / 10.0) * CORE_XP_MULTIPLIER, xpMods)
    }

    suspend fun ProtectedAccess.craftAether(xpMods: XpModifiers) {
        val aetherRune =
            RunecraftingRunesRow.all().firstOrNull { it.output.internalName == "obj.aetherrune" }
                ?: return

        val level = player.baseRunecraftingLvl
        if (level < aetherRune.statReq.first().t1) {
            mesbox(
                "You need Runecrafting level ${aetherRune.statReq.first().t1} to craft aether runes."
            )
            return
        }

        if (!player.isActive()) {
            mes("You need to cast Magic Imbue before you can craft aether runes here.")
            return
        }

        val essenceCount = countPureLikeEssence()
        val soulCount = inv.count("obj.soulrune")
        val craftCount = minOf(essenceCount, soulCount)
        if (craftCount <= 0) {
            mes("You need pure essence and soul runes to craft aether runes.")
            return
        }

        if (!inv.contains("obj.cosmic_soul_catalyst")) {
            mes("You need an aether catalyst to craft aether runes.")
            return
        }

        val guardianUsed = (craftCount - inv.count(PURE_ESSENCE)).coerceAtLeast(0)
        if (!canFitGotrCraft(aetherRune.output.internalName, guardianUsed, mapOf(
                PURE_ESSENCE to craftCount - guardianUsed,
                GUARDIAN_ESSENCE to guardianUsed,
                "obj.soulrune" to craftCount,
                // A successful attempt frees a catalyst slot; complete failure needs no rune slot.
                "obj.cosmic_soul_catalyst" to 1,
                "obj.scar_extract_scarred" to minOf(inv.count("obj.scar_extract_scarred"), 1),
            ))) return
        preCraft()

        if (removePureLikeEssence(craftCount).not()) {
            return
        }
        if (invDel(inv, "obj.soulrune", craftCount).failure) {
            return
        }

        val successCount = (1..craftCount).count { random.of(100) < 50 }
        if (successCount <= 0) {
            grantGotrStonesForAether(guardianUsed)
            chargeGotrCell("obj.cosmicrune")
            return
        }

        var totalRunes = applyBonus(successCount)

        val extractBonusPerEssence = runecraftingExtract["obj.scar_extract_scarred"] ?: 0
        if (extractBonusPerEssence > 0) {
            val extractCount = minOf(inv.count("obj.scar_extract_scarred"), successCount)
            if (extractCount > 0 && invDel(inv, "obj.scar_extract_scarred", extractCount).success) {
                totalRunes += extractCount * extractBonusPerEssence
            }
        }

        val catalystAvailable = inv.count("obj.cosmic_soul_catalyst")
        val cappedRunes = minOf(totalRunes, catalystAvailable)
        if (cappedRunes <= 0 || invDel(inv, "obj.cosmic_soul_catalyst", cappedRunes).failure) {
            return
        }

        invAdd(inv, aetherRune.output.internalName, cappedRunes)

        grantGotrStonesForAether(guardianUsed)
        chargeGotrCell("obj.cosmicrune")
        advanceRunecraftingXp(successCount * (aetherRune.xpTenths / 10.0), xpMods)
    }

    suspend fun ProtectedAccess.craftSunfire(xpMods: XpModifiers) {
        val sunfireRune =
            RunecraftingRunesRow.all().firstOrNull { it.output.internalName == "obj.sunfirerune" }
                ?: return

        val level = player.baseRunecraftingLvl
        val levelReq = sunfireRune.statReq.first().t1
        if (level < levelReq) {
            mesbox("You need Runecrafting level $levelReq to craft sunfire runes.")
            return
        }

        val daeyaltCount = inv.count(DAEYALT_ESSENCE)
        if (daeyaltCount > 0) {
            craftSunfireBatch(sunfireRune, DAEYALT_ESSENCE, daeyaltCount, DAEYALT_XP_MULTIPLIER, xpMods)
            return
        }

        val pureCount = inv.count(PURE_ESSENCE)
        if (pureCount <= 0) {
            mesbox("You do not have any pure essence to bind.")
            return
        }
        craftSunfireBatch(sunfireRune, PURE_ESSENCE, pureCount, 1.0, xpMods)
    }

    private suspend fun ProtectedAccess.craftSunfireBatch(
        sunfireRune: RunecraftingRunesRow,
        essenceItem: String,
        essenceCount: Int,
        xpMultiplier: Double,
        xpMods: XpModifiers,
    ) {
        val fireRuneCount = inv.count(FIRE_RUNE)
        val splinterCount = inv.count(SUNFIRE_SPLINTER)
        val craftCount = minOf(essenceCount, fireRuneCount, splinterCount)
        if (craftCount <= 0) {
            mes("You need fire runes and sunfire splinters to craft sunfire runes here.")
            return
        }

        preCraft()

        if (invDel(inv, essenceItem, craftCount).failure) {
            return
        }
        if (invDel(inv, FIRE_RUNE, craftCount).failure) {
            return
        }
        if (invDel(inv, SUNFIRE_SPLINTER, craftCount).failure) {
            return
        }

        val level = player.baseRunecraftingLvl
        val multiplier = getBonusMultiplier(sunfireRune.output.internalName, level).toInt()
        var totalRunes = applyBonus(craftCount * multiplier)

        val extractName = sunfireRune.extract.internalName
        val extractBonusPerEssence = runecraftingExtract[extractName] ?: 0
        if (extractBonusPerEssence > 0) {
            val extractCount = minOf(inv.count(extractName), craftCount)
            if (extractCount > 0 && invDel(inv, extractName, extractCount).success) {
                totalRunes += extractCount * extractBonusPerEssence
            }
        }

        invAdd(inv, sunfireRune.output.internalName, totalRunes)
        advanceRunecraftingXp(craftCount * (sunfireRune.xpTenths / 10.0) * xpMultiplier, xpMods)
    }

    private fun ProtectedAccess.rollOuraniaRune(level: Int): RunecraftingRunesRow {
        val weights = OuraniaRates.weightsForLevel(level)
        val roll = random.of(OuraniaRates.WEIGHT_TOTAL)
        var cumulative = 0
        var chosen = weights.first().rune
        for (entry in weights) {
            cumulative += entry.weight
            if (roll < cumulative) {
                chosen = entry.rune
                break
            }
        }
        return RunecraftingRunesRow.all().first { it.output.internalName == chosen }
    }

    fun getBonusMultiplier(rune: String, level: Int): Double =
        when (rune) {
            "obj.airrune" -> floor(level / 11.0) + 1
            "obj.mindrune" -> floor(level / 14.0) + 1
            "obj.waterrune" -> floor(level / 19.0) + 1
            "obj.earthrune" -> floor(level / 26.0) + 1
            "obj.firerune" -> floor(level / 35.0) + 1
            "obj.bodyrune" -> floor(level / 46.0) + 1
            "obj.cosmicrune" -> floor(level / 59.0) + 1
            "obj.chaosrune" -> floor(level / 74.0) + 1
            "obj.naturerune" -> floor(level / 91.0) + 1
            "obj.sunfirerune" -> floor(level / 49.0) + 1
            "obj.astralrune" -> floor(level / 82.0) + 1
            "obj.lawrune" -> floor(level / 95.0) + 1
            "obj.deathrune" -> floor(level / 99.0) + 1
            else -> 1.0
        }

    suspend fun ProtectedAccess.craftCombination(
        combo: ComboruneRecipeRow, xpMods: XpModifiers, altarRune: String? = null,
    ) {
        val output = combo.output ?: return
        val input = combo.input ?: return
        val talisman = combo.talisman ?: return
        val xp = combo.xp ?: return

        if (!canCraftCombo(output, input, talisman, combo.statReq.first().t1)) {
            return
        }

        val daeyaltCount = inv.count(DAEYALT_ESSENCE)
        val pureLikeCount = countPureLikeEssence()
        val inputRuneCount = inv.count(input.internalName)
        val totalCraftable = minOf(daeyaltCount + pureLikeCount, inputRuneCount)
        if (totalCraftable <= 0) {
            return
        }

        val usingMagicImbue = player.isActive()
        val plannedDaeyalt = minOf(daeyaltCount, totalCraftable)
        val plannedPure = minOf(inv.count(PURE_ESSENCE), totalCraftable - plannedDaeyalt)
        val plannedGuardian = totalCraftable - plannedDaeyalt - plannedPure
        if (!canFitGotrCraft(output.internalName, plannedGuardian, mapOf(
                DAEYALT_ESSENCE to plannedDaeyalt,
                PURE_ESSENCE to plannedPure,
                GUARDIAN_ESSENCE to plannedGuardian,
                input.internalName to totalCraftable,
                talisman.internalName to if (usingMagicImbue) 0 else 1,
            ))) return
        preCraft()

        if (!usingMagicImbue && invDel(inv, talisman.internalName, 1).failure) {
            return
        }

        val wearingBinding = player.isWearing()
        val xpPerRune = xp.toDouble() / 10.0
        var anyCrafted = false

        val daeyaltCraftCount = minOf(daeyaltCount, inputRuneCount)
        if (daeyaltCraftCount > 0 && invDel(inv, DAEYALT_ESSENCE, daeyaltCraftCount).success) {
            val removed = invDel(inv, input.internalName, daeyaltCraftCount)
            if (removed.success) {
                craftComboBatch(
                    output.internalName,
                    removed.completed(),
                    xpPerRune * DAEYALT_XP_MULTIPLIER,
                    wearingBinding,
                    xpMods,
                )
                anyCrafted = true
            }
        }

        val remainingInputCount = inv.count(input.internalName)
        val pureCraftCount = minOf(pureLikeCount, remainingInputCount)
        val guardianUsed = (pureCraftCount - inv.count(PURE_ESSENCE)).coerceAtLeast(0)
        if (pureCraftCount > 0 && removePureLikeEssence(pureCraftCount)) {
            val removed = invDel(inv, input.internalName, pureCraftCount)
            if (removed.success) {
                craftComboBatch(
                    output.internalName,
                    removed.completed(),
                    xpPerRune,
                    wearingBinding,
                    xpMods,
                    guardianEssenceConsumed = guardianUsed,
                )
                anyCrafted = true
            }
        }

        if (wearingBinding && anyCrafted) {
            consumeChargeAfterCombo()
        }
        if (anyCrafted && altarRune != null) chargeGotrCell(altarRune)
    }

    private suspend fun ProtectedAccess.craftComboBatch(
        output: String,
        removedCount: Int,
        xpPerRune: Double,
        wearingBinding: Boolean,
        xpMods: XpModifiers,
        guardianEssenceConsumed: Int = 0,
    ) {
        val successCount =
            if (wearingBinding) {
                removedCount
            } else {
                (1..removedCount).count { random.of(100) < 50 }
            }

        // Compute XP from successful base output before extract and outfit bonuses.
        advanceRunecraftingXp(successCount * xpPerRune, xpMods)

        var finalCount = applyBonus(successCount)

        val extractBonusPerEssence = runecraftingExtract["obj.scar_extract_twisted"] ?: 0
        if (successCount > 0 && extractBonusPerEssence > 0) {
            val extractCount = minOf(inv.count("obj.scar_extract_twisted"), successCount)
            if (extractCount > 0 && invDel(inv, "obj.scar_extract_twisted", extractCount).success) {
                finalCount += extractCount * extractBonusPerEssence
            }
        }

        val added = invAdd(inv, output, finalCount)
        if (successCount > 0 && guardianEssenceConsumed == 0 && added.success) {
            publish(PlayerActionCompleteEvent(player, PlayerAction.CraftRune, output, count = finalCount))
        }

        grantGotrStonesForCombo(output, guardianEssenceConsumed)
    }

    fun ProtectedAccess.advanceRunecraftingXp(baseXp: Double, xpMods: XpModifiers) {
        statAdvance("stat.runecrafting", baseXp * xpMods.get(player, "stat.runecrafting"))
    }

    private fun ProtectedAccess.countPureLikeEssence(): Int =
        inv.count(PURE_ESSENCE) + inv.count(GUARDIAN_ESSENCE)

    private fun ProtectedAccess.hasPureLikeEssence(): Boolean = countPureLikeEssence() > 0

    private fun ProtectedAccess.removePureLikeEssence(amount: Int): Boolean {
        var remaining = amount
        if (remaining <= 0) {
            return true
        }

        val pureAvailable = inv.count(PURE_ESSENCE)
        if (pureAvailable > 0) {
            val take = minOf(pureAvailable, remaining)
            if (invDel(inv, PURE_ESSENCE, take).failure) {
                return false
            }
            remaining -= take
        }

        if (remaining > 0 && invDel(inv, GUARDIAN_ESSENCE, remaining).failure) {
            return false
        }

        return true
    }

    private suspend fun ProtectedAccess.canCraftCombo(
        output: ItemServerType,
        input: ItemServerType,
        talisman: ItemServerType,
        requiredLevel: Int?,
    ): Boolean {
        val levelReq = requiredLevel ?: return false
        val level = player.baseRunecraftingLvl
        val outputName = output.name
        val inputName = input.name
        val talismanName = talisman.name

        if (level < levelReq) {
            mesbox("You need Runecrafting level $levelReq to craft ${outputName}s.")
            return false
        }
        if (!hasPureLikeEssence() && inv.count(DAEYALT_ESSENCE) <= 0) {
            mes("You need pure essence to craft ${outputName}s.")
            return false
        }
        if (!inv.contains(input.internalName)) {
            mes("You need ${inputName}s to craft ${outputName}s.")
            return false
        }
        if (!player.isActive() && !inv.contains(talisman.internalName)) {
            mes("You need a $talismanName to craft ${outputName}s.")
            return false
        }
        return true
    }
}
