package org.rsmod.content.areas.city.lumbridge.locs

import jakarta.inject.Inject
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.table.MusicRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChurchOrgan
@Inject
constructor(private val musicPlayer: MusicPlayer, private val musicRepo: MusicRepository) :
    PluginScript() {

    // These music rows lack RSCM names; resolve display names after the table loads.
    private val organTracks: List<MusicRow> by lazy {
        listOfNotNull(musicRow(ORGAN_MUSIC_1), musicRow(ORGAN_MUSIC_2))
    }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.smallchurchorgan") { playOrgan() }
    }

    private fun ProtectedAccess.playOrgan() {
        val tracks = organTracks
        if (tracks.size != EXPECTED_TRACK_COUNT) {
            return
        }

        val playing = vars[MUSIC_CURR_ID_VARBIT]
        val next = tracks.firstOrNull { musicRepo.forRow(it)?.id != playing } ?: tracks.first()

        musicPlayer.unlockAndPlay(player, next)
    }

    private fun musicRow(displayName: String): MusicRow? =
        MusicRow.all().firstOrNull { it.displayname == displayName }

    private companion object {
        const val ORGAN_MUSIC_1 = "Organ Music 1"

        const val ORGAN_MUSIC_2 = "Organ Music 2"

        const val EXPECTED_TRACK_COUNT = 2

        const val MUSIC_CURR_ID_VARBIT = "varbit.music_curr_id"
    }
}
