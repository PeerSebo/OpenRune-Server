package org.rsmod.content.minigames.tempoross.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseFishingLvl
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.tempoross.TemporossAmmo
import org.rsmod.content.minigames.tempoross.TemporossEncounter
import org.rsmod.content.minigames.tempoross.TemporossEncounterManager
import org.rsmod.content.minigames.tempoross.TemporossFishing
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants
import org.rsmod.content.skills.fishing.FishingHarpoonCharges
import org.rsmod.content.skills.fishing.configs.FishingEquipment
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TemporossArenaScript
@Inject
constructor(
    private val encounters: TemporossEncounterManager,
    private val worldClock: MapClock,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (spawn in TemporossConstants.FISHING_SPOTS) {
            onOpNpc1(spawn.npc) { attemptFish(it.npc) }
        }

        onOpNpc1(TemporossConstants.SPECIAL_FISHING_SPOT) { attemptFish(it.npc) }

        onOpNpc1(TemporossConstants.SPIRIT_POOL_INACTIVE) { harpoonPool(it.npc) }
        onOpNpc1(TemporossConstants.SPIRIT_POOL_ACTIVE) { harpoonPool(it.npc) }

        for (spawn in TemporossConstants.AMMO_CRATES) {
            onOpNpc1(spawn.npc) { fillCrate(it.npc) }
            onOpNpc4(spawn.npc) { checkAmmo(it.npc) }
            onOpNpcU(spawn.npc, TemporossConstants.RAW_HARPOONFISH) { fillCrate(it.npc) }
            onOpNpcU(spawn.npc, TemporossConstants.COOKED_HARPOONFISH) { fillCrate(it.npc) }
            onOpNpcU(spawn.npc, TemporossConstants.CRYSTAL_HARPOONFISH) { fillCrate(it.npc) }
        }

        for (spawn in TemporossConstants.HOSTS) {
            onOpNpc1(spawn.npc) { mes("The host nods you back to work.") }
            onOpNpc3(spawn.npc) { forfeit() }
            onOpNpc1(spawn.npc + "_victory") { forfeit() }
            onOpNpc3(spawn.npc + "_victory") { forfeit() }
        }

        onOpLoc1(TemporossConstants.SHRINE) { cookHarpoonfish(it.loc) }
        onOpLocU(TemporossConstants.SHRINE, TemporossConstants.RAW_HARPOONFISH) { cookHarpoonfish(it.loc) }

        for (pump in TemporossConstants.PUMPS) {
            onOpLoc1(pump) { fillWater(it.loc) }
            onOpLocU(pump, TemporossConstants.BUCKET) { fillWater(it.loc) }
        }
        for (anchor in TemporossConstants.ANCHOR_OPS) {
            onOpLoc1(anchor) { useAnchor(it.loc) }
            onOpLocU(anchor, TemporossConstants.ROPE) { useAnchor(it.loc) }
            onOpLocU(anchor, TemporossConstants.HAMMER) { useAnchor(it.loc) }
            onOpLocU(anchor, "obj.imcando_hammer") { useAnchor(it.loc) }
        }
        onOpNpc1(TemporossConstants.FIRE_NPC) { douseFire(it.npc) }
        onOpNpcU(TemporossConstants.FIRE_NPC, TemporossConstants.WATER_BUCKET) { douseFire(it.npc) }

        onOpLoc1(TemporossConstants.CRATE_HARPOON) { takeSupply(TemporossConstants.HARPOON, "harpoon") }
        onOpLoc1(TemporossConstants.CRATE_BUCKET) { takeSupply(TemporossConstants.BUCKET, "bucket") }
        onOpLoc2(TemporossConstants.CRATE_BUCKET) { takeSupplies(TemporossConstants.BUCKET, "bucket", 5) }
        onOpLoc3(TemporossConstants.CRATE_BUCKET) { takeSupplies(TemporossConstants.BUCKET, "bucket", 10) }
        onOpLoc4(TemporossConstants.CRATE_BUCKET) {
            val game = encounters.gameForPlayer(player) ?: return@onOpLoc4
            val origin = player.coords
            val count = countDialog("How many buckets would you like?")
            if (encounters.gameForPlayer(player) === game && player.coords == origin) takeSupplies(TemporossConstants.BUCKET, "bucket", count)
        }
        onOpLoc1(TemporossConstants.CRATE_ROPE) { takeSupply(TemporossConstants.ROPE, "rope") }
        onOpLoc1(TemporossConstants.CRATE_HAMMER) { takeSupply(TemporossConstants.HAMMER, "hammer") }
    }

    private fun ProtectedAccess.attemptFish(spot: Npc) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        if (!encounters.ownsNpc(game, spot)) { resetAnim(); return }
        if (!encounters.isEnergyPhase(game)) {
            resetAnim()
            mes("The fish have scattered - Tempoross is vulnerable. Harpoon the spirit pools.")
            return
        }
        val harpoon = FishingEquipment.findHarpoon(player)
        if (harpoon == null && !TemporossFishing.canBarehand(player)) {
            resetAnim()
            mes("You need a harpoon. Take one from a crate on the ship.")
            return
        }
        if (inv.isFull()) {
            resetAnim()
            mes("Your inventory is too full to fish.")
            return
        }

        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + TemporossConstants.ROLL_TICKS)
            skillAnimDelay = mapClock + TemporossConstants.ROLL_TICKS
            anim(harpoonAnimation(harpoon))
            spam("You start harpooning fish.")
            opNpc1(spot)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + TemporossConstants.ROLL_TICKS
            anim(harpoonAnimation(harpoon))
        }
        if (actionDelay == mapClock) {
            actionDelay = mapClock + TemporossConstants.ROLL_TICKS
            val tier = harpoon?.let { FishingEquipment.harpoonTier(getInvObj(it)) } ?: FishingEquipment.HarpoonTier.Standard
            if (random.of(256) < TemporossFishing.catchChance(player.fishingLvl, tier)) {
                if (!awardCatch(game, harpoon)) return
                if (!inv.isFull() && game.fishingSpots.getOrNull(game.specialSpot) === spot && random.of(2) == 0) {
                    if (!awardCatch(game, FishingEquipment.findHarpoon(player))) return
                }
            }
        }
        opNpc1(spot)
    }

    private fun ProtectedAccess.awardCatch(game: TemporossEncounter, harpoon: InvObj?): Boolean {
        val selected = FishingHarpoonCharges.select(player, harpoon)
        val infernal = FishingHarpoonCharges.isInfernal(harpoon) && random.of(3) == 0
        val crystal = FishingHarpoonCharges.isCrystal(harpoon) && random.of(3) == 0
        val fish = when { infernal -> TemporossConstants.COOKED_HARPOONFISH; crystal -> TemporossConstants.CRYSTAL_HARPOONFISH; else -> TemporossConstants.RAW_HARPOONFISH }
        val add = invAdd(inv, fish, count = 1)
        if (!add.success) {
            mes("Your inventory is too full to fish.")
            resetAnim()
            return false
        }
        if (infernal || FishingHarpoonCharges.isCrystal(harpoon)) FishingHarpoonCharges.consume(player, FishingHarpoonCharges.refreshAfterTransaction(player, selected), random)
        encounters.awardPoints(player, game, TemporossConstants.POINTS_CATCH)
        statAdvance("stat.fishing", catchXp(player.baseFishingLvl) * xpMods.get(player, "stat.fishing"))
        if (infernal) {
            statAdvance("stat.cooking", 5.0)
            encounters.awardPoints(player, game, TemporossConstants.POINTS_COOK)
        }
        if (harpoon == null) statAdvance("stat.strength", 10.0)
        spam(when { infernal -> "Your infernal harpoon cooks the harpoonfish as you catch it."; crystal -> "You catch a crystallised harpoonfish!"; else -> "You catch a harpoonfish!" })
        return true
    }

    // A foreign action delay must not skip cooking warm-up; keep the shared skill clock co-gate.
    private fun ProtectedAccess.cookHarpoonfish(shrine: BoundLocInfo) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        if (TemporossConstants.RAW_HARPOONFISH !in inv) {
            resetAnim()
            mes("You have no raw harpoonfish to cook.")
            return
        }
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + TemporossConstants.COOK_INTERVAL_TICKS)
            skillAnimDelay = mapClock + TemporossConstants.COOK_INTERVAL_TICKS
            anim(TemporossConstants.COOK_ANIM)
            opLoc1(shrine)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + TemporossConstants.COOK_INTERVAL_TICKS
            anim(TemporossConstants.COOK_ANIM)
        }
        if (actionDelay == mapClock) {
            actionDelay = mapClock + TemporossConstants.COOK_INTERVAL_TICKS
            val cooked = invReplace(inv, TemporossConstants.RAW_HARPOONFISH, 1, TemporossConstants.COOKED_HARPOONFISH)
            if (!cooked.success) { resetAnim(); return }
            encounters.awardPoints(player, game, TemporossConstants.POINTS_COOK)
            statAdvance("stat.cooking", 10.0)
            spam("You successfully cook a harpoonfish.")
        }
        if (TemporossConstants.RAW_HARPOONFISH in inv) {
            opLoc1(shrine)
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.fillCrate(crate: Npc) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.ownsNpc(game, crate)) { resetAnim(); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        val cannon = encounters.cannonIndex(game, crate) ?: run { resetAnim(); return }
        if (mapClock < game.cannons[cannon].disabledUntil) {
            resetAnim()
            mes("The cannon is electrified. Use another ammunition crate.")
            return
        }
        encounters.trackLoader(game, player, cannon)
        val raw = invTotal(inv, TemporossConstants.RAW_HARPOONFISH)
        val cooked = invTotal(inv, TemporossConstants.COOKED_HARPOONFISH)
        val crystal = invTotal(inv, TemporossConstants.CRYSTAL_HARPOONFISH)
        if (raw + cooked + crystal == 0) {
            resetAnim()
            game.loaders.remove(player)
            mes("You have no harpoonfish to load.")
            return
        }
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + TemporossConstants.FILL_INTERVAL_TICKS)
            skillAnimDelay = mapClock + TemporossConstants.FILL_INTERVAL_TICKS
            opNpc1(crate)
            return
        }
        var remaining = raw + cooked + crystal
        if (actionDelay == mapClock) {
            actionDelay = mapClock + TemporossConstants.FILL_INTERVAL_TICKS
            skillAnimDelay = mapClock + TemporossConstants.FILL_INTERVAL_TICKS
            val loadCooked = cooked > 0
            val loadCrystal = !loadCooked && crystal > 0
            val obj = when { loadCooked -> TemporossConstants.COOKED_HARPOONFISH; loadCrystal -> TemporossConstants.CRYSTAL_HARPOONFISH; else -> TemporossConstants.RAW_HARPOONFISH }
            val del = invDel(inv, obj, count = 1)
            if (!del.success) { resetAnim(); return }
            remaining -= 1
            val points =
                if (loadCooked) TemporossConstants.POINTS_DEPOSIT_COOKED else TemporossConstants.POINTS_DEPOSIT_RAW
            encounters.load(game, cannon, when { loadCooked -> TemporossAmmo.Cooked; loadCrystal -> TemporossAmmo.Crystal; else -> TemporossAmmo.Raw })
            encounters.awardPoints(player, game, points)
            statAdvance("stat.fishing", depositXp(player.baseFishingLvl) * (if (loadCrystal) 1.2 else 1.0) * xpMods.get(player, "stat.fishing"))
            spam("You load the cannon with a harpoonfish.")
        }
        if (remaining > 0 && encounters.isPlaying(game)) {
            opNpc1(crate)
        } else {
            game.loaders.remove(player)
            resetAnim()
        }
    }

    private fun ProtectedAccess.checkAmmo(crate: Npc) {
        val game = encounters.gameForPlayer(player) ?: return
        val index = encounters.cannonIndex(game, crate) ?: return
        val ammo = game.cannons[index].ammunition
        mes("The crate contains ${ammo.count { it == TemporossAmmo.Raw }} raw, ${ammo.count { it == TemporossAmmo.Cooked }} cooked and ${ammo.count { it == TemporossAmmo.Crystal }} crystallised harpoonfish.")
    }

    private fun ProtectedAccess.harpoonPool(pool: Npc) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        if (!encounters.ownsNpc(game, pool)) { resetAnim(); return }
        if (!encounters.isVulnerable(game)) {
            resetAnim()
            if (encounters.isEnergyPhase(game)) {
                mes("The spirit pool is dormant. Load the cannons to weaken Tempoross first.")
            }
            return
        }
        val harpoon = FishingEquipment.findHarpoon(player)
        if (harpoon == null && !TemporossFishing.canBarehand(player)) {
            resetAnim()
            mes("You need a harpoon. Take one from a crate on the ship.")
            return
        }
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + TemporossConstants.POOL_ROLL_TICKS)
            skillAnimDelay = mapClock + TemporossConstants.POOL_ROLL_TICKS
            anim(harpoonAnimation(harpoon))
            opNpc1(pool)
            return
        }
        if (skillAnimDelay <= mapClock) {
            skillAnimDelay = mapClock + TemporossConstants.POOL_ROLL_TICKS
            anim(harpoonAnimation(harpoon))
        }
        if (actionDelay == mapClock) {
            actionDelay = mapClock + TemporossConstants.POOL_ROLL_TICKS
            val drain = random.of(TemporossConstants.ESSENCE_DRAIN_MIN, TemporossConstants.ESSENCE_DRAIN_MAX)
            encounters.drainEssence(game, drain)
            if (FishingHarpoonCharges.isCrystal(harpoon)) FishingHarpoonCharges.consume(player, FishingHarpoonCharges.select(player, harpoon), random)
            encounters.awardPoints(player, game, TemporossConstants.POINTS_SCATTER)
            statAdvance("stat.fishing", poolXp(player.baseFishingLvl) * xpMods.get(player, "stat.fishing"))
            player.runEnergy =
                minOf(constants.run_max_energy, player.runEnergy + constants.run_max_energy * 3 / 100)
            spam("The glowing fish scatter, shedding their magical scales.")
        }
        if (encounters.isVulnerable(game)) {
            opNpc1(pool)
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.takeSupply(obj: String, label: String) = takeSupplies(obj, label, 1)

    private fun ProtectedAccess.takeSupplies(obj: String, label: String, requested: Int) {
        if (requested <= 0) return
        val game = encounters.gameForPlayer(player) ?: return
        if (!encounters.canAct(player, game)) return
        if (inv.isFull()) {
            mes("You don't have room for that.")
            return
        }
        val count = minOf(requested, inv.indices.count { inv[it] == null })
        if (invAdd(inv, obj, count = count).success) mes(if (count == 1) "You take a $label from the crate." else "You take $count ${label}s from the crate.")
    }

    private fun ProtectedAccess.forfeit() {
        val game = encounters.gameForPlayer(player) ?: return
        val won = game.phase == org.rsmod.content.minigames.tempoross.TemporossPhase.WON
        telejump(TemporossConstants.RETURN_COORD)
        if (player.coords != TemporossConstants.RETURN_COORD) return
        encounters.forfeit(player, worldClock.cycle)
        resetAnim()
        mes(if (won) "You sail back to the Ruins of Unkah." else "You forfeit the encounter and sail back to the Ruins of Unkah.")
    }

    private fun ProtectedAccess.harpoonAnimation(harpoon: InvObj?): String =
        if (harpoon == null) "seq.brut_player_hand_fishing_start" else FishingEquipment.harpoonAnim(player, harpoon)

    private fun ProtectedAccess.fillWater(pump: BoundLocInfo) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        if (TemporossConstants.BUCKET !in inv) { resetAnim(); mes("You have no empty buckets to fill."); return }
        if (actionDelay > mapClock) { opLoc1(pump); return }
        actionDelay = mapClock + 1
        val filled = invReplace(inv, TemporossConstants.BUCKET, 1, TemporossConstants.WATER_BUCKET)
        if (!filled.success) { resetAnim(); return }
        spam("You fill the bucket with water.")
        if (TemporossConstants.BUCKET in inv) opLoc1(pump) else resetAnim()
    }

    private fun ProtectedAccess.useAnchor(loc: BoundLocInfo) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.isPlaying(game)) { resetAnim(); return }
        val index = encounters.anchorIndex(game, loc) ?: return
        if (player !in game.tethers && !encounters.canAct(player, game)) { resetAnim(); return }
        if (index !in game.brokenAnchors) { encounters.tether(game, player, index); return }
        if (!encounters.canAct(player, game)) { resetAnim(); return }
        val imcando = "obj.imcando_hammer" in inv || "obj.imcando_hammer" in worn || "obj.imcando_hammer_offhand" in worn
        if (TemporossConstants.HAMMER !in inv && !imcando) { resetAnim(); mes("You need a hammer to repair this."); return }
        if (actionDelay < mapClock || skillAnimDelay < mapClock) {
            actionDelay = maxOf(actionDelay, mapClock + 3)
            skillAnimDelay = mapClock + 3
            anim(if (imcando) "seq.pisc_repair_imcando_hammer" else "seq.pisc_repair_hammer")
            opLoc1(loc)
            return
        }
        if (actionDelay > mapClock) { opLoc1(loc); return }
        if (encounters.repair(game, index)) {
            encounters.awardPoints(player, game, 40)
            if (vars["varbit.poh_house_location"] > 0) statAdvance("stat.construction", player.constructionLvl * 4.0)
            spam("You repair the ${if (TemporossConstants.ANCHORS[index].mast) "mast" else "totem pole"}.")
        }
        resetAnim()
    }

    private fun ProtectedAccess.douseFire(npc: Npc) {
        val game = encounters.gameForPlayer(player) ?: run { resetAnim(); return }
        if (!encounters.canAct(player, game) || !encounters.hasFire(game, npc)) { resetAnim(); return }
        if (TemporossConstants.WATER_BUCKET !in inv) { resetAnim(); mes("You need a bucket of water to extinguish the fire."); return }
        if (actionDelay > mapClock) { opNpc1(npc); return }
        val used = invReplace(inv, TemporossConstants.WATER_BUCKET, 1, TemporossConstants.BUCKET)
        if (!used.success) { resetAnim(); return }
        check(encounters.douse(game, npc)) // Keep inventory and fire mutations adjacent without suspension.
        actionDelay = mapClock + 3
        anim("seq.throw_bucketofwater")
        encounters.awardPoints(player, game, 40)
        spam("You extinguish the fire.")
    }

    private fun catchXp(base: Int): Double = xpBase(base) / 100.0

    private fun depositXp(base: Int): Double = xpBase(base) / 10.0

    private fun poolXp(base: Int): Double = xpBase(base) * 11.0 / 100.0

    private fun xpBase(base: Int): Double {
        val level = base.coerceIn(35, 99)
        return if (level < 70) {
            Math.floor(450.0 + 550.0 * (level - 35) / 64.0)
        } else {
            Math.floor(890.0 + 110.0 * (level - 70) / 29.0)
        }
    }
}
