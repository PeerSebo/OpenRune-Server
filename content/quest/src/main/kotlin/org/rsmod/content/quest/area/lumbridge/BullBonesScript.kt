package org.rsmod.content.quest.area.lumbridge

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.prayer.PrayerSkillAction
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpObj3
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BullBonesScript @Inject constructor(private val objs: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        val type = checkNotNull(ServerCacheManager.getItem(Bones.asRSCM()))
        // Revision 240 puts Bury in zero-based slot 2: the same op3 ordinary items call Take.
        // The exact binding must win over ObjTakePlugin's default op3 pickup handler.
        check(type.options.getOpOrNull(2) == "Bury")
        onOpObj3(type) { bury(it.obj) }
    }

    internal suspend fun ProtectedAccess.bury(ground: Obj) {
        if (actionDelay >= mapClock && skillAnimDelay >= mapClock) return
        arriveDelay()
        if (!consume(player, ground)) return

        stopAction()
        actionDelay = maxOf(actionDelay, mapClock + 1)
        skillAnimDelay = mapClock + 1
        anim("seq.human_pickupfloor")
        mes("You dig a hole in the ground...")
        soundSynth("synth.BONES_DOWN")
        statAdvance("stat.prayer", Experience)
        publish(SkillingActionCompleteEvent(player, SkillingActionContext.Prayer(
            PrayerSkillAction.BuryComplete(
                itemInternal = Bones,
                ashes = false,
                experienceGranted = Experience,
                catacombsBonePrayerRestore = 3,
            ),
        )))
        mes("You bury the bones.")
    }

    internal fun consume(player: Player, ground: Obj): Boolean {
        if (ground.type != Bones.asRSCM() || !ground.isVisibleTo(player)) return false
        if (player.coords.level != ground.level || player.coords.chebyshevDistance(ground.coords) > 1) {
            return false
        }
        // Deletion must succeed before XP is granted: a repeat packet or a despawn during the
        // arrival delay must not reward a second burial. Inventory capacity never enters the flow.
        return objs.del(ground)
    }

    companion object {
        const val Bones = "obj.bull_bones"

        const val Experience = 40.0
    }
}

internal class BullBonesTakeGate @Inject constructor() : PlayerObjTakeValidateHook {
    override fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String? =
        if (objType.id == BullBonesScript.Bones.asRSCM()) "These bones must be buried where they lie."
        else null
}
