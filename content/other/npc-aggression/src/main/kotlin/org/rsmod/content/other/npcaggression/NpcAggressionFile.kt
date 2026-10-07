package org.rsmod.content.other.npcaggression

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

@JsonIgnoreProperties(ignoreUnknown = true)
data class NpcAggressionEntry(
    val id: String,
    val wiki: String? = null,
    val note: String? = null,
    val aggressive: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NpcAggressionFile(val npc: List<NpcAggressionEntry> = emptyList()) {
    companion object {
        const val GENERATED_RESOURCE: String = "npc-aggression.toml"

        const val OVERRIDES_RESOURCE: String = "npc-aggression-overrides.toml"

        private val mapper: ObjectMapper = ObjectMapper(TomlFactory()).registerKotlinModule()

        fun loadResource(name: String): NpcAggressionFile {
            val stream =
                NpcAggressionFile::class.java.classLoader.getResourceAsStream(name)
                    ?: error("Missing resource: $name")
            return stream.use { parse(it.readBytes().decodeToString()) }
        }

        fun parse(toml: String): NpcAggressionFile = mapper.readValue(toml)
    }
}
