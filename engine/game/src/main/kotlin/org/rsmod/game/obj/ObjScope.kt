package org.rsmod.game.obj

public enum class ObjScope(public val id: Int) {
    Temp(0),
    Perm(1),
    Private(2);

    public companion object {
        public operator fun get(id: Int): ObjScope =
            when (id) {
                Temp.id -> Temp
                Perm.id -> Perm
                Private.id -> Private
                else -> throw IllegalArgumentException("ObjScope with id `$id` does not exist.")
            }
    }
}
