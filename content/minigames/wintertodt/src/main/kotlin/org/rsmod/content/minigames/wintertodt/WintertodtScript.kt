package org.rsmod.content.minigames.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.events.PlayerFoodConsumedEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.*
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.axeWoodcuttingAnim
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WintertodtScript @Inject constructor(
    private val game: WintertodtGame, private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        game.initialise()
        onEvent<GameLifecycle.LateCycle> { game.tick() }
        onEvent<GameLifecycle.PreShutdown> { game.shutdown() }
        onEvent<PlayerFoodConsumedEvent> { game.food(player, item, heal) }
        onPlayerLogin { game.login(player) }
        onPlayerLogout { game.leave(player) }
        onOpLoc1("loc.wint_door") { door(it.loc) }
        onOpLoc2("loc.wint_door") {
            mes("The Wintertodt has ${game.round.energy * 100 / WintertodtData.rules.maxEnergy}% energy left.")
        }
        onOpLoc1("loc.wint_roots") { chop(it.loc) }
        onOpLoc1("loc.wint_herb_roots") { herb(it.loc) }
        onOpLoc1("loc.wint_brazier") { light(it.loc) }
        onOpLoc1("loc.wint_brazier_broken") { repair(it.loc) }
        onOpLoc1("loc.wint_brazier_lit") { feed(it.loc) }
        onOpLocU("loc.wint_brazier", "obj.tinderbox") { light(it.loc) }
        for (torch in listOf("obj.wint_torch", "obj.wint_torch_offhand")) {
            onOpLocU("loc.wint_brazier", torch) { light(it.loc) }
        }
        onOpLocU("loc.wint_brazier_broken", "obj.hammer") { repair(it.loc) }
        onOpLocU("loc.wint_brazier_broken", "obj.imcando_hammer") { repair(it.loc) }
        for (root in listOf("obj.wint_bruma_root", "obj.wint_bruma_kindling")) {
            onOpLocU("loc.wint_brazier_lit", root) { feed(it.loc) }
        }
        onOpHeldU("obj.knife", "obj.wint_bruma_root") { fletch() }
        onOpHeldU("obj.wint_vial", "obj.wint_herb") { makePotion() }
        for (dose in 1..4) {
            val obj = "obj.wint_potion$dose"
            onOpHeld1(obj) { drink(it.slot, dose) }
            for (pyro in listOf("npc.wint_wizard", "npc.wint_wizard_down")) {
                onOpNpcU(pyro, obj) { heal(it.npc) }
            }
            onOpNpcU("npc.wint_brewma", obj) { mes("You already have a rejuvenation potion.") }
        }
        for (pyro in listOf("npc.wint_wizard", "npc.wint_wizard_down"))
            onOpNpc1(pyro) { heal(it.npc) }
        for ((crate, item) in listOf("hammer" to "hammer", "knife" to "knife",
            "axe" to "bronze_axe", "tinderbox" to "tinderbox", "vial" to "wint_vial")) {
            onOpLoc1("loc.wint_chest_$crate") { take(item, 1) }
            if (crate == "vial") {
                onOpLoc2("loc.wint_chest_vial") { take(item, 5) }
                onOpLoc3("loc.wint_chest_vial") { take(item, 10) }
                onOpLoc4("loc.wint_chest_vial") { take(item, countDialog("How many unfinished potions would you like?")) }
            }
        }
        for (cart in listOf("loc.wint_reward_pile", "loc.wint_reward_pile_empty",
            "loc.wint_reward_pile_full") + (1..4).map { "loc.wint_reward_pile_$it" }) {
            onOpLoc1(cart) { searchCart(it.loc, Int.MAX_VALUE) }
            onOpLoc2(cart) { mes("You are owed ${vars[WintertodtState.RewardPool]} rewards.") }
            onOpLoc3(cart) { searchCart(it.loc, 5) }
        }
        onOpNpc1("npc.wint_brewma") { brewma() }
        onOpNpcU("npc.wint_brewma", "obj.wint_herb") { makePotion() }
        onOpNpcU("npc.wint_brewma", "obj.wint_vial") { makePotion() }
        onOpNpc1("npc.wint_master_pyromancer") { ignisia() }
    }
    private suspend fun ProtectedAccess.door(loc: BoundLocInfo) {
        faceLoc(loc)
        if (WintertodtData.rules.contains(coords)) {
            teleport(WintertodtData.rules.exit)
            game.leave(player)
            return
        }
        if (player.statBase("stat.firemaking") < WintertodtData.rules.level) {
            mes("You need a Firemaking level of 50 to enter the Wintertodt's prison.")
            return
        }
        teleport(WintertodtData.rules.entry)
    }
    private suspend fun ProtectedAccess.chop(loc: BoundLocInfo) {
        if (!game.canAct(player)) { mes("The roots can only be chopped while the Wintertodt is awake."); return }
        val tree = ServerCacheManager.getObject("loc.tree".asRSCM(RSCMType.LOC))!!
        val axe = Woodcutting.findAxe(player, tree)
        if (axe == null) { mes("You need an axe which you have the Woodcutting level to use."); return }
        faceLoc(loc)
        while (game.canAct(player)) {
            if (inv.isFull()) { mes("Your inventory is too full to hold more roots."); resetAnim(); return }
            val currentAxe = Woodcutting.findAxe(player, tree) ?: break
            val seq = ServerCacheManager.getItem(currentAxe.id)!!.axeWoodcuttingAnim.id
            if (!work("chop", RSCM.getReverseMapping(RSCMType.SEQ, seq), "synth.CHOPPING")) return
            if (!invAdd(inv, "obj.wint_bruma_root", 1).success) break
            game.award(player, "chop")
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.fletch() {
        if ("obj.knife" !in inv) { mes("You need a knife to fletch the roots."); return }
        while ("obj.wint_bruma_root" in inv && game.canAct(player)) {
            if (!work("fletch", "seq.human_fletching", "synth.FLETCH")) return
            if ("obj.knife" !in inv || !invReplace(inv, "obj.wint_bruma_root", 1,
                "obj.wint_bruma_kindling").success) break
            game.award(player, "fletch")
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.light(loc: BoundLocInfo) {
        val index = game.campAt(loc.coords) ?: return
        if (!game.canAct(player)) return
        val torches = listOf("obj.wint_torch", "obj.wint_torch_offhand")
        if ("obj.tinderbox" !in inv && torches.none { it in inv || it in worn }) {
            mes("You need a tinderbox or bruma torch to light the brazier."); return
        }
        if (game.round.camps[index].health == 0) { mes("The pyromancer needs healing first."); return }
        faceLoc(loc)
        val seq = if (torches.any { it in inv || it in worn }) "seq.wint_light_brazier_bruma"
            else "seq.human_createfire"
        if (!work("light", seq, "synth.TINDERBOX_STRIKE")) return
        if ("obj.tinderbox" !in inv && torches.none { it in inv || it in worn }) return
        if (game.round.camps[index].light()) {
            game.synchroniseCamps()
            game.award(player, "light")
            spam("You light the brazier.")
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.repair(loc: BoundLocInfo) {
        val index = game.campAt(loc.coords) ?: return
        fun hammer() = "obj.hammer" in inv || "obj.imcando_hammer" in inv ||
            "obj.imcando_hammer" in worn || "obj.imcando_hammer_offhand" in worn
        if (!hammer()) { mes("You need a hammer to repair the brazier."); return }
        faceLoc(loc)
        if (!work("repair", "seq.pisc_repair_hammer", "synth.HAMMER_WOOD")) return
        if (hammer() && game.round.camps[index].repair()) {
            game.synchroniseCamps()
            game.award(player, "repair")
            spam("You repair the brazier.")
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.feed(loc: BoundLocInfo) {
        val index = game.campAt(loc.coords) ?: return
        faceLoc(loc)
        while (game.canAct(player) && game.round.camps[index].state == BrazierState.Lit) {
            val item = if ("obj.wint_bruma_kindling" in inv) "obj.wint_bruma_kindling"
                else if ("obj.wint_bruma_root" in inv) "obj.wint_bruma_root"
                else { mes("You have no bruma roots or kindling to feed the brazier."); break }
            val action = if (item.endsWith("kindling")) "kindling" else "root"
            if (!work(action, "seq.human_pickuptable", null)) return
            if (game.round.camps[index].state != BrazierState.Lit) break
            if (!invDel(inv, item, 1).success) break
            game.award(player, action)
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.work(action: String, seq: String, sound: String?): Boolean {
        if (!game.canAct(player) || mapClock < vars[WintertodtState.ActionTick]) return false
        val id = game.round.id
        val origin = coords
        val interruption = vars[WintertodtState.InterruptTick]
        val ticks = WintertodtData.actions.getValue(action).ticks
        WintertodtState.set(player, WintertodtState.ActionTick, mapClock + ticks)
        anim(seq)
        if (sound != null) soundSynth(sound)
        delay(ticks)
        val valid = game.canAct(player, id) && coords == origin &&
            (action == "chop" || vars[WintertodtState.InterruptTick] == interruption)
        if (!valid) resetAnim()
        return valid
    }
    private suspend fun ProtectedAccess.herb(loc: BoundLocInfo) {
        faceLoc(loc)
        if (inv.isFull()) { mes("You don't have room for a bruma herb."); return }
        anim("seq.human_pickuptable")
        delay(1)
        if (invAdd(inv, "obj.wint_herb", 1).success) spam("You pick a bruma herb.")
        resetAnim()
    }
    private suspend fun ProtectedAccess.makePotion() {
        if ("obj.wint_herb" !in inv || "obj.wint_vial" !in inv) {
            mes("You need a bruma herb and an unfinished rejuvenation potion."); return
        }
        anim("seq.human_herbing_vial")
        delay(WintertodtData.actions.getValue("potion").ticks)
        val result = player.invTransaction(inv) {
            val inventory = select(inv)
            delete(inventory, "obj.wint_herb".asRSCM(), 1)
            delete(inventory, "obj.wint_vial".asRSCM(), 1)
            add(inventory, "obj.wint_potion4".asRSCM(), 1)
        }
        if (result.success) {
            statAdvance("stat.herblore", player.statBase("stat.herblore") * 0.1)
            spam("You mix the herb into the potion.")
        }
        resetAnim()
    }
    private fun ProtectedAccess.drink(slot: Int, doses: Int) {
        if (!WintertodtData.rules.contains(coords)) { mes("This potion only helps inside the Wintertodt's prison."); return }
        if (mapClock < vars[WintertodtState.ActionTick]) return
        val old = "obj.wint_potion$doses"
        if (inv[slot]?.id != old.asRSCM()) return
        val next = if (doses == 1) "obj.wint_vial" else "obj.wint_potion${doses - 1}"
        if (!invReplace(inv, slot, 1, ServerCacheManager.getItem(next.asRSCM())!!).success) return
        WintertodtState.set(player, WintertodtState.ActionTick, mapClock + 3)
        anim("seq.human_eat")
        soundSynth("synth.DRINK")
        game.restoreWarmth(player, 300)
        spam("You drink a dose of rejuvenation potion.")
    }
    private suspend fun ProtectedAccess.heal(npc: Npc) {
        val index = game.pyroIndex(npc) ?: return
        if (!game.canAct(player)) return
        if (game.round.camps[index].health == 14) { mes("The pyromancer doesn't need healing."); return }
        val dose = (1..4).firstOrNull { "obj.wint_potion$it" in inv }
        if (dose == null) { mes("You need a rejuvenation potion to heal the pyromancer."); return }
        faceEntitySquare(npc)
        if (!work("heal", "seq.human_pickuptable", null)) return
        if (game.round.camps[index].health == 14) { resetAnim(); return }
        val next = if (dose == 1) "obj.wint_vial" else "obj.wint_potion${dose - 1}"
        if (invReplace(inv, "obj.wint_potion$dose", 1, next).success &&
            game.round.camps[index].heal()) {
            game.award(player, "heal")
            game.synchroniseCamps()
            spam("You heal the pyromancer.")
        }
        resetAnim()
    }
    private fun ProtectedAccess.take(item: String, requested: Int) {
        if (requested <= 0) return
        val count = minOf(requested, inv.indices.count { inv[it] == null })
        if (count == 0) { mes("You don't have room for that."); return }
        if (invAdd(inv, "obj.$item", count).success) anim("seq.human_pickuptable")
    }
    private suspend fun ProtectedAccess.searchCart(loc: BoundLocInfo, maximum: Int) {
        faceLoc(loc)
        if (vars[WintertodtState.RewardPool] == 0) { mes("You have no rewards to collect."); return }
        repeat(minOf(maximum, vars[WintertodtState.RewardPool])) {
            if (!WintertodtRewards.collect(player, random)) { mes("You need more inventory space to collect your reward."); return }
            anim("seq.human_pickuptable")
            if (maximum == Int.MAX_VALUE) delay(3)
        }
        resetAnim()
    }
    private suspend fun ProtectedAccess.brewma() {
        startDialogue {
            chatNpc(neutral, "I can mix bruma herbs with unfinished rejuvenation potions for you.")
            when (choice3("Make a potion.", 1, "How can I keep warm?", 2, "Goodbye.", 3)) {
                1 -> makePotion()
                2 -> chatNpc(neutral, "Wear four pieces of warm clothing. Food and rejuvenation potions will restore your warmth.")
            }
        }
    }
    private suspend fun ProtectedAccess.ignisia() {
        startDialogue {
            chatNpc(neutral, "We keep the Wintertodt imprisoned with bruma flames. Keep the braziers lit and heal the pyromancers.")
            when (choice3("How do I help?", 1, "What rewards can I earn?", 2, "Trade spare equipment for burnt pages.", 3)) {
                1 -> chatNpc(neutral, "Chop bruma roots and add them to a lit brazier. A knife turns roots into kindling. Use a hammer to repair broken braziers.")
                2 -> chatNpc(neutral, "Earn at least 500 points before the Wintertodt is subdued, then search the reward cart outside.")
                3 -> {
                    val items = WintertodtData.uniques.flatMap { it.items }.filter {
                        it != "obj.phoenixpet" && it != "obj.dragon_axe" && it != "obj.wint_burnt_page"
                    }
                    val item = items.firstOrNull { it in inv }
                    if (item == null) chatNpc(neutral, "Bring me spare pyromancer clothing, warm gloves, a bruma torch or an empty tome of fire.")
                    else if (choice2("Trade ${ServerCacheManager.getItem(item.asRSCM())!!.name} for burnt pages.", 1, "Keep it.", 2) == 1) {
                        val pages = if (item == "obj.tome_of_fire_uncharged") 250 else 50
                        if (!WintertodtRewards.give(player, listOf(WinterReward("obj.wint_burnt_page", pages)), item))
                            mes("You need more inventory space.")
                    }
                }
            }
        }
    }
}
