package dev.openrune.tools

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerConfigOwnersTest {
    @Test
    fun `different tables may use the same numeric id`() {
        val owners = ServerConfigOwners()
        owners.claim("npc", 1, Path.of("npcs.toml"))
        owners.claim("object", 1, Path.of("locs.toml"))
        owners.claim("npc", 2, Path.of("npcs.toml"))
    }

    @Test
    fun `a second owner reports both sources and the resolved id`() {
        val owners = ServerConfigOwners()
        owners.claim("npc", 2215, Path.of("raw/npcs.toml"))
        val failure = assertThrows(IllegalArgumentException::class.java) {
            owners.claim("npc", 2215, Path.of("godwars/pack/npcs.toml"))
        }
        assertTrue(failure.message!!.contains("id=2215"))
        assertTrue(failure.message!!.contains(Path.of("raw/npcs.toml").toString()))
        assertTrue(failure.message!!.contains(Path.of("godwars/pack/npcs.toml").toString()))
    }

    @Test
    fun `duplicate records in the same file are rejected too`() {
        val owners = ServerConfigOwners()
        val source = Path.of("npcs.toml")
        owners.claim("npc", 2215, source)
        assertThrows(IllegalArgumentException::class.java) { owners.claim("npc", 2215, source) }
    }
}
