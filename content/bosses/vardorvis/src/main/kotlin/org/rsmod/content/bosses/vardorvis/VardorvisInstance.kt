package org.rsmod.content.bosses.vardorvis

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.instances.withInstanceLeaveTransition
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.script.onOpLoc5
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class VardorvisInstance @Inject constructor(registry: BossInstanceRegistry) :
    InstanceScript(registry) {

    private var Player.pendingAwakened by boolVarp("varp.vardorvis_pending_awakened")

    override fun settingsRow(): String = "dbrow.instance_vardorvis"

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onEnterPrelude { result, enter ->
            withInstanceEnterTransition(InstanceEnterTransition(message = ENTER_MESSAGE), enter)
            if (result is InstanceManager.Result.Created && player.pendingAwakened) {
                val npc = manager.npcsForInstance(result.session.id).firstOrNull()
                if (npc != null && invDel(inv, AWAKENERS_ORB, 1).success) markAwakened(npc)
            }
        }

        onEnterObject { enterInstance() }
        onExitObject { defaultLeaveFlow() }

        ARENA_ESCAPE_LOCS.forEach { loc -> onOpLoc5(loc) { quickEscape() } }
    }

    private suspend fun ProtectedAccess.quickEscape() {
        withInstanceLeaveTransition { defaultLeaveFlow() }
    }

    private suspend fun ProtectedAccess.enterInstance() {
        val owned = player.uuid?.let { manager.sessionOwnedBy(key, it) }
        player.pendingAwakened = owned == null && chooseAwakened()
        try {
            defaultInstanceEntry()
        } finally {
            player.pendingAwakened = false
        }
    }

    private suspend fun ProtectedAccess.chooseAwakened(): Boolean {
        if (AWAKENERS_ORB !in inv) {
            return false
        }
        val useOrb =
            choice2(
                "Yes - consume an Awakener's orb.",
                true,
                "No - fight the normal encounter.",
                false,
                title = "Use an Awakener's orb to fight an Awakened Vardorvis?",
            )
        if (!useOrb) {
            return false
        }
        return true
    }

    private fun markAwakened(npc: Npc) {
        npc.vars["varn.awakened_state"] = 1
        npc.vars["varn.skip_killcount"] = 1
        npc.baseHitpointsLvl *= 2
        npc.hitpoints = npc.baseHitpointsLvl
    }

    private companion object {
        private const val ARENA_REGION = 4405

        private const val AWAKENERS_ORB = "obj.dt2_awakeners_orb"

        private const val ENTER_MESSAGE = "You enter the ritual site."

        private val ARENA_ESCAPE_LOCS =
            arrayOf(
                "loc.vardorvis_escape_1",
                "loc.vardorvis_escape_2",
                "loc.vardorvis_escape_3",
            )

        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = listOf(ARENA_REGION),
                npcSpawns = listOf(InstanceNpc("npc.vardorvis", CoordGrid(1128, 3417, 0))),
            )
    }
}
