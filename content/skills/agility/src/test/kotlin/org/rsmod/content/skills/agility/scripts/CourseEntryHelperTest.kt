package org.rsmod.content.skills.agility.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.constants.MappingProvider
import dev.openrune.definition.type.VarBitType
import dev.openrune.rscm.RSCM
import dev.openrune.types.StatType
import dev.openrune.types.varp.VarpServerType
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AgilityCourse
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CourseEntryHelperTest {
    @Test
    fun `Draynor publishes client course three while preserving internal course two`() = withDefinitions {
        val player = player(level = 10, previousCourse = 15)
        val course = course(id = 2, requiredLevel = 10)

        assertTrue(access(player).meetsCourseReq(course))

        assertEquals(2, course.id)
        assertEquals(UNRELATED_BITS or 3, player.vars.backing[HELPER_VARP])
    }

    @Test
    fun `Seers publishes client course fifteen while preserving internal course seven`() = withDefinitions {
        val player = player(level = 60, previousCourse = 3)
        val course = course(id = 7, requiredLevel = 60)

        assertTrue(access(player).meetsCourseReq(course))

        assertEquals(7, course.id)
        assertEquals(UNRELATED_BITS or 15, player.vars.backing[HELPER_VARP])
    }

    @Test
    fun `denied entry leaves the previous helper course unchanged`() = withDefinitions {
        val player = player(level = 9, previousCourse = 15)

        assertFalse(access(player).meetsCourseReq(course(id = 2, requiredLevel = 10)))

        assertEquals(UNRELATED_BITS or 15, player.vars.backing[HELPER_VARP])
    }

    @Test
    fun `out of order rooftop finish cannot collect the combined final XP`() = withDefinitions {
        val player = rewardPlayer()
        val seers = rewardCourse().copy(finalObstacleXpRequiresLap = true)

        val events = EventBus()
        val completed = mutableListOf<PlayerActionCompleteEvent>()
        events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completed += this }
        access(player, events).awardObstacle(seers, 5, 435.0, XpModifiers(emptySet()))

        assertEquals(0, player.statMap.getFineXP("stat.agility"))
        assertEquals(0, player.vars.backing[COURSE_PROGRESS])
        assertEquals(0, player.vars.backing[LAPS_VARP])
        assertTrue(completed.isEmpty())
    }

    @Test
    fun `valid rooftop lap keeps its complete final XP and increments laps`() = withDefinitions {
        val player = rewardPlayer()
        player.vars.backing[COURSE_PROGRESS] = (5 shl 5) or 7
        val seers = rewardCourse().copy(finalObstacleXpRequiresLap = true)

        val events = EventBus()
        val completed = mutableListOf<PlayerActionCompleteEvent>()
        events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completed += this }
        access(player, events).awardObstacle(seers, 5, 435.0, XpModifiers(emptySet()))

        assertEquals(4350, player.statMap.getFineXP("stat.agility"))
        assertEquals(0, player.vars.backing[COURSE_PROGRESS])
        assertEquals(1, player.vars.backing[LAPS_VARP])
        assertEquals(1, completed.size)
        assertEquals(PlayerAction.AgilityLap, completed.single().action)
        assertEquals(seers.lapCountVarp, completed.single().target)
    }

    @Test
    fun `nonterminal obstacle still pays its own XP even when out of order`() = withDefinitions {
        val player = rewardPlayer()
        val seers = rewardCourse().copy(finalObstacleXpRequiresLap = true)

        access(player).awardObstacle(seers, 3, 50.0, XpModifiers(emptySet()))

        assertEquals(500, player.statMap.getFineXP("stat.agility"))
        assertEquals(0, player.vars.backing[COURSE_PROGRESS])
        assertEquals(0, player.vars.backing[LAPS_VARP])
    }

    @Test
    fun `course with a known separate lap bonus retains its final obstacle base XP`() = withDefinitions {
        val player = rewardPlayer()
        val course = rewardCourse().copy(lapBonusXp = 100.0)

        access(player).awardObstacle(course, 5, 25.0, XpModifiers(emptySet()))

        assertEquals(250, player.statMap.getFineXP("stat.agility"))
        assertEquals(0, player.vars.backing[LAPS_VARP])
    }

    private fun rewardPlayer() = player(level = 99, previousCourse = 15).apply {
        // Avoid unrelated combat-level/interface work in this statAdvance fixture.
        statMap.setBaseLevel("stat.agility", 99)
    }

    private fun rewardCourse() = course(id = 7, requiredLevel = 60).copy(
        obstacleCount = 6,
        restoresRunEnergy = false,
    )

    private fun player(level: Int, previousCourse: Int) = Player().apply {
        statMap.setCurrentLevel("stat.agility", level.toByte())
        vars.backing[HELPER_VARP] = UNRELATED_BITS or previousCourse
    }

    private fun course(id: Int, requiredLevel: Int) = AgilityCourse(
        id = id,
        displayName = "Test course",
        levelReq = requiredLevel,
        obstacleCount = 7,
        lapBonusXp = 0.0,
        restoresRunEnergy = true,
        lapCountVarp = "varp.unused_test_laps",
    )

    private fun access(player: Player, events: EventBus = EventBus()) = ProtectedAccess(
        player,
        GameCoroutine(),
        ProtectedAccessContext(
            getRandom = { error("unused") },
            getEventBus = { events },
            getNpcList = { error("unused") },
            getPlayerList = { error("unused") },
            getCollision = { error("unused") },
            getAreaChecker = { error("unused") },
            getAlignment = { error("unused") },
            getLocInteractions = { error("unused") },
            getNpcInteractions = { error("unused") },
            getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") },
            getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") },
            getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") },
            getHitModifier = { error("unused") },
        ),
    )

    private fun withDefinitions(block: () -> Unit) {
        val mappings = mutableMapOf(
            "stat" to mutableMapOf("stat.agility" to 16),
            "varbit" to mutableMapOf("varbit.helper_agility_current_course" to 12633),
            "varp" to mutableMapOf("varp.agility_course_progress" to COURSE_PROGRESS, "varp.unused_test_laps" to LAPS_VARP),
        )
        val cached = mappings.values.flatMap { it.entries }.associate { it.key to it.value }
        withEntries(RSCM, "cache", cached) {
            val varps = listOf(HELPER_VARP, COURSE_PROGRESS, LAPS_VARP).associateWith { VarpServerType(id = it) }
            withEntries(ServerCacheManager, "varps", varps) {
                val varbit = VarBitType(id = 12633, varp = HELPER_VARP, startBit = 0, endBit = 6)
                withEntries(ServerCacheManager, "varbits", mapOf(12633 to varbit)) {
                    withEntries(ServerCacheManager, "statTypes", mapOf(16 to StatType(id = 16))) {
                        withProvider(mappings, block)
                    }
                }
            }
        }
    }

    private fun withProvider(definitions: MutableMap<String, MutableMap<String, Int>>, block: () -> Unit) {
        val provider = object : MappingProvider {
            override val mappings = definitions
            override fun load(vararg mappings: File) = error("No file loading in this test")
            override fun getSupportedExtensions(): List<String> = emptyList()
        }
        val field = ConstantProvider.javaClass.getDeclaredField("provider").apply { isAccessible = true }
        val previous = field.get(ConstantProvider)
        field.set(ConstantProvider, provider)
        try {
            block()
        } finally {
            field.set(ConstantProvider, previous)
        }
    }

    private fun <K, V> withEntries(owner: Any, fieldName: String, entries: Map<K, V>, block: () -> Unit) {
        val field = owner.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val values = field.get(owner) as MutableMap<K, V>
        val previous = entries.keys.associateWith { values[it] }
        values.putAll(entries)
        try {
            block()
        } finally {
            for ((key, value) in previous) {
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }

    private companion object {
        const val HELPER_VARP = 3153
        const val COURSE_PROGRESS = 65479
        const val LAPS_VARP = 60000
        const val UNRELATED_BITS = 1 shl 12
    }
}
