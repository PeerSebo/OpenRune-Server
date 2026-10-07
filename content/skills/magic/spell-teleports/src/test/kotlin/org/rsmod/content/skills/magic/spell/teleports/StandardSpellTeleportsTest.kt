package org.rsmod.content.skills.magic.spell.teleports

import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.types.dbcol.DbHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.params
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class StandardSpellTeleportsTest {
    @Test
    fun `all fourteen rows preserve the frozen catalogue in order`() {
        val expected = LegacyStandardSpellTeleport.entries.map { row ->
            listOf(
                row.ordinal, row.spellObj.asRSCM(), row.destination, row.destinationLevel,
                row.alternate?.snapshot(), row.requiredQuest, row.lockedMessage,
                row.missingDestinationMessage,
            )
        }
        val actual = StandardSpellTeleports.entries.take(14).map { row ->
            listOf(
                row.order, row.spellObj.id, row.destination, row.destinationLevel,
                row.alternate?.snapshot(), row.requiredQuest, row.lockedMessage,
                row.missingDestinationMessage,
            )
        }
        assertEquals(14, actual.size)
        assertEquals(expected, actual)
    }

    @Test
    fun `every button option preserves its destination fallback level override and message`() {
        for ((old, current) in LegacyStandardSpellTeleport.entries.zip(StandardSpellTeleports.entries)) {
            val spell = checkNotNull(spells.getObjSpell(current.spellObj))
            for (op in IfButtonOp.entries) {
                val expectedOption = old.option(op)
                val actualOption = current.option(op)
                assertEquals(expectedOption.snapshot(), actualOption.snapshot(), "${old.name} $op")
                assertEquals(
                    old.destination(spell, expectedOption), current.destination(spell, actualOption),
                    "${old.name} $op",
                )
            }
        }
    }

    @Test
    fun `Varrock alternate remains Grand Exchange while the primary uses the native destination`() {
        val teleport = StandardSpellTeleports.entries.single { it.spellObj.id == "obj.25_varrock_teleport".asRSCM() }
        val spell = checkNotNull(spells.getObjSpell(teleport.spellObj))
        val nativeDestination = checkNotNull(spell.obj.paramOrNull(params.spell_telecoord))
        assertNull(teleport.destination)
        assertEquals(nativeDestination, teleport.destination(spell, teleport.option(IfButtonOp.Op1)))
        assertEquals(CoordGrid(3164, 3487, 0), teleport.destination(spell, teleport.option(IfButtonOp.Op2)))
        assertEquals(nativeDestination, teleport.destination(spell, teleport.option(IfButtonOp.Op3)))
    }

    @Test
    fun `Ape Atoll changes only the plane and Home retains its explicit tile`() {
        val apeAtoll = StandardSpellTeleports.apeAtoll
        val spell = checkNotNull(spells.getObjSpell(apeAtoll.spellObj))
        val nativeDestination = checkNotNull(spell.obj.paramOrNull(params.spell_telecoord))
        assertEquals(nativeDestination.copy(level = 1), apeAtoll.destination(spell, apeAtoll.option(IfButtonOp.Op1)))
        assertNull(apeAtoll.destination)

        val home = StandardSpellTeleports.home
        val homeSpell = checkNotNull(spells.getObjSpell(home.spellObj))
        assertEquals(CoordGrid(3222, 3218, 0), home.destination(homeSpell, home.option(IfButtonOp.Op1)))
    }

    @Test
    fun `all Ancient teleports resolve native destinations with a separate Edgeville Home`() {
        val ancients = StandardSpellTeleports.entries.filter { it.ancientStyle }
        assertEquals(9, ancients.size)
        assertEquals(listOf("obj.48_home_teleport", "obj.01_zaros_home_tele").map { it.asRSCM() },
            StandardSpellTeleports.entries.filter { it.isHome }.map { it.spellObj.id })
        for (teleport in ancients) {
            val spell = checkNotNull(spells.getObjSpell(teleport.spellObj))
            val expected = if (teleport.isHome) CoordGrid(3087, 3496, 0)
                else checkNotNull(spell.obj.paramOrNull(params.spell_telecoord))
            assertEquals(true, teleport.ancientStyle)
            assertEquals(expected, teleport.destination(spell, teleport.option(IfButtonOp.Op1)))
        }
    }

    @Test
    fun `Lunar and Arceuus additions use native spellbook rune and destination metadata`() {
        val additions = StandardSpellTeleports.entries.drop(23)
        assertEquals(19, additions.size)
        val books = additions.groupBy { checkNotNull(spells.getObjSpell(it.spellObj)).spellbook }
        assertEquals(8, books.getValue(Spellbook.Lunars).size)
        assertEquals(11, books.getValue(Spellbook.Arceuus).size)
        for (teleport in additions) {
            val spell = checkNotNull(spells.getObjSpell(teleport.spellObj))
            assertEquals(checkNotNull(spell.obj.paramOrNull(params.spell_telecoord)),
                teleport.destination(spell, teleport.option(IfButtonOp.Op1)))
            assertTrue(spell.objReqs.isNotEmpty(), spell.name)
        }
        val library = additions.single { it.spellObj.id == "obj.br_mithril_platebody".asRSCM() }
        assertEquals(9.5, checkNotNull(spells.getObjSpell(library.spellObj)).castXp)
    }

    @Test
    fun `packed columns use typed objects coordinates and distinguish absent optional values`() {
        val table = "dbtable.standard_spell_teleports".asRSCM()
        assertNotNull(ServerCacheManager.getDbtable(table))
        val rows = DbHelper.table("dbtable.standard_spell_teleports")
        assertEquals(42, rows.size)
        assertEquals(42, rows.distinctBy { it.id }.size)
        for (row in rows) {
            assertEquals(table, row.tableId)
            val teleport = StandardSpellTeleports.read(row)
            val columns = linkedMapOf(
                "order" to VarType.INT, "spell" to VarType.OBJ,
                "has_alternate" to VarType.BOOLEAN, "locked_message" to VarType.STRING,
                "missing_destination_message" to VarType.STRING,
                "is_home" to VarType.BOOLEAN, "ancient_style" to VarType.BOOLEAN,
            )
            if (teleport.destination != null) columns["destination"] = VarType.COORDGRID
            if (teleport.destinationLevel != null) columns["destination_level"] = VarType.INT
            if (teleport.requiredQuest != null) columns["required_quest"] = VarType.STRING
            if (teleport.alternate != null) columns["alternate_missing_destination_message"] = VarType.STRING
            if (teleport.alternate?.destination != null) columns["alternate_destination"] = VarType.COORDGRID
            if (teleport.alternate?.destinationLevel != null) columns["alternate_destination_level"] = VarType.INT
            for ((name, type) in columns) {
                val column = row.getColumn("dbcol.standard_spell_teleports:$name")
                assertEquals(listOf(type), column.types.toList(), name)
                assertEquals(1, column.size, name)
            }
            val raw = checkNotNull(ServerCacheManager.getDbrow(row.id))
            val expectedColumns = columns.keys.map { "dbcol.standard_spell_teleports:$it".asRSCM() and 0xFFFF }.toSet()
            assertEquals(expectedColumns, raw.definedColumns().keys)
        }
    }

    @Test
    fun `reader rejects missing order duplicate spell objects invalid planes and blank messages`() {
        val rows = StandardSpellTeleports.entries
        val first = rows.first()
        val invalid = listOf(
            emptyList(),
            rows.drop(1),
            listOf(first.copy(order = 1)) + rows.drop(1),
            listOf(first.copy(spellObj = rows[1].spellObj)) + rows.drop(1),
            listOf(first.copy(destinationLevel = 4)) + rows.drop(1),
            listOf(first.copy(alternate = TeleportOption(null, -1, "missing"))) + rows.drop(1),
            listOf(first.copy(lockedMessage = "")) + rows.drop(1),
            listOf(first.copy(missingDestinationMessage = "")) + rows.drop(1),
            listOf(first.copy(requiredQuest = "invalid quest key")) + rows.drop(1),
        )
        for ((index, catalogue) in invalid.withIndex()) {
            assertThrows(IllegalArgumentException::class.java, { StandardSpellTeleports.validate(catalogue) }, "Case $index")
        }
        assertEquals(rows, StandardSpellTeleports.validate(rows.reversed()))
    }

    private fun LegacyTeleportOption.snapshot() = listOf(destination, destinationLevel, missingDestinationMessage)

    private fun TeleportOption.snapshot() = listOf(destination, destinationLevel, missingDestinationMessage)

    companion object {
        private lateinit var spells: MagicSpellRegistry

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            spells = MagicSpellRegistry().also { registry ->
                registry.javaClass.declaredMethods.single { it.name.startsWith("init") && it.parameterCount == 0 }
                    .apply { isAccessible = true }.invoke(registry)
            }
        }
    }
}
