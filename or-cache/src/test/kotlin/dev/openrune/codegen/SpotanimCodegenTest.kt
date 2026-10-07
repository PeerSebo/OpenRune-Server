package dev.openrune.codegen

import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import dev.openrune.definition.type.EnumType
import dev.openrune.definition.type.SpotAnimType
import dev.openrune.definition.util.VarType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.SpotCodec
import dev.openrune.types.enums.EnumTypeMap
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SpotanimCodegenTest {
    @TempDir lateinit var output: Path

    @Test
    fun `table spotanim property type matches the DB decoder and scalar reader`() {
        val decoded = DbColumnCodec.Iterator(SpotCodec, listOf(123)).single()
        val type = kotlinTypeForVarType(VarType.SPOTANIM, nullable = false)
        assertEquals(123, decoded.id)
        assertEquals(decoded::class.asTypeName(), type)
        assertEquals(type.copy(nullable = true), kotlinTypeForVarType(VarType.SPOTANIM, nullable = true))
        assertEquals(SpotCodec::class.asClassName(), codecClassForVarType(VarType.SPOTANIM))
        val readerName = scalarDbHelperReaders(VarType.SPOTANIM).first
        val reader = Class.forName("dev.openrune.types.dbcol.DbHelperColumnsKt")
            .declaredMethods.single { it.name == readerName }
        assertEquals(decoded.javaClass, reader.returnType)
    }

    @Test
    fun `generated scalar optional list and mixed slot columns use the DB spotanim type`() {
        val columns = listOf(
            column("required_spot"),
            column("optional_spot", optional = true),
            column("spots", count = 3),
            column("optional_spots", optional = true, count = 3),
            TableColumn(
                name = "dbcol.codegen_spots:mixed", simpleName = "mixed",
                varTypes = linkedMapOf(0 to VarType.INT, 1 to VarType.SPOTANIM), maxValues = 1,
            ),
        )
        generateTable(
            TableDef("codegen_spots", "SpotFixtureRow", columns, sourceTableId = 0),
            GeneratedFileTracker(output.toFile()), emptyMap(), emptyMap(), emptyMap(),
        )
        val source = output.resolve("org/rsmod/api/table/SpotFixtureRow.kt").toFile().readText()
            .replace(Regex("\\s+"), " ")
        assertTrue(source.contains("import ${SpotAnimType::class.qualifiedName}"), source)
        assertFalse(source.contains(SpotanimType::class.qualifiedName!!), source)
        val compactSource = source.replace(" ", "")
        for (property in listOf(
            "requiredSpot: SpotAnimType = row.spot(\"dbcol.codegen_spots:required_spot\")",
            "optionalSpot: SpotAnimType? = row.columnOptional(\"dbcol.codegen_spots:optional_spot\", DbColumnCodec.SpotCodec)",
            "spots: List<SpotAnimType> = row.list(\"dbcol.codegen_spots:spots\", DbColumnCodec.SpotCodec)",
            "optionalSpots: List<SpotAnimType> = row.slotsOptional(\"dbcol.codegen_spots:optional_spots\", DbColumnCodec.SpotCodec)",
            "mixed: Tuple2<Int, SpotAnimType>",
        )) {
            assertTrue(compactSource.contains(property.replace(" ", "")), "Missing generated property: $property\n$source")
        }
    }

    @Test
    fun `enum spotanim types retain the cache literal decoder contract including nested enums`() {
        val definition = EnumType(
            id = 1, keyType = VarType.INT, valueType = VarType.SPOTANIM,
            values = mutableMapOf(0 to 123),
        )
        val mapType = EnumTypeMap::class.asClassName()
        val generated = kotlinTypePairForEnumDefinition(definition, mapOf(1 to definition), mapType)
        val decoded = EnumTypeMap<Int, SpotanimType>(definition).getValue(0)
        assertEquals(123, decoded.id)
        assertEquals(INT, generated.first)
        assertEquals(decoded::class.asTypeName(), generated.second)

        val host = EnumType(
            id = 2, keyType = VarType.INT, valueType = VarType.ENUM,
            values = mutableMapOf(0 to 1),
        )
        val nested = kotlinTypePairForEnumDefinition(host, mapOf(1 to definition, 2 to host), mapType)
        assertEquals(mapType.parameterizedBy(INT, generated.second), nested.second)
    }

    private fun column(name: String, optional: Boolean = false, count: Int = 1) = TableColumn(
        name = "dbcol.codegen_spots:$name", simpleName = name,
        varTypes = mapOf(0 to VarType.SPOTANIM), optional = optional, maxValues = count,
    )
}
