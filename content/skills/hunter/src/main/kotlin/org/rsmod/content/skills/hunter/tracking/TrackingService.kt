@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.tracking

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.herbiboarCount
import org.rsmod.content.skills.hunter.pursuitRingUsed
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

@Singleton
internal class TrackingService @Inject constructor(private val random: GameRandom, private val xpMods: XpModifiers, private val events: EventBus) {
    internal class Hunt(val area: TrackingArea, var node: String) {
        val visited = mutableMapOf(node to 1)
        val used = mutableSetOf<String>()
        var next: TrackingEdge? = null
        var finished = false
        var searchedDen = false
        var emerged = false
        var harvest: List<HerbiboarLoot.Item>? = null
        var inspection: HerbiboarLoot.Item? = null
        var inspectionRolled = false
        var previous: String? = null
    }
    fun hunt(player: Player): Hunt? = player.attr[HUNT]
    fun area(loc: BoundLocInfo): TrackingArea? = TrackingRoutes.all.firstOrNull { a -> (a.starts + a.clues + a.dens).any { it.matches(loc) } }

    fun start(player: Player, loc: BoundLocInfo): Boolean {
        val area = TrackingRoutes.all.firstOrNull { a -> a.starts.any { it.matches(loc) } } ?: return false
        if (!near(player, loc.coords, 2)) return false
        if (player.hunterLvl < area.prey.level) { player.mes("You need a Hunter level of ${area.prey.level} to track this creature."); return false }
        if (area.prey == TrackingPrey.HERBIBOAR && !QuestRequirements.hasCompleted(player, "quest_bonevoyage")) {
            player.mes("You must complete Bone Voyage before tracking herbiboars."); return false
        }
        clear(player)
        val node = area.nodes.first { it.start?.matches(loc) == true }
        val hunt = Hunt(area, node.name)
        player.attr[HUNT] = hunt
        if (!choose(hunt, initial = true)) { clear(player); return false }
        reveal(player, hunt)
        if (!pursuit(player, hunt)) player.spam("You find some tracks leading away from here.")
        return true
    }

    fun inspect(player: Player, loc: BoundLocInfo): Boolean {
        val hunt = hunt(player) ?: run { player.spam("You find no signs of a creature. Try inspecting a burrow to begin a trail."); return false }
        val edge = hunt.next
        if (hunt.finished || edge == null || edge.clue(hunt.node)?.matches(loc) != true || !near(player, loc.coords, 2)) {
            player.spam("You find no new tracks here."); return false
        }
        // Hold inspection loot with its clue so inventory failure cannot reroll it.
        if (hunt.area.prey == TrackingPrey.HERBIBOAR && !willFinish(hunt, edge)) {
            if (!hunt.inspectionRolled) { hunt.inspection = HerbiboarLoot.inspection(random); hunt.inspectionRolled = true }
            val item = hunt.inspection
            if (item != null && !give(player, listOf(item))) { player.mes("Make room in your inventory to collect what you found."); return false }
            player.statAdvance("stat.hunter", 50.0 * xpMods.get(player, "stat.hunter"))
            hunt.inspection = null
            hunt.inspectionRolled = false
        }
        reveal(player, hunt)
        if (!pursuit(player, hunt)) player.spam(if (hunt.finished) "The tracks lead into a hiding place nearby." else "You find more tracks leading away from here.")
        return true
    }

    private fun choose(hunt: Hunt, initial: Boolean = false): Boolean {
        val choices = hunt.area.edges.filter { edge ->
            (edge.from == hunt.node || edge.to == hunt.node) && edge.suffix !in hunt.used &&
                (hunt.visited[edge.other(hunt.node)] ?: 0) < 2 && (initial || edge.clue(hunt.node) != null) &&
                (initial || guaranteedClue(hunt)?.let { edge.clue(hunt.node)?.type == it } != false)
        }
        if (choices.isEmpty()) { hunt.next = null; return false }
        hunt.next = choices[random.of(choices.size)]
        return true
    }

    private fun reveal(player: Player, hunt: Hunt) {
        val edge = checkNotNull(hunt.next)
        VarPlayerIntMapSetter.set(player, edge.varbit, edge.state(hunt.node))
        hunt.used += edge.suffix
        hunt.previous = hunt.node
        hunt.node = edge.other(hunt.node)
        hunt.visited[hunt.node] = (hunt.visited[hunt.node] ?: 0) + 1
        if (hunt.area.prey == TrackingPrey.HERBIBOAR) VarPlayerIntMapSetter.set(player, "varbit.hunting_trails_used_fossil", if (hunt.used.size > 1) 1 else 0)
        val node = hunt.area.byName.getValue(hunt.node)
        val minimum = if (hunt.area.prey == TrackingPrey.POLAR) 3 else 4
        if (hunt.used.size >= minimum && node.den != null) {
            hunt.finished = true
            hunt.next = null
            if (hunt.area.prey == TrackingPrey.HERBIBOAR) VarPlayerIntMapSetter.set(player, "varbit.hunting_trail_ends_fossil", node.denIndex)
            return
        }
        if (!choose(hunt)) {
            if (node.den != null) {
                hunt.finished = true
                if (hunt.area.prey == TrackingPrey.HERBIBOAR) VarPlayerIntMapSetter.set(player, "varbit.hunting_trail_ends_fossil", node.denIndex)
                return
            }
            clear(player)
            player.mes("The creature has confused you with its tracks. You'll need to start again.")
            return
        }
        val next = checkNotNull(hunt.next)
        VarPlayerIntMapSetter.set(player, next.varbit, next.state(hunt.node) - 2)
    }

    private fun willFinish(hunt: Hunt, edge: TrackingEdge): Boolean {
        val next = edge.other(hunt.node)
        if (hunt.area.byName.getValue(next).den == null) return false
        if (hunt.used.size + 1 >= (if (hunt.area.prey == TrackingPrey.POLAR) 3 else 4)) return true
        return hunt.area.edges.none { candidate -> (candidate.from == next || candidate.to == next) &&
            candidate.suffix != edge.suffix && candidate.suffix !in hunt.used && candidate.clue(next) != null &&
            (hunt.visited[candidate.other(next)] ?: 0) < 2 }
    }

    private fun guaranteedClue(hunt: Hunt): String? {
        if (hunt.area.prey != TrackingPrey.HERBIBOAR) return null
        return when (hunt.previous to hunt.node) {
            "E" to "A" -> "loc.hunting_trail_clue9_1"
            "S" to "C" -> "loc.hunting_trail_clue9_0"
            "C" to "D" -> "loc.hunting_trail_clue9_3"
            "A" to "E" -> "loc.hunting_trail_clue9_6"
            "G" to "F" -> "loc.hunting_trail_clue9_2"
            "D" to "G" -> "loc.hunting_trail_clue9_8"
            "D" to "H" -> "loc.hunting_trail_clue9_9"
            "N" to "H" -> "loc.hunting_trail_clue10_9"
            "L" to "I" -> "loc.hunting_trail_clue10_6"
            "K" to "I" -> if (hunt.used.size == 1) "loc.hunting_trail_clue10_6" else null
            "E" to "I" -> "loc.hunting_trail_clue9_7"
            else -> null
        }
    }

    private fun pursuit(player: Player, hunt: Hunt): Boolean {
        if (hunt.area.prey == TrackingPrey.HERBIBOAR || hunt.finished || player.worn.count(RING) == 0 || this.hunt(player) !== hunt) return false
        val spent = player.pursuitRingUsed + 1
        player.pursuitRingUsed = if (spent == 10) 0 else spent
        if (spent == 10) { player.worn[12] = null; player.mes("Your ring of pursuit crumbles to dust.") }
        repeat(hunt.area.edges.size) {
            if (!hunt.finished && this.hunt(player) === hunt && hunt.next != null) reveal(player, hunt)
        }
        player.spam("Your ring of pursuit reveals the creature's trail.")
        return true
    }

    fun searchDen(player: Player, loc: BoundLocInfo): Boolean {
        val hunt = hunt(player)
        val found = hunt != null && hunt.finished && hunt.area.byName.getValue(hunt.node).den?.matches(loc) == true && near(player, loc.coords, 2)
        if (found) hunt!!.searchedDen = true
        player.spam(if (found) "It looks like there might be something hiding here." else "You find no creature hiding here.")
        return found
    }

    fun canCatch(player: Player, loc: BoundLocInfo): Hunt? {
        val hunt = hunt(player) ?: return null
        if (!hunt.finished || !near(player, loc.coords, 2) || hunt.area.byName.getValue(hunt.node).den?.matches(loc) != true || hunt.emerged) return null
        if (hunt.area.prey != TrackingPrey.HERBIBOAR) {
            if (!hunt.searchedDen) { player.mes("Search this hiding place before trying to catch the creature."); return null }
            if (player.worn.count(WAND) == 0) { player.mes("You need to wield a noose wand to catch this creature."); return null }
            if (player.hunterLvl < hunt.area.prey.level) { player.mes("Your Hunter level is too low to catch this creature."); return null }
            if (player.inv.freeSpace() < 3) { player.mes("You need three free inventory spaces for your catch."); return null }
        }
        return hunt
    }

    fun catch(player: Player, loc: BoundLocInfo, expected: Hunt): Boolean {
        if (hunt(player) !== expected || canCatch(player, loc) !== expected) return false
        val prey = expected.area.prey
        if (prey == TrackingPrey.HERBIBOAR) {
            expected.emerged = true
            val node = expected.area.byName.getValue(expected.node)
            VarPlayerIntMapSetter.set(player, "varbit.fossil_herbiboar_visible", node.denIndex)
            player.statAdvance("stat.hunter", HerbiboarLoot.hunterXp(player.statBase("stat.hunter")) * xpMods.get(player, "stat.hunter"))
            player.spam("You kick the tunnel and startle a herbiboar out of hiding.")
            return true
        }
        if (prey == TrackingPrey.POLAR && random.of(256) >= (239 + (player.hunterLvl - 1) * 9).coerceAtMost(256)) {
            clear(player); player.spam("The kebbit escapes your noose."); return false
        }
        if (!give(player, listOf(HerbiboarLoot.Item("obj.bones", 1), HerbiboarLoot.Item("obj.spit_raw_beast_meat", 1), HerbiboarLoot.Item(prey.loot, 1)))) return false
        clear(player)
        player.statAdvance("stat.hunter", prey.xp * xpMods.get(player, "stat.hunter"))
        events.publish(HunterCatchEvent(player, prey.eventKey))
        player.spam("You catch the creature in your noose.")
        return true
    }

    fun canHarvest(player: Player, npc: Npc): Hunt? {
        val hunt = hunt(player) ?: return null
        if (!hunt.emerged || hunt.area.prey != TrackingPrey.HERBIBOAR || !near(player, npc.coords, 2)) return null
        val index = hunt.area.byName.getValue(hunt.node).denIndex
        if (npc.type.id != "npc.fossil_herbiboar_$index".asRSCM()) return null
        if (player.stat("stat.herblore") < 31) { player.mes("You need a Herblore level of 31 to harvest this herbiboar."); return null }
        return hunt
    }

    fun harvest(player: Player, npc: Npc, expected: Hunt): Boolean {
        if (hunt(player) !== expected || canHarvest(player, npc) !== expected) return false
        var items = expected.harvest ?: player.attr[PENDING_HARVEST]?.split(';')?.map {
            val fields = it.split(':')
            HerbiboarLoot.Item(fields[0], fields[1].toInt())
        }
        if (items == null) {
            val count = random.of(1, 3) + if (hasMagicSecateurs(player)) 1 else 0
            items = buildList {
                add(HerbiboarLoot.Item(HerbiboarLoot.herb(player.stat("stat.herblore"), random), count))
                if (random.of(6500) == 0 && !ownsHerbi(player)) add(HerbiboarLoot.Item(PET, 1))
            }
            expected.harvest = items
            player.attr[PENDING_HARVEST] = items.joinToString(";") { "${it.type}:${it.count}" }
        }
        if (ownsHerbi(player)) items = items.filter { it.type != PET }
        if (!give(player, items)) { player.mes("Make room in your inventory to harvest the herbs."); return false }
        player.attr.remove(PENDING_HARVEST)
        val herbCount = items.first().count
        clear(player)
        player.statAdvance("stat.herblore", (herbCount - 1) * 25.0 * xpMods.get(player, "stat.herblore"))
        player.herbiboarCount = (player.herbiboarCount.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (items.any { it.type == PET }) {
            VarPlayerIntMapSetter.set(player, "varbit.pet_insurance_herbiboar", 1)
            CollectionLog.grant(player, PET, 1)
            player.mes("You have a funny feeling like you're being followed.")
        }
        events.publish(HunterCatchEvent(player, TrackingPrey.HERBIBOAR.eventKey))
        player.spam("You harvest the herbs from the herbiboar's back. It scampers away.")
        return true
    }

    fun clear(player: Player) {
        player.attr.remove(HUNT)
        for (area in TrackingRoutes.all) for (edge in area.edges) VarPlayerIntMapSetter.set(player, edge.varbit, 0)
        VarPlayerIntMapSetter.set(player, "varbit.hunting_trail_ends_fossil", 0)
        VarPlayerIntMapSetter.set(player, "varbit.hunting_trails_used_fossil", 0)
        VarPlayerIntMapSetter.set(player, "varbit.fossil_herbiboar_visible", 0)
        VarPlayerIntMapSetter.set(player, "varbit.fossil_herbiboar_anim", 0)
    }
    private fun give(player: Player, items: List<HerbiboarLoot.Item>): Boolean = player.invTransaction(player.inv) {
        val inventory = select(player.inv)
        for (item in items) add(inventory, item.type.asRSCM(), item.count)
    }.success
    private fun hasMagicSecateurs(player: Player): Boolean = player.inv.count("obj.fairy_enchanted_secateurs") > 0 || player.worn.count("obj.fairy_enchanted_secateurs") > 0
    private fun ownsHerbi(player: Player): Boolean {
        if (player.vars["varbit.pet_insurance_herbiboar"] > 0 || player.vars["varbit.pet_menagerie_herbiboar"] > 0) return true
        // Resolve the lazy inventory before iterating invMap; it can add a map entry.
        val collection = player.collectionTransmit
        return (sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence().filter { it !== collection }).any { it.count(PET) > 0 }
    }
    private fun near(player: Player, coords: CoordGrid, range: Int) = player.coords.level == coords.level && player.coords.chebyshevDistance(coords) <= range
    companion object {
        private val HUNT = AttributeKey<Hunt>()
        private val PENDING_HARVEST = AttributeKey<String>(persistenceKey = "hunter_pending_herbiboar_harvest")
        const val WAND = "obj.noose_wand"
        const val RING = "obj.ring_of_pursuit"
        const val PET = "obj.herbiboarpet"
    }
}
