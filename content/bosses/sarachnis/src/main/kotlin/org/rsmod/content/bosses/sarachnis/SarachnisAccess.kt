package org.rsmod.content.bosses.sarachnis

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.PlayerDeathRedirectHook
import org.rsmod.api.instances.*
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.*
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SarachnisAccess @Inject constructor(
    private val manager: InstanceManager,
    private val clock: MapClock,
    private val locRepo: LocRepository,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (symbol in listOf("loc.hosdun_sarachnis_entrance", "loc.hosdun_sarachnis_exit", "loc.hosdun_sarachnis_entrance_instance")) {
            onOpLoc1(symbol) { cross(it.loc) }
        }
        onOpLoc2("loc.hosdun_sarachnis_entrance") { cross(it.loc) }
        onOpLoc3("loc.hosdun_sarachnis_entrance") { privateFight() }
        for (web in listOf("loc.sarachnis_web", "loc.sarachnis_web_peek")) {
            onOpLoc1(web) { slash(it.loc) }
        }
        for (web in listOf("loc.sarachnis_web_peek", "loc.sarachnis_web_peek_slashed")) {
            onOpLoc2(web) {
                val count = players.count { !it.staffInvisible && Sarachnis.inRoom(Sarachnis.Spawn, it.coords) }
                mes("There ${if (count == 1) "is 1 adventurer" else "are $count adventurers"} in Sarachnis's crypt.")
            }
        }
        onOpLoc1("loc.hosdun_entrance_ladder_hole") { enterDungeon(LadderInside) }
        onOpLoc1("loc.hosdun_entrance_ladder") { enterDungeon(LadderOutside) }
        onOpLoc1("loc.hosdun_entrance_spiral_steps_hole") {
            enterDungeon(SpiralInside)
        }
        onOpLoc1("loc.hosdun_entrance_spiral_stairs") {
            enterDungeon(SpiralOutside)
        }
    }

    private suspend fun ProtectedAccess.enterDungeon(destination: CoordGrid) {
        arriveDelay()
        anim("seq.human_reachforladder")
        delay(1)
        telejump(destination, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        arriveDelay()
        val session = manager.sessionForPlayer(player)
        if (session?.key == Key) {
            telejump(Outside, TeleportType.Exempt)
            if (coords == Outside) manager.leave(player, session, clock.cycle)
        } else {
            val destination = if (coords.z > loc.coords.z) Inside else Outside
            telejump(destination, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.privateFight() {
        if (!choice2("Pay 25,000 coins for a private fight.", true, "Cancel.", false)) return
        val area = InstanceArea.copyRegions(
            regionIds = listOf((28 shl 8) or 154),
            enterCoord = RegionLocal(0, Inside.mx, Inside.mz, Inside.lx, Inside.lz),
            exitCoord = Outside,
            npcSpawns = listOf(InstanceNpc("npc.sarachnis", Sarachnis.Spawn)),
        )
        val spec = InstanceSpec(fee = 25_000, maxPlayers = 1, reclaimTicks = 0, graceTicks = 0,
            destroyWhenEmpty = true, area = area, settingsRowId = -1, bossName = "Sarachnis")
        when (val result = manager.create(player, Key, spec, InstanceAccess.Private, clock.cycle)) {
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Created -> {
                telejump(result.enter, TeleportType.Exempt)
                if (coords == result.enter) manager.finalizeEntry(player, result.session, clock.cycle)
                else manager.cancelPendingEntry(player, clock.cycle)
            }
            is InstanceManager.Result.Joined -> Unit
        }
    }

    private suspend fun ProtectedAccess.slash(loc: BoundLocInfo) {
        val weapon = player.righthand?.id?.let(ServerCacheManager::getItem)
        val equippedBlade = weapon != null && !weapon.isCert && (weapon.paramOrNull(BaseParams.attack_slash) ?: 0) > 0
        val knife = inv.count("obj.knife") > 0
        if (!equippedBlade && !knife) {
            mes("You need an equipped slash weapon or a knife in your inventory to cut the web.")
            return
        }
        arriveDelay()
        anim(if (equippedBlade) "seq.human_sword_slash" else "seq.human_knife_slash")
        delay(1)
        locRepo.del(loc, 100)
        mes("You slash through the web.")
    }

    internal companion object {
        const val Key = "sarachnis"
        val Inside = CoordGrid(1841, 9911)
        val Outside = CoordGrid(1841, 9913)
        val LadderInside = CoordGrid(1830, 9973)
        val LadderOutside = CoordGrid(1702, 3575)
        val SpiralInside = CoordGrid(1797, 9967)
        val SpiralOutside = CoordGrid(1668, 3566)
    }
}

class SarachnisModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathRedirectHook>(SarachnisDeathExit::class.java)
        addSetBinding<NpcDeathDropHook>(SarachnisYoungRemains::class.java)
    }
}

class SarachnisYoungRemains @Inject constructor() : NpcDeathDropHook {
    override fun tryConsume(context: NpcDeathDropContext): Boolean =
        context.npc.type.isType("npc.sarachnis_melee_spawn") || context.npc.type.isType("npc.sarachnis_mage_spawn")
}

class SarachnisDeathExit @Inject constructor(private val manager: InstanceManager) : PlayerDeathRedirectHook {
    override fun destination(player: Player): CoordGrid? =
        manager.sessionForPlayer(player)?.takeIf { it.key == SarachnisAccess.Key }?.spec?.area?.let {
            SarachnisAccess.Outside
        }
}
