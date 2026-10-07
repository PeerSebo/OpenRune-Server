package org.rsmod.content.skills.prayer

import org.rsmod.api.player.events.prayer.PrayerSkillAction
import org.rsmod.api.player.events.skilling.SkillingActionCompleteEvent
import org.rsmod.api.player.events.skilling.SkillingActionContext
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PrayerBuryEvents : PluginScript() {

    private val bones = Companion.bones

    override fun ScriptContext.startup() {
        bones.forEach { bone ->
            onOpHeld1(bone.item) {
                bury(bone, it.slot)
            }
        }
        onPlayerQueueWithArgs("queue.prayer_bury") { processBuryTick(it.args) }
    }

    // Keep re-entry state on the player; new clicks cancel protected coroutines.
    private suspend fun ProtectedAccess.bury(row: SkillPrayerRow, slot: Int) {
        if (actionDelay >= mapClock && skillAnimDelay >= mapClock) {
            return
        }

        if (!row.ashes && inArea("area.chaos_temple", player.coords)) {
            val confirmed = choice2(
                "Bury the Bone",
                true, "Cancel", false,
                title = "Are you sure you want to do that?",
            )

            if (!confirmed) {
                return
            }
        }

        stopAction()
        // The >= gate blocks one following tick, producing a two-tick cadence. Never shorten a foreign delay.
        actionDelay = maxOf(actionDelay, mapClock + BURY_BLOCK_TICKS)
        skillAnimDelay = mapClock + BURY_BLOCK_TICKS

        val animation = if (row.ashes) "seq.human_pour_pot" else "seq.human_pickupfloor"
        anim(animation)

        // Consume on the click tick; delaying consumption allows repeated clicks to reuse the same bone.
        if (!row.ashes) {
            mes("You dig a hole in the ground...")
        }

        if (inv[slot]?.isType(row.item.internalName) != true) {
            return
        }
        val applyZealotSave = !isDemonicAsh(row.item.internalName)
        val consumeBone = if (applyZealotSave) shouldConsume() else true
        if (consumeBone && invDel(inv = inv, type = row.item.internalName, count = 1, slot = slot).failure) {
            return
        }

        if (!row.ashes) {
            soundSynth(BURY_SYNTH)
        }

        // Prayer table XP is in tenths, including fractional bone awards.
        val buryXp = row.xpTenths / 10.0
        statAdvance("stat.prayer", buryXp)
        publish(SkillingActionCompleteEvent(player = player, context =
            SkillingActionContext.Prayer(PrayerSkillAction.BuryComplete(
                itemInternal = row.item.internalName,
                ashes = row.ashes,
                experienceGranted = buryXp,
                catacombsBonePrayerRestore = row.prayerRestore
            ))),
        )

        queue("queue.prayer_bury", 1, BuryTask(row, slot))
    }

    private fun ProtectedAccess.processBuryTick(task: BuryTask) {
        val message = if (task.row.ashes) "You scatter the ashes." else "You bury the bones."
        mes(message)
    }

    private data class BuryTask(
        val row: SkillPrayerRow,
        val slot: Int,
    )

    companion object {
        val bones = SkillPrayerRow.all()

        // The >= comparison makes a one-tick lock produce two-tick spacing.
        private const val BURY_BLOCK_TICKS = 1

        private const val BURY_SYNTH = "synth.BONES_DOWN"

        private val demonicAshes = setOf(
            "obj.infernal_ashes",
            "obj.abyssal_ashes",
            "obj.malicious_ashes",
            "obj.vile_ashes",
            "obj.fiendish_ashes",
        )

        fun isDemonicAsh(itemInternal: String): Boolean = itemInternal in demonicAshes
    }
}
