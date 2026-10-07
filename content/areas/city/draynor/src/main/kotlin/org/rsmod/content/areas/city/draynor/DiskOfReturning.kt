package org.rsmod.content.areas.city.draynor

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DiskOfReturning @Inject constructor(
    private val regions: RegionRepository,
    private val npcs: NpcRepository,
    private val objects: ObjRepository,
    private val areas: AreaChecker,
    private val teleports: PlayerTeleportValidator,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(Disk) { activate(blackout = false) }
        onOpHeld3(Disk) { activate(blackout = true) }
        onOpHeld5(Disk) { dropDisk(it.inventory, it.slot) }
        onOpLoc1("loc.blackhole_exit") { leave() }
        onOpLoc2("loc.blackhole_exit") { enter(blackout = false) }
        onOpNpc1("npc.thordur") { talk(it.npc) }
        onPlayerLogout { release(player) }
        onPlayerCoordsChanged {
            val room = player.attr[RoomKey] ?: return@onPlayerCoordsChanged
            if (!room.contains(player.coords)) {
                release(player)
                player.loginExitCoord = null
            }
        }
    }

    private suspend fun ProtectedAccess.activate(blackout: Boolean) {
        if (Disk !in inv || actionDelay > mapClock) return
        val room = player.attr[RoomKey]
        if (room != null) {
            if (!blackout && player.coords.level == 2) leave()
            else enter(blackout = blackout)
            return
        }
        if (!areas.inArea("area.diango_disk_mines", coords)) {
            mes("You can only enter Thordur's Blackholes from the Dwarven Mines, beneath Falador.")
            return
        }
        val denial = teleports.validate(player, TeleportType.Standard, areas)
        if (denial != null) {
            mes(denial)
            return
        }
        enter(blackout)
    }

    private suspend fun ProtectedAccess.enter(blackout: Boolean) {
        if (player.attr[RoomKey] != null) {
            val option = if (blackout) "Go to Thordur's blacker Blackhole." else "Go to Thordur's other Blackhole."
            val accepted = choice2(option, true, "Cancel", false,
                title = "Anything left on the ground will be lost.")
            ifClose()
            if (!accepted) return
        }
        val region = regions.add(if (blackout) Blackout else Blackhole)
        if (region == null) {
            mes("The Blackhole is unavailable. Please try again shortly.")
            return
        }
        regions.protect(region)
        var entered = false
        try {
            cast()
            release(player)
            val destination = if (blackout) region.normal[CoordGrid(1625, 4697)]
                else CoordGrid(region.southWest.x + 48, region.southWest.z + 48, 2)
            val thordur = if (blackout) null else Npc("npc.thordur", destination.translate(-2, 0))
            thordur?.let {
                npcs.add(it, Int.MAX_VALUE)
                it.noneMode()
                it.faceSquare(destination)
            }
            player.attr[RoomKey] = Room(region, thordur)
            player.loginExitCoord = Exit
            telejump(destination, TeleportType.Exempt)
            resetAnim()
            entered = true
        } finally {
            if (!entered) regions.unprotect(region)
        }
    }

    private suspend fun ProtectedAccess.leave() {
        if (player.attr[RoomKey] == null) return
        val accepted = choice2("Leave", true, "Cancel", false,
            title = "Anything left on the ground will be lost.")
        ifClose()
        if (!accepted) return
        cast()
        telejump(Exit, TeleportType.Exempt)
        resetAnim()
        release(player)
        player.loginExitCoord = null
    }

    private suspend fun ProtectedAccess.cast() {
        player.abortRoute()
        anim("seq.human_castteleport")
        spotanim("spotanim.teleport_casting", height = 92)
        soundSynth("synth.TELEPORT_ALL")
        delay(3)
    }

    private suspend fun ProtectedAccess.dropDisk(inventory: Inventory, slot: Int) {
        val disk = inventory[slot] ?: return
        if (player.attr[RoomKey] == null) {
            invDrop(slot, inventory)
            return
        }
        val accepted = choice2("Yes, something cool might happen.", true,
            "No, keep hold of it.", false, title = "Really drop your Disk of Returning?")
        ifClose()
        if (!accepted) {
            mesbox("Smart choice.")
            return
        }
        if (inventory[slot] !== disk) return
        invDrop(slot, inventory)
        if (inventory[slot] === disk) return
        if (random.randomBoolean()) {
            anim("seq.partyroom_dance")
            try {
                mesbox("Something cool happens.")
            } finally {
                resetAnim()
            }
        } else {
            mesbox("That was disappointing.")
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (player.attr[RoomKey] == null) return
        startDialogue(npc) {
            chatNpc(happy, "Welcome to my Blackhole, human. Can I help you with anything?")
            when (choice4("Who are you?", 0, "What is this place?", 1, "How do I leave?", 2, "Nothing, thanks.", 3)) {
                0 -> {
                    chatNpc(neutral, "Why, I'm Thordur. Thordur the dwarf. A long time ago, I used to run a business letting people visit my Blackhole for fun.")
                    chatNpc(neutral, "I've retired now, though. It's a nice peaceful place to live.")
                }
                1 -> chatNpc(neutral, "Blackhole? Well, it's sort of like a hole, and it's generally not very well lit. I'll let you work out the rest.")
                2 -> if (Disk in inv) {
                    chatNpc(neutral, "Just activate your Disk of Returning. It'll pop you back out to the mines.")
                } else {
                    chatNpc(quiz, "Why, surely you've got your Disk of Returning? I can't believe you could have lost it in here.")
                    chatNpc(neutral, "I really don't understand humans sometimes. Hold tight and I'll sort you out...")
                    access.ifClose()
                    access.cast()
                    access.telejump(Exit, TeleportType.Exempt)
                    access.resetAnim()
                    release(player)
                    player.loginExitCoord = null
                }
            }
        }
    }

    private fun release(player: Player) {
        val room = player.attr[RoomKey] ?: return
        player.attr.remove(RoomKey)
        room.npc?.let { npcs.del(it, Int.MAX_VALUE) }
        for (zone in room.region.toZoneList()) {
            objects.findAll(zone).toList().forEach { objects.del(it, Int.MAX_VALUE) }
        }
        regions.unprotect(room.region)
    }

    internal class Room(val region: Region, val npc: Npc?) {
        fun contains(coords: CoordGrid): Boolean =
            coords.x in region.southWest.x until region.northEast.x &&
                coords.z in region.southWest.z until region.northEast.z
    }

    internal companion object {
        const val Disk = "obj.discofreturning"
        val Exit = CoordGrid(2984, 9811)
        val RoomKey = AttributeKey<Room>()
        val Blackhole = RegionTemplate.create {
            for (level in 0..2) for (x in 2..9) for (z in 2..9) {
                this[x, z, level] = ZoneKey(202, 591, level)
            }
        }
        val Blackout = RegionTemplate.create {
            copy(203, 587, 0) {
                regionZoneX = 6
                regionZoneZ = 6
                zoneWidth = 4
                zoneLength = 5
            }
        }
    }
}

class BlackholeTeleportHook : PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (player.attr[DiskOfReturning.RoomKey] != null)
            "You can't teleport out of the Blackhole. Use your Disk of Returning instead."
        else null
}

class DiangoItemsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerTeleportValidateHook>(BlackholeTeleportHook::class.java)
    }
}
