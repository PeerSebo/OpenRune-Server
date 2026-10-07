package org.rsmod.content.skills.thieving.drops

import dtx.rs.RSDropTable
import dtx.rs.locs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

public object StallDropTables {
    @field:RegisterDropTable
    @JvmField
    public val cannonballStall: RSDropTable<Player, DropRollItem> = RSDropTable(
        tableIdentifier = "Cannonball stall",
        locs = locs("loc.port_roberts_market_stall_cball"),
        mainTable = rsPlayerWeightedTable(total = 22) {
            name("Cannonball stall")
            8 weight "obj.bronze_cannonball" count 1..4
            6 weight "obj.iron_cannonball" count 1..4
            4 weight "obj.mcannonball" count 1..4
            2 weight "obj.mithril_cannonball" count 1..3
            1 weight "obj.adamant_cannonball" count 1..2
            1 weight "obj.rune_cannonball" count 1..2
        },
    )

    @field:RegisterDropTable
    @JvmField
    public val vegStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Vegetable stall",
            locs = locs("loc.misc_veg_market", "loc.etc_veg_market", "loc.port_roberts_market_stall_veg"),
            mainTable =
                rsPlayerWeightedTable(total = 10) {
                    name("Vegetable stall")
                    3 weight "obj.potato" count 1
                    2 weight "obj.cabbage" count 1
                    2 weight "obj.onion" count 1
                    2 weight "obj.tomato" count 1
                    1 weight "obj.garlic" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val bakeryStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Bakery stall",
            locs =
                locs(
                    "loc.cakethiefstall",
                    "loc.hos_stall_bread",
                    "loc.fortis_market_stall_bakers",
                    "loc.dwarf_market_bakery",
                ),
            mainTable =
                rsPlayerWeightedTable(total = 20) {
                    name("Bakery stall")
                    13 weight "obj.cake" count 1
                    5 weight "obj.bread" count 1
                    2 weight "obj.chocolate_slice" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val teaStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Tea stall",
            locs = locs("loc.tea_stall", "loc.icthalarins_tea_stall", "loc.contact_tea_stall"),
            guaranteed = rsPlayerGuaranteedTable { "obj.cup_of_tea" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val monkeyFoodStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Monkey food stall",
            locs = locs("loc.mm_stall_food"),
            guaranteed = rsPlayerGuaranteedTable { "obj.banana" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val craftingStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Crafting stall",
            locs = locs("loc.mm_stall_crafting", "loc.dwarf_market_crafting"),
            mainTable =
                rsPlayerWeightedTable(total = 54) {
                    name("Crafting stall")
                    12 weight "obj.amulet_mould" count 1
                    12 weight "obj.jewl_bracelet_mould" count 1
                    11 weight "obj.necklace_mould" count 1
                    11 weight "obj.ring_mould" count 1
                    6 weight "obj.chisel" count 1
                    2 weight "obj.gold_bar" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val monkeyGeneralStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Monkey general stall",
            locs = locs("loc.mm_stall_general"),
            mainTable =
                rsPlayerWeightedTable(total = 3) {
                    name("Monkey general stall")
                    1 weight "obj.hammer" count 1
                    1 weight "obj.pot_empty" count 1
                    1 weight "obj.tinderbox" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val rockCakeCounter: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Gu'Tanoth counter",
            locs = locs("loc.rockcounter_withcakes"),
            guaranteed = rsPlayerGuaranteedTable { "obj.rockcake" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val silkStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Silk stall",
            locs =
                locs(
                    "loc.silkthiefstall",
                    "loc.prif_marketstall_silk",
                    "loc.fortis_market_stall_silk",
                    "loc.port_roberts_market_stall_silk",
                ),
            guaranteed = rsPlayerGuaranteedTable { "obj.silk" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val wineStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Wine stall",
            locs = locs("loc.rag_market_stall"),
            mainTable =
                rsPlayerWeightedTable(total = 100) {
                    name("Wine stall")
                    39 weight "obj.jug_empty" count 1
                    20 weight "obj.jug_water" count 1
                    17 weight "obj.grapes" count 1
                    13 weight "obj.jug_wine" count 1
                    11 weight "obj.rag_bottle_wine" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val fruitStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Fruit stall",
            locs = locs("loc.hos_fruit_stall_02"),
            mainTable =
                rsPlayerWeightedTable(total = 100) {
                    name("Fruit stall")
                    40 weight "obj.cooking_apple" count 1
                    20 weight "obj.banana" count 1
                    7 weight "obj.strawberry" count 1
                    5 weight "obj.jangerberries" count 1
                    5 weight "obj.lemon" count 1
                    5 weight "obj.redberries" count 1
                    5 weight "obj.pineapple" count 1
                    5 weight "obj.lime" count 1
                    5 weight "obj.macro_triffidfruit" count 1
                    2 weight "obj.golovanova_top" count 1
                    1 weight "obj.papaya" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val seedStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Seed stall",
            locs = locs("loc.seed_stall"),
            mainTable =
                rsPlayerWeightedTable(total = 1000) {
                    name("Seed stall")
                    120 weight "obj.hammerstone_hop_seed" count 1
                    119 weight "obj.potato_seed" count 1
                    119 weight "obj.marigold_seed" count 1
                    118 weight "obj.barley_seed" count 1
                    89 weight "obj.onion_seed" count 1
                    83 weight "obj.asgarnian_hop_seed" count 1
                    71 weight "obj.cabbage_seed" count 1
                    47 weight "obj.yanillian_hop_seed" count 1
                    36 weight "obj.rosemary_seed" count 1
                    35 weight "obj.nasturtium_seed" count 1
                    35 weight "obj.tomato_seed" count 1
                    35 weight "obj.jute_seed" count 1
                    30 weight "obj.sweetcorn_seed" count 1
                    24 weight "obj.krandorian_hop_seed" count 1
                    18 weight "obj.strawberry_seed" count 1
                    12 weight "obj.wildblood_hop_seed" count 1
                    9 weight "obj.watermelon_seed" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val furStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Fur stall",
            locs =
                locs("loc.furthiefstall", "loc.viking_fur_market", "loc.fortis_market_stall_fur", "loc.port_roberts_market_stall_fur"),
            guaranteed = rsPlayerGuaranteedTable { "obj.grey_wolf_fur" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val fishStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Fish stall",
            locs =
                locs(
                    "loc.viking_fish_market",
                    "loc.misc_fish_market",
                    "loc.etc_fish_market",
                    "loc.fish_stall_warrens",
                    "loc.port_roberts_market_stall_fish",
                ),
            mainTable =
                rsPlayerWeightedTable(total = 20) {
                    name("Fish stall")
                    14 weight "obj.raw_salmon" count 1
                    5 weight "obj.raw_tuna" count 1
                    1 weight "obj.raw_lobster" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val crossbowStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Crossbow stall",
            locs = locs("loc.xbows_dwarf_market"),
            mainTable =
                rsPlayerWeightedTable(total = 80) {
                    name("Crossbow stall")
                    46 weight "obj.bolt" count 3
                    18 weight "obj.xbows_crossbow_limbs_bronze" count 3
                    12 weight "obj.xbows_crossbow_stock_wood" count 3
                    3 weight "obj.xbows_crossbow_bolts_mithril" count 3
                    1 weight "obj.xbows_crossbow_limbs_mithril" count 3
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val silverStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Silver stall",
            locs = locs("loc.silverthiefstall", "loc.prif_marketstall_silver", "loc.port_roberts_market_stall_silver"),
            mainTable =
                rsPlayerWeightedTable(total = 20) {
                    name("Silver stall")
                    16 weight "obj.silver_ore" count 1
                    3 weight "obj.silver_bar" count 1
                    1 weight "obj.tiara" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val keldagrimSilverStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Silver stall (Keldagrim)",
            locs = locs("loc.dwarf_market_silver"),
            guaranteed = rsPlayerGuaranteedTable { "obj.silver_ore" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val spiceStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Spice stall",
            locs =
                locs(
                    "loc.spicethiefstall",
                    "loc.prif_marketstall_spice",
                    "loc.fortis_market_stall_spice",
                    "loc.port_roberts_market_stall_spice",
                ),
            guaranteed = rsPlayerGuaranteedTable { "obj.spicespot" count 1 },
        )

    @field:RegisterDropTable
    @JvmField
    public val magicStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Magic stall",
            locs = locs("loc.mm_stall_magic"),
            mainTable =
                rsPlayerWeightedTable(total = 20) {
                    name("Magic stall")
                    6 weight "obj.airrune" count 1
                    6 weight "obj.earthrune" count 1
                    6 weight "obj.firerune" count 1
                    1 weight "obj.lawrune" count 1
                    1 weight "obj.naturerune" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val scimitarStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Scimitar stall",
            locs = locs("loc.mm_stall_scimitar"),
            mainTable =
                rsPlayerWeightedTable(total = 40) {
                    name("Scimitar stall")
                    23 weight "obj.iron_scimitar" count 1
                    13 weight "obj.steel_scimitar" count 1
                    3 weight "obj.mithril_scimitar" count 1
                    1 weight "obj.adamant_scimitar" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val gemStall: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Gem stall",
            locs =
                locs(
                    "loc.gemthiefstall",
                    "loc.dwarf_market_gems",
                    "loc.prif_marketstall_gem",
                    "loc.fortis_market_stall_gems",
                    "loc.port_roberts_market_stall_gems",
                    "loc.tzhaar_shopcounter_gem",
                ),
            mainTable =
                rsPlayerWeightedTable(total = 128) {
                    name("Gem stall")
                    105 weight "obj.uncut_sapphire" count 1
                    17 weight "obj.uncut_emerald" count 1
                    5 weight "obj.uncut_ruby" count 1
                    1 weight "obj.uncut_diamond" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val oreCounter: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Shop counter (ore)",
            locs = locs("loc.tzhaar_shopcounter_ore", "loc.port_roberts_market_stall_ore"),
            mainTable =
                rsPlayerWeightedTable(total = 80) {
                    name("Shop counter (ore)")
                    25 weight "obj.iron_ore" count 1
                    15 weight "obj.coal" count 1
                    10 weight "obj.silver_ore" count 1
                    10 weight "obj.gold_ore" count 1
                    10 weight "obj.mithril_ore" count 1
                    8 weight "obj.adamantite_ore" count 1
                    2 weight "obj.runite_ore" count 1
                },
        )
}
