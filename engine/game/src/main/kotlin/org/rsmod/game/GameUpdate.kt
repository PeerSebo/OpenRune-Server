package org.rsmod.game

import kotlin.contracts.contract

public class GameUpdate {
    public var state: State? = null
        private set

    public fun reset() {
        state = null
    }

    public fun clear() {
        check(state != State.Updating) { "Game has already signaled for shutdown." }
        state = State.Clear
    }

    public fun startCountdown(cycles: Int) {
        require(cycles > 0) { "Countdown cycles must be greater than zero: $cycles" }
        check(state != State.Updating) { "Game has already signaled for shutdown." }
        state = State.Countdown(cycles)
    }

    public fun setUpdating() {
        state = State.Updating
    }

    public fun isUpdating(): Boolean = state == State.Updating

    public sealed class State {
        public data class Countdown(val start: Int, var current: Int = start) : State()

        public data object Clear : State()

        public data object Updating : State()
    }

    public companion object {
        public fun State?.isCountdown(): Boolean {
            contract { returns(true) implies (this@isCountdown is State.Countdown) }
            return this is State.Countdown
        }

        public fun State.isClear(): Boolean = this == State.Clear

        public fun State?.isUpdating(): Boolean = this == State.Updating
    }
}
