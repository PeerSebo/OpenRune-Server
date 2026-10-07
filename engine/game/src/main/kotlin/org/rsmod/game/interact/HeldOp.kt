package org.rsmod.game.interact

public enum class HeldOp(public val slot: Int) {
    Op1(1),
    Op2(2),
    Op3(3),
    Op4(4),
    Op5(5);

    public companion object {
        public operator fun get(slot: Int): HeldOp? =
            when (slot) {
                1 -> Op1
                2 -> Op2
                3 -> Op3
                4 -> Op4
                5 -> Op5
                else -> null
            }
    }
}
