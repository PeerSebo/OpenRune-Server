package org.rsmod.content.quest.manager

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onCommand
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class QuestCommands
@Inject
constructor(
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("queststage") {
            desc = "Jump a quest to a stage without rewards (ex: ::queststage demonslayer 2)"
            invalidArgs = "Use as ::queststage <quest key> <stage>"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val key = args.firstOrNull()?.trim()?.lowercase().orEmpty()
                val stage = args.getOrNull(1)?.toIntOrNull()
                val quest = Quest.get(key) ?: Quest.get("quest_$key")
                if (quest == null) {
                    player.mes("No quest registered under '$key'.")
                    return@cheat
                }
                if (stage == null || stage !in 0..quest.maxSteps) {
                    player.mes("Use ::queststage $key <stage from 0 to ${quest.maxSteps}>.")
                    return@cheat
                }
                quest.jumpToStage(player, stage)
                player.mes("${quest.displayName} is now at stage ${quest.getQuestStage(player)} of ${quest.maxSteps}.")
            }
        }

        onCommand("testloc") {
            desc = "Spawn a loc east of you with a rotation (ex: ::testloc qip_ds_wizards_key_wardrobe_magic 0)"
            invalidArgs = "Use as ::testloc <loc name> <angle 0-3>"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val name = args.firstOrNull()?.removePrefix("loc.")
                val type = name?.let { runCatching { ServerCacheManager.getObject("loc.$it".asRSCM(RSCMType.LOC)) }.getOrNull() }
                if (type == null) {
                    player.mes("Use ::testloc <valid loc name> <angle 0-3>.")
                    return@cheat
                }
                val angleId = if (args.size < 2) 0 else args[1].toIntOrNull()
                val angle = LocAngle.entries.firstOrNull { it.id == angleId }
                if (angle == null) {
                    player.mes("Angle must be 0-3.")
                    return@cheat
                }
                val tile = player.coords.translate(1, 0)
                val loc = locRepo.add(tile, "loc.$name", TEST_LOC_TICKS, angle, LocShape.CentrepieceStraight)
                worldRepo.locAnim(loc, "seq.qip_ds_wardrobe_appear")
                player.mes("Spawned loc.$name east of you at rotation $angleId (${angle.name}) for $TEST_LOC_TICKS ticks.")
            }
        }
    }

    private companion object {
        const val TEST_LOC_TICKS = 25
    }
}
