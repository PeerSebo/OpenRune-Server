package org.rsmod.content.skills.thieving.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.refs.params
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpContentLoc2
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.shops.StallShopAccess
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.ThievingParams
import org.rsmod.content.skills.thieving.configs.ThievingPhrasing
import org.rsmod.content.skills.thieving.configs.ThievingRates
import org.rsmod.content.skills.thieving.configs.ThievingStallGuards
import org.rsmod.content.skills.thieving.pets.RockyPetManager
import org.rsmod.content.skills.thieving.pets.RockyPetRates
import org.rsmod.content.skills.thieving.scripts.ThievingLoot.awardLoot
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class StallThievingScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val dropRegistry: DropTableRegistry,
    private val npcSearch: NpcSearch,
    private val aiInteractions: AiPlayerInteractions,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val rocky: RockyPetManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpContentLoc2("content.stall") { steal(it.loc, it.type) }

        for (empty in ThievingRates.EMPTY_STALLS_WITH_OPS) {
            onOpLoc2(empty) { mes("There is nothing left to steal. Wait for it to restock.") }
        }
    }

    private suspend fun ProtectedAccess.steal(stall: BoundLocInfo, type: ObjectServerType) {
        // Buffer with a leading wait; trailing suspension adds an extra approach tick.
        val cooldownRemaining = actionDelay - mapClock
        if (cooldownRemaining > 0) {
            delay(cooldownRemaining)
        }

        arriveDelay()

        if (!ThievingLocAction.canFinish(player, stall, locRepo.findExact(stall.coords, stall.shape))) return

        val levelReq = type.stallLevelReq
        if (player.thievingLvl < levelReq) {
            mes("You need a Thieving level of $levelReq to steal from this stall.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more items.")
            soundSynth("synth.PILLORY_WRONG")
            return
        }

        actionDelay = mapClock + ThievingRates.STALL_ACTION_DELAY

        anim(ThievingRates.STALL_STEAL_ANIM)

        if (!rollSuccess(type)) {
            mes("You attempt to steal from the stall but you miss your chance...")
            return
        }

        val loot = awardLoot(dropRegistry, objRepo, RSCM.getReverseMapping(RSCMType.LOC, type.id))
        if (loot.isNotEmpty()) {
            StallShopAccess.record(player, RSCM.getReverseMapping(RSCMType.LOC, type.id), stall.coords)
        }
        for (name in loot) {
            spam("You steal ${ThievingPhrasing.nounPhrase(name)}.")
        }
        // Sequence 832 has no baked sound.
        soundSynth(ThievingRates.PICKPOCKET_SUCCESS_SOUND)

        statAdvance("stat.thieving", type.stallXp * xpMods.get(player, "stat.thieving"))
        rocky.roll(this, RockyPetRates.stall(RSCM.getReverseMapping(RSCMType.LOC, type.id)))
        locRepo.change(stall, type.stallEmptyLoc, type.stallRespawnTime)

        alertGuard(stall, type)
    }

    private fun ProtectedAccess.rollSuccess(type: ObjectServerType): Boolean {
        val high = type.stallSuccessHigh
        if (high <= 0) {
            return true
        }
        return statRandom("stat.thieving", type.stallSuccessLow, high, invisibleLvls)
    }

    // Restrict responses to known guards; an Attack op alone would recruit unrelated passers-by.
    private fun ProtectedAccess.alertGuard(stall: BoundLocInfo, type: ObjectServerType) {
        val range = type.stallGuardRange
        if (range <= 0) {
            return
        }
        val responders = ThievingStallGuards.forStall(RSCM.getReverseMapping(RSCMType.LOC, type.id))
        if (responders.isEmpty()) {
            return
        }
        val guard: Npc =
            responders
                .mapNotNull { npcSearch.find(stall.coords, it, range, HuntVis.LineOfSight) }
                .filter { it.type.hasOp(InteractionOp.Op2.slot) }
                .minByOrNull { stall.coords.chebyshevDistance(it.coords) } ?: return
        aiInteractions.interactOp(guard, player, InteractionOp.Op2)
    }

    companion object {
        val ObjectServerType.stallLevelReq: Int by locParam(params.levelrequire)
        val ObjectServerType.stallXp: Double by locXpParam(params.skill_xp)
        val ObjectServerType.stallRespawnTime: Int by locParam(params.respawn_time)
        val ObjectServerType.stallEmptyLoc: ObjectServerType by locParam(params.next_loc_stage)
        val ObjectServerType.stallSuccessLow: Int by locParam(ThievingParams.thieving_success_low)
        val ObjectServerType.stallSuccessHigh: Int by locParam(ThievingParams.thieving_success_high)
        val ObjectServerType.stallGuardRange: Int by locParam(ThievingParams.thieving_guard_range)
    }
}
