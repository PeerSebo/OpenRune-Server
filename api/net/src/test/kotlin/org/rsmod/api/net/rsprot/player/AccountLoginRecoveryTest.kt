package org.rsmod.api.net.rsprot.player

import dev.openrune.ServerCacheManager
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.protocol.api.EntityInfoProtocols
import net.rsprot.protocol.api.suppliers.NpcInfoSupplier
import net.rsprot.protocol.api.suppliers.PlayerInfoSupplier
import net.rsprot.protocol.api.suppliers.WorldEntityInfoSupplier
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.InfoProtocols
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("rsprot-info-protocols")
class AccountLoginRecoveryTest {
    @Test
    fun `legacy cutscene recovery changes the prelogin coordinates used by the initial rebuild`() {
        val player = Player().apply { coords = CoordGrid(7000, 7000, 2) }
        val returnTo = CoordGrid(3215, 3210, 1)
        player.attr.putAllFromPersistence(mapOf("instance_exit_coord" to returnTo.packed))

        applyAccountConfig(player)

        assertEquals(returnTo, player.coords)
        assertFalse("instance_exit_coord" in player.attr.toPersistentMap())
        assertNull(player.loginExitCoord)
        assertEquals(returnTo.packed, initialRebuildPosition(player))
    }

    @Test
    fun `new saved zero is consumed during prelogin without falling through to the realm spawn`() {
        val player = Player().apply { coords = CoordGrid(7000, 7000, 2) }
        player.loginExitCoord = CoordGrid.ZERO

        applyAccountConfig(player)

        assertEquals(CoordGrid.ZERO, player.coords)
        assertNull(player.loginExitCoord)
        assertEquals(0, initialRebuildPosition(player))
    }

    @Test
    fun `normal return cannot be undone by stale legacy data on the next login`() {
        val player = Player().apply { coords = CoordGrid(3012, 3212) }
        player.loginExitCoord = null
        player.attr.putAllFromPersistence(mapOf("instance_exit_coord" to CoordGrid(3215, 3210).packed))

        applyAccountConfig(player)
        applyAccountConfig(player)

        assertEquals(CoordGrid(3012, 3212), player.coords)
        assertNull(player.loginExitCoord)
        assertFalse("instance_exit_coord" in player.attr.toPersistentMap())
    }

    @Test
    fun `new accounts retain realm spawn and experience configuration`() {
        val player = Player().apply { coords = CoordGrid(7000, 7000, 2) }
        VarPlayerIntMapSetter.set(player, "varbit.new_player_account", 1)
        player.attr.putAllFromPersistence(mapOf("instance_exit_coord" to CoordGrid(3215, 3210).packed))

        applyAccountConfig(player)

        assertEquals(config.spawnCoord, player.coords)
        assertEquals(config.baseXpRate, player.xpRate)
    }

    @Test
    fun `orphaned house and boss instance saves use the realm spawn before the initial rebuild`() {
        for (area in listOf(
            RegionRegistry.workingAreaSmall,
            RegionRegistry.workingAreaLarge,
            RegionRegistry.workingAreaWorldEntity,
        )) {
            val player = Player().apply { coords = area.calculateCoord(0).copy(level = 2) }
            applyAccountConfig(player)
            assertEquals(config.spawnCoord, player.coords)
            assertEquals(config.spawnCoord.packed, initialRebuildPosition(player))
        }
    }

    @Test
    fun `an explicit recovery destination still outranks orphan fallback`() {
        val player = Player().apply { coords = RegionRegistry.workingAreaLarge.calculateCoord(0) }
        val exit = CoordGrid(2953, 3224)
        player.loginExitCoord = exit
        applyAccountConfig(player)
        assertEquals(exit, player.coords)
    }

    @Test
    fun `interrupted tears visit returns to Juna before the initial login rebuild`() {
        val exit = CoordGrid(3251, 9516, 2)
        for (saved in listOf(CoordGrid(3252, 9516, 2), CoordGrid(3256, 9517, 2), CoordGrid(3260, 9516, 2))) {
            val player = Player().apply {
                coords = saved
                loginExitCoord = exit
            }
            applyAccountConfig(player)
            assertEquals(exit, player.coords)
            assertEquals(exit.packed, initialRebuildPosition(player))
            assertNull(player.loginExitCoord)
        }
    }

    @Test
    fun `padding and legacy allocated coordinates recover before the initial rebuild`() {
        for (saved in listOf(
            CoordGrid(6401, 6000), CoordGrid(7000, 10), CoordGrid(6464, 64),
            CoordGrid(6464, 5280), CoordGrid(14080, 0),
        )) {
            val player = Player().apply { coords = saved }
            applyAccountConfig(player)
            assertEquals(config.spawnCoord, player.coords)
            assertEquals(config.spawnCoord.packed, initialRebuildPosition(player))
        }
    }

    @Test
    fun `static world tiles are never classified as orphaned instances`() {
        for (saved in listOf(CoordGrid(3222, 3218), CoordGrid(6399, 6000))) {
            val player = Player().apply { coords = saved }
            applyAccountConfig(player)
            assertEquals(saved, player.coords)
        }
    }

    private fun applyAccountConfig(player: Player) {
        // The prelogin transform needs no connection; skip constructing the authentication services.
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        val hook = unsafe.allocateInstance(AccountLoadResponseHook::class.java)
        val apply = AccountLoadResponseHook::class.java.getDeclaredMethod(
            "applyConfigTransforms", Player::class.java, RealmConfig::class.java,
        ).apply { isAccessible = true }
        apply.invoke(hook, player, config)
    }

    private fun initialRebuildPosition(player: Player): Int {
        val huffmanBytes = Unpooled.wrappedBuffer(ByteArray(256) { 8 })
        val huffman = try {
            DefaultHuffmanCodecProvider(HuffmanCodec.create(huffmanBytes))
        } finally {
            huffmanBytes.release()
        }
        val companion = EntityInfoProtocols::class.java.getDeclaredField("Companion")
            .apply { isAccessible = true }.get(null)
        val initialize = companion.javaClass.declaredMethods.single {
            it.name.startsWith("initialize") && it.parameterCount == 8
        }.apply { isAccessible = true }
        val entities = initialize.invoke(
            companion, UnpooledByteBufAllocator.DEFAULT,
            listOf(OldSchoolClientType.DESKTOP), huffman,
            PlayerInfoSupplier(), NpcInfoSupplier(), WorldEntityInfoSupplier(), null, null,
        ) as EntityInfoProtocols
        val protocols = InfoProtocols(entities.playerInfoProtocol, entities.npcInfoProtocol, entities.worldEntityInfoProtocol)
        val infos = protocols.alloc(1, OldSchoolClientType.DESKTOP)
        try {
            val coords = player.coords
            infos.updateRootCoord(coords.level, coords.x, coords.z)
            infos.updateRootBuildAreaCenteredOnPlayer(coords.x, coords.z)
            val rebuild = RebuildLoginV2(coords.x shr 3, coords.z shr 3, -1, infos.playerInfo)
            try {
                return (rebuild.gpiInitBlock.getUnsignedInt(rebuild.gpiInitBlock.readerIndex()) ushr 2).toInt()
            } finally {
                rebuild.release()
            }
        } finally {
            protocols.dealloc(infos)
        }
    }

    companion object {
        private val config = RealmConfig(
            id = 1,
            loginMessage = null,
            loginBroadcast = null,
            baseXpRate = 2.0,
            globalXpRate = 1.0,
            spawnCoord = CoordGrid(3221, 3218),
            respawnCoord = CoordGrid(3221, 3218),
            devMode = false,
            requireRegistration = false,
            autoAssignDisplayNames = false,
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
