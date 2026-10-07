package org.rsmod.content.minigames.giantsfoundry.quest

import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class SleepingGiantsCutscene @Inject constructor(private val workshop: SleepingGiantsWorkshop) {
    internal suspend fun play(access: ProtectedAccess, kovac: Npc) = with(access) {
        fun mapped(coords: CoordGrid) = workshop.map(player, coords)
        var restoreScenery: (() -> Unit)? = null
        tempDisableAcceptAid()
        try {
            ifClose()
            fadeOverlay(0, 255, 0, 0, 50)
            delay(2)
            restoreScenery = workshop.showCutsceneScenery(player)
            hideEntityOps()
            hideTopLevel()
            hideHealthHud()
            minimapHideMap()
            camModeClose()
            maxDrawDistance(true)
            syncDrawDistance()
            camShakeResetAll()
            teleport(mapped(ENTRY))
            faceSquare(mapped(WALK_END))
            kovac.faceSquare(mapped(WALK_END))
            for (shot in shots.take(5)) {
                camera(this, shot)
                when (shot.order) {
                    1 -> {
                        fadeOverlay(0, 0, 0, 255, 50)
                        walk(mapped(WALK_END))
                    }
                    2 -> closeFadeOverlayNow()
                    3, 4 -> {
                        faceSquare(mapped(shot.look))
                        anim("seq.emote_think")
                    }
                }
                delay(shot.ticks)
            }
            startDialogue { chatPlayer(shocked, "Woah... What is this place?") }
            ifClose()
            camera(this, shots.last())
            walk(mapped(TALK_TILE))
            delay(4)
            faceSquare(kovac.coords)
            kovac.faceSquare(coords)
            startDialogue(kovac) {
                chatPlayer(quiz, "Did you build this?")
                chatNpcNoTurn(neutral, "No... Kovac... accep... ancestor build.")
                chatPlayer(quiz, "Giants built this place?")
                chatNpcNoTurn(neutral, "Yes... long ago.")
                chatPlayer(quiz, "How? Giants don't build, do they? I thought they were brutes...")
                chatNpcNoTurn(neutral, "Before... giants differing.")
                chatNpcNoTurn(neutral, "Now... not.")
                chatPlayer(quiz, "Huh? I don't understand what you mean.")
                chatNpcNoTurn(neutral, "Look.")
                kovac.faceSquare(mapped(TOOLS))
                mesbox("Kovac looks out to the tools around you.")
                chatNpcNoTurn(neutral, "Help fix.")
                chatPlayer(quiz, "You want me to help you by fixing those tools?")
                chatNpcNoTurn(neutral, "Yes.")
                chatPlayer(quiz, "Okay, I'll help, but first you need to tell me what this place was used for?")
                chatNpcNoTurn(neutral, "Make... wheelpo... weapons.")
                chatPlayer(shocked, "Giants made weapons here?")
                chatPlayer(quiz, "That's incredible! But why do you want to make weapons? Most giants I've come across already have weapons?")
                chatNpcNoTurn(neutral, "Make... giants... remember.")
                chatPlayer(quiz, "Remember what?")
                chatNpcNoTurn(neutral, "Giants... not just... killers.")
                chatNpcNoTurn(neutral, "Kovac want... help giants remember.")
                chatPlayer(neutral, "I see. To be honest, if I hadn't seen this place myself, I wouldn't have believed you.")
                chatPlayer(happy, "Alright, Kovac, I'll help you out. What do we need to do first?")
                chatNpcNoTurn(neutral, "We fix tools...")
            }
        } finally {
            try {
                restoreScenery?.invoke()
            } finally {
                player.abortRoute()
                resetAnim()
                camUnlock(false)
                camReset()
                showEntityOps()
                showTopLevel()
                showHealthHud()
                minimapReset()
                camModeReset()
                restoreLastAcceptAid()
                maxDrawDistance(false)
                syncDrawDistance()
                closeFadeOverlayNow()
            }
        }
    }

    private fun camera(access: ProtectedAccess, shot: Shot) = with(access) {
        camMoveToV3(workshop.map(player, shot.eye), shot.eyeHeight, shot.moveRate, shot.moveRate)
        camLookAtV3(workshop.map(player, shot.look), shot.lookHeight, shot.lookRate, shot.lookRate)
    }

    private data class Shot(
        val order: Int,
        val eye: CoordGrid,
        val eyeHeight: Int,
        val look: CoordGrid,
        val lookHeight: Int,
        val moveRate: Int,
        val lookRate: Int,
        val ticks: Int,
    )

    companion object {
        internal val ENTRY = CoordGrid(3366, 11483)
        internal val WALK_END = CoordGrid(3367, 11489)
        internal val TALK_TILE = CoordGrid(3367, 11485)
        private val TOOLS = CoordGrid(3363, 11496)
        private val shots by lazy {
            DbHelper.table("dbtable.sleeping_giants_camera").map { row ->
                fun field(name: String) = row.column("dbcol.sleeping_giants_camera:$name", IntCodec)
                fun coord(name: String) = row.column("dbcol.sleeping_giants_camera:$name", CoordGridCodec)
                Shot(field("order"), coord("eye"), field("eye_height"),
                    coord("look"), field("look_height"), field("move_rate"),
                    field("look_rate"), field("ticks"))
            }.sortedBy { it.order }.also { shots ->
                require(shots.map { it.order } == (0..5).toList())
                require(shots.take(5).all { it.ticks > 0 })
                require(shots.all { it.moveRate in 0..255 && it.lookRate in 0..255 })
            }
        }
    }
}
