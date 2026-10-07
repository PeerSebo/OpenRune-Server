package org.rsmod.content.other.consumables.potion

import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.table.PotionRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Exact same-family pairs must preempt Herblore single-item wildcard handlers.

class PotionDecantScript : PluginScript() {
    override fun ScriptContext.startup() {
        PotionRow.all().forEach { potion ->
            val items = potion.items

            for (i in items.indices) {
                for (j in i..items.lastIndex) {
                    onOpHeldU(items[i], items[j]) {
                        combine(
                            potion = potion,
                            ev = it,
                        )
                    }
                }
            }
        }
    }

    private fun ProtectedAccess.combine(
        potion: PotionRow,
        ev: HeldUEvents.Type,
    ) {
        val cap = potion.items.size

        val hiDoses =
            cap - potion.items.indexOfFirst { it.id == ev.first.id }

        val loDoses =
            cap - potion.items.indexOfFirst { it.id == ev.second.id }

        val combined = minOf(cap, hiDoses + loDoses)
        val remainder = hiDoses + loDoses - combined

        if (combined == hiDoses) {
            return
        }

        val equal = hiDoses == loDoses

        val fullSlot =
            if (equal) {
                ev.secondSlot
            } else {
                ev.firstSlot
            }

        val restSlot =
            if (equal) {
                ev.firstSlot
            } else {
                ev.secondSlot
            }

        val fullItem = potion.items[cap - combined]

        val restItem =
            if (remainder == 0) {
                potion.empty
            } else {
                potion.items[cap - remainder]
            }

        val fullReplace =
            invReplaceSlot(
                inv = inv,
                slot = fullSlot,
                count = 1,
                replacement = fullItem,
            )

        if (fullReplace.failure) {
            return
        }

        val restReplace =
            invReplaceSlot(
                inv = inv,
                slot = restSlot,
                count = 1,
                replacement = restItem,
            )

        if (restReplace.failure) {
            // Revert the first replacement so the inventory stays consistent.
            val original =
                if (equal) {
                    ev.second
                } else {
                    ev.first
                }

            invReplaceSlot(
                inv = inv,
                slot = fullSlot,
                count = 1,
                replacement = original,
            )
            return
        }

        soundSynth("synth.LIQUID")
    }
}
