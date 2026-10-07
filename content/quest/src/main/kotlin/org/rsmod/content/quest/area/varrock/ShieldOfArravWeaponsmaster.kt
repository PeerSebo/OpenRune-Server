package org.rsmod.content.quest.area.varrock

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.PlayerObjTakeValidator
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpObj3
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.objtx.TransactionResultList
import org.rsmod.plugin.scripts.ScriptContext

internal class ShieldOfArravWeaponsmaster(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
    private val takeValidator: PlayerObjTakeValidator,
) {

    fun ScriptContext.register() {
        onOpNpc1(SoaRefs.Weaponsmaster) { startWeaponsmasterTalk(it.npc) }

        val crossbowType =
            checkNotNull(ServerCacheManager.getItem(SoaRefs.Crossbow.asRSCM(RSCMType.OBJ))) {
                "Shield of Arrav crossbow does not resolve: ${SoaRefs.Crossbow}"
            }
        onOpObj3(crossbowType) { takeCrossbow(it.obj) }
    }

    private suspend fun ProtectedAccess.startWeaponsmasterTalk(npc: Npc) {
        if (phoenixGang >= PhoenixState.Member) {
            startDialogue(npc) { weaponsmasterFellowPhoenix() }
            return
        }
        startDialogue(npc) {
            chatPlayer(neutral, "Hello.")
            chatNpc(
                angry,
                "Hey! Who are you? I'm gonna teach you not to stick your nose where it don't " +
                    "belong!",
            )
        }
        npc.opPlayer2(player, aiPlayerInteractions)
    }

    private suspend fun Dialogue.weaponsmasterFellowPhoenix() {
        chatPlayer(neutral, "Hello.")
        chatNpc(happy, "Hello fellow Phoenix! What are you after?")
        val pick = choice2("I'm after a weapon or two.", true, "I'm looking for treasure.", false)
        if (pick) {
            chatPlayer(neutral, "I'm after a weapon or two.")
            chatNpc(neutral, "No problem. Feel free to look around.")
        } else {
            chatPlayer(neutral, "I'm looking for treasure.")
            chatNpc(
                neutral,
                "Aren't we all? We've not got any up here. Go mug someone somewhere if you want " +
                    "some treasure.",
            )
        }
    }

    private suspend fun ProtectedAccess.takeCrossbow(obj: Obj) {
        val type = ServerCacheManager.getItem(obj.type) ?: return
        val denial = takeValidator.validate(player, obj, type)
        if (denial != null) {
            mes(denial)
            return
        }
        if (!player.hasInvSpace(obj)) {
            player.mes(Constants.dm_take_invspace)
            return
        }
        player.resetAnim()
        if (player.coords != obj.coords) {
            delay(1)
            anim("seq.human_pickuptable")
        }
        soundSynth(SoaRefs.PickupSound)
        val removed = objRepo.del(obj)
        if (!removed) {
            mes(Constants.dm_take_taken)
            return
        }
        val take = player.transaction(obj)
        if (take.failure) {
            player.mes(Constants.dm_take_invspace)
            return
        }
        take.commitAll()

        weaponsmasterReaction(obj.coords)
    }

    private fun ProtectedAccess.weaponsmasterReaction(takeCoords: CoordGrid) {
        if (!inStashRoom(takeCoords)) {
            return
        }
        if (weaponsmasterDead != 0) {
            return
        }
        val guard = findWeaponsmaster() ?: return

        if (phoenixGang >= PhoenixState.Member) {
            guard.say("Hey! That's Straven's! He won't like you messing with that!")
            return
        }
        guard.say("Stop! Thief!")
        guard.opPlayer2(player, aiPlayerInteractions)
    }

    private fun inStashRoom(coords: CoordGrid): Boolean =
        coords.level == 1 && coords.x in 3241..3247 && coords.z in 3381..3387

    private fun findWeaponsmaster(): Npc? {
        val id = SoaRefs.Weaponsmaster.asRSCM(RSCMType.NPC)
        return npcRepo.findAll(ZoneKey.from(WeaponsmasterSpawn), zoneRadius = 1).firstOrNull {
            it.id == id
        }
    }

    private fun Player.hasInvSpace(obj: Obj): Boolean = transaction(obj).success

    private fun Player.transaction(obj: Obj): TransactionResultList<InvObj> =
        invAdd(inv, obj.type, obj.count, autoCommit = false)

    private companion object {
        private val WeaponsmasterSpawn = CoordGrid(3246, 3384, 1)
    }
}

internal class ShieldOfArravWeaponsmasterKillHook @Inject constructor() : NpcDeathKillHook {
    private val weaponsmasterId by lazy { SoaRefs.Weaponsmaster.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != weaponsmasterId) {
            return
        }
        context.hero.weaponsmasterDeadVar = 1
    }
}
