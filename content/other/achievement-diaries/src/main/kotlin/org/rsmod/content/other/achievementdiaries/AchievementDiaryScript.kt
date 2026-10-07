package org.rsmod.content.other.achievementdiaries

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.events.skilling.SkillingProductSource
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import toRs

class AchievementDiaryScript @Inject constructor(private val areas: AreaChecker) : PluginScript() {
    override fun ScriptContext.startup() {
        DiaryData.tasks
        DiaryData.tiers
        onPlayerLogin { DiaryProgress.synchronize(player) }
        onIfOpen("interface.area_task") {
            player.ifSetEvents("component.area_task:taskbox", 0..11, IfEvent.Op1)
        }
        onIfOverlayButton("component.area_task:taskbox") { event ->
            if (event.comsub == DiaryData.easy.region) openJournal()
            else mes("This region's diary tasks are not implemented yet.")
        }
        onEvent<PlayerActionCompleteEvent> {
            if (count > 0) DiaryProgress.record(player, action.name, target, coords, areas::inArea, count)
        }
        onEvent<SkillingActionCompleteEvent> {
            val product = context as? SkillingActionContext.Product ?: return@onEvent
            if (product.isBonus || product.count <= 0) return@onEvent
            val source = product.source
            val action: String
            val target: String
            val coords: CoordGrid
            when (source) {
                is SkillingProductSource.Fishing -> {
                    action = "Fishing"
                    target = source.productType.internalName
                    coords = source.spot.coords
                }
                is SkillingProductSource.Mining -> {
                    if (product.item.startsWith("obj.uncut_")) return@onEvent
                    action = "Mining"
                    target = source.rockData.oreItem?.internalName ?: return@onEvent
                    coords = source.rock.coords
                }
                is SkillingProductSource.Woodcutting -> {
                    action = "Woodcutting"
                    target = source.productType.internalName
                    coords = source.tree.coords
                }
            }
            DiaryProgress.record(player, action, target, coords, areas::inArea)
        }
        onPlayerCoordsChanged { DiaryProgress.resetStagesOutsideArea(player, areas::inArea) }
    }

    private fun ProtectedAccess.openJournal() {
        val lines = buildList {
            for (tier in DiaryData.tiers) {
                val completed = DiaryProgress.completedCount(player, tier)
                val total = DiaryProgress.taskCount(tier)
                add("<col=000080>${tier.label} tasks: $completed/$total</col>")
                add("")
                for (task in DiaryData.tasks(tier).filter { it.bit >= 0 }) {
                    val text =
                        if (DiaryProgress.isComplete(player, task)) "<str>${task.description}</str>"
                        else task.description
                    addAll(text.toRs(wrapAt = 64, inheritPreviousTags = true).split("<br>"))
                    if (task.unavailable.isNotEmpty()) {
                        addAll("<col=666666>${task.unavailable}</col>".toRs(
                            wrapAt = 64, inheritPreviousTags = true).split("<br>"))
                    }
                }
                add("")
                val rewardStatus =
                    if (player.vars[tier.rewardVar] > 0) "<col=007f00>${tier.label} rewards claimed.</col>"
                    else "Claim your ${tier.label} rewards from Hatius Cosaintus."
                add(rewardStatus)
                add("")
            }
            add("Elite tasks are not implemented yet.")
        }
        ifSetText("component.questjournal:title", "<col=7f0000>${DiaryData.easy.name} Diary</col>")
        runClientScript("clientscript.[clientscript,quest_journal_reset]")
        lines.forEachIndexed { index, line ->
            ifSetText("component.questjournal:qj${index + 1}", line)
        }
        ifOpenOverlay("interface.questjournal")
        runClientScript("clientscript.[clientscript,script2523]", 0, lines.size)
        ifSetHide("component.questjournal:switch", true)
    }
}
