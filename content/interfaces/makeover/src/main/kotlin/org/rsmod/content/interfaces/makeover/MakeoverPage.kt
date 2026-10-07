package org.rsmod.content.interfaces.makeover

internal enum class MakeoverPage(
    val varbitValue: Int,
    val navSub: Int,
    val identKitSlot: Int?,
    val colourSlot: Int?,
    val itemAreaEnd: Int,
    val colourEnd: Int?,
    val confirmMessage: String?,
) {
    Hair(0, 10, identKitSlot = 0, colourSlot = 0, 285, 60, "You get a fresh, new haircut!"),
    FacialHair(1, 12, identKitSlot = 1, colourSlot = 0, 75, 60, confirmMessage = null),

    Body(2, 14, identKitSlot = null, colourSlot = null, 0, null, confirmMessage = null),
    Top(3, 16, identKitSlot = 2, colourSlot = 1, 100, 58, "You get some neat new clothes!"),
    Arms(4, 18, identKitSlot = 3, colourSlot = 1, 85, 58, confirmMessage = null),
    Bottoms(5, 20, identKitSlot = 5, colourSlot = 2, 110, 58, confirmMessage = null),
    Feet(6, 22, identKitSlot = 6, colourSlot = 3, 10, 12, confirmMessage = null),
    Wrist(7, 24, identKitSlot = 4, colourSlot = null, 10, null, confirmMessage = null);

    val styleCount: Int
        get() = itemAreaEnd / STYLE_SUB_STEP

    val colourCount: Int
        get() = (colourEnd ?: 0) / COLOUR_SUB_STEP

    val interfaceName: String
        get() = if (this == Body) MAKEOVER_MAGE_INTERFACE else MAKEOVER_INTERFACE

    val selectAdvancesTo: MakeoverPage?
        get() = if (this == Top) Arms else null

    internal companion object {
        const val MAKEOVER_INTERFACE: String = "interface.makeover"

        const val MAKEOVER_MAGE_INTERFACE: String = "interface.makeover_mage"

        /** `If3Button(516, 7, sub=…)` counts in fives: index 39 arrives as `sub=195`. */
        const val STYLE_SUB_STEP: Int = 5

        /** `If3Button(516, 24, sub=…)` counts in twos: colour 26 arrives as `sub=52`. */
        const val COLOUR_SUB_STEP: Int = 2

        val NAV_RANGE: IntRange = 10..24

        val BUTTON_RANGE: IntRange = 0..10

        const val MAGE_SKIN_END: Int = 13

        fun fromNavSub(sub: Int): MakeoverPage? = entries.firstOrNull { it.navSub == sub }

        fun fromVarbit(value: Int): MakeoverPage? = entries.firstOrNull { it.varbitValue == value }
    }
}
