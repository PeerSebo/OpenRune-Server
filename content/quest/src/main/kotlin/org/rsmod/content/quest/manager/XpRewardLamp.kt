package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter

// Lamp subcomponents are grid slots resolved through enum 5993, not stat IDs.
public object XpRewardLamp {
    public const val DEFAULT_TITLE: String = "Choose the stat you wish to be advanced!"

    private val ALL_GRID_SLOTS: IntRange = 0..23

    public suspend fun choose(
        access: ProtectedAccess,
        title: String = DEFAULT_TITLE,
        selectable: IntRange = ALL_GRID_SLOTS,
    ): String? {
        VarPlayerIntMapSetter.set(access.player, "varp.if1", 0)
        VarPlayerIntMapSetter.set(access.player, "varp.if2", -1)

        access.ifOpenMainModal("interface.xpreward")
        access.ifSetEvents("component.xpreward:universe", selectable, IfEvent.PauseButton)
        access.ifSetText("component.xpreward:title", title)
        access.hideUnselectableCells(selectable)

        val input = access.pauseButton()
        access.ifCloseSub("interface.xpreward")

        val gridSlot = input.subcomponent
        if (gridSlot !in selectable) {
            return null
        }
        val stat = NamedEnums.xpreward_slot_to_stat[gridSlot] ?: return null
        return RSCM.getReverseMapping(RSCMType.STAT, stat.id)
    }

    private fun ProtectedAccess.hideUnselectableCells(selectable: IntRange) {
        for (slot in ALL_GRID_SLOTS) {
            if (slot in selectable) {
                continue
            }
            val stat = NamedEnums.xpreward_slot_to_stat[slot] ?: continue
            val suffix = STAT_CELL_SUFFIX[stat.id] ?: continue
            ifSetHide("component.xpreward:$suffix", hide = true)
        }
    }

    private val STAT_CELL_SUFFIX: Map<Int, String> =
        mapOf(
            0 to "attack",
            1 to "defence",
            2 to "strength",
            3 to "hitpoints",
            4 to "ranged",
            5 to "prayer",
            6 to "magic",
            7 to "cooking",
            8 to "woodcutting",
            9 to "fletching",
            10 to "fishing",
            11 to "firemaking",
            12 to "crafting",
            13 to "smithing",
            14 to "mining",
            15 to "herblore",
            16 to "agility",
            17 to "thieving",
            18 to "slayer",
            19 to "farming",
            20 to "runecraft",
            21 to "hunter",
            22 to "construction",
            23 to "sailing",
        )

    public fun displayName(stat: String): String {
        val type =
            ServerCacheManager.getStats(RSCM.getRSCM(stat)) ?: error("No stat found for $stat")
        return type.displayName
    }
}
