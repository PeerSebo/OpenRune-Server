package org.rsmod.content.other.xprates

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

@JsonIgnoreProperties(ignoreUnknown = true)
data class XpRateConfig(
    val enabled: Boolean = false,
    val rate: Double = AUTHENTIC_RATE,
    @JsonProperty("global-rate") val globalRate: Double = AUTHENTIC_RATE,
    @JsonProperty("enforce-on-login") val enforceOnLogin: Boolean = true,
    @JsonProperty("reset-existing-levels") val resetExistingLevels: Boolean = false,
) {
    fun sanitized(): XpRateConfig =
        copy(rate = clampRate(rate), globalRate = clampRate(globalRate))

    companion object {
        const val AUTHENTIC_RATE: Double = 1.0

        const val MIN_RATE: Double = 0.01
        const val MAX_RATE: Double = 10_000.0

        fun clampRate(rate: Double): Double =
            if (rate.isFinite()) rate.coerceIn(MIN_RATE, MAX_RATE) else AUTHENTIC_RATE
    }
}

class XpRateConfigLoader internal constructor(private val defaultFile: Path) {
    @Inject constructor() : this(DEFAULT_FILE)

    private val logger = InlineLogger()

    fun loadOrCreate(file: Path = defaultFile): XpRateConfig {
        if (!file.exists()) {
            file.writeText(DEFAULT_FILE_CONTENTS)
            logger.info { "Created default xp rate config in file: $file" }
            return XpRateConfig()
        }
        return load(file)
    }

    fun load(file: Path): XpRateConfig {
        val parsed =
            runCatching { yamlMapper.readValue(file.toFile(), XpRateConfig::class.java) }
                .getOrElse { error ->
                    logger.warn(error) {
                        "Could not read '$file'; optional XP overrides are disabled. " +
                            "game.yml and realm rates remain active."
                    }
                    return XpRateConfig()
                }
        val sanitized = parsed.sanitized()
        if (sanitized != parsed) {
            logger.warn {
                "Clamped out-of-range xp rates in '$file': $parsed -> $sanitized " +
                    "(valid range: ${XpRateConfig.MIN_RATE}-${XpRateConfig.MAX_RATE})"
            }
        }
        return sanitized
    }

    companion object {
        val DEFAULT_FILE: Path = Paths.get("./", "xp-rates.yml")

        private val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        private val DEFAULT_FILE_CONTENTS =
            """
            # Experience rates.
            # Existing game.yml gameplay.xp-rate and realm event rates stay authoritative until
            # this optional override is explicitly enabled. Installing the plugin changes no XP.
            enabled: false
            #
            # 1.0 is authentic Old School RuneScape. Every skill keeps the xp values Jagex uses -
            # 4 xp per damage in combat, 25 xp per willow log, and so on - and this multiplier is
            # applied on top of them.
            #
            #   https://oldschool.runescape.wiki/w/Experience
            #   https://oldschool.runescape.wiki/w/Skills
            #
            # `rate` is each player's personal multiplier. It is stored on the character, so it can
            # also be set per player in game with `::setxprate <rate> [player]`.
            rate: 1.0

            # `global-rate` is compounded on top of `rate` and applies to everyone. It is meant for
            # temporary events like a double xp weekend - set it to 2.0, then back to 1.0 after.
            global-rate: 1.0

            # Re-apply `rate` to every character as they log in, replacing the rate stored on their
            # account. Leave this on to keep the whole server at `rate`; turn it off if you want
            # `::setxprate` to grant lasting per-player rates.
            enforce-on-login: true

            # Wipe levels that were earned at an old, inflated rate.
            #
            # When this is on, each character's skills are reset to fresh-account values (all 1,
            # Hitpoints 10) the first time they log in. Items, bank, quests and location are not
            # touched. Each character is flagged once it has been reset, so this fires once per
            # character and is safe to leave on afterwards.
            #
            # THIS IS DESTRUCTIVE AND CANNOT BE UNDONE. Turn it on deliberately, once, after you
            # have settled on `rate` above. `::resetxp [player]` and `::resetxpall` do the same
            # thing on demand if you would rather pick who and when.
            reset-existing-levels: false
            """
                .trimIndent() + "\n"
    }
}
