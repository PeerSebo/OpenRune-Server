package org.rsmod.content.other.commandbrowser

import dev.or2.central.account.Rights
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.rsmod.api.cheat.register
import org.rsmod.game.cheat.CheatCommandMap

class CommandCatalogTest {
    @Test
    fun `privilege search includes commands using the rights enforced by their handlers`() {
        val registry = CheatCommandMap()
        registry.register("heal") { desc = "Refill HP"; requiredRights = Rights.ADMINISTRATOR; cheat {} }
        registry.register("home") { desc = "Go home"; cheat {} }
        registry.register("mute") { desc = "Mute"; requiredRights = Rights.MOD; cheat {} }
        assertEquals("heal", CommandCatalog.page(registry, "ADMINISTRATOR", 0).entries.single().command)
        assertEquals("home", CommandCatalog.page(registry, "player", 0).entries.single().command)
        assertEquals("mute", CommandCatalog.page(registry, "moderator", 0).entries.single().command)
    }

    @Test
    fun `builder retains the privilege actually enforced by each command`() {
        val registry = CheatCommandMap()
        for ((name, rights) in listOf("public" to null, "player" to Rights.NONE,
            "moderator" to Rights.MOD, "administrator" to Rights.ADMINISTRATOR)) {
            registry.register(name) { desc = name; requiredRights = rights; cheat {} }
            assertEquals(rights, registry[name]?.requiredRights)
        }
        assertEquals(listOf("Administrator", "Moderator", "Player", "Player"),
            CommandCatalog.page(registry, "", 0).entries.map { it.privilege })
    }

    @Test
    fun `pagination exposes every registry key including aliases once without executing anything`() {
        val registry = CheatCommandMap()
        var executed = false
        repeat(73) { index -> registry.register("test${index.toString().padStart(2, '0')}") {
            desc = "Test command"; requiredRights = Rights.ADMINISTRATOR; cheat { executed = true }
        } }
        registry["alias"] = checkNotNull(registry["test00"])
        val first = CommandCatalog.page(registry, "", 0)
        val all = (0 until first.pages).flatMap { CommandCatalog.page(registry, "", it).entries }
        assertEquals(registry.commands.keys.sorted(), all.map { it.command })
        assertEquals(74, all.size)
        assertFalse(executed)
    }

    @Test
    fun `late registrations replacements and removals immediately appear with current rights`() {
        val registry = CheatCommandMap()
        registry.register("home") { desc = "Travel to Lumbridge"; cheat {} }
        assertEquals(1, CommandCatalog.page(registry, "", 0).total)
        registry.register("invis") { desc = "Staff invisibility"; requiredRights = Rights.ADMINISTRATOR; cheat {} }
        assertEquals(Rights.ADMINISTRATOR, CommandCatalog.page(registry, "INVIS", 0).entries.single().requiredRights)
        registry.put("home", checkNotNull(registry["invis"]))
        assertEquals("Administrator", CommandCatalog.page(registry, "::home", 0).entries.single().privilege)
        registry.commands.remove("invis")
        assertEquals(1, CommandCatalog.page(registry, "", Int.MAX_VALUE).total)
    }

    @Test
    fun `search matches descriptions case insensitively and empty results clamp navigation`() {
        val registry = CheatCommandMap()
        registry.register("home") { desc = "Travel to Lumbridge"; cheat {} }
        assertEquals("home", CommandCatalog.page(registry, "LUMBRIDGE", -5).entries.single().command)
        val empty = CommandCatalog.page(registry, "missing", Int.MAX_VALUE)
        assertEquals(0, empty.total)
        assertEquals(0, empty.index)
        assertEquals(1, empty.pages)
        assertEquals("(col=ff0000)text(/col)", CommandCatalog.plainText("<col=ff0000>text</col>"))
    }
}
