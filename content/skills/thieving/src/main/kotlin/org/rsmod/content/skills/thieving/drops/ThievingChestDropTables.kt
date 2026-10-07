package org.rsmod.content.skills.thieving.drops

import dtx.rs.RSDropTable
import dtx.rs.locs
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.RegisterDropTable
import org.rsmod.api.droptable.rsPlayerGuaranteedTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.game.entity.Player

public object ThievingChestDropTables {
    @field:RegisterDropTable
    @JvmField
    public val roguesCastleChest: RSDropTable<Player, DropRollItem> = RSDropTable(
        tableIdentifier = "Rogues' Castle chest (PR 216)",
        locs = locs("loc.wilderness_rogue_chest"),
        guaranteed = rsPlayerGuaranteedTable {
            "obj.coins" count 1000
            "obj.dragonstone" count 1
        },
    )

    @field:RegisterDropTable
    @JvmField
    public val tenCoinChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (10 coins)",
            locs = locs("loc.trapchest1"),
            guaranteed = rsPlayerGuaranteedTable { "obj.coins" count 10 },
        )

    @field:RegisterDropTable
    @JvmField
    public val natureRuneChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (nature rune)",
            locs = locs("loc.trapchest2"),
            guaranteed =
                rsPlayerGuaranteedTable {
                    "obj.naturerune" count 1
                    "obj.coins" count 3
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val fiftyCoinChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (50 coins)",
            locs = locs("loc.trapchest3"),
            guaranteed = rsPlayerGuaranteedTable { "obj.coins" count 50 },
        )

    @field:RegisterDropTable
    @JvmField
    public val steelArrowtipChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (steel arrowtips)",
            locs = locs("loc.pickchest3"),
            guaranteed =
                rsPlayerGuaranteedTable {
                    "obj.steel_arrowheads" count 5
                    "obj.coins" count 20
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val bloodRuneChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (blood runes)",
            locs = locs("loc.trapchest4"),
            guaranteed =
                rsPlayerGuaranteedTable {
                    "obj.bloodrune" count 2
                    "obj.coins" count 500
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val ardougneCastleChest: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Chest (Ardougne Castle)",
            locs = locs("loc.trapchest5"),
            guaranteed =
                rsPlayerGuaranteedTable {
                    "obj.coins" count 1000
                    "obj.raw_shark" count 1
                    "obj.adamantite_ore" count 1
                    "obj.uncut_sapphire" count 1
                },
        )

    @field:RegisterDropTable
    @JvmField
    public val roguesDenLobbySafe: RSDropTable<Player, DropRollItem> =
        RSDropTable(
            tableIdentifier = "Wall safe (Rogues' Den lobby)",
            locs = locs("loc.roguesden_walldecor_safe"),
            mainTable =
                rsPlayerWeightedTable(total = 128) {
                    name("Wall safe")
                    56 weight "obj.coins" count 20
                    25 weight "obj.coins" count 10
                    21 weight "obj.coins" count 40
                    12 weight "obj.uncut_sapphire" count 1
                    9 weight "obj.uncut_emerald" count 1
                    4 weight "obj.uncut_ruby" count 1
                    1 weight "obj.uncut_diamond" count 1
                },
        )
}
