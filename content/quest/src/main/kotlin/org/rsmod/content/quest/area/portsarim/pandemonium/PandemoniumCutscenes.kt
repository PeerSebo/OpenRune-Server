package org.rsmod.content.quest.area.portsarim.pandemonium

import jakarta.inject.Inject
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.skills.sailing.SailingIntroVoyage
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

internal class PandemoniumCutscenes @Inject constructor(
    private val regions: RegionRepository,
    private val npcs: NpcRepository,
    private val voyage: SailingIntroVoyage,
    private val clock: MapClock,
) {
    suspend fun betray(access: ProtectedAccess, onBetrayed: (ProtectedAccess) -> Unit): Boolean {
        if (voyage.entity(access.player) == null) return false
        val boatRegion = regions.add(boatTemplate) ?: return false
        regions.protect(boatRegion)
        val beachRegion = regions.add(beachTemplate)
        if (beachRegion == null) {
            regions.unprotect(boatRegion)
            return false
        }
        regions.protect(beachRegion)
        val boat = SceneRegion(boatRegion)
        val beach = SceneRegion(beachRegion)
        val actors = mutableListOf<Npc>()
        var landed = false
        var thrownOverboard = false
        val oldExit = access.player.loginExitCoord
        access.player.loginExitCoord = SARIM_RETURN
        try {
            access.closeChat()
            access.lockedScene(
                vantage = boat[BOAT_PLAYER],
                faceAt = boat[BOAT_WILL],
                camera = boat.map(BOAT_CAMERA),
                underFade = {
                    landed = true
                    check(voyage.land(access, boat[BOAT_PLAYER]))
                    actors += actor(access, "npc.sailing_intro_will_cutscene", boat[BOAT_WILL], boat[BOAT_PLAYER])
                    actors += actor(access, "npc.sailing_intro_anne_cutscene", boat[BOAT_ANNE], boat[BOAT_PLAYER])
                },
                teardown = {
                    access.resetAnim()
                    actors.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
                    if (landed) {
                        voyage.land(access, if (thrownOverboard) ISLAND_RETURN else SARIM_RETURN)
                    }
                },
            ) {
                startDialogue { playPandemoniumScene(PandemoniumScene.BETRAYAL) }
                closeChat()
                faceSquare(boat[BOAT_WILL])
                actors.last().anim("seq.sailing_intro_anne_push")
                anim("seq.sailing_intro_human_overboard")
                onBetrayed(this)
                thrownOverboard = true
                player.loginExitCoord = ISLAND_RETURN
                delay(2)
                fadeOverlay(0, 255, 0, 0, 50)
                delay(5)
                telejump(beach[BEACH_PLAYER])
                resetAnim()
                val camera = beach.map(BEACH_CAMERA)
                camMoveToV3(camera.eye, camera.eyeHeight, 232, 100)
                camLookAtV3(camera.lookAt, camera.lookAtHeight, 232, 100)
                faceSquare(beach[BEACH_PLAYER.translate(0, -1)])
                anim("seq.human_onfloor")
                fadeOverlay(0, 0, 0, 255, 50)
                delay(2)
                closeFadeOverlayNow()
                delay(2)
                anim("seq.human_getup_priority_10")
                delay(2)
                startDialogue { playPandemoniumScene(PandemoniumScene.WAKE) }
            }
            return thrownOverboard
        } finally {
            actors.forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
            access.player.loginExitCoord = if (landed) null else oldExit
            regions.unprotect(beachRegion)
            regions.unprotect(boatRegion)
        }
    }

    private fun ProtectedAccess.closeChat() {
        ifCloseSub("interface.chat_left")
        ifCloseSub("interface.chat_right")
    }

    private fun actor(access: ProtectedAccess, symbol: String, coords: CoordGrid, face: CoordGrid): Npc =
        Npc(symbol, coords).also {
            npcs.add(it, Int.MAX_VALUE)
            it.respawns = false
            it.noneMode()
            it.assignSpawnOwner(access.player, clock.cycle)
            it.faceSquare(face)
        }

    companion object {
        val SARIM_RETURN = CoordGrid(3027, 3209, 0)
        val ISLAND_RETURN = CoordGrid(3049, 2970, 0)
        private val BOAT_PLAYER = CoordGrid(1498, 4510, 1)
        private val BOAT_ANNE = CoordGrid(1499, 4511, 1)
        private val BOAT_WILL = CoordGrid(1498, 4512, 1)
        private val BEACH_PLAYER = CoordGrid(3034, 2995, 0)
        private val BOAT_CAMERA = SceneCamera(CoordGrid(1490, 4513, 1), 550, CoordGrid(1510, 4508, 1), 350)
        private val BEACH_CAMERA = SceneCamera(CoordGrid(3026, 3000, 0), 550, CoordGrid(3036, 2993, 0), 350)
        private val boatTemplate = template(184, 560, 3)
        private val beachTemplate = template(376, 368, 4)

        private fun template(x: Int, z: Int, levels: Int): RegionTemplate = RegionTemplate.create {
            for (level in 0 until levels) {
                for (dx in 0 until 8) {
                    for (dz in 0 until 8) {
                        this[dx, dz, level] = ZoneKey(x + dx, z + dz, level)
                    }
                }
            }
        }
    }
}
