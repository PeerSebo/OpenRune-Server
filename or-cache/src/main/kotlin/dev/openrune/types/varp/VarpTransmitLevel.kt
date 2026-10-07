package dev.openrune.types.varp

public enum class VarpTransmitLevel(public val id: Int) {
    Never(0),
    OnSetDifferent(1),
    OnSetAlways(2);

    public val never: Boolean
        get() = this == Never

    public val always: Boolean
        get() = this == OnSetAlways

    public val onDiff: Boolean
        get() = this == OnSetDifferent

    public companion object {
        public operator fun get(id: Int): VarpTransmitLevel? =
            when (id) {
                Never.id -> Never
                OnSetDifferent.id -> OnSetDifferent
                OnSetAlways.id -> OnSetAlways
                else -> null
            }
    }
}
