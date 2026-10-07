package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.sailing.SailingBoatFacilityRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.FacilityService
import org.rsmod.content.skills.sailing.SHIPBUILDING_BUILD_ANIM
import org.rsmod.content.skills.sailing.SailingQuestMilestone
import org.rsmod.content.skills.sailing.SailingQuestProgressEvent
import org.rsmod.content.skills.sailing.SailingQuestSupport
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.sailingQueue
import org.rsmod.content.skills.sailing.missingShipbuildingTool
import org.rsmod.content.skills.sailing.rollbackShipbuildingMaterials
import org.rsmod.content.skills.sailing.shipbuildingConstructionLvl
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class FacilityBuildTask(
    val boatSlot: Int,
    val hotspotIndex: Int,
    val facilityRow: Int,
    val coords: CoordGrid,
    val angleId: Int,
    val shapeId: Int,
)

internal class FacilityBuildScript @Inject constructor(private val facilities: FacilityService) :
    PluginScript() {

    override fun ScriptContext.startup() {
        for (symbol in indexedPlaceholders()) {
            onOpLoc1(symbol) { openBuildMenu(it.loc) }
        }
        // Unindexed placeholders cannot identify a hotspot varbit.
        onOpLoc1(FacilityService.LOC_PLACEHOLDER_GENERIC) { unindexedHotspot() }
        onOpLoc1(FacilityService.LOC_PLACEHOLDER_GENERIC_NONBLOCKING) { unindexedHotspot() }

        onPlayerQueueWithArgs<FacilityBuildTask>(SailingConstants.QUEUE_BUILD_FACILITY) {
            commitBuild(it.args)
        }
    }

    private fun indexedPlaceholders(): List<String> = buildList {
        for ((family, count) in FacilityService.HOTSPOT_COUNT_BY_FAMILY) {
            for (index in 0 until count) {
                add("${FacilityService.PLACEHOLDER_PREFIX}${family}_$index")
            }
        }
    }

    private fun ProtectedAccess.unindexedHotspot() {
        mes(SailingFacilityMessages.CANNOT_BUILD_YET)
    }

    private suspend fun ProtectedAccess.openBuildMenu(loc: BoundLocInfo) {
        val boat = facilities.boardedBoat(player)
        val slot = facilities.boardedSlot(player)
        if (boat == null || slot == null) {
            mes(SailingFacilityMessages.NOT_ON_YOUR_BOAT)
            return
        }
        val key = facilities.placeholderKey(loc.id)
        if (key == null || key.family != FacilityService.HOTSPOT_FAMILY_BY_TYPE_ID[boat.typeId]) {
            // Reject a hotspot family that disagrees with the active boat's deck.
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }
        val hotspotIndex = key.hotspotIndex
        if (facilities.hotspot(boat, hotspotIndex) == null) {
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }
        if (facilities.builtFacility(player, slot, boat, hotspotIndex) != null) {
            mes(SailingFacilityMessages.ALREADY_BUILT)
            return
        }
        missingShipbuildingTool()?.let {
            mes(it)
            return
        }

        val options = facilities.options(boat, hotspotIndex)
        if (options.isEmpty()) {
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }

        val chosen = chooseFacility(options) ?: return
        startBuild(slot, boat, hotspotIndex, chosen, loc)
    }

    private suspend fun ProtectedAccess.chooseFacility(
        options: List<SailingBoatFacilityRow>
    ): SailingBoatFacilityRow? {
        var page = 0
        while (true) {
            val start = page * PAGE_SIZE
            val window = options.drop(start).take(PAGE_SIZE)
            if (window.isEmpty()) {
                return null
            }
            val hasMore = options.size > start + window.size
            val tail =
                if (hasMore || page > 0) {
                    SailingFacilityMessages.MORE_OPTIONS
                } else {
                    SailingFacilityMessages.CANCEL
                }
            val labels = window.map(SailingBoatFacilityRow::name) + tail
            val picked = pickOne(labels) ?: return null
            if (picked < window.size) {
                return window[picked]
            }
            if (!hasMore && page == 0) {
                return null
            }
            page = if (hasMore) page + 1 else 0
        }
    }

    private suspend fun ProtectedAccess.pickOne(labels: List<String>): Int? =
        when (labels.size) {
            2 -> choice2(labels[0], 0, labels[1], 1)
            3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2)
            4 -> choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3)
            5 -> choice5(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, labels[4], 4)
            else -> null
        }

    private suspend fun ProtectedAccess.startBuild(
        slot: Int,
        boat: SailingBoatRow,
        hotspotIndex: Int,
        row: SailingBoatFacilityRow,
        loc: BoundLocInfo,
    ) {
        // Revalidate after the chooser; boat, tools, materials and fitted facilities may have changed.
        if (facilities.boardedSlot(player) != slot) {
            return
        }
        if (facilities.boardedBoat(player)?.typeId != boat.typeId) {
            return
        }
        if (facilities.builtFacility(player, slot, boat, hotspotIndex) != null) {
            mes(SailingFacilityMessages.ALREADY_BUILT)
            return
        }
        missingShipbuildingTool()?.let {
            mes(it)
            return
        }
        if (stat(SailingConstants.SAILING_STAT) < row.sailingRequirement) {
            mesbox(SailingFacilityMessages.needSailing(row.sailingRequirement))
            return
        }
        if (shipbuildingConstructionLvl() < row.constructionRequirement) {
            val required = row.constructionRequirement
            val message =
                if (facilities.shipwrightWillBuild(row)) {
                    SailingFacilityMessages.needConstructionOrShipwright(required)
                } else {
                    SailingFacilityMessages.needConstruction(required)
                }
            mesbox(message)
            return
        }
        if (facilities.buildMaxReached(player, slot, boat, row)) {
            mes(SailingFacilityMessages.buildMaxReached(facilities.buildMax(row) ?: 0))
            return
        }
        if (facilities.constructionXp(row) == null) {
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }
        if (facilities.facilityIdOf(row) == null || facilities.facilityLoc(row) == null) {
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }
        if (!SailingQuestSupport.isFreeCargoBuild(player, slot, row) && !hasMaterials(row)) {
            mes(SailingFacilityMessages.NEED_MATERIALS)
            soundSynth(SYNTH_WRONG)
            return
        }

        // Replace a pending build so repeated confirmations cannot commit twice.
        clearWeakQueue(SailingConstants.QUEUE_BUILD_FACILITY)
        anim(SHIPBUILDING_BUILD_ANIM)
        soundSynth(buildSynth(row))
        sailingQueue(
            SailingConstants.QUEUE_BUILD_FACILITY,
            BUILD_TICKS,
            FacilityBuildTask(
                boatSlot = slot,
                hotspotIndex = hotspotIndex,
                facilityRow = row.rowId,
                coords = loc.coords,
                angleId = loc.angleId,
                shapeId = loc.shapeId,
            ),
        )
    }

    private fun ProtectedAccess.commitBuild(task: FacilityBuildTask) {
        val boat = facilities.boardedBoat(player)
        val slot = facilities.boardedSlot(player)
        if (boat == null || slot != task.boatSlot) {
            resetAnim()
            return
        }
        val row = runCatching { SailingBoatFacilityRow.getRow(task.facilityRow) }.getOrNull()
        if (row == null) {
            resetAnim()
            return
        }
        if (facilities.builtFacility(player, slot, boat, task.hotspotIndex) != null) {
            resetAnim()
            mes(SailingFacilityMessages.ALREADY_BUILT)
            return
        }
        if (facilities.buildMaxReached(player, slot, boat, row)) {
            resetAnim()
            mes(SailingFacilityMessages.buildMaxReached(facilities.buildMax(row) ?: 0))
            return
        }
        val xp = facilities.constructionXp(row)
        val facilityLoc = facilities.facilityLoc(row)
        if (xp == null || facilityLoc == null || facilities.facilityIdOf(row) == null) {
            resetAnim()
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }

        missingShipbuildingTool()?.let {
            resetAnim()
            mes(it)
            return
        }
        val tutorialBuild = SailingQuestSupport.isFreeCargoBuild(player, slot, row)
        val removed = mutableListOf<Pair<String, Int>>()
        for ((name, amount) in if (tutorialBuild) emptyList() else facilities.materials(row)) {
            if (invDel(inv, name, amount).failure) {
                rollbackShipbuildingMaterials(removed)
                resetAnim()
                mes(SailingFacilityMessages.NEED_MATERIALS)
                return
            }
            removed += name to amount
        }

        if (!facilities.setBuiltFacility(player, slot, task.hotspotIndex, row)) {
            rollbackShipbuildingMaterials(removed)
            resetAnim()
            mes(SailingFacilityMessages.CANNOT_BUILD_YET)
            return
        }
        // Clear payload data left by the previous facility.
        facilities.clearExtraData(player, slot, task.hotspotIndex)
        facilities.draw(
            coords = task.coords,
            locSymbol = facilityLoc,
            angle = LocAngle[task.angleId],
            shape = LocShape[task.shapeId],
        )

        // Reset the looping build sequence before statAdvance can select a level-up animation.
        resetAnim()
        if (tutorialBuild) {
            publish(SailingQuestProgressEvent(this, SailingQuestMilestone.CargoHoldBuilt))
        } else {
            statAdvance(SailingConstants.CONSTRUCTION_STAT, xp)
        }
    }

    private fun ProtectedAccess.hasMaterials(row: SailingBoatFacilityRow): Boolean =
        facilities.materials(row).all { (name, amount) -> inv.count(name) >= amount }

    private fun buildSynth(row: SailingBoatFacilityRow): String {
        val names = facilities.materials(row).map { it.first }
        val wood = names.any { it.contains("plank") || it.contains("logs") }
        return if (wood) SYNTH_BUILD_WOOD else SYNTH_BUILD_METAL
    }

    private companion object {
        const val PAGE_SIZE: Int = 4

        const val SYNTH_BUILD_WOOD: String = "synth.POH_BUILD_WOOD"
        const val SYNTH_BUILD_METAL: String = "synth.POH_BUILD_METAL"
        const val SYNTH_WRONG: String = "synth.POH_WRONG"

        const val BUILD_TICKS: Int = 3
    }
}

private object SailingFacilityMessages {
    const val NOT_ON_YOUR_BOAT: String = "You need to be on your own boat to do that."
    const val ALREADY_BUILT: String = "There is already a facility built there."
    const val NEED_MATERIALS: String = "You don't have enough materials to build that."

    const val CANNOT_BUILD_YET: String = "You can't build anything there yet."

    const val MORE_OPTIONS: String = "More options..."
    const val CANCEL: String = "Cancel"

    fun needSailing(level: Int): String = "You need a Sailing level of $level."

    fun needConstruction(level: Int): String = "You need a Construction level of $level."

    fun needConstructionOrShipwright(level: Int): String =
        "You need a Construction level of $level, or a shipwright to build it for you."

    fun buildMaxReached(max: Int): String = "You can only have $max of those on your boat."
}
