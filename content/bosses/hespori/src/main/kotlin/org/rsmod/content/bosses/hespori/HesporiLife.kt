package org.rsmod.content.bosses.hespori

/** Combat-only state, discarded on escape/death; the planted crop survives failed attempts. */
internal class HesporiLife {
    var wave: Int = 0
        private set
    private val open = mutableSetOf<Int>()
    var rootDeadline: Int? = null
        private set
    var rootClicks = 0
        private set
    var attacks = 0
    val immune: Boolean get() = open.isNotEmpty()

    fun openWave() { check(wave < 3); wave++; open.clear(); open.addAll(0..3) }
    fun closeFlower(index: Int): Boolean = open.remove(index)
    fun threshold(): Int? = when (wave) { 1 -> 200; 2 -> 100; else -> null }
    fun damage(hitpoints: Int, proposed: Int): Int {
        if (immune) return 0
        val threshold = threshold() ?: 0
        return proposed.coerceIn(0, (hitpoints - threshold).coerceAtLeast(0))
    }
    fun needsWave(hitpoints: Int): Boolean = !immune && threshold()?.let { hitpoints <= it } == true
    fun root(now: Int) { rootDeadline = now + 10; rootClicks = 0 }
    fun struggle(): Boolean {
        if (rootDeadline == null) return false
        rootClicks++
        if (rootClicks < 5) return false
        clearRoots()
        return true
    }
    fun clearRoots() { rootDeadline = null; rootClicks = 0 }
}
