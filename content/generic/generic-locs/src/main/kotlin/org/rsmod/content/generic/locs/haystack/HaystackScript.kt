package org.rsmod.content.generic.locs.haystack

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HaystackScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((loc, noun) in Haystacks) {
            onOpLoc1(loc) { search(noun) }
        }
    }

    private suspend fun ProtectedAccess.search(noun: String) {
        arriveDelay()

        spam("You search the $noun...")
        anim(SearchAnim)

        delay(ResultDelay)

        val roll = random.of(1, 100)
        when {
            roll <= NeedleChance -> findNeedle()
            roll <= NeedleChance + PrickChance -> prickFinger()
            else -> spam("You find nothing of interest.")
        }
    }

    // Deliver the item before opening the dialogue so cancellation cannot lose the reward.
    private suspend fun ProtectedAccess.findNeedle() {
        invAddOrDrop(objRepo, Needle)
        startDialogue {
            chatPlayer(happy, "Wow! A needle! Now what are the chances of finding that?")
        }
    }

    // Apply prick damage immediately; a queued hit could outlive the search or player movement.
    private suspend fun ProtectedAccess.prickFinger() {
        takeInstantHit(type = HitType.Typeless, damage = PrickDamage)
        soundSynth(PrickSound, delay = PrickSoundDelay)
        startDialogue { chatPlayer(sad, "Ow! There's something sharp in there!") }
    }

    private companion object {
        private const val SearchAnim = "seq.human_pickupfloor"

        private const val ResultDelay = 3

        private const val Needle = "obj.needle"

        // Mod Ash: 10% needle and a separate 2% unsuccessful prick chance.
        private const val NeedleChance = 10

        private const val PrickChance = 2

        private const val PrickDamage = 1

        private const val PrickSound = "synth.HUMAN_HIT_02"

        /** Raw wire value from `SynthSound(id=519, loops=1, delay=20)`. Do not reinterpret. */
        private const val PrickSoundDelay = 20

        private val Haystacks =
            listOf(
                "loc.haystack" to "hay bales",
                "loc.haystack2" to "hay bales",
                "loc.haystack3" to "haystack",
                "loc.haybail" to "hay bale",
            )
    }
}
