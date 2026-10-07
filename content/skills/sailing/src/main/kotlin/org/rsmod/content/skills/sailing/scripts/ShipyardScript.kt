package org.rsmod.content.skills.sailing.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.sailingLvl
import org.rsmod.api.script.onApNpc4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.BoatBuildService
import org.rsmod.content.skills.sailing.SAILING_COINS
import org.rsmod.content.skills.sailing.SHIPBUILDING_BUILD_ANIM
import org.rsmod.content.skills.sailing.SailingSessionState.migrateShipyardReturn
import org.rsmod.content.skills.sailing.SailingSessionState.shipyardReturn
import org.rsmod.content.skills.sailing.ShipPartCategory
import org.rsmod.content.skills.sailing.ShipbuildingMessages
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.configs.sailingQueue
import org.rsmod.content.skills.sailing.hasShipbuildingMaterials
import org.rsmod.content.skills.sailing.missingShipbuildingTool
import org.rsmod.content.skills.sailing.rollbackShipbuildingMaterials
import org.rsmod.content.skills.sailing.shipbuildingCoins
import org.rsmod.content.skills.sailing.shipbuildingConstructionLvl
import org.rsmod.content.skills.sailing.table.ShipbuildingXp
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal data class ShipPartBuildTask(
    val slot: Int,
    val category: ShipPartCategory,
    val optionIndex: Int,
    val shipwrightFee: Int,
)

internal class ShipyardScript @Inject constructor(private val builds: BoatBuildService) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.migrateShipyardReturn() }
        for (jim in listOf("npc.junior_jim", "npc.junior_jim_4ops")) {
            onOpNpc4(jim) { teleportToShipyard() }
        }
        // Shipwright definitions lack the claimed content tag; register their NPC symbols.
        for (shipwright in PORT_SHIPWRIGHTS) {
            onApNpc4(shipwright) {
                if (isWithinApRange(it.npc, distance = SHIPWRIGHT_COUNTER_RANGE)) {
                    teleportToShipyard()
                }
            }
            onOpNpc4(shipwright) { teleportToShipyard() }
        }

        onOpLoc1(LOC_SCHEMATICS) { openSchematics() }
        onOpLoc1(LOC_EXIT_PORTAL) { leaveShipyard() }

        onPlayerQueueWithArgs<ShipPartBuildTask>(SailingConstants.QUEUE_BUILD_PART) {
            commitBuild(it.args)
        }
    }

    private fun ProtectedAccess.teleportToShipyard() {
        if (builds.resolveCustomisingSlot(player) == null) {
            mes(ShipbuildingMessages.NO_BOAT)
            return
        }
        val origin = player.coords
        telejump(SHIPYARD_ARRIVAL)
        if (player.coords == SHIPYARD_ARRIVAL && MapSquareKey.from(origin) != MapSquareKey.from(SHIPYARD_ARRIVAL)) {
            player.shipyardReturn = origin
        }
    }

    private fun ProtectedAccess.leaveShipyard() {
        val saved = player.shipyardReturn
        val destination = saved?.takeUnless { MapSquareKey.from(it) == MapSquareKey.from(SHIPYARD_ARRIVAL) }
            ?: checkNotNull(SailingDocks.PORT_SARIM.landCoords)
        telejump(destination)
        // Preserve the entry port until the outbound teleport succeeds.
        if (player.coords == destination) player.shipyardReturn = null
    }

    private suspend fun ProtectedAccess.openSchematics() {
        val slot = builds.resolveCustomisingSlot(player)
        if (slot == null) {
            mes(ShipbuildingMessages.NO_BOAT)
            return
        }
        val boat = builds.boatOf(player, slot)
        if (boat == null) {
            mes(ShipbuildingMessages.CANNOT_BUILD_YET)
            return
        }
        missingShipbuildingTool()?.let {
            mes(it)
            return
        }

        val categories = builds.availableCategories(boat)
        val core = categories.filter(ShipPartCategory::isCore)
        val cosmetic = categories.filterNot(ShipPartCategory::isCore)
        if (core.isEmpty() && cosmetic.isEmpty()) {
            mes(ShipbuildingMessages.CANNOT_BUILD_YET)
            return
        }

        val group =
            when {
                core.isEmpty() -> cosmetic
                cosmetic.isEmpty() -> core
                else ->
                    choice2(
                        "Core boat parts.",
                        core,
                        "Cosmetics.",
                        cosmetic,
                        title = TITLE_WHAT_TO_CHANGE,
                    )
            }

        val category = pickFromList(TITLE_WHAT_TO_CHANGE, group) { it.displayName } ?: return
        val options = builds.options(boat, category)
        val option =
            pickFromList(category.displayName, options) {
                "${it.name} (${it.sailingRequirement} Sailing, " +
                    "${it.constructionRequirement} Construction)"
            } ?: return

        startBuild(slot, boat, option)
    }

    private suspend fun ProtectedAccess.startBuild(
        slot: Int,
        boat: SailingBoatRow,
        option: BoatBuildService.PartOption,
    ) {
        // Revalidate tools, materials and boat state after the menus suspend.
        if (builds.boatOf(player, slot)?.rowId != boat.rowId) {
            return
        }
        missingShipbuildingTool()?.let {
            mes(it)
            return
        }

        if (player.sailingLvl < option.sailingRequirement) {
            mesbox("You need a Sailing level of ${option.sailingRequirement} to build that.")
            return
        }

        var fee = 0
        if (shipbuildingConstructionLvl() < option.constructionRequirement) {
            // construction_coins_alternative is permission, not a price.
            val quote =
                if (option.coinsAlternative) {
                    ShipbuildingXp.shipwrightFee(option.category, option.tierIndex)
                } else {
                    null
                }
            if (quote == null) {
                mesbox(
                    "You need a Construction level of ${option.constructionRequirement} " +
                        "to build that."
                )
                return
            }
            val hire =
                choice2(
                    "Pay a shipwright $quote coins to build it.",
                    true,
                    "Never mind.",
                    false,
                    title = TITLE_SHIPWRIGHT,
                )
            if (!hire) {
                return
            }
            if (shipbuildingCoins() < quote) {
                mes(ShipbuildingMessages.NEED_COINS)
                return
            }
            fee = quote
        }

        val missingXp =
            option.category.isCore && ShipbuildingXp.constructionXp(option.dbrow) == null
        if (option.dbrow == null || !option.materialsResolved || missingXp) {
            mes(ShipbuildingMessages.CANNOT_BUILD_YET)
            return
        }

        if (builds.fitted(player, slot, option.category) == option.index) {
            mes(ShipbuildingMessages.ALREADY_FITTED)
            return
        }
        if (!hasShipbuildingMaterials(option)) {
            mes(ShipbuildingMessages.NEED_MATERIALS)
            return
        }

        // Replace pending builds; repeated confirmations must not commit twice.
        clearWeakQueue(SailingConstants.QUEUE_BUILD_PART)
        anim(SHIPBUILDING_BUILD_ANIM)
        soundSynth(BUILD_SYNTH)
        sailingQueue(
            SailingConstants.QUEUE_BUILD_PART,
            ShipbuildingXp.BUILD_TICKS,
            ShipPartBuildTask(slot, option.category, option.index, fee),
        )
    }

    private fun ProtectedAccess.commitBuild(task: ShipPartBuildTask) {
        val boat = builds.boatOf(player, task.slot)
        if (boat == null) {
            resetAnim()
            return
        }
        // Match the stored option index, not list position.
        val option =
            builds.options(boat, task.category).firstOrNull { it.index == task.optionIndex }
        if (option == null || option.dbrow == null || !option.materialsResolved) {
            resetAnim()
            mes(ShipbuildingMessages.CANNOT_BUILD_YET)
            return
        }
        val xp = ShipbuildingXp.constructionXp(option.dbrow)
        if (option.category.isCore && xp == null) {
            resetAnim()
            mes(ShipbuildingMessages.CANNOT_BUILD_YET)
            return
        }
        if (player.sailingLvl < option.sailingRequirement) {
            resetAnim()
            return
        }
        if (
            task.shipwrightFee == 0 &&
                shipbuildingConstructionLvl() < option.constructionRequirement
        ) {
            resetAnim()
            return
        }

        val removed = mutableListOf<Pair<String, Int>>()
        for ((name, amount) in option.material) {
            if (invDel(inv, name, amount).failure) {
                rollbackShipbuildingMaterials(removed)
                resetAnim()
                mes(ShipbuildingMessages.NEED_MATERIALS)
                return
            }
            removed += name to amount
        }
        if (task.shipwrightFee > 0) {
            if (invDel(inv, SAILING_COINS, task.shipwrightFee).failure) {
                rollbackShipbuildingMaterials(removed)
                resetAnim()
                mes(ShipbuildingMessages.NEED_COINS)
                return
            }
            removed += SAILING_COINS to task.shipwrightFee
        }

        builds.fit(player, task.slot, task.category, task.optionIndex)

        if (task.category == ShipPartCategory.HULL || task.category == ShipPartCategory.KEEL) {
            builds.restoreFullHp(player, task.slot, boat)
        }

        // Reset the looping build sequence before statAdvance can select a level-up animation.
        resetAnim()

        if (task.shipwrightFee == 0 && xp != null) {
            statAdvance(SailingConstants.CONSTRUCTION_STAT, xp)
        }
    }

    private suspend fun <T : Any> ProtectedAccess.pickFromList(
        title: String,
        items: List<T>,
        label: (T) -> String,
    ): T? {
        var offset = 0
        while (offset < items.size) {
            val remaining = items.drop(offset)
            val paged = remaining.size >= MAX_CHOICES
            val shown = if (paged) remaining.take(MAX_CHOICES - 1) else remaining
            val terminator = if (paged) MORE_OPTIONS else NEVER_MIND
            val picked = presentChoice(title, shown, label, terminator)
            if (picked != null) {
                return picked
            }
            if (!paged) {
                return null
            }
            offset += MAX_CHOICES - 1
        }
        return null
    }

    private suspend fun <T : Any> ProtectedAccess.presentChoice(
        title: String,
        shown: List<T>,
        label: (T) -> String,
        terminator: String,
    ): T? {
        val text = shown.map(label)
        return when (shown.size) {
            1 -> choice2<T?>(text[0], shown[0], terminator, null, title = title)
            2 -> choice3<T?>(text[0], shown[0], text[1], shown[1], terminator, null, title = title)
            3 ->
                choice4<T?>(
                    text[0],
                    shown[0],
                    text[1],
                    shown[1],
                    text[2],
                    shown[2],
                    terminator,
                    null,
                    title = title,
                )
            else ->
                choice5<T?>(
                    text[0],
                    shown[0],
                    text[1],
                    shown[1],
                    text[2],
                    shown[2],
                    text[3],
                    shown[3],
                    terminator,
                    null,
                    title = title,
                )
        }
    }

    private companion object {
        private const val LOC_SCHEMATICS: String = "loc.sailing_shipyard_schematics"

        private const val LOC_EXIT_PORTAL: String = "loc.sailing_shipyard_portal_exit"

        private const val BUILD_SYNTH: String = "synth.POH_BUILD_WOOD"

        private val SHIPYARD_ARRIVAL: CoordGrid = CoordGrid(0, 32, 42, 20, 33)

        private const val MAX_CHOICES: Int = 5

        private const val MORE_OPTIONS: String = "More options..."

        private const val NEVER_MIND: String = "Never mind."

        private const val TITLE_WHAT_TO_CHANGE: String = "What would you like to change?"

        private const val TITLE_SHIPWRIGHT: String = "Your Construction level isn't high enough."

        private val PORT_SHIPWRIGHTS: List<String> =
            listOf(
                "npc.sailing_shipwright_port_sarim",
                "npc.sailing_shipwright_musa_point",
                "npc.sailing_shipwright_catherby",
                "npc.sailing_shipwright_brimhaven",
                "npc.sailing_shipwright_port_khazard",
                "npc.sailing_shipwright_lands_end",
                "npc.sailing_shipwright_port_piscarilius",
                "npc.sailing_shipwright_civitas_illa_fortis",
                "npc.sailing_shipwright_rellekka",
                "npc.sailing_shipwright_aldarin",
                "npc.sailing_shipwright_corsair_cove",
                "npc.sailing_shipwright_port_roberts",
                "npc.sailing_shipwright_red_rock",
                "npc.sailing_shipwright_deepfin_point",
                "npc.sailing_shipwright_prifddinas",
            )
    }
}
