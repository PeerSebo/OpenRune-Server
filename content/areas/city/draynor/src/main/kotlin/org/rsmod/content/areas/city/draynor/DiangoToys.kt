package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.SeqIdCodec
import dev.openrune.types.dbcol.SynthIdCodec
import dev.openrune.types.dbcol.column
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.wildernessLevel
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiangoToys @Inject constructor(private val areaChecker: AreaChecker) : PluginScript() {
    override fun ScriptContext.startup() {
        for (toy in toys) onOpHeld1(toy.item) { play(toy) }
    }

    private suspend fun ProtectedAccess.play(toy: Toy) {
        if (actionDelay > mapClock) return
        if (toy.item == "obj.af15_dragon_claws" && player.coords.wildernessLevel(areaChecker) >= 0) {
            mes("You can't play with these in the Wilderness.")
            return
        }
        val bodyB = player.appearance.bodyType == 1
        val sequence = if (bodyB) toy.animationB else toy.animation
        player.abortRoute()
        if (toy.overhead.isNotEmpty()) say(toy.overhead)
        anim(sequence)
        toy.sound?.let { soundSynth(it) }
        delay(checkNotNull(ServerCacheManager.getAnim(sequence.asRSCM())))
        val finish = if (bodyB) toy.finishB else toy.finish
        if (finish != null) anim(finish)
    }

    internal data class Toy(
        val item: String, val animation: String, val animationB: String,
        val sound: String?, val overhead: String, val finish: String?, val finishB: String?,
    )

    internal companion object {
        val toys: List<Toy> by lazy {
            DbHelper.table("dbtable.diango_toys").map { row ->
                val prefix = "dbcol.diango_toys:"
                fun symbol(name: String, type: RSCMType): String? =
                    row.column(prefix + name, if (type == RSCMType.SYNTH) SynthIdCodec else SeqIdCodec).takeIf { it >= 0 }
                        ?.let { RSCM.getReverseMapping(type, it) }
                Toy(
                    RSCM.getReverseMapping(RSCMType.OBJ, row.column(prefix + "item", ObjIdCodec)),
                    checkNotNull(symbol("animation", RSCMType.SEQ)),
                    checkNotNull(symbol("animation_b", RSCMType.SEQ)),
                    symbol("sound", RSCMType.SYNTH), row.column(prefix + "overhead", StringCodec),
                    symbol("finish", RSCMType.SEQ), symbol("finish_b", RSCMType.SEQ),
                )
            }.also { require(it.map { toy -> toy.item }.distinct().size == 10) }
        }
    }
}
