package org.rsmod.content.other.levelup

import dev.openrune.ServerCacheManager
import dev.openrune.types.StatType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.slotsOptional
import dev.openrune.types.dbcol.stringOptional
import dev.openrune.types.enums.enum
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.advanced.onAdvanceStat
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.StatAdvance
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

public class LevelUpScript @Inject constructor(
    private val worldRepo: WorldRepository,
    private val protectedAccess: ProtectedAccessLauncher,
) :
    PluginScript() {
    private var Player.maxTotalCelebrated by boolVarp("varp.levelup_max_total_celebrated")
    private var Player.lastCombatLevel by nullableIntVarp("varp.levelup_last_combat_level")
    private var Player.lastJingleId by nullableIntVarp("varp.levelup_last_jingle_id")

    override fun ScriptContext.startup() {
        // Seed previous levels at login so rewards that skip levels still detect every guide unlock.
        onPlayerLogin {
            player.seedBaseLevelSnapshot()
            player.lastCombatLevel = player.combatLevel
        }
        onAdvanceStat { celebrate(it.args) }
        onPlayerSoftTimer(SkillGuideHint.Timer) { player.flushSkillGuideHints() }
        onCommand("testlevelup") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Preview a skill level-up celebration"
            cheat {
                val name = args.firstOrNull()
                val stat = ServerCacheManager.getStats().values.firstOrNull {
                    !it.unreleased && (it.internalName.removePrefix("stat.").equals(name, true) ||
                        it.displayName.equals(name, true))
                }
                if (stat == null) {
                    player.mes("Usage: ::testlevelup <skill> [level]")
                    return@cheat
                }
                val level = args.getOrNull(1)?.toIntOrNull() ?: player.statBase(stat.internalName)
                protectedAccess.launch(player) { celebrate(StatAdvance(stat, level.coerceIn(1, stat.maxLevel))) }
            }
        }
        onCommand("jingle") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Preview a jingle archive group"
            cheat {
                val id = args.firstOrNull()?.toIntOrNull() ?: ((player.lastJingleId ?: -1) + 1)
                if (id !in 0..65534) {
                    player.mes("Use an archive group between 0 and 65534.")
                    return@cheat
                }
                player.lastJingleId = id
                player.midiJingle(id)
                player.mes("Playing jingle $id.")
            }
        }
    }

    private suspend fun ProtectedAccess.celebrate(advance: StatAdvance) {
        val stat = advance.stat
        if (stat.unreleased) {
            return
        }
        val statKey = stat.internalName
        val shortName = statKey.removePrefix("stat.")
        // Snapshot the awarded level; protected access may defer delivery until later level-ups.
        val level = advance.level
        val displayName = stat.displayName.replaceFirstChar { it.uppercaseChar() }

        val article = if (shortName == "attack" || shortName == "agility") "an" else "a"

        val line1 = "Congratulations, you just advanced $article $displayName level."
        val line2 = "Your $displayName level is now $level."

        val chatLine =
            "Congratulations, you've just advanced your $displayName level. " +
                "You are now level $level."

        mes(chatLine)

        // Height 124 puts the level-up effect around the head rather than the feet.
        spotanim(levelUpSpotanim(level), delay = 0, height = LevelUpSpotanimHeight, slot = 0)

        // Level-up sounds are area sounds, audible to nearby players.
        soundArea(worldRepo, player.coords, "synth.FIREWORK", delay = 0, loops = 1, radius = 1)
        soundArea(worldRepo, player.coords, "synth.BURST", delay = 25, loops = 1, radius = 1)

        val unlock = statToGuideId[stat.id]?.let { hasUnlockInRange(it, level - 1, level) } ?: false
        LevelUpJingles[statKey]?.let { Jingles.play(player, it.pick(LevelUp(level, stat.maxLevel, unlock))) }
        val previousCombat = player.lastCombatLevel
        player.lastCombatLevel = player.combatLevel
        if (previousCombat != null && player.combatLevel > previousCombat) {
            mes("Congratulations, you just advanced a Combat level.")
            mes("Your Combat level is now ${player.combatLevel}.")
        }

        // Arm the hint before the dialogue suspends; it must arrive while the window is open.
        armSkillGuideHint(stat, displayName, level)

        levelUpDialogue(levelUpIcon(shortName), line1, line2)

        celebrateMaxTotalIfReached(shortName)
    }

    private suspend fun ProtectedAccess.celebrateMaxTotalIfReached(shortName: String) {
        if (player.maxTotalCelebrated) {
            return
        }
        val total = totalLevel()
        if (total < maxTotalLevel) {
            return
        }
        player.maxTotalCelebrated = true

        val line1 = "Congratulations, you've reached the highest possible total level!"
        val line2 = "Your total level is now $total."

        soundSynth("synth.FIREWORK")
        mes("<col=ff0000>$line1</col>")
        mes(line2)
        levelUpDialogue(levelUpIcon(shortName), line1, line2)
    }

    private fun ProtectedAccess.armSkillGuideHint(stat: StatType, displayName: String, level: Int) {
        // Advance the snapshot even when the stat has no guide entry, avoiding a stale previous level.
        val previous = player.consumePreviousBaseLevel(stat, level)
        val guideSkillId = statToGuideId[stat.id] ?: return
        if (!hasUnlockInRange(guideSkillId, previous, level)) {
            return
        }

        // The unread-level varbit drives the tab flash; the chat message alone does not.
        val unreadVarbit = levelUpListVarbit(stat)
        if (unreadVarbit != null && player.vars[unreadVarbit] == 0) {
            VarPlayerIntMapSetter.set(player, unreadVarbit, previous + 1)
        }

        val pending = player.pendingSkillGuideHints()
        pending += SkillGuideHint.message(guideSkillId, displayName)
        player.softTimer(SkillGuideHint.Timer, cycles = 1)
    }

    // Clear the soft timer after delivery; otherwise it automatically re-arms.
    private fun Player.flushSkillGuideHints() {
        clearSoftTimer(SkillGuideHint.Timer)
        val pending = attr[PendingSkillGuideHints] ?: return
        for (message in pending) {
            mes(message, ChatType.LevelUpMessage)
        }
        pending.clear()
    }

    private fun Player.pendingSkillGuideHints(): MutableList<String> {
        val existing = attr[PendingSkillGuideHints]
        if (existing != null) {
            return existing
        }
        val created = mutableListOf<String>()
        attr[PendingSkillGuideHints] = created
        return created
    }

    private fun Player.consumePreviousBaseLevel(stat: StatType, level: Int): Int {
        val snapshot =
            attr[PreviousBaseLevels]
                ?: buildBaseLevelSnapshot().also { attr[PreviousBaseLevels] = it }
        val index = stat.id
        if (index !in snapshot.indices) {
            return level - 1
        }
        val recorded = snapshot[index]
        snapshot[index] = level
        return if (recorded in 0 until level) recorded else level - 1
    }

    private fun Player.seedBaseLevelSnapshot() {
        attr[PreviousBaseLevels] = buildBaseLevelSnapshot()
    }

    private fun Player.buildBaseLevelSnapshot(): IntArray {
        val snapshot = IntArray(statSnapshotSize)
        for (stat in ServerCacheManager.getStats().values) {
            if (stat.id in snapshot.indices) {
                snapshot[stat.id] = statBase(stat.internalName)
            }
        }
        return snapshot
    }

    private val statSnapshotSize: Int by lazy {
        (ServerCacheManager.getStats().keys.maxOrNull() ?: -1) + 1
    }

    private fun hasUnlockInRange(guideSkillId: Int, previousLevel: Int, newLevel: Int): Boolean {
        val levels = unlockLevels[guideSkillId] ?: return false
        return levels.any { it > previousLevel && it <= newLevel }
    }

    private val statToGuideId: Map<Int, Int> by lazy {
        enum<StatType, Int>("skill_guide_skill_to_id")
            .backing
            .entries
            .mapNotNull { (stat, guideId) -> guideId?.let { stat.id to it } }
            .toMap()
    }

    private val unlockLevels: Map<Int, Set<Int>> by lazy {
        val index = mutableMapOf<Int, MutableSet<Int>>()
        for (row in DbHelper.table("dbtable.skill_features")) {
            row.stringOptional("dbcol.skill_features:text") ?: continue
            val tuples =
                row.slotsOptional("dbcol.skill_features:skill", DbColumnCodec.IntCodec).chunked(3)
            for (tuple in tuples) {
                if (tuple.size < 2) {
                    continue
                }
                val requiredLevel = tuple[1]
                if (requiredLevel <= 1) {
                    continue
                }
                index.getOrPut(tuple[0]) { mutableSetOf() } += requiredLevel
            }
        }
        index
    }

    private fun ProtectedAccess.levelUpSpotanim(level: Int): String =
        when {
            totalLevel() == maxTotalLevel -> "spotanim.levelup_max"
            level == 99 -> "spotanim.levelup_99_anim"
            else -> "spotanim.levelup_anim"
        }

    private val maxTotalLevel: Int by lazy {
        ServerCacheManager.getStats().values.filterNot { it.unreleased }.sumOf { it.maxLevel }
    }

    private fun ProtectedAccess.totalLevel(): Int {
        var total = 0
        for (stat in ServerCacheManager.getStats().values) {
            if (stat.unreleased) {
                continue
            }
            total += statBase(stat.internalName)
        }
        return total
    }

    private fun levelUpIcon(shortName: String): String {
        val icon = if (shortName == "runecrafting") "runecraft" else shortName
        return "component.levelup_display:$icon"
    }

    private companion object {
        private const val LevelUpSpotanimHeight = 124

        private val PreviousBaseLevels = AttributeKey<IntArray>()

        // Queue a list because one quest reward can advance several stats before delivery.
        private val PendingSkillGuideHints = AttributeKey<MutableList<String>>()
    }
}
