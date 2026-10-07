package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.poh.PohDataStore
import org.rsmod.api.poh.PohFloor
import org.rsmod.api.poh.PohHotspot
import org.rsmod.api.poh.PohHotspotSlot
import org.rsmod.api.poh.PohHouse
import org.rsmod.api.poh.PohManager
import org.rsmod.api.poh.PohRoomSlot
import org.rsmod.api.poh.pohBuildingMode
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.table.PohFurnitureRow
import org.rsmod.content.skills.construction.PohFurniture
import org.rsmod.content.skills.construction.PohFurnitureDbRows
import org.rsmod.content.skills.construction.PohStairs
import org.rsmod.content.skills.construction.configs.PohFurnitureMenu
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FurnitureScript
@Inject
constructor(private val manager: PohManager, private val dataStore: PohDataStore) : PluginScript() {
    override fun ScriptContext.startup() {
        val hotspotLocs = dataStore.hotspots.map { it.loc }.distinct()
        for (hotspotLoc in hotspotLocs) {
            onOpLoc5(hotspotLoc) { onHotspotBuild(it.loc.coords, it.type.id) }
        }
        for (builtLoc in PohFurniture.allBuiltLocs()) {
            onOpLoc5(builtLoc) { onFurnitureRemove(it.loc.coords, it.type.id) }
        }
        onIfClose(PohFurnitureMenu.INTERFACE) { ClientScripts.chatDefaultRestoreInput(player) }
    }

    private suspend fun ProtectedAccess.onHotspotBuild(coords: CoordGrid, locId: Int) {
        if (player.pohBuildingMode != 1) {
            mes("You can only do that in building mode.")
            return
        }
        val (hotspotSlot, hotspot) = manager.findHotspot(player, coords, locId) ?: return
        val options = PohFurniture.optionsFor(locId)
        if (options.isEmpty()) {
            mes("You can't build anything there yet.")
            return
        }

        val chosen = openBuildMenu(options) ?: return

        val builtLoc = PohFurniture.builtLocFor(chosen, locId)?.internalName
        val mirrorTargets =
            if (builtLoc != null) linkedEmptyStairSpaces(hotspotSlot, builtLoc) else emptyList()
        val mirrorMaterials =
            if (mirrorTargets.isEmpty()) emptyList() else mirrorMaterials(builtLoc!!)

        val requiresTools = requiresHammerAndSaw(hotspot)
        if (player.constructionLvl + crystalSawBoost(requiresTools) < chosen.level) {
            mes("You need a Construction level of ${chosen.level} to build this.")
            return
        }
        if (requiresTools) {
            if (!player.hasHammerAndSaw()) {
                mes("You need a hammer and a saw to build furniture.")
                return
            }
        } else {
            if (requiresWateringCan(hotspot) && !player.hasWateringCanDose()) {
                mes("You need a watering can with at least one dose of water to do this.")
                return
            }
            if (requiresSecateurs(hotspot) && !player.invContainsAny(SECATEURS)) {
                mes("You need a pair of secateurs to do this.")
                return
            }
        }
        val nailType = selectNails(chosen.nails)
        if (!hasMaterials(chosen) || (chosen.nails > 0 && nailType == null)) {
            mes("You don't have enough materials to build that.")
            return
        }
        for ((material, count) in mirrorMaterials) {
            if (invTotal(inv, material) < count) {
                mes("You don't have the materials to build the matching staircase.")
                return
            }
        }

        playBuildAnim(hotspot.shape)
        soundSynth(buildSynthFor(chosen))
        delay(2)
        if (!driveNails(chosen.nails)) {
            mes("You've run out of nails.")
            resetAnim()
            return
        }
        consumeMaterials(chosen)
        mirrorMaterials.forEach { (material, count) -> invDel(inv, material, count) }

        statAdvance("stat.construction", chosen.xp / PlayerStatMap.XP_FINE_PRECISION.toDouble())
        applyBuild(chosen, hotspotSlot, locId)
        if (mirrorTargets.isNotEmpty()) {
            val mirror = PohStairs.MIRRORS.getValue(builtLoc!!).builtLoc
            for (target in mirrorTargets) {
                manager.applyFurniture(player, target, mirror)
            }
        }
        // Build sequences loop; explicitly reset them.
        resetAnim()
    }

    private suspend fun ProtectedAccess.onFurnitureRemove(coords: CoordGrid, locId: Int) {
        if (player.pohBuildingMode != 1) {
            mes("You can only do that in building mode.")
            return
        }
        val (hotspotSlot, _) = manager.findBuiltFurniture(player, coords, locId) ?: return
        val house = manager.houseOf(player) ?: return
        val removedLoc = house.furniture[hotspotSlot]
        val row = PohFurniture.rowsForBuilt(locId).firstOrNull() ?: return

        val confirm = choice2("Yes.", true, "No.", false, title = "Really remove it?")
        if (!confirm) {
            return
        }

        anim(REMOVE_ANIM)
        soundSynth(random.of(DEMOLISH_SYNTHS.size).let(DEMOLISH_SYNTHS::get))
        delay(2)

        if (row.groupBuild) {
            val roomSlot = hotspotSlot.roomSlot
            val builtIds = row.builtLoc.map { it.id }.toSet()
            val grouped =
                house.furnitureAt(roomSlot).filterValues { locName ->
                    dev.openrune.rscm.RSCM.getRSCM(locName) in builtIds
                }
            for (slot in grouped.keys) {
                manager.removeFurniture(player, slot)
            }
        } else {
            manager.removeFurniture(player, hotspotSlot)
        }
        if (removedLoc != null) {
            removeLinkedStairs(house, hotspotSlot, removedLoc)
        }
        // human_throw_away loops; explicitly reset it.
        resetAnim()
    }

    private fun ProtectedAccess.linkedEmptyStairSpaces(
        slot: PohHotspotSlot,
        builtLoc: String,
    ): List<PohHotspotSlot> {
        val house = manager.houseOf(player) ?: return emptyList()
        return buildList {
            for (direction in PohStairs.linkedDirections(builtLoc)) {
                val linked =
                    stairSpaceSlot(house, slot, slot.roomSlot.level + direction) ?: continue
                if (house.furniture[linked] == null) {
                    add(linked)
                }
            }
        }
    }

    private fun mirrorMaterials(builtLoc: String): List<Pair<String, Int>> =
        PohStairs.MIRRORS[builtLoc]?.materials.orEmpty()

    private fun ProtectedAccess.removeLinkedStairs(
        house: PohHouse,
        slot: PohHotspotSlot,
        removedLoc: String,
    ) {
        for (direction in PohStairs.linkedDirections(removedLoc)) {
            var fromLoc = removedLoc
            var level = slot.roomSlot.level + direction
            while (true) {
                val expected = PohStairs.MIRRORS[fromLoc]?.builtLoc ?: break
                val linked = stairSpaceSlot(house, slot, level) ?: break
                val linkedLoc = house.furniture[linked] ?: break
                if (linkedLoc != expected) {
                    break
                }
                manager.removeFurniture(player, linked)
                if (direction !in PohStairs.linkedDirections(linkedLoc)) {
                    break
                }
                fromLoc = linkedLoc
                level += direction
            }
        }
    }

    private fun stairSpaceSlot(house: PohHouse, slot: PohHotspotSlot, level: Int): PohHotspotSlot? {
        if (level !in PohFloor.DUNGEON.destLevel..PohFloor.UPPER.destLevel) {
            return null
        }
        val roomSlot = PohRoomSlot(level, slot.roomSlot.gridX, slot.roomSlot.gridZ)
        val room = house.rooms[roomSlot] ?: return null
        val hotspot =
            dataStore.hotspots(room.type).firstOrNull { it.name == PohStairs.STAIR_SPACE_NAME }
                ?: return null
        return PohHotspotSlot(level, roomSlot.gridX, roomSlot.gridZ, hotspot.index)
    }

    private suspend fun ProtectedAccess.openBuildMenu(
        options: List<PohFurnitureRow>
    ): PohFurnitureRow? {
        // Clientscript 1406 supports only 31 rows.
        val shown = options.take(PohFurnitureMenu.MAX_ROWS)
        // Use the main-modal helper so the client gets its required window setup.
        ifOpenMainModal(PohFurnitureMenu.INTERFACE)
        for ((index, row) in shown.withIndex()) {
            // Clientscript 1404 requires a client furniture dbrow ID, not an object ID.
            runClientScript(
                PohFurnitureMenu.ENTRY_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
                index + 1,
                PohFurnitureDbRows.clientRow(row),
                row.level,
                menuText(row),
                if (canAfford(row)) 1 else 0,
            )
        }
        runClientScript(PohFurnitureMenu.LAYOUT_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT), shown.size, 0)
        // Client resumes target dynamic children under 458:2, not the visible static row layers.
        ifSetEvents(PohFurnitureMenu.CONTENTS, 1..shown.size, IfEvent.PauseButton)
        // pauseButton revalidates ownership after suspension; a raw pause could accept another modal's response.
        val input = pauseButton()
        // Close only this modal, preserving a level-up modal opened during the build.
        ifCloseSub(PohFurnitureMenu.INTERFACE)
        if (!input.isComponentType(PohFurnitureMenu.CONTENTS)) {
            return null
        }
        val slot = input.subcomponent
        if (slot !in 1..shown.size) {
            return null
        }
        return shown[slot - 1]
    }

    private fun menuText(row: PohFurnitureRow): String {
        val materials = buildList {
            for (material in row.material) {
                add("${material.t1} x ${material.t0.name}")
            }
            if (row.nails > 0) {
                add("${row.nails} x Nails")
            }
        }
        return "${row.menuName}|Materials: ${materials.joinToString("<br>")}"
    }

    private fun ProtectedAccess.canAfford(row: PohFurnitureRow): Boolean =
        hasMaterials(row) && (row.nails == 0 || selectNails(row.nails) != null)

    private fun ProtectedAccess.hasMaterials(row: PohFurnitureRow): Boolean =
        row.material.all { invTotal(inv, it.t0.internalName) >= it.t1 }

    private fun ProtectedAccess.selectNails(count: Int): String? {
        if (count == 0) {
            return null
        }
        return NAIL_TYPES.firstOrNull { invTotal(inv, it) >= count }
    }

    private fun ProtectedAccess.driveNails(required: Int): Boolean {
        var driven = 0
        while (driven < required) {
            val type = NAIL_TYPES.firstOrNull { invTotal(inv, it) > 0 } ?: return false
            invDel(inv, type, 1)
            if (random.of(100) < NAIL_BEND.getValue(type)) {
                mes("You accidentally bend a nail!")
            } else {
                driven++
            }
        }
        return true
    }

    private fun ProtectedAccess.consumeMaterials(row: PohFurnitureRow) {
        for (material in row.material) {
            invDel(inv, material.t0.internalName, material.t1)
        }
    }

    private fun buildSynthFor(row: PohFurnitureRow): String {
        val materials = row.material.map { it.t0.internalName }
        return when {
            materials.any { it in WOOD_MATERIALS } -> BUILD_WOOD_SYNTH
            materials.any { it in STONE_MATERIALS } -> BUILD_STONE_SYNTH
            materials.any { it in METAL_MATERIALS } -> BUILD_METAL_SYNTH
            else -> BUILD_WOOD_SYNTH
        }
    }

    private fun ProtectedAccess.playBuildAnim(hotspotShape: Int) {
        val imcando = player.hasImcandoHammer()
        val anim =
            when {
                hotspotShape in WALL_SHAPES ->
                    if (imcando) "seq.human_poh_build_wall_imcando_hammer"
                    else "seq.human_poh_build_wall"
                hotspotShape == GROUND_DECOR_SHAPE ->
                    if (imcando) "seq.human_poh_build_floor_imcando_hammer"
                    else "seq.human_poh_build_floor"
                else -> if (imcando) "seq.human_poh_build_imcando_hammer" else "seq.human_poh_build"
            }
        anim(anim)
    }

    private fun ProtectedAccess.applyBuild(
        row: PohFurnitureRow,
        hotspotSlot: PohHotspotSlot,
        clickedLocId: Int,
    ) {
        if (row.groupBuild) {
            val house = manager.houseOf(player) ?: return
            val roomSlot = hotspotSlot.roomSlot
            val room = house.rooms[roomSlot] ?: return
            val hotspotLocIds = row.hotspotLoc.map { it.id }
            for (hotspot in dataStore.hotspots(room.type)) {
                val locId = dev.openrune.rscm.RSCM.getRSCM(hotspot.loc)
                if (locId !in hotspotLocIds) {
                    continue
                }
                val built = PohFurniture.builtLocFor(row, locId) ?: continue
                val slot =
                    PohHotspotSlot(roomSlot.level, roomSlot.gridX, roomSlot.gridZ, hotspot.index)
                manager.applyFurniture(player, slot, built.internalName)
            }
        } else {
            val built = PohFurniture.builtLocFor(row, clickedLocId) ?: return
            manager.applyFurniture(player, hotspotSlot, built.internalName)
        }
    }

    private fun requiresHammerAndSaw(hotspot: PohHotspot): Boolean = hotspot.roomKey !in GARDEN_ROOMS

    private fun requiresWateringCan(hotspot: PohHotspot): Boolean =
        hotspot.roomKey in GARDEN_ROOMS && hotspot.name in WATERING_CAN_HOTSPOTS

    private fun requiresSecateurs(hotspot: PohHotspot): Boolean = hotspot.name in SECATEURS_HOTSPOTS

    private fun Player.hasHammerAndSaw(): Boolean {
        val hammer = invContainsAny(HAMMERS) || hasImcandoHammer()
        val saw = invContainsAny(SAWS)
        return hammer && saw
    }

    private fun Player.hasImcandoHammer(): Boolean = invContainsAny(IMCANDO_HAMMERS)

    private fun Player.hasWateringCanDose(): Boolean = invContainsAny(WATERING_CANS_WITH_DOSE)

    private fun ProtectedAccess.crystalSawBoost(requiresTools: Boolean): Int {
        if (!requiresTools || !player.invContainsAny(listOf(CRYSTAL_SAW))) {
            return 0
        }
        return CRYSTAL_SAW_BOOST
    }

    private fun Player.invContainsAny(types: List<String>): Boolean {
        val ids = types.map { dev.openrune.rscm.RSCM.getRSCM(it) }
        val inInv = inv.any { it != null && it.id in ids }
        val worn = righthand?.id in ids || lefthand?.id in ids
        return inInv || worn
    }

    private companion object {
        const val IMCANDO_HAMMER = "obj.imcando_hammer"

        const val IMCANDO_HAMMER_OFFHAND = "obj.imcando_hammer_offhand"
        val IMCANDO_HAMMERS = listOf(IMCANDO_HAMMER, IMCANDO_HAMMER_OFFHAND)

        val HAMMERS = listOf("obj.hammer")

        val SAWS =
            listOf(
                "obj.poh_saw",
                "obj.eyeglo_crystal_saw",
                "obj.wearable_saw",
                "obj.wearable_saw_offhand",
            )

        const val CRYSTAL_SAW = "obj.eyeglo_crystal_saw"
        const val CRYSTAL_SAW_BOOST = 3

        val GARDEN_ROOMS = setOf("garden", "formal_garden", "superior_garden")

        val WATERING_CAN_HOTSPOTS =
            setOf("Centrepiece space", "Teleport space", "Theme space", "Topiary space")

        val SECATEURS_HOTSPOTS = setOf("Topiary space")

        val WATERING_CANS_WITH_DOSE =
            listOf(
                "obj.watering_can_1",
                "obj.watering_can_2",
                "obj.watering_can_3",
                "obj.watering_can_4",
                "obj.watering_can_5",
                "obj.watering_can_6",
                "obj.watering_can_7",
                "obj.watering_can_8",
            )

        val SECATEURS = listOf("obj.secateurs", "obj.fairy_enchanted_secateurs")

        val NAIL_TYPES =
            listOf(
                "obj.nails_rune",
                "obj.nails_adamant",
                "obj.nails_mithril",
                "obj.nails_black",
                "obj.nails",
                "obj.nails_iron",
                "obj.nails_bronze",
            )

        // TODO: verify per-metal bending probabilities; these are approximations.
        val NAIL_BEND =
            mapOf(
                "obj.nails_bronze" to 60,
                "obj.nails_iron" to 50,
                "obj.nails" to 40,
                "obj.nails_black" to 30,
                "obj.nails_mithril" to 20,
                "obj.nails_adamant" to 10,
                "obj.nails_rune" to 5,
            )

        const val REMOVE_ANIM = "seq.human_throw_away"

        const val BUILD_WOOD_SYNTH = "synth.POH_BUILD_WOOD"
        const val BUILD_STONE_SYNTH = "synth.POH_BUILD_STONE"
        const val BUILD_METAL_SYNTH = "synth.POH_BUILD_METAL"

        val DEMOLISH_SYNTHS = listOf("synth.DEMOLISH_01", "synth.DEMOLISH_02")

        val WOOD_MATERIALS =
            setOf("obj.woodplank", "obj.plank_oak", "obj.plank_teak", "obj.plank_mahogany")

        val STONE_MATERIALS = setOf("obj.marble_block", "obj.limestonebrick", "obj.softclay")

        val METAL_MATERIALS = setOf("obj.steel_bar", "obj.gold_leaf")

        val WALL_SHAPES = 0..9
        const val GROUND_DECOR_SHAPE = 22
    }
}
