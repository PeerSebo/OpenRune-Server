@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.FalconryPrey
import org.rsmod.content.skills.hunter.configs.FalconryRules
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.inv.InvObj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Singleton
internal class FalconryService @Inject constructor(
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val world: WorldRepository,
    private val ray: RayCastValidator,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val events: EventBus,
) {
    internal data class Flight(val target: Npc, val uid: NpcUid, val prey: FalconryPrey, val arrives: Int)
    internal data class Catch(val falcon: Npc, val prey: FalconryPrey, val expires: Int)
    internal data class Return(val origin: CoordGrid, val arrives: Int)
    internal class Rental(var flight: Flight? = null, var catch: Catch? = null,
        var returning: Return? = null)

    private val rentals = mutableMapOf<Player, Rental>()
    private val reservations = mutableMapOf<NpcUid, Player>()
    private val preyById by lazy { FalconryPrey.entries.associateBy { it.npc.asRSCM(RSCMType.NPC) } }
    private val gloveIds by lazy { setOf(EMPTY_GLOVE.asRSCM(RSCMType.OBJ), FULL_GLOVE.asRSCM(RSCMType.OBJ)) }

    fun rent(player: Player, invest: Boolean = false): Boolean {
        val level = FalconryPrey.SPOTTED.level
        if (!inArea(player.coords) || player.hunterLvl < level) {
            player.mes("You need a Hunter level of $level to practise falconry here.")
            return false
        }
        val rental = rentals[player]
        if (rental?.flight != null || rental?.catch != null || rental?.returning != null) {
            player.mes("Your falcon is still hunting. Retrieve it before asking for another.")
            return false
        }
        if (invest && player.falconryInvested) {
            player.mes("You have already invested in Matthias' falconry.")
            return false
        }
        if (!invest && rental != null && player.vars[RETURNED] == 0 && player.worn.count(FULL_GLOVE) > 0) {
            player.mes("You already have a falcon.")
            return false
        }
        val fee = if (invest) INVESTMENT else if (rental != null || player.falconryInvested) 0 else RENTAL_FEE
        if (player.inv.count("obj.coins") < fee) {
            player.mes("You need ${if (invest) "500,000" else "500"} coins to ${if (invest) "invest in the falconry" else "rent a falcon"}.")
            return false
        }
        val unequip = EQUIPMENT_SLOTS.filter { player.worn[it]?.id !in gloveIds && player.worn[it] != null }
        val freedCoinSlot = if (fee > 0 && player.inv.count("obj.coins") == fee) 1 else 0
        if (player.inv.freeSpace() + freedCoinSlot < unequip.size) {
            player.mes("Please make room to unequip your weapon, shield and gloves first.")
            return false
        }
        if (fee > 0 && !player.invDel(player.inv, "obj.coins", fee).success) return false
        for (slot in unequip) {
            val item = checkNotNull(player.worn[slot])
            check(player.invAdd(player.inv, item.id, item.count, vars = item.vars).success)
            player.worn[slot] = null
        }
        if (invest) player.falconryInvested = true
        removeGloves(player)
        rentals[player] = Rental()
        VarPlayerIntMapSetter.set(player, RETURNED, 0)
        equip(player, FULL_GLOVE)
        player.mes(if (invest) "Matthias thanks you for your investment. You may now use his falcons free of charge." else "Matthias gives you a falcon and a falconer's glove.")
        return true
    }

    fun withinLaunchRange(player: Player, target: Npc): Boolean =
        player.coords.level == target.coords.level && player.coords.chebyshevDistance(target.coords) <= LAUNCH_RANGE &&
            ray.hasLineOfSight(player.coords, target.coords, destWidth = target.size, destLength = target.size)

    fun launch(player: Player, target: Npc): Boolean {
        val prey = preyById[target.visType.id] ?: return false
        val rental = rentals[player]
        if (rental == null || player.vars[RETURNED] != 0 || player.worn.count(FULL_GLOVE) == 0) {
            player.mes("Speak to Matthias to obtain a falcon first.")
            return false
        }
        if (rental.flight != null || rental.catch != null || rental.returning != null) { player.mes("Your falcon is already hunting."); return false }
        if (!inArea(player.coords) || !inArea(target.coords) || !target.isValidTarget() || !withinLaunchRange(player, target)) return false
        if (player.hunterLvl < prey.level) { player.mes("You need a Hunter level of ${prey.level} to catch this kebbit."); return false }
        if (player.worn[5] != null || player.worn[9] != null) { player.mes("You must remove your shield and gloves to use the falcon."); return false }
        if (reservations.containsKey(target.uid)) { player.spam("Another falcon is already pursuing that kebbit."); return false }
        val ticks = FalconryRules.flightTicks(player.coords.chebyshevDistance(target.coords))
        reservations[target.uid] = player
        rental.flight = Flight(target, target.uid, prey, clock.cycle + ticks)
        equip(player, EMPTY_GLOVE)
        player.faceNpc(target)
        player.anim("seq.human_falconer_attack")
        player.spotanim("spotanim.falcon_launch_anim")
        world.soundArea(player.coords, "synth.HUNTING_FALCON_FLY")
        world.projAnim(ProjAnim(
            spotanim = "spotanim.falcon_travel_anim".asRSCM(RSCMType.SPOTANIM),
            startHeight = 32, endHeight = 0, startTime = 0, endTime = ticks * 30 - 1,
            angle = 0, progress = 0, sourceIndex = -(player.slotId + 1), targetIndex = target.slotId + 1,
            startCoord = player.coords, endCoord = target.coords,
        ))
        player.spam("You send your falcon after the kebbit.")
        return true
    }

    fun tick() {
        for ((player, rental) in rentals.toList()) {
            if (!player.isSlotAssigned || !inArea(player.coords) || player.statMap.getCurrentLevel("stat.hitpoints") <= 0 || player.worn[3]?.id !in gloveIds) {
                cleanup(player)
                continue
            }
            rental.flight?.takeIf { clock.cycle >= it.arrives }?.let { finishFlight(player, rental, it) }
            rental.returning?.takeIf { clock.cycle >= it.arrives }?.let {
                rental.returning = null
                equip(player, FULL_GLOVE)
                player.spam("Your falcon fails to catch the kebbit and returns to you.")
            }
            rental.catch?.takeIf { clock.cycle >= it.expires }?.let {
                deleteFalcon(it.falcon)
                rental.catch = null
                VarPlayerIntMapSetter.set(player, RETURNED, 1)
                clearHint(player)
                player.spam("Your falcon has left its prey. You see it heading back toward the falconer.")
            }
        }
    }

    internal fun finishFlight(player: Player, rental: Rental, flight: Flight) {
        if (rentals[player] !== rental || rental.flight !== flight) return
        if (!inArea(player.coords) || player.worn[3]?.id !in gloveIds) { cleanup(player); return }
        rental.flight = null
        reservations.remove(flight.uid, player)
        val target = flight.target
        val valid = target.uid == flight.uid && target.isValidTarget() && target.visType.id == flight.prey.npc.asRSCM(RSCMType.NPC) && inArea(target.coords)
        if (!valid || random.of(256) >= HunterGuildOutfit.catchChance(player, flight.prey.chance(player.hunterLvl))) {
            returnFalcon(player, rental, target.coords.takeIf(::inArea) ?: player.coords)
            return
        }
        val falcon = Npc(flight.prey.falcon, target.coords).apply { noneMode(); hitpoints = 1 }
        npcs.add(falcon, CATCH_TIMEOUT + 1)
        npcs.despawn(target, PREY_RESPAWN)
        rental.catch = Catch(falcon, flight.prey, clock.cycle + CATCH_TIMEOUT)
        world.soundArea(falcon.coords, "synth.HUNTING_FALCON_SWOOP")
        world.spotanimMap(SpotanimType("spotanim.falcon_hit_anim".asRSCM()), falcon.coords)
        player.client.write(HintArrow(HintArrow.NpcHintArrow(falcon.slotId)))
        player.spam("Your falcon catches the kebbit. Retrieve it to collect your catch.")
    }

    fun retrieve(player: Player, falcon: Npc): Boolean {
        val rental = rentals[player]
        val caught = rental?.catch
        if (caught == null || caught.falcon !== falcon) { player.mes("That is not your falcon."); return false }
        if (player.worn[3]?.id !in gloveIds) { cleanup(player); return false }
        if (!falcon.isValidTarget() || player.coords.level != falcon.coords.level || player.coords.chebyshevDistance(falcon.coords) > 1 || !inArea(player.coords)) return false
        rental.catch = null
        player.faceSquare(falcon.coords)
        player.anim("seq.human_pickupfloor")
        deleteFalcon(falcon)
        clearHint(player)
        equip(player, FULL_GLOVE)
        var discarded = false
        for (loot in caught.prey.loot) {
            if (!player.invAdd(player.inv, loot).success) discarded = true
        }
        player.statAdvance("stat.hunter", caught.prey.xp * xpMods.get(player, "stat.hunter"))
        events.publish(HunterCatchEvent(player, caught.prey.npc))
        player.spam("You retrieve your falcon and its catch.")
        if (discarded) player.spam("You have no room for some of the catch, so you leave it behind.")
        return true
    }

    fun cleanup(player: Player) {
        val rental = rentals.remove(player)
        VarPlayerIntMapSetter.set(player, RETURNED, 0)
        rental?.flight?.let { reservations.remove(it.uid, player) }
        rental?.catch?.let { deleteFalcon(it.falcon) }
        if (rental != null) clearHint(player)
        removeGloves(player)
    }

    private fun deleteFalcon(falcon: Npc) { if (falcon.isSlotAssigned) npcs.del(falcon, Int.MAX_VALUE) }
    private fun returnFalcon(player: Player, rental: Rental, origin: CoordGrid) {
        val ticks = FalconryRules.flightTicks(origin.chebyshevDistance(player.coords))
        rental.returning = Return(origin, clock.cycle + ticks)
        world.projAnim(ProjAnim(
            spotanim = "spotanim.falcon_travel_anim".asRSCM(RSCMType.SPOTANIM),
            startHeight = 0, endHeight = 32, startTime = 0, endTime = ticks * 30 - 1,
            angle = 0, progress = 0, sourceIndex = 0, targetIndex = -(player.slotId + 1),
            startCoord = origin, endCoord = player.coords,
        ))
    }

    fun active(player: Player): Boolean = player in rentals
    private fun clearHint(player: Player) { player.client.write(HintArrow(HintArrow.ResetHintArrow)) }
    private fun equip(player: Player, symbol: String) {
        player.worn[3] = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM(RSCMType.OBJ))))
        player.rebuildAppearance()
    }
    private fun removeGloves(player: Player) {
        for (inv in (player.invMap.values + listOf(player.inv, player.worn)).distinct()) {
            for (slot in inv.objs.indices) if (inv[slot]?.id in gloveIds) inv[slot] = null
        }
        player.rebuildAppearance()
    }

    internal fun rental(player: Player): Rental? = rentals[player]

    companion object {
        val RENTAL_FEE get() = FalconryRules.rentalFee
        val INVESTMENT get() = FalconryRules.investment
        const val EMPTY_GLOVE = "obj.falcon_gloves"
        const val FULL_GLOVE = "obj.falcon_on_gloves"
        private const val RETURNED = "varp.hunter_falconry_returned"
        val LAUNCH_RANGE get() = FalconryRules.range
        val CATCH_TIMEOUT get() = FalconryRules.catchTimeout
        val PREY_RESPAWN get() = FalconryRules.preyRespawn
        private val EQUIPMENT_SLOTS = listOf(3, 5, 9)

        fun inArea(coords: CoordGrid): Boolean = FalconryRules.contains(coords)
    }
}
