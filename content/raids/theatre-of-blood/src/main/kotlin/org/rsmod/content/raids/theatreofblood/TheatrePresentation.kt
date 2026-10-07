package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.MusicRow
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.utils.bits.bitMask

@Singleton
internal class TheatrePresentation @Inject constructor(private val music: MusicPlayer) {
    private val progressCapacity by lazy {
        listOf(PROGRESS_VALUE, PROGRESS_MAX).minOf { symbol ->
            val type = checkNotNull(ServerCacheManager.getVarbit(symbol.asRSCM(RSCMType.VARBIT)))
            type.bits.bitMask.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
    private val drawComponents by lazy {
        DRAW_COMPONENT_NAMES.map { "component.tob_hud:$it".asRSCM(RSCMType.COMPONENT) }
    }

    fun open(player: Player, run: TheatreRun, eventBus: EventBus) {
        if (!player.ui.containsOverlay("interface.tob_hud")) {
            player.ifOpenFullOverlay("interface.tob_hud", eventBus)
        }
        updatePlayer(player, run)
    }

    suspend fun beginEntrance(access: ProtectedAccess, eventBus: EventBus) = with(access) {
        ifClose()
        player.clearInteractionRoute()
        player.ifOpenFullOverlay("interface.tob_hud", eventBus)
        player.runClientScript(FADE_SCRIPT, 0, 0, "The Theatre awaits...")
        player.soundSynth("synth.TOB_TRANSITION_CARD")
        for ((index, sound) in TheatreData.rules.entranceFootsteps.withIndex()) {
            player.soundSynth(sound, delay = index * TheatreData.rules.entranceFootstepDelayCycles)
        }
        delay(ENTRANCE_HOLD_TICKS)
    }

    suspend fun revealEntrance(access: ProtectedAccess, title: String) = with(access) {
        player.runClientScript(FADE_SCRIPT, 255, 0, title)
        delay(FADE_TICKS)
    }

    fun cancelEntrance(player: Player) {
        if (!player.ui.containsOverlay("interface.tob_hud")) return
        player.runClientScript(FADE_SCRIPT, 255, 1, "")
        player.ifSetHide("component.tob_hud:atmospheric", true)
    }

    fun update(run: TheatreRun, players: List<Player>) {
        for (player in players) updatePlayer(player, run)
    }

    fun close(player: Player, eventBus: EventBus) {
        set(player, PARTY_STATUS, 0)
        set(player, PARTY_SLOT, 0)
        set(player, PROGRESS_TYPE, 0)
        repeat(5) { set(player, "varbit.tob_client_p$it", 0) }
        player.ifCloseOverlay("interface.tob_hud", eventBus)
        for (room in TheatreData.roomsList) {
            music.finishEncounter(player, MusicRow.getRow(room.ambienceMusic))
            music.finishEncounter(player, MusicRow.getRow(room.combatMusic))
        }
        music.finishEncounter(player, MusicRow.getRow(DEFEATED_MUSIC))
    }

    private fun updatePlayer(player: Player, run: TheatreRun) {
        setParty(player, run)
        val wave = run.room == 2 && run.boss == null
        val type = if (run.stage != TheatreManager.ACTIVE) 0 else if (wave) 2 else 1
        val maximum = if (wave) TheatreData.rules.nylocasPillars.size * TheatreData.rules.nylocasPillarHealth
            else run.boss?.baseHitpointsLvl ?: run.roomrow.health
        val current = if (wave) run.adds.filter { it.vars["varn.theatre_nylo_pillar_index"] != 0 }
            .sumOf { it.hitpoints.coerceAtLeast(0) }
            else run.boss?.hitpoints ?: 0
        val progress = TheatreHudState.progress(current, maximum, progressCapacity)
        set(player, PROGRESS_TYPE, type)
        set(player, PROGRESS_VALUE, progress.first)
        set(player, PROGRESS_MAX, progress.second)
        player.runClientScript(DRAW_SCRIPT, listOf(1) + drawComponents)
        if (type != 0) {
            player.runClientScript(
                PROGRESS_SCRIPT,
                TheatreHudState.progressWidth(progress.first, progress.second),
                "component.tob_hud:progress_bar".asRSCM(RSCMType.COMPONENT),
                1,
            )
        }
        updateMusic(player, run)
    }

    private fun setParty(player: Player, run: TheatreRun) {
        val names = (0..4).map { run.members.getOrNull(it)?.displayName ?: "" }
        player.runClientScript(NAMES_SCRIPT, names)
        player.ifSetText(
            "component.tob_hud:names",
            names.filter(String::isNotEmpty).joinToString("<br>"),
        )
        val slot = if (player.vars[TheatreManager.DEAD] != 0) 0
            else (run.members.indexOf(player) + 1).coerceIn(0, 5)
        set(player, PARTY_SLOT, slot)
        set(player, PARTY_STATUS, if (run.stage == TheatreManager.LOBBY) 1 else 2)
        for (index in 0..4) {
            val member = run.members.getOrNull(index)
            val status = if (member == null) 0 else TheatreHudState.memberStatus(
                member.hitpoints,
                member.baseHitpointsLvl,
                member.vars[TheatreManager.DEAD] != 0,
                member.isSlotAssigned && !member.loggingOut && member.uuid in run.session.occupants,
            )
            set(player, "varbit.tob_client_p$index", status)
        }
    }

    private fun updateMusic(player: Player, run: TheatreRun) {
        val ambience = MusicRow.getRow(run.roomrow.ambienceMusic)
        music.unlock(player, ambience)
        val row = when (run.stage) {
            TheatreManager.ACTIVE, TheatreManager.CLEARED -> run.roomrow.combatMusic
            TheatreManager.COMPLETED -> DEFEATED_MUSIC
            else -> run.roomrow.ambienceMusic
        }
        music.startEncounter(player, MusicRow.getRow(row))
    }

    private fun set(player: Player, symbol: String, value: Int) {
        if (player.vars[symbol] != value) VarPlayerIntMapSetter.set(player, symbol, value)
    }

    private companion object {
        const val PARTY_STATUS = "varbit.tob_client_partystatus"
        const val PARTY_SLOT = "varbit.tob_client_partyslot"
        const val PROGRESS_TYPE = "varbit.tob_client_waveprogress_type"
        const val PROGRESS_VALUE = "varbit.tob_client_waveprogress_val"
        const val PROGRESS_MAX = "varbit.tob_client_waveprogress_max"
        const val NAMES_SCRIPT = "clientscript.[clientscript,tob_hud_statusnames]"
        const val DRAW_SCRIPT = "clientscript.[clientscript,tob_hud_draw]"
        const val PROGRESS_SCRIPT = "clientscript.[clientscript,tob_hud_updateprogress]"
        const val FADE_SCRIPT = "clientscript.[clientscript,tob_hud_fade]"
        const val ENTRANCE_HOLD_TICKS = 2
        const val FADE_TICKS = 1
        const val DEFEATED_MUSIC = "dbrow.music_verzik_defeated"
        val DRAW_COMPONENT_NAMES = listOf(
            "universe", "hud", "names_container", "names_header", "progress_container",
            "progress_bar", "progress_text", "status_container", "0_container", "0_filler",
            "1_container", "1_filler", "2_container", "2_filler", "3_container", "3_filler",
            "4_container", "4_filler", "spectator_container", "tooltip",
        )
    }
}

internal object TheatreHudState {
    fun memberStatus(hitpoints: Int, maximum: Int, dead: Boolean, present: Boolean): Int {
        require(maximum > 0)
        if (!present) return 31
        if (dead || hitpoints <= 0) return 30
        return 1 + (hitpoints.toLong().coerceAtMost(maximum.toLong()) * 26 / maximum).toInt()
    }

    fun progress(current: Int, maximum: Int, capacity: Int): Pair<Int, Int> {
        require(maximum > 0 && capacity > 0)
        val bounded = current.coerceIn(0, maximum)
        if (maximum <= capacity) return bounded to maximum
        return (bounded.toLong() * capacity / maximum).toInt() to capacity
    }

    fun progressWidth(current: Int, maximum: Int): Int {
        require(maximum > 0)
        return (current.toLong().coerceIn(0, maximum.toLong()) * 16384 / maximum).toInt()
    }
}
