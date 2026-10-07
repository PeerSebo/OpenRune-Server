package org.rsmod.content.areas.city.draynor

import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.column
import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.map.Direction
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorBankFacing @Inject constructor(private val npcs: NpcRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        val byType = DbHelper.table("dbtable.draynor_bankers").groupBy {
            it.column("dbcol.draynor_bankers:npc", NpcIdCodec)
        }
        for ((type, rows) in byType) {
            val directions = rows.associate {
                it.column("dbcol.draynor_bankers:tile", CoordGridCodec) to
                    Direction.valueOf(it.column("dbcol.draynor_bankers:facing", StringCodec))
            }
            fun face(npc: Npc) {
                if (npc.type.id == type) directions[npc.spawnCoords]?.let(npc::respawnDirection)
            }
            onEvent<NpcStateEvents.Spawn>(type) { face(npc) }
            onGameStartup { directions.keys.forEach { npcs.findAll(it).forEach(::face) } }
        }
    }
}
