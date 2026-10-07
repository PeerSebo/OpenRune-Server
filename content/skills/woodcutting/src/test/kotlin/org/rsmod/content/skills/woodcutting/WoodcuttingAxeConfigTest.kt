package org.rsmod.content.skills.woodcutting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.axeWoodcuttingAnim
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.axeWoodcuttingReq
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting.Companion.treeStump
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

/** Exercises packed params and enum rows; source-only checks cannot catch failed cache overrides. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WoodcuttingAxeConfigTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `every tagged axe has an animation and rates for every configured tree`() {
        val axes = axes()
        val trees = trees()
        assertEquals(31, axes.size)
        assertTrue(trees.isNotEmpty())

        for (axe in axes) {
            assertNotNull(ServerCacheManager.getAnim(axe.axeWoodcuttingAnim.id), axe.internalName)
            for (tree in trees) {
                val (low, high) = Woodcutting.cutSuccessRates(tree, InvObj(axe))
                assertTrue(low > 0 && high >= low, "${tree.internalName}: ${axe.internalName}")
            }
        }
    }

    @Test
    fun `inactive crystal variants retain level 71 and use dragon rates on every tree`() {
        val dragon = item("obj.dragon_axe")
        for (symbol in inactiveCrystalAxes) {
            val axe = itemType(symbol)
            assertEquals(71, axe.axeWoodcuttingReq, symbol)
            for (tree in trees()) {
                assertEquals(
                    Woodcutting.cutSuccessRates(tree, dragon),
                    Woodcutting.cutSuccessRates(tree, InvObj(axe)),
                    "${tree.internalName}: $symbol",
                )
            }
        }
    }

    @Test
    fun `charged crystal axes keep their better redwood rates`() {
        val tree = tree("loc.redwoodtree_l")
        val dragon = Woodcutting.cutSuccessRates(tree, item("obj.dragon_axe"))
        for (symbol in listOf("obj.crystal_axe", "obj.crystal_axe_2h")) {
            val crystal = Woodcutting.cutSuccessRates(tree, item(symbol))
            assertTrue(crystal.first + crystal.second > dragon.first + dragon.second, symbol)
        }
    }

    @Test
    fun `Reloaded variants are selectable at 61 from inventory and equipment`() {
        val tree = tree("loc.oaktree")
        for (symbol in reloadedAxes) {
            val axe = item(symbol)
            for (worn in listOf(false, true)) {
                val player = player(60)
                placeAxe(player, axe, worn)
                assertNull(Woodcutting.findAxe(player, tree), "$symbol below level 61")
                player.statMap.setCurrentLevel("stat.woodcutting", 61.toByte())
                assertEquals(axe, Woodcutting.findAxe(player, tree), "$symbol worn=$worn")
            }
        }
    }

    @Test
    fun `Reloaded variants use dragon rates and their corresponding animations`() {
        val dragon = item("obj.dragon_axe")
        for (symbol in reloadedAxes) {
            val axe = itemType(symbol)
            val animation =
                if (symbol.endsWith("_no_infernal")) {
                    "seq.human_woodcutting_trailblazer_reloaded_axe_no_infernal"
                } else {
                    "seq.human_woodcutting_trailblazer_reloaded_axe"
                }
            assertEquals(animation.asRSCM(), axe.axeWoodcuttingAnim.id, symbol)
            for (tree in trees()) {
                assertEquals(
                    Woodcutting.cutSuccessRates(tree, dragon),
                    Woodcutting.cutSuccessRates(tree, InvObj(axe)),
                    "${tree.internalName}: $symbol",
                )
            }
        }
    }

    @Test
    fun `crystal felling axes require quest completion in both inventory and equipment`() {
        withQuestPolicy(QuestRequirementPolicy(QuestRequirementMode.RespectProgress)) {
            val tree = tree("loc.redwoodtree_l")
            for (symbol in crystalFellingAxes) {
                for (worn in listOf(false, true)) {
                    val player = player(99)
                    placeAxe(player, item(symbol), worn)
                    assertNull(Woodcutting.findAxe(player, tree), "$symbol worn=$worn")

                    val fallback = item("obj.rune_axe")
                    player.inv[1] = fallback
                    assertEquals(fallback, Woodcutting.findAxe(player, tree), symbol)
                }
            }
        }
    }

    @Test
    fun `crystal felling axes become eligible when the quest policy grants completion`() {
        val policy =
            QuestRequirementPolicy(
                mode = QuestRequirementMode.VirtualCompletions,
                virtualCompletions = setOf("quest_songoftheelves"),
            )
        withQuestPolicy(policy) {
            val tree = tree("loc.oaktree")
            for (symbol in crystalFellingAxes) {
                for (worn in listOf(false, true)) {
                    val player = player(71)
                    val axe = item(symbol)
                    placeAxe(player, axe, worn)
                    assertEquals(axe, Woodcutting.findAxe(player, tree), "$symbol worn=$worn")
                }
            }
        }
    }

    @Test
    fun `Prifddinas outside tree variant two uses its own stump`() {
        assertEquals(
            "loc.prif_outside_tree_normal_2_stump".asRSCM(),
            tree("loc.prif_outside_tree_normal_2").treeStump.id,
        )
        assertEquals(
            "loc.prif_outside_tree_normal_1_stump".asRSCM(),
            tree("loc.prif_outside_tree_normal_1").treeStump.id,
        )
    }

    private fun player(level: Int) =
        Player().apply {
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setCurrentLevel("stat.woodcutting", level.toByte())
            statMap.setCurrentLevel("stat.firemaking", 99.toByte())
        }

    private fun placeAxe(player: Player, axe: InvObj, worn: Boolean) {
        if (worn) {
            player.worn[Wearpos.RightHand.slot] = axe
        } else {
            player.inv[0] = axe
        }
    }

    private inline fun withQuestPolicy(policy: QuestRequirementPolicy, action: () -> Unit) {
        val previous = QuestRequirements.activePolicy()
        QuestRequirements.install(policy)
        try {
            action()
        } finally {
            QuestRequirements.install(previous)
        }
    }

    private fun axes(): List<ItemServerType> =
        ServerCacheManager.getItemTypes().filter { it.isContentType("content.woodcutting_axe") }

    private fun trees(): List<ObjectServerType> =
        ServerCacheManager.getObjects().values.filter { it.isContentType("content.tree") }

    private fun tree(symbol: String) = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))

    private fun itemType(symbol: String) = checkNotNull(ServerCacheManager.getItem(symbol.asRSCM()))

    private fun item(symbol: String) = InvObj(itemType(symbol))

    private val inactiveCrystalAxes =
        listOf("obj.crystal_axe_inactive", "obj.crystal_axe_2h_inactive")
    private val crystalFellingAxes = listOf("obj.crystal_axe_2h", "obj.crystal_axe_2h_inactive")
    private val reloadedAxes =
        listOf(
            "obj.trailblazer_reloaded_axe",
            "obj.trailblazer_reloaded_axe_empty",
            "obj.trailblazer_reloaded_axe_no_infernal",
        )
}
