package org.rsmod.content.bosses.muspah

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VenatorShardScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1("obj.venator_shard") { combine() }
    }

    private suspend fun ProtectedAccess.combine() {
        if (invTotal(inv, "obj.venator_shard") < REQUIRED_SHARDS) {
            mes("You need $REQUIRED_SHARDS venator shards to construct a venator bow.")
            return
        }

        if (!assembleShards(player)) {
            mes("You need a free inventory space to assemble the bow.")
            return
        }
        objbox("obj.venator_bow_uncharged", 400, "The shards assemble into a venator bow.")
    }

    internal fun assembleShards(player: Player): Boolean =
        player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            delete {
                from = inventory
                obj = "obj.venator_shard".asRSCM()
                strictCount = REQUIRED_SHARDS
            }
            insert {
                into = inventory
                obj = "obj.venator_bow_uncharged".asRSCM()
                strictCount = 1
            }
        }.success

    private companion object {
        const val REQUIRED_SHARDS = 5
    }
}
