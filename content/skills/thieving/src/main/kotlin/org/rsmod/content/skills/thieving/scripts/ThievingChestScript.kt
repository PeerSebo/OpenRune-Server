package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.ThievingChest
import org.rsmod.content.skills.thieving.configs.ThievingChests
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.awardLoot
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ThievingChestScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (chest in ThievingChests.all) {
            onOpLoc1(chest.loc) { forceOpen(it.loc, chest) }
            onOpLoc2(chest.loc) { searchForTraps(it.loc, chest) }
        }
        for (empty in ThievingChests.all.map { it.openLoc }.distinct()) {
            onOpLoc1(empty) { mes("It looks like this chest has already been looted.") }
            onOpLoc2(empty) { mes("It looks like this chest has already been looted.") }
        }
        onPlayerQueueWithArgs<ChestOpenTask>(ThievingChests.OPEN_QUEUE) { event ->
            finishOpen(event.args)
        }
    }

    private data class ChestOpenTask(val loc: BoundLocInfo, val chest: ThievingChest)

    private suspend fun ProtectedAccess.forceOpen(loc: BoundLocInfo, chest: ThievingChest) {
        arriveDelay()

        if (!chest.trapped) {
            searchForTraps(loc, chest)
            return
        }

        val damage = chest.trapDamage.calculate(player.hitpoints)
        mes("You'd better search for traps first.")
        anim(ThievingChests.OPEN_ANIM)
        if (damage > 0) {
            mes("You trigger a trap and are badly hurt.")
            queueHit(delay = 1, type = HitType.Typeless, damage = damage)
        }
        delay(2)
    }

    private suspend fun ProtectedAccess.searchForTraps(loc: BoundLocInfo, chest: ThievingChest) {
        arriveDelay()

        if (actionDelay > mapClock) return

        if (player.thievingLvl < chest.levelReq) {
            mes("You need a Thieving level of ${chest.levelReq} to open this chest.")
            return
        }

        if (chest.requiresLockpick && invTotal(inv, ThievingChests.LOCKPICK) == 0) {
            mes("You need a lockpick to open this chest.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more items.")
            soundSynth("synth.PILLORY_WRONG")
            return
        }

        if (!ThievingLocAction.start(player, loc, locRepo.findExact(loc.coords, loc.shape), 2)) return

        if (chest.trapped) {
            mes("You search the chest for traps.")
        }

        if (chest.requiresLockpick) {
            anim(ThievingChests.PICKLOCK_ANIM)
            soundSynth(ThievingChests.PICKLOCK_SOUND)
        } else {
            anim(ThievingChests.OPEN_ANIM)
            soundSynth(ThievingChests.OPEN_SOUND)
        }

        // Queue committed loot and loc changes so interruption during the animation cannot discard them.
        queue(ThievingChests.OPEN_QUEUE, cycles = 2, args = ChestOpenTask(loc, chest))
    }

    private fun ProtectedAccess.finishOpen(task: ChestOpenTask) {
        // Another thief may have emptied this chest while the opening animation was playing.
        if (!ThievingLocAction.canFinish(player, task.loc, locRepo.findExact(task.loc.coords, task.loc.shape))) return
        locRepo.change(task.loc, task.chest.openLoc, task.chest.respawnTicks)

        val rogueChest = task.chest.loc == "loc.wilderness_rogue_chest"
        val loot = if (!rogueChest || vars["varbit.wilderness_diary_medium_complete"] != 0)
            awardLoot(dropRegistry, objRepo, task.chest.loc) else emptyList()
        if (loot.isEmpty()) {
            mes("You find nothing of interest inside.")
        } else {
            mes("You find treasure inside the chest!")
        }

        statAdvance("stat.thieving", task.chest.xp * xpMods.get(player, "stat.thieving"))
    }
}
