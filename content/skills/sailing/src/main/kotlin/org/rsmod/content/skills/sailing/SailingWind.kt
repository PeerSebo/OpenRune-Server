package org.rsmod.content.skills.sailing

internal class SailingWind {
    var untilOffer = OFFER_INTERVAL
        private set
    var window = 0
        private set

    fun tick(movingForward: Boolean): Boolean {
        if (!movingForward) return false
        if (window > 0) {
            window--
            if (window == 0) untilOffer = OFFER_INTERVAL
            return false
        }
        if (--untilOffer > 0) return false
        window = OFFER_WINDOW
        return true
    }

    fun trim(): Boolean {
        if (window == 0) return false
        window = 0
        untilOffer = OFFER_INTERVAL
        return true
    }

    fun reset() {
        window = 0
        untilOffer = OFFER_INTERVAL
    }

    companion object {
        const val OFFER_INTERVAL = 49

        const val OFFER_WINDOW = 14

        val TRIM_XP = listOf(10.5, 19.5, 30.0, 48.0, 64.0, 80.0, 96.0)
    }
}
