package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.script.*
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.content.minigames.giantsfoundry.configs.*
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsService
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsState
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsWorkshop
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsStage
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FoundryWorkshopScript @Inject constructor(
    private val game: FoundryGame,
    private val interfaces: FoundryInterfaces,
    private val events: EventBus,
    private val sleepingGiants: SleepingGiantsService,
    private val questWorkshop: SleepingGiantsWorkshop,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.giants_foundry_entrance") {
            if (!accessible()) return@onOpLoc1
            if (player.sleepingGiantsStage in SleepingGiantsState.ACCEPTED..SleepingGiantsState.REPAIRED &&
                choice2("Continue Sleeping Giants", true, "Use the main foundry", false)
            ) {
                sleepingGiants.enterQuest(this)
            } else {
                teleport(SleepingGiantsWorkshop.ENTRANCE)
            }
        }
        onOpLoc1("loc.giants_foundry_exit") {
            if (questWorkshop.contains(player)) {
                questWorkshop.leave(player)
                return@onOpLoc1
            }
            game.store(player)
            teleport(SleepingGiantsWorkshop.OUTSIDE)
        }
        onOpLoc1("loc.giants_foundry_chest") {
            game.store(player)
            player.openBank(events)
        }
        val bucketContainers = listOf(
            "loc.giants_foundry_supply_box",
            "loc.giants_foundry_supply_box_empty",
            "loc.my2arm_throne_room_buckets",
        )
        for (box in bucketContainers) {
            onOpLoc1(box) {
                if (!FoundryGame.isInside(player.coords)) return@onOpLoc1
                if (box != "loc.my2arm_throne_room_buckets" && sleepingGiants.searchSupplies(this)) return@onOpLoc1
                if ("obj.bucket_empty" in inv || "obj.bucket_water" in inv) {
                    mes("You already have a bucket.")
                } else if (invAdd(inv, "obj.bucket_empty", 1).success) {
                    mes("You take a bucket from the crate.")
                }
            }
        }
        val mouldJigs = listOf(
            "loc.giants_foundry_jig_empty",
            "loc.giants_foundry_jig_mould",
            "loc.giants_foundry_jig_mould_filled",
            "loc.giants_foundry_jig_mould_cooling",
            "loc.giants_foundry_jig_mould_cooled",
        )
        for (jig in mouldJigs) {
            onOpLoc1(jig) {
                if (!accessible()) return@onOpLoc1
                if (!FoundryGame.hasCommission(player)) {
                    mes("You need to get a commission from Kovac first.")
                    return@onOpLoc1
                }
                if (player.foundryMouldState >= 2) {
                    takePreform(it.loc)
                } else if (player.foundryStartQuality > 0) {
                    mes("Finish your current sword before setting up another mould.")
                } else {
                    interfaces.openMould(player)
                }
            }
            onOpLocU(jig, "obj.bucket_water") { coolMould(it.loc) }
        }
        onOpLoc1("loc.giants_foundry_preform_storage_empty") {
            if (FoundryGame.isEquipped(player)) {
                game.store(player)
                mes("You store your preform.")
            } else {
                mes("You don't have a preform to store.")
            }
        }
        onOpLoc1("loc.giants_foundry_preform_storage_full") { takePreform(it.loc, stored = true) }
        val temperatureTools = listOf(
            "loc.giants_foundry_lava_pool" to true,
            "loc.giants_foundry_waterfall" to false,
        )
        for ((loc, heat) in temperatureTools) {
            onOpLoc1(loc) { changeTemperature(it.loc, heat, fast = false) }
            onOpLoc2(loc) { changeTemperature(it.loc, heat, fast = true) }
        }
        onOpLocU("loc.giants_foundry_waterfall", "obj.bucket_empty") {
            faceLoc(it.loc)
            val result = player.invTransaction(inv) {
                val inventory = select(inv)
                delete(inventory, "obj.bucket_empty".asRSCM(), 1)
                add(inventory, "obj.bucket_water".asRSCM(), 1)
            }
            if (result.success) {
                anim("seq.human_pickuptable_walkmerge")
                soundSynth("synth.TAP_FILL")
                mes("You fill the bucket from the waterfall.")
            }
        }
        onOpLoc1("loc.giants_foundry_trip_hammer") { refine(it.loc, FoundryStation.HAMMER) }
        onOpLoc1("loc.giants_foundry_grindstone") { refine(it.loc, FoundryStation.GRINDSTONE) }
        onOpLoc1("loc.giants_foundry_polishing_wheel") {
            refine(it.loc, FoundryStation.POLISHING_WHEEL)
        }
        onPlayerLogin {
            if (player.foundryStartQuality > 0) {
                game.store(player)
                if (player.foundryStored != 0 && FoundryGame.isInside(player.coords)) {
                    player.mes(
                        "You notice your current commission is being kept warm " +
                            "in the preform storage."
                    )
                }
            }
        }
        onPlayerLogout {
            if (player.foundryStartQuality > 0 || FoundryGame.isEquipped(player)) {
                game.store(player)
            }
        }
        onPlayerCoordsChanged {
            val hasPreform = player.foundryStartQuality > 0 || FoundryGame.isEquipped(player)
            if (!hasPreform) return@onPlayerCoordsChanged
            if (!FoundryGame.isInside(player.coords)) game.store(player)
            player.foundryLastWorkStation = 0
            player.foundryLastWorkCycle = 0
        }
        onPlayerSoftTimer(FoundryGame.TIMER) { process(player) }
    }

    private fun ProtectedAccess.accessible(): Boolean {
        if (player.smithingLvl < 15) {
            mes("You need a Smithing level of 15 to use the Giants' Foundry.")
            return false
        }
        return true
    }

    private suspend fun ProtectedAccess.coolMould(loc: BoundLocInfo) {
        if (player.foundryMouldState != 2 || player.foundryStartQuality <= 0) {
            mes("There is no hot preform in the mould.")
            return
        }
        val transaction = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, "obj.bucket_water".asRSCM(), 1)
            add(inventory, "obj.bucket_empty".asRSCM(), 1)
        }
        if (transaction.failure) return
        faceLoc(loc)
        anim("seq.human_pickuptable")
        soundSynth("synth.BF_COOLDOWN")
        player.foundryMouldState = 3
        mes("You cool the preform with the water.")
    }

    private suspend fun ProtectedAccess.takePreform(loc: BoundLocInfo, stored: Boolean = false) {
        arriveDelay()
        val available = if (stored) player.foundryStored != 0 else player.foundryMouldState >= 2
        if (player.foundryStartQuality <= 0 || !available) return
        if (FoundryGame.isEquipped(player)) return
        if (worn[Wearpos.LeftHand.slot] != null) {
            mes("You need to remove your shield or off-hand item before picking up the giant sword.")
            return
        }
        if (!stored && player.foundryMouldState == 2) {
            if (ICE_GLOVES.any { it in worn }) {
                player.foundryMouldState = 3
                mes("Your Ice gloves cool the preform as you reach for it.")
            } else {
                mes(
                    "The preform is too hot to pick up. " +
                        "Cool it with a bucket of water or wear ice gloves."
                )
                return
            }
        }
        if (inv.freeSpace() < 1) {
            mes("You need a free inventory space to pick up the giant sword.")
            return
        }
        if (invAdd(inv, FoundryGame.PREFORM, 1).failure) return
        val slot = inv.indices.first { inv[it]?.id == FoundryGame.PREFORM.asRSCM() }
        if (invEquip(slot) !is HeldEquipResult.Success) {
            invDel(inv, FoundryGame.PREFORM, 1)
            return
        }
        faceLoc(loc)
        player.foundryStored = 0
        player.foundryMouldState = 0
        for (part in FoundryMouldPart.entries) {
            VarPlayerIntMapSetter.set(player, part.selectedVarbit, 0)
        }
        VarPlayerIntMapSetter.set(player, FoundryGame.ALLOY, 0)
        if (!stored) {
            player.foundryTemperature = 982
            soundSynth("synth.BF_COOLDOWN")
        }
        player.foundryNextSweetCycle = player.currentMapClock + random.of(40, 65)
        player.softTimer(FoundryGame.TIMER, 1)
        interfaces.showHud(player)
        SleepingGiantsService.refreshTutorial(player)
    }

    private suspend fun ProtectedAccess.changeTemperature(
        loc: BoundLocInfo,
        heat: Boolean,
        fast: Boolean,
    ) {
        arriveDelay()
        if (!requirePreform()) return
        faceLoc(loc)
        player.foundryLastWorkStation = 0
        player.foundryLastWorkCycle = 0
        val animation = if (heat) "seq.human_pickupfloor" else "seq.human_pickuptable"
        val sound =
            if (heat) "synth.SFX_100_SIZZLE_GLOVES" else "synth.SFX_100_UNDERWATER_PICK"
        var elapsed = 0
        try {
            while (FoundryGame.isEquipped(player)) {
                val endpoint = if (heat) 1000 else 0
                if (player.foundryTemperature == endpoint) {
                    mes(
                        if (heat) "The preform is already hot enough."
                        else "The preform is already cold enough."
                    )
                    break
                }
                anim(animation)
                soundSynth(sound)
                awaitFoundryCycles()
                val amount = temperatureStep(elapsed++) * if (fast) 2 else 1
                val adjustment = if (heat) amount else -amount
                player.foundryTemperature =
                    (player.foundryTemperature + adjustment).coerceIn(0, 1000)
            }
        } finally {
            resetAnim()
        }
    }

    private suspend fun ProtectedAccess.refine(loc: BoundLocInfo, station: FoundryStation) {
        arriveDelay()
        if (!requirePreform()) return
        val now = player.currentMapClock
        val section = FoundryMath.section(player.foundryCompletion, player.foundryDifficulty)
        val sweet =
            player.foundrySweetUntil >= now && player.foundrySweetUntil > 0 &&
                !player.foundrySweetClaimed && FoundryState.station(player, section) == station &&
                player.foundryTemperature in FoundryMath.temperatureRange(
                    station,
                    player.foundryDifficulty,
                )
        val overworked =
            !sweet && player.foundryLastWorkStation == station.ordinal + 1 &&
                now - player.foundryLastWorkCycle in 0..1
        if (sweet) {
            player.foundrySweetClaimed = true
            player.foundrySweetUntil = 0
            interfaces.sweetSpot(player, FoundryInterfaces.SweetSpot.Claimed)
        }
        val generation = ++player.foundrySessionGeneration
        player.foundryWorkStation = station.ordinal + 1
        player.foundryLastWorkStation = station.ordinal + 1
        player.foundryLastWorkCycle = now
        faceLoc(loc)
        val startAnimation =
            if (station == FoundryStation.HAMMER) "seq.human_preform_lift_start02"
            else "seq.human_preform_lift_start"
        val loopAnimation =
            if (station == FoundryStation.HAMMER) "seq.human_preform_lift_loop02"
            else "seq.human_preform_lift_loop"
        anim(startAnimation)
        var remaining = if (station == FoundryStation.HAMMER) {
            FoundryMachineryScript.cyclesUntilStrike(now)
        } else 3
        if (overworked) {
            remaining += station.cycles
            mes("You overwork the preform and miss an action.")
        }
        var finished = false
        try {
            while (FoundryGame.isEquipped(player) &&
                generation == player.foundrySessionGeneration
            ) {
                awaitFoundryCycles()
                player.foundryLastWorkStation = station.ordinal + 1
                player.foundryLastWorkCycle = player.currentMapClock
                faceLoc(loc)
                anim(loopAnimation)
                if (--remaining > 0) continue
                val boosted = player.foundrySweetClaimed
                player.foundrySweetClaimed = false
                if (boosted) interfaces.sweetSpot(player, FoundryInterfaces.SweetSpot.Hidden)
                val uniformBonus = FoundryGame.uniformBonus(player, random)
                when (game.work(player, station, boosted, uniformBonus)) {
                    FoundryGame.WorkResult.NoPreform -> break
                    FoundryGame.WorkResult.Ruined -> {
                        mes(
                            "Your sword is ruined. " +
                                "Hand it in to Kovac or ask him to cancel this commission."
                        )
                        break
                    }
                    FoundryGame.WorkResult.Damaged -> {
                        soundSynth("synth.GLASSBLOWING")
                        if (player.foundryQuality == 0) {
                            mes("<col=ff0000>Your sword is ruined.</col>")
                            break
                        }
                    }
                    FoundryGame.WorkResult.Progress -> soundSynth(stationSound(station))
                    FoundryGame.WorkResult.Complete -> {
                        soundSynth(stationSound(station))
                        soundSynth("synth.BLADE4_05")
                        mes("Your sword is complete. Hand it in to Kovac.")
                        finished = true
                        break
                    }
                }
                remaining = station.cycles
            }
        } finally {
            if (generation == player.foundrySessionGeneration) player.foundryWorkStation = 0
            resetAnim()
        }
        if (finished) anim("seq.giants_foundry_finish_sword")
    }

    internal fun process(player: Player) {
        if (!FoundryGame.isEquipped(player) || !FoundryGame.isInside(player.coords) ||
            player.foundryStartQuality <= 0
        ) {
            game.store(player)
            return
        }
        if (player.currentMapClock % 2 == 0 && player.foundryTemperature > 0) {
            player.foundryTemperature--
        }
        if (player.foundrySweetUntil > 0 && player.currentMapClock >= player.foundrySweetUntil) {
            player.foundrySweetUntil = 0
            player.foundrySweetClaimed = false
            interfaces.sweetSpot(player, FoundryInterfaces.SweetSpot.Hidden)
        }
        val limit = if (FoundryMath.sections(player.foundryDifficulty) >= 6) 3 else 2
        val refining =
            player.foundryWorkStation > 0 && player.foundryQuality > 0 &&
                player.foundryCompletion < 1000
        val sweetSpotDue =
            player.currentMapClock >= player.foundryNextSweetCycle && player.foundrySweetUntil == 0
        if (refining && player.foundrySweetCount < limit && sweetSpotDue) {
            player.foundrySweetCount++
            player.foundrySweetUntil = player.currentMapClock + 9
            player.foundryNextSweetCycle = player.currentMapClock + 120
            interfaces.sweetSpot(player, FoundryInterfaces.SweetSpot.Available)
        }
    }

    private fun ProtectedAccess.requirePreform(): Boolean {
        if (!FoundryGame.isEquipped(player) || player.foundryStartQuality <= 0) {
            mes("You need a giant sword preform equipped to use this tool.")
            return false
        }
        if (player.foundryQuality == 0) {
            mes("Your sword is ruined. Hand it in to Kovac or cancel the commission.")
            return false
        }
        if (player.foundryCompletion >= 1000) {
            mes("Your sword is complete. Hand it in to Kovac.")
            return false
        }
        return true
    }

    companion object {
        private val ICE_GLOVES = listOf("obj.ice_gloves", "obj.smithing_uniform_gloves_ice")

        internal fun temperatureStep(elapsed: Int): Int = FoundryData.temperatureStep(elapsed)

        private fun stationSound(station: FoundryStation) = when (station) {
            FoundryStation.HAMMER -> "synth.SG_HAMMER_TOOL_HIT"
            FoundryStation.GRINDSTONE -> "synth.SG_GRINDWHEEL_TOOL_GRIND"
            FoundryStation.POLISHING_WHEEL -> "synth.SG_POLISHWHEEL_TOOL_POLISH"
        }
    }
}
