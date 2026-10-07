package dev.openrune.types.dbcol

import dev.openrune.definition.util.CacheVarLiteral
import kotlin.getValue

public abstract class DbColumn<T, R> {
    internal var internalId: Int? = null
    internal var internalName: String? = null

    public val packed: Int
        get() = internalId ?: error("`internalId` must not be null.")

    public val table: Int
        get() = (packed shr 16) and 0xFFFF

    public val columnId: Int
        get() = packed and 0xFFFF

    public val name: String
        get() = internalName ?: error("`internalName` must not be null.")
}

public class DbValueColumn<T, R>(private val codec: DbColumnCodec<T, R>) : DbColumn<List<T>, R>() {
    public val types: List<CacheVarLiteral> by codec::types

    public fun decode(values: List<T>): R {
        val iterator = DbColumnCodec.Iterator(codec, values)
        return iterator.single()
    }

    public fun encode(value: R): List<T> {
        return codec.encode(value)
    }
}

public class DbListColumn<T, R>(private val codec: DbColumnCodec<T, R>) :
    DbColumn<List<T>, List<R>>() {
    public val types: List<CacheVarLiteral> by codec::types

    public fun decode(value: List<T>): List<R> {
        val iterator = DbColumnCodec.Iterator(codec, value)
        return iterator.toList()
    }

    public fun encode(value: R): List<T> {
        return codec.encode(value)
    }
}
