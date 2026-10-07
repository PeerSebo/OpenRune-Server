package org.rsmod.content.minigames.gotr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

@JsonIgnoreProperties(ignoreUnknown = true)
public data class GotrSettings(
    @JsonProperty("is_enabled") val isEnabled: Boolean = true,
    @JsonProperty("portal_rotation_ticks") val portalRotationTicks: Int = 33,
    @JsonProperty("huge_remains_first_open_ticks") val hugeRemainsFirstOpenTicks: Int = 261,
    @JsonProperty("huge_remains_cadence_ticks") val hugeRemainsCadenceTicks: Int = 230,
    @JsonProperty("huge_remains_open_ticks") val hugeRemainsOpenTicks: Int = 44,
) {
    public companion object {
        private const val RESOURCE = "gotr.toml"

        private val mapper: ObjectMapper = ObjectMapper(TomlFactory()).registerKotlinModule()

        @Volatile private var cached: GotrSettings? = null

        public fun load(): GotrSettings {
            cached?.let {
                return it
            }
            val stream =
                GotrSettings::class.java.classLoader.getResourceAsStream(RESOURCE)
                    ?: return GotrSettings().also { cached = it }
            return stream.use { input -> mapper.readValue<GotrSettings>(input).also { cached = it } }
        }
    }
}
