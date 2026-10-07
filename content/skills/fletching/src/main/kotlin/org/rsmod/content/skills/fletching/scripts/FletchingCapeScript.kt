package org.rsmod.content.skills.fletching.scripts

import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.int
import dev.openrune.types.dbcol.list
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FletchingCapeScript : PluginScript() {
    private var Player.uses by intVarBit("varbit.fletching_cape_uses")
    private var Player.day by intVarBit("varbit.fletching_cape_day")
    private val rewards = DbHelper.table("dbtable.fletching_cape_rewards").single()
    private val items = rewards.list("dbcol.fletching_cape_rewards:items", DbColumnCodec.ItemServerTypeCodec)
    private val dailyUses = rewards.int("dbcol.fletching_cape_rewards:daily_uses")

    override fun ScriptContext.startup() {
        for (cape in listOf("obj.skillcape_fletching", "obj.skillcape_fletching_trimmed")) {
            onOpWorn2(cape) { statBoost("stat.fletching", constant = 1, percent = 0) }
            onOpWorn3(cape) { search() }
            onOpHeld3(cape) { search() }
        }
    }

    internal fun ProtectedAccess.search() {
        val today = ChronoUnit.DAYS.between(Instant.parse("2002-02-27T00:00:00Z"), Instant.now()).toInt()
        if (player.day != today) {
            player.day = today
            player.uses = 0
        }
        if (player.uses >= dailyUses) {
            mes("You have already searched the cape the maximum number of times today.")
            return
        }
        val reward = player.invTransaction(inv) {
            val inventory = select(inv)
            for (item in items) insert { into = inventory; obj = item.id; strictCount = 1 }
        }
        if (reward.failure) {
            mes("You need at least ${items.size} free inventory spaces to search the cape.")
            return
        }
        player.uses++
        mes("You search the cape and find a mithril grapple and a bronze crossbow.")
    }
}
