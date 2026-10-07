package org.rsmod.content.interfaces.skill.guides

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.StatType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.StatComponentsRow
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private sealed interface StatGuideMenuChoice {
    data object Guide : StatGuideMenuChoice
    data object SetLevel : StatGuideMenuChoice
}

// The unread-level varbit controls the skills-tab flash and must clear when the guide is opened.
internal fun levelUpListVarbit(stat: StatType): String? {
    val shortName = stat.internalName.removePrefix("stat.")
    if (shortName !in LEVEL_UP_LIST_STATS) {
        return null
    }
    val suffix = if (shortName == "runecrafting") "runecraft" else shortName
    return "varbit.levelup_list_$suffix"
}

private val LEVEL_UP_LIST_STATS =
    setOf(
        "attack",
        "defence",
        "strength",
        "hitpoints",
        "ranged",
        "prayer",
        "magic",
        "cooking",
        "woodcutting",
        "fletching",
        "fishing",
        "firemaking",
        "crafting",
        "smithing",
        "mining",
        "herblore",
        "agility",
        "thieving",
        "slayer",
        "farming",
        "runecrafting",
        "hunter",
        "construction",
        "sailing",
    )

class SkillGuideEvents @Inject constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {

    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        onPlayerLogin {
            if (player.vars["varp.skill_guide_v2_default_applied"] == 0) {
                VarPlayerIntMapSetter.set(player, "varp.skill_guide_v2_default_applied", 1)
                VarPlayerIntMapSetter.set(player, "varbit.option_skill_guide", 1)
            }
        }

        StatComponentsRow.all().forEach { row ->
            onIfOverlayButton(row.component) {
                ifClose()

                protectedAccess.launch(player) {
                    if (!player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
                        openStatGuide(row)
                        return@launch
                    }

                    when (choice2("Guide", StatGuideMenuChoice.Guide, "Set Level", StatGuideMenuChoice.SetLevel)) {
                        StatGuideMenuChoice.Guide -> openStatGuide(row)
                        StatGuideMenuChoice.SetLevel -> setLevel(row)
                    }
                }
            }
        }

        onIfOverlayButton("component.skill_guide:close") {
            player.ifCloseOverlay("interface.skill_guide", eventBus)
        }

        onIfOverlayButton("component.skill_guide_v2:close") {
            player.ifCloseOverlay("interface.skill_guide_v2", eventBus)
        }
    }

    @OptIn(InternalApi::class)
    private suspend fun ProtectedAccess.setLevel(row: StatComponentsRow) {
        val stat = row.stat.internalName
        val targetLevel = countDialog("Enter a level for ${row.stat.displayName} (1-99)").coerceIn(1, 99)

        val currentLevel = stat(stat)
        val levelDelta = targetLevel - currentLevel

        player.statMap.setCurrentLevel(stat, player.statMap.getBaseLevel(stat))
        player.statMap.setXP(stat, PlayerSkillXPTable.getXPFromLevel(targetLevel))
        player.statMap.setBaseLevel(stat, targetLevel.toByte())

        when {
            levelDelta > 0 -> statAdd(stat, constant = levelDelta, percent = 0)
            levelDelta < 0 -> statSub(stat, constant = -levelDelta, percent = 0)
        }

        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
        PlayerInterfaceUpdates.updateCombatLevel(player)
    }

    private fun ProtectedAccess.openStatGuide(row: StatComponentsRow) {
        player.openSkillGuide(row, player.vars["varbit.option_skill_guide"] != 0, eventBus)
    }
}

public fun Player.openSkillGuide(row: StatComponentsRow, useV2: Boolean, eventBus: EventBus) {
    if (useV2) {
        openSkillGuideV2(row, eventBus)
    } else {
        openSkillGuideV1(row.bit, eventBus)
    }
}

private fun Player.openSkillGuideV1(skillGuideBit: Int, eventBus: EventBus) {
    ifOpenOverlay("interface.skill_guide", eventBus)
    ifSetEvents("component.skill_guide:icons", 0..99)
    runClientScript("clientscript.skill_guide_legacy_init", skillGuideBit, 0, 0, 0)
}

public fun Player.openSkillGuideV2(row: StatComponentsRow, eventBus: EventBus) {
    val unreadVarbit = levelUpListVarbit(row.stat)
    val unreadFrom = if (unreadVarbit != null) vars[unreadVarbit] else 0
    if (unreadVarbit != null && unreadFrom != 0) {
        VarPlayerIntMapSetter.set(this, unreadVarbit, 0)
    }

    ifOpenOverlay("interface.skill_guide_v2", eventBus)
    ifSetEvents("component.skill_guide_v2:tabs", 0..200, IfEvent.Op1)
    ifSetEvents(
        "component.skill_guide_v2:quest_journal_button_trigger",
        -1..-1,
        IfEvent.ScriptTrigger,
    )
    ifSetEvents(
        "component.skill_guide_v2:skill_guide_button_trigger",
        -1..-1,
        IfEvent.ScriptTrigger,
    )
    runClientScript(
        "clientscript.[clientscript,skill_guide_v2_init]",
        row.bit,
        if (unreadFrom != 0) UNREAD_SUBSECTION_MASK else 0,
        unreadFrom,
        statBase(row.stat.internalName),
    )
    ifSetEvents("component.skill_guide_v2:list", -1..-1)
}

private const val UNREAD_SUBSECTION_MASK = 31
