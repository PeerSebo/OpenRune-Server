package org.rsmod.api.farming.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.farming.FarmingAttributes
import org.rsmod.api.farming.FarmingBinKey
import org.rsmod.api.farming.FarmingBinState
import org.rsmod.api.farming.FarmingBins
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchState
import org.rsmod.api.farming.FarmingPatches
import org.rsmod.game.entity.Player

internal class FarmingPreparedSaveTest {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val pipeline = FarmingPipeline(FarmingApplier(), mapper)

    @Test
    fun `journal decoder restores the exact prepared farming document`() {
        val document = loadedDocument()
        val prepared = pipeline.prepareSave(player(document))
        val encoded = mapper.writeValueAsBytes(prepared.recoveryData.getValue("farmingJson"))
        val restored = pipeline.restoreSave(mapper.readTree(encoded))
        val store = RecordingStore(fixture())
        restored.save(store.connection, 42)
        assertEquals(listOf(prepared.recoveryData.getValue("farmingJson")), store.writes.map { it.json })
    }

    @Test
    fun `prepared SQL keeps both original halves after mutable states collections and player attributes change`() {
        val document = loadedDocument()
        val player = player(document)
        val expected = FarmingCodec.encode(mapper, document.patches, document.bins)
        val store = RecordingStore(fixture())

        val prepared = pipeline.prepareSave(player)

        assertEquals(0, store.statements)
        for (state in document.patches.asMap().values) state.clearToEmpty()
        for (state in document.bins.asMap().values) state.clearToEmpty()
        document.patches.put(FarmingPatchKey("later_patch"), FarmingPatchState().apply { cropId = "ranarr" })
        document.bins.put(FarmingBinKey("later_bin"), FarmingBinState())
        player.attr.remove(FarmingAttributes.PATCHES)
        player.attr.remove(FarmingAttributes.BINS)

        prepared.save(store.connection, 42)
        prepared.save(store.connection, 42)

        assertEquals(2, store.writes.size)
        assertEquals(listOf(expected, expected), store.writes.map { it.json })
        assertTrue(store.writes.all { it.characterId == 42 && it.key == "farming_patches" })
        val saved = mapper.readTree(store.json)
        assertEquals(1, saved["v"].asInt())
        assertEquals(59, saved["p"].size())
        assertEquals(8, saved["b"].size())
        for ((index, patch) in saved["p"].withIndex()) {
            assertEquals("potato", patch["c"].asText())
            assertEquals(1, patch["s"].asInt())
            assertEquals(5_000_000_000L + index, patch["g"].asLong())
            assertTrue(patch["pp"].asBoolean())
        }
        assertEquals("OPEN", saved["b"].first()["ph"].asText())
        assertEquals(30, saved["b"].last()["n"].asInt())
        assertTrue(store.writes.all { it.sql.contains("ON CONFLICT (character_id, attr)") })
    }

    @Test
    fun `independent prepared saves retain their own progress and bin contents`() {
        val document = loadedDocument()
        val player = player(document)
        val patch = document.patches.asMap().values.first()
        val bin = document.bins.asMap().values.first()
        val first = pipeline.prepareSave(player)
        patch.stage = 2
        bin.count = 4
        val second = pipeline.prepareSave(player)
        patch.stage = 3
        bin.count = 1
        val store = RecordingStore(fixture())

        first.save(store.connection, 42)
        second.save(store.connection, 42)

        assertNotEquals(store.writes[0].json, store.writes[1].json)
        val earlier = mapper.readTree(store.writes[0].json)
        val later = mapper.readTree(store.writes[1].json)
        assertEquals(1, earlier["p"].first()["s"].asInt())
        assertEquals(15, earlier["b"].first()["n"].asInt())
        assertEquals(2, later["p"].first()["s"].asInt())
        assertEquals(4, later["b"].first()["n"].asInt())
    }

    @Test
    fun `each present half is captured while an absent half retains the previous empty array encoding`() {
        for (patchesPresent in listOf(false, true)) {
            val document = loadedDocument()
            val player = Player().apply {
                if (patchesPresent) attr[FarmingAttributes.PATCHES] = document.patches
                else attr[FarmingAttributes.BINS] = document.bins
            }
            val prepared = pipeline.prepareSave(player)
            player.attr[FarmingAttributes.PATCHES] = document.patches
            player.attr[FarmingAttributes.BINS] = document.bins
            val store = RecordingStore(fixture())

            prepared.save(store.connection, 42)

            assertEquals(1, store.writes.size)
            val saved = mapper.readTree(store.json)
            assertEquals(1, saved["v"].asInt())
            assertEquals(if (patchesPresent) 59 else 0, saved["p"].size())
            assertEquals(if (patchesPresent) 0 else 8, saved["b"].size())
        }
    }

    @Test
    fun `no live halves at preparation leaves the existing document untouched even if state appears later`() {
        val previous = fixture()
        val store = RecordingStore(previous)
        val player = Player()

        val prepared = pipeline.prepareSave(player)

        val document = loadedDocument()
        player.attr[FarmingAttributes.PATCHES] = document.patches
        player.attr[FarmingAttributes.BINS] = document.bins
        prepared.save(store.connection, 42)
        assertEquals(0, store.statements)
        assertTrue(store.writes.isEmpty())
        assertEquals(previous, store.json)
    }

    @Test
    fun `initialized empty state overwrites old farming progress with an explicit version one empty document`() {
        val player = Player().apply {
            attr[FarmingAttributes.PATCHES] = FarmingPatches()
            attr[FarmingAttributes.BINS] = FarmingBins()
        }
        val prepared = pipeline.prepareSave(player)
        val store = RecordingStore(fixture())

        prepared.save(store.connection, 42)

        assertEquals(1, store.writes.size)
        assertEquals(mapper.readTree("""{"v":1,"p":[],"b":[]}"""), mapper.readTree(store.json))
        assertEquals("farming_patches", store.writes.single().key)
    }

    private fun player(document: FarmingDocument) = Player().apply {
        attr[FarmingAttributes.PATCHES] = document.patches
        attr[FarmingAttributes.BINS] = document.bins
    }

    private fun loadedDocument() = assertNotNull(FarmingCodec.decode(mapper, fixture()))

    private fun fixture() = checkNotNull(javaClass.getResourceAsStream("/farming-world-save-v1.json"))
        .bufferedReader().use { it.readText() }

    private data class Write(val sql: String, val characterId: Int, val key: String, val json: String)

    private class RecordingStore(var json: String) {
        val writes = mutableListOf<Write>()
        var statements = 0
            private set
        val connection = DatabaseConnection(proxy<Connection> { method, args ->
            when (method) {
                "prepareStatement" -> statement(args[0] as String)
                "close" -> null
                else -> error("Unexpected connection call $method")
            }
        })

        private fun statement(sql: String): PreparedStatement {
            statements++
            val parameters = mutableMapOf<Int, Any>()
            return proxy { method, args ->
                when (method) {
                    "setInt", "setString" -> {
                        parameters[args[0] as Int] = args[1]
                        null
                    }
                    "executeUpdate" -> {
                        val write = Write(sql, parameters.getValue(1) as Int, parameters.getValue(2) as String, parameters.getValue(3) as String)
                        writes += write
                        json = write.json
                        1
                    }
                    "close" -> null
                    else -> error("Unexpected statement call $method")
                }
            }
        }

        private inline fun <reified T> proxy(crossinline invoke: (String, Array<out Any>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                invoke(method.name, args ?: emptyArray())
            } as T
    }
}
