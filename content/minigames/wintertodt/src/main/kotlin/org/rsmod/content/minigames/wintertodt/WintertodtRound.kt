package org.rsmod.content.minigames.wintertodt

internal enum class WinterPhase { Waiting, Fighting }
internal enum class BrazierState(val hud: Int, val loc: String) {
    Unlit(0, "loc.wint_brazier"), Lit(1, "loc.wint_brazier_lit"), Broken(2, "loc.wint_brazier_broken"),
}
internal class WinterCamp {
    var state = BrazierState.Unlit
    var health = 14
    fun reset() { state = BrazierState.Unlit; health = 14 }
    fun light(): Boolean {
        if (state != BrazierState.Unlit || health == 0) return false
        state = BrazierState.Lit
        return true
    }
    fun repair(): Boolean {
        if (state != BrazierState.Broken) return false
        state = BrazierState.Unlit
        return true
    }
    fun heal(): Boolean {
        if (health == 14) return false
        health = 14
        return true
    }
}
internal class WintertodtRound(private val rules: WinterRules) {
    val camps = List(4) { WinterCamp() }
    var phase = WinterPhase.Waiting
        private set
    var id = 0
        private set
    var energy = 0
        private set
    var deadline = 0
        private set
    var pyroFell = false
    var allBroken = false
    fun initialise(tick: Int) { deadline = tick + rules.respawnTicks }
    fun begin(tick: Int) {
        id++
        phase = WinterPhase.Fighting
        energy = rules.maxEnergy
        deadline = tick
        pyroFell = false
        allBroken = false
        camps.forEach(WinterCamp::reset)
    }
    fun advance(): Boolean {
        if (phase != WinterPhase.Fighting) return false
        energy = (energy - camps.count { it.state == BrazierState.Lit && it.health > 0 } *
            rules.energyDrain).coerceAtLeast(0)
        return energy == 0
    }
    fun finish(tick: Int) {
        phase = WinterPhase.Waiting
        energy = 0
        deadline = tick + rules.respawnTicks
        camps.forEach(WinterCamp::reset)
    }
    fun extinguish(index: Int, broken: Boolean) {
        camps[index].state = if (broken) BrazierState.Broken else BrazierState.Unlit
        if (camps.all { it.state == BrazierState.Broken }) allBroken = true
    }
    fun hurtPyro(index: Int) {
        camps[index].health = (camps[index].health - 7).coerceAtLeast(0)
        if (camps[index].health == 0) pyroFell = true
    }
    fun hud(points: Int) = arrayOf(points, energy,
        *camps.map { if (it.health > 0) 1 else 0 }.toTypedArray(),
        *camps.map { it.state.hud }.toTypedArray())
}
