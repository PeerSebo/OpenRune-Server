package org.rsmod.content.quest.manager

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.baseVar
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.table.QuestRow
import org.rsmod.game.entity.Player
import org.rsmod.game.vars.VarPlayerIntMap
import org.rsmod.map.CoordGrid

val QUEST_STAGE_MAP_ATTR = AttributeKey<MutableMap<String, Int>>("quest_stages")

data class ItemRewardDisplay(
    val item: String,
    val zoom: Int = 400,
    val x: Int = DefaultModelX,
    val y: Int = DefaultModelY,
) {
    companion object {
        const val DefaultModelX = 78

        const val DefaultModelY = 128
    }
}

data class Quest(
    val id: Int,
    val key: String,
    val rowID: Int,
    val displayName: String,
    val mapElement: Int?,
    val startCoord: CoordGrid?,
    val maxSteps: Int,
    val questPoints: Int,
    val questVarp: String,
    val rewards: QuestReward,
    val itemDisplay: ItemRewardDisplay?,
    val questVarbit: String? = null,
    val completionJingle: Int? = null,
    val requiredQuestPoints: Int = 0,
    val completionJingleLength: Int = 0,
) {

    private var Player.questPoints by intVarp("varp.qp")
    private var Player.questsCompleted by intVarBit("varbit.quests_completed_count")

    // Write only the stage varbit when the backing varp is shared with other quest fields.
    private val stageVar: String
        get() = questVarbit ?: questVarp

    private val fields = mutableMapOf<String, QuestField<*>>()

    companion object {
        private val logger = InlineLogger()
        private val questsByKey = mutableMapOf<String, Quest>()

        fun get(key: String): Quest? = questsByKey[key.normalizedQuestKey()]

        fun getById(id: Int): Quest? = questsByKey.values.find { it.id == id }

        fun all(): Collection<Quest> = questsByKey.values

        fun register(
            rowKey: String,
            varp: String,
            itemDisplay: ItemRewardDisplay?,
            rewards: QuestReward,
            varbit: String? = null,
            completionJingle: Int? = null,
            completionJingleLength: Int = 0,
        ): Quest {

            val rowKeyID = "dbrow.${rowKey}".asRSCM()
            val questRow = QuestRow.getRow(rowKeyID)
            val quest =
                Quest(
                    id = questRow.id,
                    rowID = rowKeyID,
                    key = rowKey,
                    displayName = questRow.displayname,
                    mapElement = questRow.mapelement,
                    startCoord = questRow.startcoord,
                    maxSteps = questRow.endstate,
                    questPoints = questRow.questpoints,
                    questVarp = varp,
                    questVarbit = varbit,
                    itemDisplay = itemDisplay,
                    rewards = rewards,
                    completionJingle = completionJingle,
                    completionJingleLength = completionJingleLength,
                    requiredQuestPoints = questRow.requirementQuestpoints ?: 0,
                )
            questsByKey[rowKey.normalizedQuestKey()] = quest
            return quest
        }
    }

    fun getQuestStage(access: Player): Int {
        val stages = access.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        return stages[key] ?: 0
    }

    private fun mirroredStage(player: Player): Int = player.vars[stageVar]

    private fun writeMirroredStage(player: Player, stage: Int) {
        if (mirroredStage(player) != stage) {
            VarPlayerIntMapSetter.set(player, stageVar, stage)
        }
    }

    private fun storeQuestStage(player: Player, stage: Int) {
        val stages = player.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        stages[key] = stage
        player.attr[QUEST_STAGE_MAP_ATTR] = stages
    }

    fun questState(player: Player): QuestProgressState =
        QuestProgressState.fromStage(getQuestStage(player), maxSteps)

    fun isQuestNotStarted(player: Player): Boolean = questState(player).isNotStarted

    fun isQuestInProgress(player: Player): Boolean = questState(player).isInProgress

    fun isQuestCompleted(player: Player): Boolean = questState(player).isCompleted

    fun advanceQuestStage(access: ProtectedAccess, amount: Int = 1): Int {
        val currentStage = getQuestStage(access.player)
        val attemptedStage = currentStage + amount

        if (attemptedStage > maxSteps) {
            val playerName = access.player.displayName.ifEmpty { "unknown" }
            logger.error {
                "Attempted to advance quest '$key' for player '$playerName' " +
                    "from stage $currentStage by $amount (max=$maxSteps)."
            }
            throw IllegalStateException("Quest '$key' cannot advance past stage $maxSteps.")
        }

        return applyQuestStage(access, attemptedStage.coerceIn(0, maxSteps))
    }

    // Set the explicit final stage before rewarding; incrementing once may leave a non-contiguous ladder unfinished.
    fun setQuestStage(access: ProtectedAccess, stage: Int): Int {
        require(stage in 0..maxSteps) {
            "Quest '$key' has no stage $stage (valid range 0..$maxSteps)."
        }
        return applyQuestStage(access, stage)
    }

    fun completeQuest(access: ProtectedAccess): Int = applyQuestStage(access, maxSteps)

    fun resetQuest(player: Player) {
        val wasCompleted = isQuestCompleted(player)
        storeQuestStage(player, 0)
        writeMirroredStage(player, 0)
        for (field in fields.values) {
            field.clear(player)
        }
        if (wasCompleted) {
            player.questPoints = (player.questPoints - questPoints).coerceAtLeast(0)
            player.questsCompleted = (player.questsCompleted - 1).coerceAtLeast(0)
        }
    }

    /** Administrative stage changes retain the local varbit mirror and never award rewards. */
    fun jumpToStage(player: Player, stage: Int) {
        require(stage in 0..maxSteps) { "Quest '$key' has no stage $stage (0..$maxSteps)." }
        storeQuestStage(player, stage)
        writeMirroredStage(player, stage)
    }

    private fun applyQuestStage(access: ProtectedAccess, newStage: Int): Int {
        val player = access.player
        val currentStage = getQuestStage(player)
        val wasCompleted = currentStage >= maxSteps

        if (newStage < currentStage) {
            val playerName = player.displayName.ifEmpty { "unknown" }
            logger.warn {
                "Quest '$key' moved backwards for player '$playerName': " +
                    "$currentStage -> $newStage (max=$maxSteps)."
            }
        }

        val wasNotStarted = currentStage == 0

        storeQuestStage(player, newStage)

        writeMirroredStage(player, newStage)

        if (wasNotStarted && newStage > 0) {
            access.mes("You've started a new quest: <col=081190>$displayName</col>")
        }

        if (!wasCompleted && newStage >= maxSteps) {
            completedQuest(access)
        }

        return newStage
    }

    // Recover from the native stage mirror only when the saved quest attribute is absent.
    fun syncStageVar(player: Player) {
        val stages = player.attr.getOrPut(QUEST_STAGE_MAP_ATTR) { mutableMapOf() }
        val recorded = stages[key]

        if (recorded == null) {
            val mirrored = mirroredStage(player).coerceIn(0, maxSteps)
            if (mirrored > 0) {
                logger.warn {
                    "Quest '$key' had no stored stage for player " +
                        "'${player.displayName.ifEmpty { "unknown" }}'; " +
                        "recovering stage $mirrored from '$stageVar'."
                }
                storeQuestStage(player, mirrored)
                writeMirroredStage(player, mirrored)
                return
            }
        }

        writeMirroredStage(player, recorded ?: 0)
    }

    // Validate stage symbols and bit width at startup so invalid definitions cannot fail midway through a quest.
    fun assertStageVarResolves() {
        val varpType =
            checkNotNull(ServerCacheManager.getVarp(questVarp.asRSCM(RSCMType.VARP))) {
                "Quest '$key' stage varp does not resolve: $questVarp"
            }

        val varbit = questVarbit ?: return

        val varbitType =
            checkNotNull(ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))) {
                "Quest '$key' stage varbit does not resolve: $varbit"
            }

        check(varbitType.baseVar.id == varpType.id) {
            "Quest '$key' stage varbit '$varbit' is on varp ${varbitType.baseVar.id}, " +
                "but the quest declares '$questVarp' (${varpType.id})."
        }

        VarPlayerIntMap.assertVarBitBounds(varbitType, maxSteps)
    }

    fun <T> attribute(
        name: String,
        default: T,
        resetOnDeath: Boolean = false,
        temp: Boolean = false,
    ): QuestAttribute<T> = attribute(name, { default }, resetOnDeath, temp)

    fun <T> attribute(
        name: String,
        default: () -> T,
        resetOnDeath: Boolean = false,
        temp: Boolean = false,
    ): QuestAttribute<T> {
        @Suppress("UNCHECKED_CAST")
        return fields.getOrPut(name) {
            QuestAttribute(
                name = name,
                attributeKey =
                    AttributeKey(
                        persistenceKey = "quest.$key.$name",
                        resetOnDeath = resetOnDeath,
                        temp = temp,
                    ),
                defaultProvider = default,
            )
        } as QuestAttribute<T>
    }

    fun flag(name: String, varbit: String, migratedVarbit: String): QuestFlag =
        fields.getOrPut(name) { QuestFlag(name, key, varbit, migratedVarbit) } as QuestFlag

    fun int(name: String, varp: String, migratedVarbit: String, default: Int = 0): QuestInt =
        fields.getOrPut(name) { QuestInt(name, key, varp, migratedVarbit, default) } as QuestInt

    fun boolean(name: String, varp: String, migratedVarp: String): QuestVar<Boolean> =
        registerVar(QuestVar.boolean(name, varp, "quest.$key.$name", migratedVarp))

    fun temporaryBoolean(name: String, varp: String): QuestVar<Boolean> =
        registerVar(QuestVar.boolean(name, varp, temp = true))

    fun temporaryInt(name: String, varp: String, default: Int = 0): QuestVar<Int> =
        registerVar(QuestVar.integer(name, varp, default, temp = true))

    private fun <T> registerVar(field: QuestVar<T>): QuestVar<T> {
        @Suppress("UNCHECKED_CAST")
        return fields.getOrPut(field.name) { field } as QuestVar<T>
    }

    fun migrateFields(player: Player) {
        for (field in fields.values) field.migrate(player)
    }

    fun assertFieldsResolve() {
        for (field in fields.values) field.assertResolves()
    }

    // Observe modal closure without another protected-access suspension; cancellation must not strand completion.
    suspend fun awaitRewardScroll(access: ProtectedAccess) {
        access.ifCloseSub("interface.chat_left")
        access.ifCloseSub("interface.chat_right")
        access.coroutine.pause { access.player.ui.modals.isEmpty() }
    }

    private fun completedQuest(access: ProtectedAccess) {

        access.player.questPoints += questPoints
        access.player.questsCompleted++

        access.mes("Congratulations, you've completed a quest: <col=081190>$displayName</col>")

        completionJingle?.let { access.midiJingle(it, completionJingleLength) }

        access.ifOpenMain("interface.questscroll")

        access.ifSetEvents("component.questscroll:close_button", -1..-1, IfEvent.Op1)

        access.ifSetText("component.questscroll:quest_title", "You have completed ${displayName}!")
        val pointLabel = if (questPoints == 1) "Quest Point" else "Quest Points"
        access.ifSetText("component.questscroll:quest_reward1", "$questPoints $pointLabel")

        itemDisplay?.let { display ->
            access.ifSetObj(
                "component.questscroll:quest_model",
                obj = display.item,
                zoom = display.zoom,
            )
            access.ifSetPosition("component.questscroll:quest_model", display.x, display.y)
        }

        val rewardLines = mutableListOf<String>()

        rewards.xp.forEach { (skill, amount) ->
            val stat =
                ServerCacheManager.getStats(skill.asRSCM(RSCMType.STAT))
                    ?: error("No stat found for $skill")

            val granted = access.statAdvance(skill, amount, globalRate = 1.0)
            rewardLines.add("$granted ${stat.displayName} XP")
        }

        rewards.items.forEach { reward ->
            access.invAdd(access.inv, reward.item, reward.amount)
            val type =
                ServerCacheManager.getItem(reward.item.asRSCM(RSCMType.OBJ))
                    ?: error("No item found for ${reward.item}")
            rewardLines.add(reward.label ?: "${reward.amount} ${type.name}")
        }

        rewardLines.addAll(rewards.extraLines)

        val linesToShow = rewardLines.take(6)

        for (i in 0 until 6) {
            val componentId = "component.questscroll:quest_reward${i + 2}"
            val text = linesToShow.getOrNull(i) ?: ""
            access.ifSetText(componentId, text)
        }
    }
}
