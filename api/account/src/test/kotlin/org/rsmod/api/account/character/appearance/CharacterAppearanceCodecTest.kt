package org.rsmod.api.account.character.appearance

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.player.Appearance

class CharacterAppearanceCodecTest {
    private val mapper: ObjectMapper =
        ObjectMapper(JsonFactory())
            .registerKotlinModule()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun `round trips ident kit colours bodytype and pronoun`() {
        val original = Appearance()
        original.setIdentKit(0, 3)
        original.setIdentKit(1, 15)
        original.setIdentKit(2, 108)
        original.setIdentKit(3, 27)
        original.setIdentKit(4, 34)
        original.setIdentKit(5, 37)
        original.setIdentKit(6, 43)
        original.setColour(0, 11)
        original.setColour(1, 22)
        original.setColour(2, 33)
        original.setColour(3, 44)
        original.setColour(4, 200)
        original.bodyType = Appearance.BODY_TYPE_B
        original.pronoun = Appearance.PRONOUN_THEY

        val restored = restore(decode(encode(original)))

        assertEquals(original.identKitSnapshot(), restored.identKitSnapshot())
        assertEquals(original.coloursSnapshot(), restored.coloursSnapshot())
        assertEquals(Appearance.BODY_TYPE_B, restored.bodyType)
        assertEquals(Appearance.PRONOUN_THEY, restored.pronoun)
    }

    /** Colours above 127 wrap in the backing `ByteArray`; they must still survive the document. */
    @Test
    fun `round trips an empty body type B jaw model`() {
        val original = Appearance().apply {
            bodyType = Appearance.BODY_TYPE_B
            setIdentKit(1, Appearance.NO_IDENT_KIT)
        }
        val restored = restore(decode(encode(original)))
        assertEquals(Appearance.BODY_TYPE_B, restored.bodyType)
        assertEquals(Appearance.NO_IDENT_KIT.toShort(), restored.identKitSnapshot()[1])
    }

    /** Colours above 127 wrap in the backing `ByteArray`; they must still survive the document. */
    @Test
    fun `round trips a colour above the signed byte range`() {
        val original = Appearance()
        original.setColour(4, 255)

        val decoded = decode(encode(original))

        assertEquals(255, decoded.colours[4])
        assertEquals(original.coloursSnapshot(), restore(decoded).coloursSnapshot())
    }

    @Test
    fun `does not persist transient scene flags`() {
        val original = Appearance()
        original.hideObjs = true

        val json = encode(original)
        val restored = restore(decode(json))

        assertEquals(false, restored.hideObjs)
        assertEquals(null, restored.transmog)
        assertEquals(false, json.contains("hide"))
        assertEquals(false, json.contains("transmog"))
    }

    @Test
    fun `ignores out of range and missing values`() {
        val restored =
            restore(
                CharacterAppearanceDto(
                    identKit = listOf(-2, 70000, 5),
                    colours = listOf(-4, 999, 7),
                    bodyType = 9,
                    pronoun = 9,
                )
            )

        val defaults = Appearance()
        assertEquals(defaults.identKitSnapshot()[0], restored.identKitSnapshot()[0])
        assertEquals(defaults.identKitSnapshot()[1], restored.identKitSnapshot()[1])
        assertEquals(5.toShort(), restored.identKitSnapshot()[2])
        assertEquals(defaults.coloursSnapshot()[0], restored.coloursSnapshot()[0])
        assertEquals(defaults.coloursSnapshot()[1], restored.coloursSnapshot()[1])
        assertEquals(7.toByte(), restored.coloursSnapshot()[2])
        assertEquals(defaults.bodyType, restored.bodyType)
        assertEquals(defaults.pronoun, restored.pronoun)
    }

    @Test
    fun `ignores trailing slots the appearance does not have`() {
        val restored =
            restore(CharacterAppearanceDto(identKit = List(32) { 1 }, colours = List(32) { 1 }))

        assertEquals(7, restored.identKitSnapshot().size)
        assertEquals(5, restored.coloursSnapshot().size)
    }

    private fun encode(appearance: Appearance): String =
        CharacterAppearanceCodec.encode(mapper, appearance)

    private fun decode(json: String): CharacterAppearanceDto =
        assertNotNull(CharacterAppearanceCodec.decode(mapper, json))

    private fun restore(dto: CharacterAppearanceDto): Appearance {
        val appearance = Appearance()
        CharacterAppearanceCodec.applyTo(appearance, dto)
        return appearance
    }
}
