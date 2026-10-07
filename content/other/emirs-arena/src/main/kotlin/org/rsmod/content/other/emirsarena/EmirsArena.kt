package org.rsmod.content.other.emirsarena

import org.rsmod.api.attr.AttributeKey
import org.rsmod.content.other.emirsarena.duel.Duel
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

object EmirsArena {
    const val AREA: String = "area.emirs_arena"

    val LOBBY: CoordGrid = CoordGrid(3365, 3275, 0)
    const val LOBBY_RADIUS: Int = 2

    const val OPTIONS_INTERFACE: String = "interface.pvp_arena_legacyduel_options"
    const val CONFIRM_INTERFACE: String = "interface.pvp_arena_legacyduel_confirm"
    const val SCOREBOARD_INTERFACE: String = "interface.pvp_arena_scoreboard"

    const val OPTIONS_PREFIX: String = "component.pvp_arena_legacyduel_options:"
    const val CONFIRM_PREFIX: String = "component.pvp_arena_legacyduel_confirm:"
    const val SCOREBOARD_TEXT: String = "component.pvp_arena_scoreboard:text"

    // The native duel stake screen is absent; reuse trade interfaces and their inventory IDs.
    const val STAKE_INTERFACE: String = "interface.trademain"
    const val STAKE_SIDE_INTERFACE: String = "interface.partydrop_side"
    const val STAKE_CONFIRM_INTERFACE: String = "interface.tradeconfirm"
    const val STAKE_PREFIX: String = "component.trademain:"
    const val STAKE_SIDE_ITEMS: String = "component.partydrop_side:items"
    const val STAKE_CONFIRM_PREFIX: String = "component.tradeconfirm:"

    const val STAKE_INV: String = "inv.emirs_arena_stake"

    const val STAKE_DISPLAY_INV: String = "inv.tradeoffer"
    const val STAKE_MODIFIED_MINE_VARBIT: String = "varbit.trade_this_player_removed"
    const val STAKE_MODIFIED_OTHER_VARBIT: String = "varbit.trade_other_player_removed"
    const val OFFER_QUANTITY_VARBIT: String = "varbit.farming_tools_selectedquantity"

    const val RULES_VARP: String = "varp.dueloptions"
    const val ACCEPT_OPTIONS_VARBIT: String = "varbit.pvpa_legacyduel_transmit_iacceptoptions"
    const val ACCEPT_CONFIRM_VARBIT: String = "varbit.pvpa_legacyduel_transmit_iacceptconfirm"

    const val RANK_POINTS_VARBIT: String = "varbit.pvpa_rank_points"
    const val REWARD_POINTS_VARBIT: String = "varbit.pvpa_points_currency"
    const val REWARD_CURRENCY: String = "currency.pvp_arena_points"
    const val REWARD_SHOP_INV: String = "inv.pvpa_shop_inv"

    const val CHALLENGE_SLOT: Int = 1
    const val FIGHT_SLOT: Int = 1

    const val START_QUEUE: String = "queue.emirs_arena_start"
    const val LEAVE_QUEUE: String = "queue.emirs_arena_leave"

    const val TRAPDOOR_LOC: String = "loc.pvpa_battlearea_trapdoor"

    const val DUEL_START_JINGLE: Int = 97
    const val DUEL_WIN_JINGLE: Int = 98

    val WORN_OPTION_SLOTS: List<Int> = listOf(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)

    val FUN_WEAPONS: List<String> =
        listOf(
            "obj.rubber_chicken",
            "obj.flowers_waterfall_quest",
            "obj.flowers_waterfall_quest_red",
            "obj.flowers_waterfall_quest_blue",
            "obj.flowers_waterfall_quest_yellow",
            "obj.flowers_waterfall_quest_purple",
            "obj.flowers_waterfall_quest_orange",
            "obj.flowers_waterfall_quest_mixed",
            "obj.flowers_waterfall_quest_white",
            "obj.flowers_waterfall_quest_black",
            "obj.stale_baguette",
            "obj.golden_tench",
            "obj.birthday_cake",
            "obj.twocats_mouse_toy",
        )

    const val SCOREBOARD_SIZE: Int = 50

    const val SUPPLIES_MESSAGE: String =
        "The supplies chest is only stocked on official PvP Arena worlds."
}

object EmirsArenaAttributes {
    val DUEL: AttributeKey<Duel> = AttributeKey()
    val CHALLENGE_TARGET: AttributeKey<Player> = AttributeKey()
}
