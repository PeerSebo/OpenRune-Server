package dev.openrune.types.varp

public enum class VarpLifetime(public val id: Int) {
    Temp(0),
    Perm(1);

    public companion object {
        public operator fun get(id: Int): VarpLifetime? =
            when (id) {
                Temp.id -> Temp
                Perm.id -> Perm
                else -> null
            }
    }
}
