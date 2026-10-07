package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.ThievingPhrasing
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.content.skills.thieving.configs.ThievingSafes
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.awardLoot
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Deliberately omit the live lobby safe's leftover searchable floor-trap behavior.
class WallSafeScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(ThievingSafes.LOBBY_SAFE) { crack(it.loc) }
        onPlayerQueueWithArgs<SafeCrackTask>(ThievingSafes.CRACK_QUEUE) { event ->
            finishCrack(event.args)
        }
    }

    private data class SafeCrackTask(val safe: BoundLocInfo)

    private suspend fun ProtectedAccess.crack(safe: BoundLocInfo) {
        arriveDelay()

        if (actionDelay > mapClock) return

        if (player.thievingLvl < ThievingSafes.LEVEL_REQ) {
            mes("You need a Thieving level of ${ThievingSafes.LEVEL_REQ} to crack this safe.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more items.")
            soundSynth("synth.PILLORY_WRONG")
            return
        }

        if (!ThievingLocAction.start(player, safe, locRepo.findExact(safe.coords, safe.shape), 2)) return

        faceLoc(safe)
        anim(ThievingSafes.CRACK_ANIM)
        soundSynth(ThievingSafes.CRACK_SOUND)

        // Queue committed effects so interruption during the crack animation cannot discard them.
        queue(ThievingSafes.CRACK_QUEUE, cycles = 2, args = SafeCrackTask(safe))
    }

    private fun ProtectedAccess.finishCrack(task: SafeCrackTask) {
        if (!ThievingLocAction.canFinish(player, task.safe, locRepo.findExact(task.safe.coords, task.safe.shape))) return
        if (!rollSuccess()) {
            val damage = random.of(ThievingSafes.TRAP_DAMAGE_LOW, ThievingSafes.TRAP_DAMAGE_HIGH)
            mes("You set off a trap and are hurt by the safe's mechanism.")
            queueHit(delay = 1, type = HitType.Typeless, damage = damage)
            return
        }

        anim(ThievingSafes.OPEN_ANIM)
        soundSynth(ThievingSafes.OPEN_SOUND)
        locRepo.change(task.safe, ThievingSafes.LOBBY_SAFE_OPEN, ThievingSafes.OPEN_TICKS)

        val loot = awardLoot(dropRegistry, objRepo, ThievingSafes.LOBBY_SAFE)
        for (name in loot) {
            spam("You find ${ThievingPhrasing.nounPhrase(name)} inside the safe.")
        }

        statAdvance("stat.thieving", ThievingSafes.XP * xpMods.get(player, "stat.thieving"))
    }

    private fun ProtectedAccess.rollSuccess(): Boolean {
        val stethoscope = invTotal(inv, ThievingSafes.STETHOSCOPE) > 0
        val low = if (stethoscope) ThievingRates.SAFE_STETHOSCOPE_LOW else ThievingRates.SAFE_LOW
        val high = if (stethoscope) ThievingRates.SAFE_STETHOSCOPE_HIGH else ThievingRates.SAFE_HIGH
        return statRandom("stat.thieving", low, high, invisibleLvls)
    }
}
