package org.rsmod.api.account.character.appearance

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.rsmod.game.entity.player.Appearance

/**
 * Keep CharacterAppearanceDto JSON keys backwards compatible. Persist player choices only;
 * transient costumes and derived equipment state must reset on login. Colours are unsigned
 * bytes; ident-kit -1 means an empty body slot.
 */
internal object CharacterAppearanceCodec {
    const val VERSION: Int = 1

    private val VALID_IDENT_KIT: IntRange = 0..65535

    private val VALID_COLOUR: IntRange = 0..255

    private val VALID_BODY_TYPE: IntRange = Appearance.BODY_TYPE_A..Appearance.BODY_TYPE_B

    private val VALID_PRONOUN: IntRange = Appearance.PRONOUN_HE..Appearance.PRONOUN_THEY

    fun encode(mapper: ObjectMapper, appearance: Appearance): String {
        val dto =
            CharacterAppearanceDto(
                version = VERSION,
                identKit = appearance.identKitSnapshot().map { it.toInt() },
                colours = appearance.coloursSnapshot().map { it.toInt() and 0xFF },
                bodyType = appearance.bodyType,
                pronoun = appearance.pronoun,
            )
        return mapper.writeValueAsString(dto)
    }

    fun decode(mapper: ObjectMapper, json: String): CharacterAppearanceDto? =
        mapper.readValue(json, CharacterAppearanceDto::class.java)

    /** Invalid stored values retain defaults; throwing here would terminate login. */
    fun applyTo(appearance: Appearance, stored: CharacterAppearanceDto) {
        val identKitSlots = appearance.identKitSnapshot().size
        for (slot in 0 until minOf(identKitSlots, stored.identKit.size)) {
            val identKit = stored.identKit[slot]
            if (identKit == Appearance.NO_IDENT_KIT || identKit in VALID_IDENT_KIT) {
                appearance.setIdentKit(slot, identKit)
            }
        }

        val colourSlots = appearance.coloursSnapshot().size
        for (slot in 0 until minOf(colourSlots, stored.colours.size)) {
            val colour = stored.colours[slot]
            if (colour in VALID_COLOUR) {
                appearance.setColour(slot, colour)
            }
        }

        if (stored.bodyType in VALID_BODY_TYPE) {
            appearance.bodyType = stored.bodyType
        }

        // Restore the pronoun cache before the first appearance block; the committed value also
        // persists in its varp.
        if (stored.pronoun in VALID_PRONOUN) {
            appearance.pronoun = stored.pronoun
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class CharacterAppearanceDto(
    @param:JsonProperty("v") val version: Int = CharacterAppearanceCodec.VERSION,
    @param:JsonProperty("k") val identKit: List<Int> = emptyList(),
    @param:JsonProperty("c") val colours: List<Int> = emptyList(),
    @param:JsonProperty("b") val bodyType: Int = Appearance.BODY_TYPE_A,
    @param:JsonProperty("p") val pronoun: Int = Appearance.PRONOUN_HE,
)
