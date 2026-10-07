package org.rsmod.api.net.rsprot

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.util.concurrent.Callable
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerInfo
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerInfoProtocol
import net.rsprot.protocol.game.outgoing.info.worker.DefaultProtocolWorker
import net.rsprot.protocol.game.outgoing.info.worker.ProtocolWorker
import org.rsmod.game.entity.Player

/**
 * Native avatar hiding omits high-resolution updates but leaves low-resolution coordinates.
 * Mask both current and previous tables at the worker boundary; retain the owner's own view.
 */
@Singleton
class StaffVisibilityProtocol @Inject constructor() : ProtocolWorker {
    private val delegate: ProtocolWorker = DefaultProtocolWorker()
    private val players = LinkedHashMap<Int, TrackedPlayer>()
    private val repositoryField = PlayerInfo::class.java
        .getDeclaredField("globalLowResolutionPositionRepository").apply { isAccessible = true }
    private val repositoryType = repositoryField.type
    private val currentPositionsField = repositoryType
        .getDeclaredField("currentLowResPositions").apply { isAccessible = true }
    private val previousPositionsField = repositoryType
        .getDeclaredField("previousLowResPositions").apply { isAccessible = true }
    private val repositoryConstructor = repositoryType.getDeclaredConstructor().apply { isAccessible = true }
    private val prepareBuffer = repositoryType.declaredMethods.single {
        it.name.startsWith("prepareLowResBuffer") && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
    }.apply { isAccessible = true }

    init {
        requirePinnedClass(PlayerInfo::class.java, "b3c61c77b168964cdcd92555436447d15a065997a63abe64150cd6dbd895a83e")
        requirePinnedClass(PlayerInfoProtocol::class.java, "555880c4bd12f6e10e3c9775ab36231bf7770223382de49bf63089d6747a6fc4")
        requirePinnedClass(repositoryType, "4c24de4ca8c1d50d0d5a6026f83a3f126eb72ffacb6b98cea18ffec8635340a8")
        check(currentPositionsField.type == IntArray::class.java) { "Unsupported rsprot low-resolution table" }
        check(previousPositionsField.type == IntArray::class.java) { "Unsupported rsprot previous low-resolution table" }
        check(prepareBuffer.returnType == Void.TYPE) { "Unsupported rsprot low-resolution buffer builder" }
    }

    private fun requirePinnedClass(type: Class<*>, expected: String) {
        val digest = type.getResourceAsStream("${type.simpleName}.class").use { stream ->
            checkNotNull(stream) { "Cannot verify rsprot staff visibility compatibility: ${type.name}" }
            MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())
        }
        val actual = java.util.HexFormat.of().formatHex(digest)
        check(actual == expected) {
            "Unsupported rsprot ${type.simpleName} for staff visibility. " +
                "Review StaffVisibilityProtocol and run its wire tests before changing the pinned r241 dependency."
        }
    }

    fun register(player: Player, info: PlayerInfo) {
        check(player.slotId == info.avatar.localPlayerIndex)
        val repository = repositoryField.get(info)
        val positions = currentPositionsField.get(repository) as IntArray
        check(positions.size == 2048) { "Unsupported rsprot player capacity" }
        players[player.slotId] = TrackedPlayer(player, info, repository, positions)
        // Reused rsprot avatars can retain hidden=true: always write both enabled and disabled.
        maskHiddenPlayers()

        // RebuildLogin snapshots the current table in its constructor. A hide earlier in this
        // same tick may already have zeroed a slot, while established observers still need its
        // previous-to-zero delta. Give this observer a temporary repository whose baseline is
        // exactly its login snapshot, so that delta is not applied twice on its first update.
        val initialRepository = repositoryConstructor.newInstance()
        positions.copyInto(previousPositionsField.get(initialRepository) as IntArray)
        players.getValue(player.slotId).initialRepository = initialRepository
        repositoryField.set(info, initialRepository)
    }

    fun unregister(player: Player) {
        val tracked = players[player.slotId] ?: return
        if (tracked.player !== player) return
        repositoryField.set(tracked.info, tracked.repository)
        players.remove(player.slotId)
    }

    fun maskHiddenPlayers() {
        for ((index, tracked) in players) {
            tracked.info.avatar.hidden = tracked.player.staffInvisible
            if (!tracked.player.staffInvisible) continue
            tracked.positions[index] = 0
            prepareBuffer.invoke(tracked.repository, index)
        }
    }

    override fun execute(callables: List<Callable<Unit>>) {
        maskHiddenPlayers()
        val initializing = players.values.filter { it.initialRepository != null }
        for (tracked in initializing) {
            val initialRepository = checkNotNull(tracked.initialRepository)
            tracked.positions.copyInto(currentPositionsField.get(initialRepository) as IntArray)
            for (index in 1 until 2048) prepareBuffer.invoke(initialRepository, index)
        }
        try {
            delegate.execute(callables)
        } finally {
            // The worker's first phase writes all movement bitcodes; subsequent phases only
            // write extended info or reset state. The ordinary shared table is correct again
            // from the next cycle onward, including after a pooled PlayerInfo is reused.
            for (tracked in initializing) {
                repositoryField.set(tracked.info, tracked.repository)
                tracked.initialRepository = null
            }
        }
    }

    private class TrackedPlayer(
        val player: Player,
        val info: PlayerInfo,
        val repository: Any,
        val positions: IntArray,
    ) {
        var initialRepository: Any? = null
    }
}
