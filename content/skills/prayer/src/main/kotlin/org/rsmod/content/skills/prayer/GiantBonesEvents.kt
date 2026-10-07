package org.rsmod.content.skills.prayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerObjTakeValidateHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onOpObj4
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GiantBonesEvents
@Inject
constructor(
    private val objRepo: ObjRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val giantBones =
            checkNotNull(ServerCacheManager.getItem(BONES.asRSCM(RSCMType.OBJ))) {
                "obj.giant_bones does not resolve"
            }
        check(giantBones.options.getOpOrNull(2) == "Bury")
        check(giantBones.options.getOpOrNull(3) == "Take")
        onOpObj3(giantBones) { buryGiantBones(it.obj) }
        onOpObj4(giantBones) { mes(TAKE_MESSAGE) }
    }

    private suspend fun ProtectedAccess.buryGiantBones(ground: Obj) {
        if (actionDelay >= mapClock && skillAnimDelay >= mapClock) {
            return
        }

        if (!giantBoneBuryWarningDisabled) {
            when (
                choice3(
                    "Yes.",
                    1,
                    "Yes, and don't ask me again.",
                    2,
                    "No.",
                    3,
                    title = "Are you sure you want to do that?",
                )
            ) {
                2 -> giantBoneBuryWarningDisabled = true
                3 -> return
                1 -> Unit
                else -> return
            }
        }

        arriveDelay()
        if (!consume(player, ground)) return
        stopAction()
        actionDelay = maxOf(actionDelay, mapClock + BURY_BLOCK_TICKS)
        skillAnimDelay = mapClock + BURY_BLOCK_TICKS

        anim("seq.human_pickupfloor")
        mes("You dig a hole in the ground...")

        soundSynth("synth.BONES_DOWN")

        statAdvance("stat.prayer", GIANT_BONES_XP)
        mes("You bury the bones.")
    }

    internal fun consume(player: Player, ground: Obj): Boolean {
        if (ground.type != BONES.asRSCM(RSCMType.OBJ) || !ground.isVisibleTo(player)) return false
        if (player.coords.level != ground.level || player.coords.chebyshevDistance(ground.coords) > 1) {
            return false
        }
        return objRepo.del(ground)
    }

    private var ProtectedAccess.giantBoneBuryWarningDisabled by
        boolVarBit("varbit.giant_bone_bury_warning_disable")

    internal companion object {
        const val BONES = "obj.giant_bones"
        const val TAKE_MESSAGE = "These bones are too big to carry. You can bury them where they lie."
        const val GIANT_BONES_XP = 150.0
        const val BURY_BLOCK_TICKS = 1
    }
}

internal class GiantBonesTakeGate @Inject constructor() : PlayerObjTakeValidateHook {
    override fun validateTake(player: Player, obj: Obj, objType: ItemServerType): String? =
        if (objType.id == GiantBonesEvents.BONES.asRSCM(RSCMType.OBJ)) GiantBonesEvents.TAKE_MESSAGE
        else null
}
