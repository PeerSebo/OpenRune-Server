package org.rsmod.content.minigames.pyramidplunder.configs

import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper

internal object PlunderRoomData {
    val rooms: List<PlunderRoom> by lazy {
        validate(DbHelper.table("dbtable.plunder_rooms").map(::read))
    }

    internal fun read(row: DbHelper): PlunderRoom {
        val doorXp = try {
            row.required("door_xp", IntCodec).toDouble()
        } catch (_: DbException.MissingColumn) {
            null
        }
        return PlunderRoom(
            room = row.required("room", IntCodec),
            levelReq = row.required("level", IntCodec),
            urnXp = row.required("urn_xp", IntCodec).toDouble(),
            chestXp = row.required("chest_xp", IntCodec).toDouble(),
            doorXp = doorXp,
            sarcophagusXp = row.required("sarcophagus_xp", IntCodec).toDouble(),
            sarcophagusOpenLow = row.required("sarcophagus_low", IntCodec),
            sarcophagusOpenHigh = row.required("sarcophagus_high", IntCodec),
            mummySpawnChance = row.required("mummy_chance", IntCodec),
            sceptreDenominator = row.required("sceptre_denominator", IntCodec),
            landing = row.required("landing", CoordGridCodec),
            trapBand = PlunderLocs.TrapBand(
                PlunderLocs.TrapAxis.valueOf(row.required("trap_axis", StringCodec)),
                row.required("trap_low", IntCodec),
                row.required("trap_high", IntCodec),
            ),
        )
    }

    internal fun validate(rows: List<PlunderRoom>): List<PlunderRoom> {
        val ordered = rows.sortedBy { it.room }
        require(ordered.map { it.room } == (1..8).toList()) { "Pyramid Plunder requires eight distinct room definitions." }
        for (room in ordered) {
            require(room.levelReq in 1..99 && room.urnXp >= 0 && room.chestXp >= 0 && room.sarcophagusXp >= 0)
            require(room.doorXp == null || room.doorXp >= 0)
            require(room.sarcophagusOpenLow <= room.sarcophagusOpenHigh)
            require(room.mummySpawnChance in 0..100 && room.sceptreDenominator > 0)
            require(room.landing.level == 0 && room.trapBand.hi == room.trapBand.lo + 1)
            val landingAxis = if (room.trapBand.axis == PlunderLocs.TrapAxis.X) room.landing.x else room.landing.z
            require(landingAxis !in room.trapBand.lo..room.trapBand.hi)
        }
        return ordered
    }

    private fun <T, R> DbHelper.required(name: String, codec: DbColumnCodec<T, R>): R {
        val column = getColumn("dbcol.plunder_rooms:$name")
        require(column.size == 1) { "Plunder room row $id column $name must have one value." }
        return column.decodeAt(0, codec)
    }
}
