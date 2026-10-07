package dev.openrune.tables

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object ShopCurrencyTable {

    const val KEY = 0
    const val SINGULAR_NAME = 1
    const val PLURAL_NAME = 2
    const val OBJ = 3
    const val VARBIT = 4

    fun shopCurrencies() =
        dbTable("dbtable.shop_currency", serverOnly = true) {
            column("key", KEY, VarType.STRING)
            column("singular_name", SINGULAR_NAME, VarType.STRING)
            column("plural_name", PLURAL_NAME, VarType.STRING)
            column("obj", OBJ, VarType.OBJ)
            column("varbit", VARBIT, VarType.INT)

            row("dbrow.shop_currency_standard_gp") {
                column(KEY, "currency.standard_gp")
                column(SINGULAR_NAME, "coin")
                column(PLURAL_NAME, "coins")
                columnRSCM(OBJ, "obj.coins")
            }

            row("dbrow.shop_currency_stardust") {
                column(KEY, "currency.stardust")
                column(SINGULAR_NAME, "stardust")
                column(PLURAL_NAME, "stardust")
                columnRSCM(OBJ, "obj.star_dust")
            }

            // Guardians of the Rift "Temple Supplies" (Apprentice Felix, npc.gotr_reward_trader).
            // obj.abyssal_pearl (26792) is stackable and its cache name is already the plural
            // "Abyssal pearls", so singular/plural are spelled out rather than left to
            // InvObjShopCurrency's default "${singular}s".
            //
            // The pearl prices are fixed per item (hat 400, top/bottom 350, boots 250, ring of the
            // elements 400, ...) and are NOT the objs' `cost`, so the GOTR content module must
            // register a `ShopOperationMap.costOf("currency.abyssal_pearls") { ... }` override the
            // same way Dusuri's star shop does for currency.stardust.
            row("dbrow.shop_currency_abyssal_pearls") {
                column(KEY, "currency.abyssal_pearls")
                column(SINGULAR_NAME, "abyssal pearl")
                column(PLURAL_NAME, "abyssal pearls")
                columnRSCM(OBJ, "obj.abyssal_pearl")
            }

            // Mahogany Homes' contract-completion currency, spent at Amy's reward shop.
            // Varbit-backed (VarBitShopCurrency), not item-backed, mirroring the
            // varbit.slayer_points precedent in this table's own README example.
            row("dbrow.shop_currency_carpenter_points") {
                column(KEY, "currency.carpenter_points")
                column(SINGULAR_NAME, "carpenter point")
                column(PLURAL_NAME, "carpenter points")
                columnRSCM(VARBIT, "varbit.mahogany_homes_points")
            }

            // Prospector Percy's shop (npc.motherlode_percy). obj.motherlode_nugget (12012) is
            // stackable and its cache name is already "Golden nugget" - singular/plural spelled
            // out the same way GOTR's abyssal_pearls row is, above.
            row("dbrow.shop_currency_motherlode_nugget") {
                column(KEY, "currency.golden_nugget")
                column(SINGULAR_NAME, "golden nugget")
                column(PLURAL_NAME, "golden nuggets")
                columnRSCM(OBJ, "obj.motherlode_nugget")
            }

            row("dbrow.shop_currency_pvp_arena_points") {
                column(KEY, "currency.pvp_arena_points")
                column(SINGULAR_NAME, "PvP Arena reward point")
                column(PLURAL_NAME, "PvP Arena reward points")
                columnRSCM(VARBIT, "varbit.pvpa_points_currency")
            }

            row("dbrow.shop_currency_marks_of_grace") {
                column(KEY, "currency.marks_of_grace")
                column(SINGULAR_NAME, "mark of grace")
                column(PLURAL_NAME, "marks of grace")
                columnRSCM(OBJ, "obj.grace")
            }

            row("dbrow.shop_currency_unidentified_minerals") {
                column(KEY, "currency.unidentified_minerals")
                column(SINGULAR_NAME, "unidentified mineral")
                column(PLURAL_NAME, "unidentified minerals")
                columnRSCM(OBJ, "obj.mguild_minerals")
            }

            row("dbrow.shop_currency_molch_pearls") {
                column(KEY, "currency.molch_pearls")
                column(SINGULAR_NAME, "Molch pearl")
                column(PLURAL_NAME, "Molch pearls")
                columnRSCM(OBJ, "obj.aerial_fishing_pearl")
            }
        }
}
