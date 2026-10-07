package org.rsmod.content.bosses.skotizo

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.*
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SkotizoEntrance @Inject internal constructor(private val rooms: SkotizoRooms, private val clock: MapClock) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLocU("loc.cata_altar", "obj.cata_totem") { enter() }
        onOpLoc1("loc.cata_altar") { enter() }
        onOpLoc1("loc.cata_boss_exit_portal") {
            val room = rooms.forPlayer(player) ?: return@onOpLoc1
            if (!room.contains(player.coords)) return@onOpLoc1
            val session = rooms.manager.sessionForId(room.id) ?: return@onOpLoc1
            val destination = rooms.manager.leave(player, session, clock.cycle)
            telejump(destination, TeleportType.Exempt)
        }
        for (left in Parts.indices) for (right in left + 1 until Parts.size) {
            onOpHeldU(Parts[left], Parts[right]) { assemble() }
        }
        onEvent<GameLifecycle.LateCycle> { rooms.tick() }
        onEvent<InstanceEndedEvent>(instanceEventId(SkotizoRules.Key)) { rooms.remove(instanceId) }
    }

    private suspend fun ProtectedAccess.enter() {
        if (!invContains(inv, "obj.cata_totem")) { mes("You need a dark totem to enter Skotizo's lair."); return }
        if (!choice2("Offer a dark totem and face Skotizo.", true, "Cancel.", false)) return
        val result = rooms.create(player)
        if (result is InstanceManager.Result.Failed) { mes(result.reason); return }
        if (result !is InstanceManager.Result.Created) return
        // Allocation and arrival must succeed before the only entrance item is consumed.
        telejump(result.enter, TeleportType.Exempt)
        if (player.coords != result.enter) { rooms.manager.cancelPendingEntry(player, clock.cycle); return }
        if (!invDel(inv, "obj.cata_totem", 1).success) {
            telejump(SkotizoRules.Exit, TeleportType.Exempt)
            rooms.manager.cancelPendingEntry(player, clock.cycle)
            return
        }
        rooms.manager.finalizeEntry(player, result.session, clock.cycle)
        rooms.forPlayer(player)?.let(rooms::start)
        mes("You offer the dark totem. Skotizo awakens!")
    }

    private fun ProtectedAccess.assemble() {
        if (Parts.any { !invContains(inv, it) }) { mes("You need the base, middle and top of a dark totem to assemble it."); return }
        for (part in Parts) check(invDel(inv, part, 1).success)
        invAdd(inv, "obj.cata_totem", 1)
        mes("You combine the three pieces into a dark totem.")
    }

    private companion object { val Parts = listOf("obj.cata_totem1", "obj.cata_totem2", "obj.cata_totem3") }
}
