package org.rsmod.api.attr

/**
 * Persistent values must avoid Float/Double: storage may decode them as integers. Use scaled
 * integers instead. Original author: Tom <rspsmods@gmail.com>.
 */
public class AttributeKey<T>(public val persistenceKey: String? = null, public val resetOnDeath: Boolean = false, public val temp: Boolean = false) {

    override fun equals(other: Any?): Boolean {
        if (other !is AttributeKey<*>) {
            return false
        }
        return if (persistenceKey != null) {
            other.persistenceKey == persistenceKey && other.resetOnDeath == resetOnDeath
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = persistenceKey?.hashCode() ?: super.hashCode()
}
