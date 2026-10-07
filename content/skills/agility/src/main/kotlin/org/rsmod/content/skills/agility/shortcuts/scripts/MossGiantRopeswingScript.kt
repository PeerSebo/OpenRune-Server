package org.rsmod.content.skills.agility.shortcuts.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.scripts.approachMount
import org.rsmod.content.skills.agility.scripts.leapTo
import org.rsmod.content.skills.agility.shortcuts.configs.MossGiantRopeswings
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MossGiantRopeswingScript
@Inject
constructor(private val xpMods: XpModifiers, private val worldRepo: WorldRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.tree_ropeswing1") {
            swing(it.loc, MossGiantRopeswings.ROPE1_MOUNT, MossGiantRopeswings.ROPE1_DEST)
        }
        onOpLoc1("loc.tree_ropeswing2") {
            swing(it.loc, MossGiantRopeswings.ROPE2_MOUNT, MossGiantRopeswings.ROPE2_DEST)
        }
    }

    private suspend fun ProtectedAccess.swing(rope: BoundLocInfo, mount: CoordGrid, dest: CoordGrid) {
        arriveDelay()
        if (!meetsShortcutReq(MossGiantRopeswings.LEVEL_REQ, "use this shortcut")) {
            return
        }
        // Require the rope mount before moving across the water to a fixed destination.
        if (!approachMount(mount)) {
            return
        }
        locAnim(worldRepo, rope, "seq.ropeswing_long")
        leapTo(
            dest = dest,
            busyTicks = MossGiantRopeswings.SWING_TICKS,
            seq = "seq.human_ropeswing_long",
            synth = "synth.SWING_ACROSS",
            face = dest,
        )
        awardShortcutXp(MossGiantRopeswings.XP, xpMods)
    }
}
