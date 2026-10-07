package org.rsmod.content.skills.thieving.pets

import org.rsmod.content.skills.thieving.configs.PickpocketNpc

internal object RockyPetRates {
    fun pickpocket(target: PickpocketNpc): Int = when (target.displayName.lowercase()) {
        "watchman" -> 134_625
        "paladin" -> 127_056
        "gnome" -> 108_718
        "hero", "vyre", "elf" -> 99_175
        "tzhaar-hur" -> 176_743
        else -> 257_211
    }

    fun stall(loc: String): Int = stallBases[loc] ?: 36_490

    fun denominator(base: Int, level: Int): Int = (base - level.coerceIn(1, 99) * 25).coerceAtLeast(1)

    private val stallBases = mapOf(
        "loc.misc_veg_market" to 206_777,
        "loc.etc_veg_market" to 206_777,
        "loc.port_roberts_market_stall_veg" to 206_777,
        "loc.cakethiefstall" to 124_066,
        "loc.hos_stall_bread" to 124_066,
        "loc.dwarf_market_bakery" to 124_066,
        "loc.fortis_market_stall_bakers" to 124_066,
        "loc.tea_stall" to 68_926,
        "loc.icthalarins_tea_stall" to 68_926,
        "loc.contact_tea_stall" to 68_926,
        "loc.mm_stall_food" to 47_718,
        "loc.mm_stall_crafting" to 47_718,
        "loc.dwarf_market_crafting" to 47_718,
        "loc.mm_stall_general" to 47_718,
        "loc.rockcounter_withcakes" to 47_718,
        "loc.silkthiefstall" to 68_926,
        "loc.prif_marketstall_silk" to 68_926,
        "loc.fortis_market_stall_silk" to 68_926,
        "loc.port_roberts_market_stall_silk" to 68_926,
        "loc.hos_fruit_stall_02" to 124_066,
        "loc.seed_stall" to 36_490,
        "loc.furthiefstall" to 36_490,
        "loc.viking_fur_market" to 36_490,
        "loc.fortis_market_stall_fur" to 36_490,
        "loc.port_roberts_market_stall_fur" to 36_490,
        "loc.viking_fish_market" to 36_490,
        "loc.misc_fish_market" to 36_490,
        "loc.etc_fish_market" to 36_490,
        "loc.fish_stall_warrens" to 36_490,
        "loc.port_roberts_market_stall_fish" to 36_490,
        "loc.xbows_dwarf_market" to 36_490,
        "loc.silverthiefstall" to 36_490,
        "loc.dwarf_market_silver" to 36_490,
        "loc.prif_marketstall_silver" to 36_490,
        "loc.port_roberts_market_stall_silver" to 36_490,
        "loc.spicethiefstall" to 36_490,
        "loc.prif_marketstall_spice" to 36_490,
        "loc.fortis_market_stall_spice" to 36_490,
        "loc.port_roberts_market_stall_spice" to 36_490,
        "loc.mm_stall_magic" to 36_490,
        "loc.mm_stall_scimitar" to 36_490,
        "loc.gemthiefstall" to 36_490,
        "loc.dwarf_market_gems" to 36_490,
        "loc.prif_marketstall_gem" to 36_490,
        "loc.fortis_market_stall_gems" to 36_490,
        "loc.port_roberts_market_stall_gems" to 36_490,
        "loc.tzhaar_shopcounter_gem" to 36_490,
        "loc.tzhaar_shopcounter_ore" to 36_490,
        "loc.port_roberts_market_stall_ore" to 36_490,
        "loc.port_roberts_market_stall_cball" to 36_490,
    )
}
