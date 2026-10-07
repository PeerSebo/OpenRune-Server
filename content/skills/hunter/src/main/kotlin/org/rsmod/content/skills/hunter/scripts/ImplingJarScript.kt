package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.skills.hunter.configs.NettingParams
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ImplingJarScript @Inject constructor(private val objRepo: ObjRepository, private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        for (index in 1..12) {
            onOpHeld1("obj.ii_captured_impling_$index") { openJar(index, it.slot) }
        }
    }

    internal fun ProtectedAccess.openJar(index: Int, slot: Int) {
        val jar = "obj.ii_captured_impling_$index"

        if (!inv[slot].isType(jar)) return
        if (!invDel(inv, jar, 1, slot = slot, ignoreVirtualStorage = true).success) return
        val rewards = ImplingLoot.roll(index, random)
        if (random.of(10) != 0) invAddOrDrop(objRepo, NettingParams.IMPLING_JAR, 1)
        else mes("The impling jar breaks as you open it.")
        with(ImplingLoot) { award(objRepo, rewards) }
        if (rewards.isEmpty()) mes("You find nothing in the impling jar.")
    }
}
