package dev.openrune.codec.osrs.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import dev.openrune.definition.type.InventoryType
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import dev.openrune.types.InvStock
import dev.openrune.types.InventoryServerType
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InventoryServerCodecTest {
    private val codec = InventoryServerCodec()

    @Test
    fun `large stock quantities and intervals survive a complete codec roundtrip`() {
        val source =
            InventoryServerType(
                id = 42,
                size = 40,
                stack = InvStackType.Always,
                scope = InvScope.Shared,
                flags = InventoryServerType.RESTOCK_FLAG or InventoryServerType.ALL_STOCK_FLAG,
                stock = listOf(InvStock(1931, 50000, 60000), InvStock(1059, 10, -1)),
                name = "Thessalia's Fine Clothes.",
                buyMultiplier = 550,
                sellMultiplier = 1000,
                delta = 10,
            )
        assertEquals(source, roundtrip(source))
    }

    @Test
    fun `stock entry counts use the full unsigned byte range`() {
        for (count in listOf(0, 127, 128, 255)) {
            val source =
                InventoryServerType(
                    id = 5,
                    size = count.coerceAtLeast(1),
                    stock = List(count) { InvStock(it, 65535, 65534) },
                    name = "After the stock list",
                    buyMultiplier = 0,
                    sellMultiplier = 1300,
                    delta = 0,
                )
            assertEquals(source, roundtrip(source), "stock entries=$count")
        }
    }

    @Test
    fun `legacy definitions load unsigned stocks without needing pricing opcodes`() {
        val buffer = Unpooled.buffer()
        try {
            buffer.writeByte(1).writeShort(40)
            buffer.writeByte(5).writeByte(2)
            buffer.writeInt(1931).writeShort(50000).writeShort(60000)
            buffer.writeInt(1059).writeShort(10).writeShort(65535)
            buffer.writeByte(0)
            val restored = codec.loadData(4, buffer)
            assertEquals(40, restored.size)
            assertEquals(listOf(InvStock(1931, 50000, 60000), InvStock(1059, 10, -1)), restored.stock)
            assertEquals("", restored.name)
            assertEquals(-1, restored.buyMultiplier)
            assertEquals(-1, restored.sellMultiplier)
            assertEquals(-1, restored.delta)
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `custom shop metadata is preserved when combining native and server definitions`() {
        val custom =
            InventoryServerType(
                id = 4,
                size = 40,
                name = "Edgeville General Store",
                buyMultiplier = 400,
                sellMultiplier = 1300,
                delta = 30,
                stock = listOf(InvStock(1931, 5, 10)),
            )
        val combined = InventoryServerType(id = 4)
        InventoryServerCodec(mapOf(4 to InventoryType(id = 4, size = 40)), mapOf(4 to custom)).run {
            combined.createData()
        }
        assertEquals(custom, combined)
        assertEquals(custom, roundtrip(combined))
    }

    @Test
    fun `out of range stocks are rejected instead of silently wrapping`() {
        val invalid =
            listOf(
                InventoryServerType(stock = List(256) { InvStock(it, 1, 1) }),
                InventoryServerType(stock = listOf(InvStock(1, -1, 1))),
                InventoryServerType(stock = listOf(InvStock(1, 65536, 1))),
                InventoryServerType(stock = listOf(InvStock(1, 1, -2))),
                InventoryServerType(stock = listOf(InvStock(1, 1, 65535))),
            )
        for (source in invalid) {
            assertThrows(IllegalArgumentException::class.java) { codec.encodeToBuffer(source) }
        }
    }

    @Test
    fun `every shop catalog fits packed stock fields without losing values`() {
        val directory = Path.of(checkNotNull(System.getProperty("shop.catalogDir")))
        val mapper = ObjectMapper(TomlFactory())
        Files.list(directory).use { files ->
            files.filter { it.toString().endsWith(".toml") }.forEach { path ->
                for (inventory in mapper.readTree(path.toFile()).get("inventory")) {
                    val stock = inventory.get("stock")
                    val source =
                        InventoryServerType(
                            id = 1,
                            size = inventory.path("size").asInt(),
                            name = inventory.path("name").asText(""),
                            buyMultiplier = inventory.path("buyMultiplier").asInt(-1),
                            sellMultiplier = inventory.path("sellMultiplier").asInt(-1),
                            delta = inventory.path("delta").asInt(-1),
                            stock = stock.mapIndexed { index, line ->
                                InvStock(index, line.path("count").asInt(), line.path("restockCycles").asInt())
                            },
                        )
                    assertTrue(source.size >= source.stock.size, "$path capacity")
                    assertEquals(source, roundtrip(source), path.toString())
                }
            }
        }
    }

    private fun roundtrip(source: InventoryServerType): InventoryServerType =
        codec.loadData(source.id, codec.encodeToBuffer(source))
}
