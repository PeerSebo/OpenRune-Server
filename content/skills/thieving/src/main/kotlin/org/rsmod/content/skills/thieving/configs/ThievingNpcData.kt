package org.rsmod.content.skills.thieving.configs

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.NpcTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.game.stat.PlayerStatMap

internal object ThievingNpcData {
    private val profiles: Map<String, Profile> by lazy {
        val rows = DbHelper.table("dbtable.thieving_pickpockets")
            .sortedBy { it.column("dbcol.thieving_pickpockets:order", IntCodec) }
        require(rows.isNotEmpty()) { "Thieving profiles are missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.thieving_pickpockets:order", IntCodec) } == rows.indices.toList())
        rows.associate { row ->
            val symbol = RSCM.getReverseMapping(RSCMType.DBROW, row.id)
            symbol to Profile(
                base = PickpocketNpc(
                    npcs = row.npcSymbols("npcs"),
                    displayName = row.column("dbcol.thieving_pickpockets:display_name", StringCodec),
                    levelReq = row.int("level"),
                    xp = row.int("xp_fine") / PlayerStatMap.XP_FINE_PRECISION.toDouble(),
                    low = row.int("low"),
                    high = row.int("high"),
                    damage = row.int("damage"),
                    failBark = row.column("dbcol.thieving_pickpockets:fail_bark", StringCodec),
                    pouch = row.pouch(),
                    loot = ThievingPickpocketLoot.callback(symbol),
                    inArdougne = row.bool("in_ardougne"),
                    prifddinas = row.bool("prifddinas"),
                    pickpocketOp = row.int("pickpocket_op"),
                    blackjack = row.bool("blackjack"),
                    stunMovement = row.int("stun_movement"),
                    stunAction = row.int("stun_action"),
                    actionDelay = row.int("action_delay"),
                    directCoins = row.int("direct_coins").takeUnless { it == -1 },
                    usesCoinPouches = row.bool("uses_coin_pouches"),
                    koXp = row.int("ko_xp_fine") / PlayerStatMap.XP_FINE_PRECISION.toDouble(),
                    alwaysDamagesWithoutIceGloves = row.bool("always_damages_without_ice_gloves"),
                ),
                additionalNpcs = row.additionalNpcs(),
            )
        }.also { profiles ->
            require(profiles.size == rows.size)
            for ((symbol, profile) in profiles) {
                val target = profile.base
                require(target.npcs.isNotEmpty()) { "Thieving target has no NPCs: $symbol" }
                require(target.levelReq in 1..99 && target.pickpocketOp in 1..5) { symbol }
                require(target.xp >= 0 && target.koXp >= 0 && target.low <= target.high) { symbol }
                require(target.damage >= 0 && target.actionDelay > 0) { symbol }
                require(target.stunMovement >= 0 && target.stunAction >= 0) { symbol }
                require(target.directCoins == null || target.directCoins > 0) { symbol }
                require(target.directCoins == null || target.pouch == null) { symbol }
            }
            val npcs = profiles.values.flatMap { it.base.npcs + it.additionalNpcs }
            require(npcs.distinct().size == npcs.size) { "Duplicate thieving NPC bindings." }
        }
    }

    val all: List<PickpocketNpc> by lazy {
        profiles.values.map {
            if (it.additionalNpcs.isEmpty()) it.base
            else it.base.copy(npcs = it.base.npcs + it.additionalNpcs)
        }
    }

    fun profile(row: String): PickpocketNpc = profiles.getValue(row).base

    fun additionalNpcs(row: String): List<String> = profiles.getValue(row).additionalNpcs

    private fun DbHelper.int(name: String): Int = column("dbcol.thieving_pickpockets:$name", IntCodec)

    private fun DbHelper.bool(name: String): Boolean =
        column("dbcol.thieving_pickpockets:$name", BooleanCodec)

    private fun DbHelper.npcSymbols(name: String): List<String> =
        multiColumn("dbcol.thieving_pickpockets:$name", NpcTypeCodec)
            .map { RSCM.getReverseMapping(RSCMType.NPC, it.id) }

    private fun DbHelper.additionalNpcs(): List<String> = try {
        npcSymbols("additional_npcs")
    } catch (_: DbException.MissingColumn) {
        emptyList()
    }

    private fun DbHelper.pouch(): String? = try {
        val item = column("dbcol.thieving_pickpockets:pouch", ItemServerTypeCodec)
        RSCM.getReverseMapping(RSCMType.OBJ, item.id)
    } catch (_: DbException.MissingColumn) {
        null
    }

    private data class Profile(val base: PickpocketNpc, val additionalNpcs: List<String>)
}
