package org.rsmod.content.quest.area.goblinvillage

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.misthalin.SceneCamera
import org.rsmod.content.quest.area.misthalin.SceneRegion
import org.rsmod.content.quest.area.misthalin.lockedScene
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

internal object GrubfootScene {
    val Vantage: CoordGrid = CoordGrid(2957, 3513)

    val WartfaceTile: CoordGrid = CoordGrid(2958, 3511)
    val BentnozeTile: CoordGrid = CoordGrid(2958, 3512)

    val WartfaceFaces: CoordGrid = CoordGrid(2957, 3511)
    val BentnozeFaces: CoordGrid = CoordGrid(2957, 3512)

    val GrubfootSpawn: CoordGrid = CoordGrid(2954, 3511)

    val GrubfootModels: CoordGrid = CoordGrid(2955, 3511)

    val GrubfootAttends: CoordGrid = CoordGrid(2956, 3511)

    val GrubfootCurtainFront: CoordGrid = CoordGrid(2954, 3511)

    val GrubfootCurtain: CoordGrid = CoordGrid(2953, 3511)

    val Camera: SceneCamera =
        SceneCamera(
            eye = CoordGrid(2961, 3514),
            eyeHeight = 450,
            lookAt = CoordGrid(2957, 3512),
            lookAtHeight = 150,
        )

    val HutDoorWest: CoordGrid = CoordGrid(2957, 3509)
    val HutDoorEast: CoordGrid = CoordGrid(2958, 3509)

    val CaveEntrance: CoordGrid = CoordGrid(2947, 3506)

    val NoopSwaps: List<Triple<CoordGrid, String, String>> =
        listOf(
            Triple(
                HutDoorWest,
                "loc.goblin_outpost_poordoor_double_inner",
                "loc.goblin_outpost_poordoor_double_inner_noop",
            ),
            Triple(
                HutDoorEast,
                "loc.goblin_outpost_poordoor_doubler_inner",
                "loc.goblin_outpost_poordoor_doubler_inner_noop",
            ),
            Triple(
                CaveEntrance,
                "loc.xmas20_goblin_cave_entrance",
                "loc.xmas20_goblin_cave_entrance_noop",
            ),
        )
}

internal class GoblinDiplomacyCutscene(
    private val quest: Quest,
    private val regionRepo: RegionRepository,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
) {

    // Release held regions on login too; removal may bypass the scene finally block.
    private val sceneRegions = HashMap<Long, Region>()

    fun ScriptContext.register() {
        onPlayerLogin { discardLeakedRegion(player) }
    }

    private fun discardLeakedRegion(player: Player) {
        val region = player.uuid?.let(sceneRegions::remove) ?: return
        regionRepo.unprotect(region)
    }

    suspend fun handIn(access: ProtectedAccess, colour: GoblinMailColour) {
        lateinit var grubfoot: Npc

        val openingLeaf = GoblinMailColour.leafForVis(access.gobdipGrubfootVis)
        val restore = mutableListOf<LocInfo>()

        val cast = mutableListOf<Npc>()

        var handedOver = true

        val returnTo = access.coords

        access.closeChat()

        access.scene(
            vantage = GrubfootScene.Vantage,
            faceAt = GrubfootScene.WartfaceTile,
            camera = GrubfootScene.Camera,
            returnTo = returnTo,
            underFade = { r ->
                val wartface = spawn(WartfaceNpc, r[GrubfootScene.WartfaceTile])
                val bentnoze = spawn(BentnozeNpc, r[GrubfootScene.BentnozeTile])
                grubfoot = spawn(openingLeaf, r[GrubfootScene.GrubfootSpawn])
                cast += listOf(wartface, bentnoze, grubfoot)

                wartface.faceSquare(r[GrubfootScene.WartfaceFaces])
                bentnoze.faceSquare(r[GrubfootScene.BentnozeFaces])
                grubfoot.faceSquare(r[GrubfootScene.GrubfootAttends])

                restore += swapNoopLocs(r)

                player.midiSong(
                    GoblinDiplomacyState.VillageMusic,
                    fadeOutSpeed = MusicFadeOutSpeed,
                    fadeInDelay = MusicFadeInDelay,
                )
            },
            teardown = {
                cast.forEach(::despawn)
                restore.forEach { locRepo.add(it, Int.MAX_VALUE) }
            },
        ) { r ->
            startDialogue {
                wartfaceLine(neutral, "Grubfoot!")

                access.closeChat()
                grubfoot.walk(
                    listOf(r[GrubfootScene.GrubfootModels], r[GrubfootScene.GrubfootAttends])
                )
                grubfootLine(openingLeaf, quiz, "Yes General Wartface?")

                wartfaceLine(neutral, "Put on this armour!")

                access.closeChat()
                grubfoot = changeArmour(access, r, cast, grubfoot, colour)

                grubfootLine(colour.cutsceneNpc, quiz, "What do you think?")

                when (colour) {
                    GoblinMailColour.Orange -> {
                        wartfaceLine(neutral, "No I don't like that much.")
                        bentnozeLine(neutral, "It clashes with skin colour.")
                        wartfaceLine(neutral, "We need darker colour, like blue.")
                        bentnozeLine(neutral, "Yeah blue might be good.")
                        wartfaceLine(neutral, "Human! Get us blue armour!")
                    }
                    GoblinMailColour.Blue -> {
                        bentnozeLine(neutral, "That not right. Not goblin colour at all.")
                        wartfaceLine(neutral, "Goblins wear dark earthy colours like brown.")
                        bentnozeLine(neutral, "Yeah brown might be good.")
                        wartfaceLine(neutral, "Human! Get us brown armour!")
                        chatPlayer(
                            confused,
                            "But I thought brown was the armour you were<br>changing from...",
                        )
                        chatPlayer(confused, "Never mind, anything is worth a try.")
                    }
                    GoblinMailColour.Brown -> {
                        wartfaceLine(
                            neutral,
                            "That colour quite nice. Me can see myself wearing<br>that.",
                        )
                        bentnozeLine(neutral, "It a deal then. Brown armour it is.")
                        wartfaceLine(
                            neutral,
                            "Thank you for sorting out argument, human. You have<br>reward now.",
                        )
                        chatPlayer(confused, "Thanks... I think...")
                    }
                }

                if (access.invDel(access.inv, colour.obj, 1).failure) {
                    handedOver = false
                    return@startDialogue
                }

                access.gobdipGrubfootVis = colour.grubfootVis
                if (colour != GoblinMailColour.Brown) {
                    quest.setQuestStage(access, colour.nextStage)
                }
            }
        }

        if (handedOver && colour == GoblinMailColour.Brown) {
            quest.completeQuest(access)
        }
    }

    private suspend fun changeArmour(
        access: ProtectedAccess,
        r: SceneRegion,
        cast: MutableList<Npc>,
        current: Npc,
        colour: GoblinMailColour,
    ): Npc {
        current.walk(listOf(r[GrubfootScene.GrubfootModels], r[GrubfootScene.GrubfootCurtainFront]))
        access.delay(WalkToCurtainTicks)

        if (current.coords != r[GrubfootScene.GrubfootCurtainFront]) {
            current.telejump(collision, r[GrubfootScene.GrubfootCurtainFront])
        }

        despawn(current)
        cast -= current
        access.delay(ChangingTicks)

        val changed = spawn(colour.cutsceneNpc, r[GrubfootScene.GrubfootCurtainFront])
        cast += changed
        changed.faceSquare(r[GrubfootScene.GrubfootAttends])
        access.delay(1)

        changed.walk(listOf(r[GrubfootScene.GrubfootModels]))
        access.delay(WalkBackTicks)

        changed.faceSquare(r[GrubfootScene.WartfaceTile])
        access.delay(1)
        return changed
    }

    private fun swapNoopLocs(r: SceneRegion): List<LocInfo> {
        val restore = mutableListOf<LocInfo>()
        for ((tile, real, noop) in GrubfootScene.NoopSwaps) {
            val coords = r[tile]
            val realId = real.asRSCM(RSCMType.LOC)
            val existing = locRepo.findAll(coords).firstOrNull { it.id == realId } ?: continue
            locRepo.add(coords, noop, SceneLifespan, existing.angle, existing.shape)
            restore += existing
        }
        return restore
    }

    private suspend fun ProtectedAccess.scene(
        vantage: CoordGrid,
        faceAt: CoordGrid,
        camera: SceneCamera,
        returnTo: CoordGrid,
        underFade: suspend ProtectedAccess.(SceneRegion) -> Unit = {},
        teardown: () -> Unit = {},
        body: suspend ProtectedAccess.(SceneRegion) -> Unit,
    ) {
        val access = this
        val template: RegionTemplate = GoblinDiplomacyRegions.GoblinVillage
        val region = regionRepo.add(template)
        region?.let(regionRepo::protect)
        val scene = SceneRegion(region)

        // Persist the exit on entry so the next login rebuild uses a valid world coordinate.
        player.loginExitCoord = returnTo
        player.uuid?.let { uuid -> region?.let { sceneRegions[uuid] = it } }

        try {
            lockedScene(
                vantage = scene[vantage],
                faceAt = scene[faceAt],
                camera = scene.map(camera),
                returnTo = returnTo,
                underFade = { access.underFade(scene) },
                teardown = teardown,
            ) {
                body(scene)
            }
        } finally {
            player.loginExitCoord = null
            player.uuid?.let(sceneRegions::remove)

            // Unprotect, not delete; reclaim only after players have left the region.
            region?.let(regionRepo::unprotect)
        }
    }

    private fun spawn(type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npcRepo.add(npc, SceneLifespan)
        npc.noneMode()
        return npc
    }

    private fun despawn(npc: Npc) {
        if (!npc.isSlotAssigned) {
            return
        }
        npcRepo.del(npc, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.closeChat() {
        ifCloseSub("interface.chat_left")
        ifCloseSub("interface.chat_right")
    }

    private suspend fun Dialogue.wartfaceLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Wartface", WartfaceNpc, mesanim, text)

    private suspend fun Dialogue.bentnozeLine(mesanim: MesAnimType, text: String) =
        chatNpcSpecific("General Bentnoze", BentnozeNpc, mesanim, text)

    private suspend fun Dialogue.grubfootLine(leaf: String, mesanim: MesAnimType, text: String) =
        chatNpcSpecific("Grubfoot", leaf, mesanim, text)

    private companion object {

        const val WartfaceNpc = "npc.general_wartface_green_noop"
        const val BentnozeNpc = "npc.general_bentnoze_red_noop"

        const val SceneLifespan = 3000

        const val MusicFadeOutSpeed = 60
        const val MusicFadeInDelay = 60

        const val WalkToCurtainTicks = 2

        const val ChangingTicks = 5

        const val WalkBackTicks = 1
    }
}
