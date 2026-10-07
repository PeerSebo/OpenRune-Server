package org.rsmod.content.quest.area.crandor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

internal class DragonSlayerFight(
    private val quest: Quest,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
) {

    suspend fun claimHead(access: ProtectedAccess, corpseTile: CoordGrid) {
        val corpse =
            locRepo.add(
                coords = corpseTile,
                internal = DragonSlayerRefs.ElvargCorpseFullBody,
                duration = CorpseLifetimeTicks,
                angle = LocAngle.North,
                shape = LocShape.CentrepieceStraight,
            )

        // The stage changes one tick after the corpse appears; logout in that gap requires another fight.
        access.delay(DespawnToStageTicks)
        if (quest.getQuestStage(access.player) < DragonSlayerStage.ElvargSlain) {
            quest.setQuestStage(access, DragonSlayerStage.ElvargSlain)
        }

        access.delay(DeathToFirstCutTicks)

        access.anim(DragonSlayerRefs.SeqRemoveHead1)
        access.soundSynth(DragonSlayerRefs.SynthCutOffHead1, delay = FirstCutSynthDelay)
        access.delay(FirstCutToSecondTicks)

        locRepo.add(
            coords = corpse.coords,
            internal = DragonSlayerRefs.ElvargCorpseNoHead,
            duration = CorpseLifetimeTicks,
            angle = LocAngle.North,
            shape = LocShape.CentrepieceStraight,
        )

        access.anim(DragonSlayerRefs.SeqRemoveHead2)
        access.mes("You cut off Elvarg's head as proof of your victory!")
        access.soundSynth(DragonSlayerRefs.SynthCutOffHead2)
        access.midiSong("midi.stop_music")
        access.midiJingle(DragonSlayerRefs.ElvargSlainJingle)

        access.delay(SecondCutToHeadTicks)

        if (access.invAdd(access.inv, DragonSlayerRefs.ElvargHead, 1).failure) {
            access.invAddOrDrop(objRepo, DragonSlayerRefs.ElvargHead, 1)
        }
    }

    private companion object {
        const val DespawnToStageTicks = 1

        const val DeathToFirstCutTicks = 4

        const val FirstCutToSecondTicks = 6

        const val SecondCutToHeadTicks = 10

        // Sound delay is in client cycles, not game ticks.
        const val FirstCutSynthDelay = 35

        const val CorpseLifetimeTicks = 500
    }
}

internal class DragonSlayerElvargKillHook
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
) : NpcDeathKillHook {
    private val elvargId by lazy { DragonSlayerRefs.Elvarg.asRSCM(RSCMType.NPC) }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id != elvargId) {
            return
        }

        val player = context.hero
        val quest = Quest.get(DragonSlayerRefs.QuestKey) ?: return
        if (quest.getQuestStage(player) != DragonSlayerStage.Shipwrecked) {
            return
        }

        val corpseTile = context.npc.coords
        protectedAccess.launch(player) {
            DragonSlayerFight(quest, objRepo, locRepo).claimHead(this, corpseTile)
        }
    }
}
