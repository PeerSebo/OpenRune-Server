package org.rsmod.content.interfaces.xpdrops

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.types.aconverted.interf.IfButtonOp
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onIfModalButton
import org.rsmod.content.interfaces.xpdrops.configs.XpTrackerSkill
import org.rsmod.content.interfaces.xpdrops.configs.XpTrackerSkills
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class XpDropsSetupScript : PluginScript() {
    override fun ScriptContext.startup() {
        onIfModalButton("component.xpdrops_setup:position_options") {
            selectDropdown("varbit.xpdrops_position", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:size_options") {
            selectDropdown("varbit.xpdrops_size", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:duration_options") {
            selectDropdown("varbit.xpdrops_duration", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:counter_options") {
            selectDropdown("varbit.xpdrops_counter_type", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:progress_options") {
            selectDropdown("varbit.xpdrops_progress_type", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:colour_options") {
            selectDropdown("varbit.xpdrops_colour", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:grouping_options") {
            selectDropdown("varbit.xpdrops_groupskills", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:speed_options") {
            selectDropdown("varbit.xpdrops_speed", it.comsub)
        }
        onIfModalButton("component.xpdrops_setup:fakes_options") { selectFakesDropdown(it.comsub) }

        onIfModalButton("component.xpdrops_setup:skilllist") { selectSkill(it.comsub) }
        onIfModalButton("component.xpdrops_setup:none") { selectModeNone() }
        onIfModalButton("component.xpdrops_setup:tracker") { selectModeTracker() }
        onIfModalButton("component.xpdrops_setup:goal") { selectModeGoal() }
        onIfModalButton("component.xpdrops_setup:tracker_start") { trackerStartButton(it.op) }
        onIfModalButton("component.xpdrops_setup:goal_start") { goalStartButton(it.op) }
        onIfModalButton("component.xpdrops_setup:goal_end") { goalEndButton(it.op) }
        onIfModalButton("component.xpdrops_setup:cancel") { cancelEditor() }
        onIfModalButton("component.xpdrops_setup:save") { saveEditor() }
    }

    private fun ProtectedAccess.selectDropdown(varbit: String, comsub: Int) {
        // Subcomponents are one-based; option indices are zero-based.
        VarPlayerIntMapSetter.set(player, varbit, comsub - 1)
        runClientScript(XPDROPS_FAKE_CLIENTSCRIPT, 3, 20000001)
    }

    private fun ProtectedAccess.selectFakesDropdown(comsub: Int) {
        VarPlayerIntMapSetter.set(player, "varbit.xpdrops_fakesoff", comsub - 1)
    }

    private fun ProtectedAccess.selectSkill(comsub: Int) {
        val setupSkill = comsub + 1
        val entry = XpTrackerSkills.bySetupIndex(setupSkill) ?: return
        val savedStart = player.vars[entry.startVarp]
        val savedEnd = player.vars[entry.endVarp]
        when {
            savedStart == 0 -> writeEditor(TYPE_NONE, end = 0, start = 0, skill = setupSkill)
            savedEnd == -1 ->
                writeEditor(TYPE_TRACKER, end = -1, start = savedStart, skill = setupSkill)
            else -> writeEditor(TYPE_GOAL, end = savedEnd, start = savedStart, skill = setupSkill)
        }
    }

    private fun ProtectedAccess.selectModeNone() {
        if (player.xpSetupSkill == 0) {
            return
        }
        player.goalEditEnd = 0
        player.goalEditStart = 0
        player.xpSetupType = TYPE_NONE
    }

    private fun ProtectedAccess.selectModeTracker() {
        val entry = selectedSkill() ?: return
        player.xpSetupType = TYPE_TRACKER
        player.goalEditEnd = -1
        player.goalEditStart = currentXp(entry)
    }

    private fun ProtectedAccess.selectModeGoal() {
        val entry = selectedSkill() ?: return
        val currentXp = currentXp(entry)
        player.xpSetupType = TYPE_GOAL
        player.goalEditEnd = nextLevelXp(currentXp)
        player.goalEditStart = currentXp
    }

    private suspend fun ProtectedAccess.trackerStartButton(op: IfButtonOp) {
        val entry = selectedSkill() ?: return
        when (op) {
            IfButtonOp.Op6 ->
                player.goalEditStart =
                    levelToXp(countDialog("Set tracker start point: (skill level)"))
            IfButtonOp.Op7 ->
                player.goalEditStart = countDialog("Set tracker start point: (XP value)")
            IfButtonOp.Op10 -> {
                player.goalEditEnd = -1
                player.goalEditStart = currentXp(entry)
            }
            else -> Unit
        }
    }

    private suspend fun ProtectedAccess.goalStartButton(op: IfButtonOp) {
        val entry = selectedSkill() ?: return
        when (op) {
            IfButtonOp.Op6 ->
                player.goalEditStart = levelToXp(countDialog("Set goal start point: (skill level)"))
            IfButtonOp.Op7 ->
                player.goalEditStart = countDialog("Set goal start point: (XP value)")
            IfButtonOp.Op10 -> player.goalEditStart = currentXp(entry)
            else -> Unit
        }
    }

    private suspend fun ProtectedAccess.goalEndButton(op: IfButtonOp) {
        val entry = selectedSkill() ?: return
        when (op) {
            IfButtonOp.Op6 ->
                player.goalEditEnd = levelToXp(countDialog("Set goal end point: (skill level)"))
            IfButtonOp.Op7 -> player.goalEditEnd = countDialog("Set goal end point: (XP value)")
            // TODO: Verify goal-end reset behavior.
            IfButtonOp.Op10 -> player.goalEditEnd = nextLevelXp(currentXp(entry))
            else -> Unit
        }
    }

    private fun ProtectedAccess.cancelEditor() {
        writeEditor(TYPE_NONE, end = 0, start = 0, skill = 0)
    }

    private fun ProtectedAccess.saveEditor() {
        val setupSkill = player.xpSetupSkill
        val entry = XpTrackerSkills.bySetupIndex(setupSkill) ?: return
        val type = player.xpSetupType
        val start = player.goalEditStart
        val end = player.goalEditEnd

        if (type == TYPE_TRACKER && start > currentXp(entry)) {
            // TODO: Validate goal-mode saves against the intended bounds.
            mes(
                "You can't set the start point of your tracker higher than your current XP " +
                    "in the skill."
            )
            return
        }

        val (savedStart, savedEnd) =
            when (type) {
                TYPE_TRACKER -> start to -1
                TYPE_GOAL -> start to end
                else -> 0 to 0
            }
        VarPlayerIntMapSetter.set(player, entry.endVarp, savedEnd)
        VarPlayerIntMapSetter.set(player, entry.startVarp, savedStart)

        writeEditor(TYPE_NONE, end = 0, start = 0, skill = 0)
        runClientScript(XPTRACKER_UPDATE_GOAL_CLIENTSCRIPT, setupSkill - 1, savedStart, savedEnd)
    }

    private fun ProtectedAccess.writeEditor(type: Int, end: Int, start: Int, skill: Int) {
        player.xpSetupType = type
        player.goalEditEnd = end
        player.goalEditStart = start
        player.xpSetupSkill = skill
    }

    private fun ProtectedAccess.selectedSkill(): XpTrackerSkill? =
        XpTrackerSkills.bySetupIndex(player.xpSetupSkill)

    private fun ProtectedAccess.currentXp(entry: XpTrackerSkill): Int =
        if (entry.stat != null) {
            player.statMap.getXP(entry.stat)
        } else {
            XpTrackerSkills.statNames.sumOf { player.statMap.getXP(it) }
        }

    private fun levelToXp(level: Int): Int {
        return PlayerSkillXPTable.getXPFromLevel(level.coerceIn(1, MAX_LEVEL))
    }

    private fun nextLevelXp(currentXp: Int): Int {
        val nextLevel = (PlayerSkillXPTable.getLevelFromXP(currentXp) + 1).coerceAtMost(MAX_LEVEL)
        return PlayerSkillXPTable.getXPFromLevel(nextLevel)
    }

    internal companion object {
        internal const val TYPE_NONE = 0
        internal const val TYPE_TRACKER = 1
        internal const val TYPE_GOAL = 2

        private const val MAX_LEVEL = 126

        private const val XPDROPS_FAKE_CLIENTSCRIPT = "clientscript.[clientscript,xpdrops_fake]"

        private const val XPTRACKER_UPDATE_GOAL_CLIENTSCRIPT = "clientscript.[clientscript,xptracker_update_goal]"
    }
}

/** `varbit.xpdrops_setup_type`: 0 = none, 1 = tracker, 2 = goal (varp 638 bits 28-29). */
internal var Player.xpSetupType by intVarBit("varbit.xpdrops_setup_type")

/** `varbit.xpdrops_setup_skill`: 0 = none, 1..25 = skill row + 1 (varp 638 bits 23-27). */
internal var Player.xpSetupSkill by intVarBit("varbit.xpdrops_setup_skill")

internal var Player.goalEditStart by intVarp("varp.if1")

/** Scratch end value while editing (`varp.if2`, 262); -1 = tracker (no end point). */
internal var Player.goalEditEnd by intVarp("varp.if2")

internal fun ProtectedAccess.openXpDropsSetup() {
    player.xpSetupType = XpDropsSetupScript.TYPE_NONE
    player.goalEditEnd = 0
    player.goalEditStart = 0
    player.xpSetupSkill = 0
    ifOpenMainModal("interface.xpdrops_setup")
    ifSetEvents("component.xpdrops_setup:position_options", 1..3, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:size_options", 1..3, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:duration_options", 1..4, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:counter_options", 1..32, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:progress_options", 1..32, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:colour_options", 1..8, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:grouping_options", 1..2, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:speed_options", 1..3, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:fakes_options", 1..2, IfEvent.Op1)
    ifSetEvents("component.xpdrops_setup:skilllist", 0..25, IfEvent.Op1)
}
