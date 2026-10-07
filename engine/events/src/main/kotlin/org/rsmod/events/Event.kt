package org.rsmod.events

public interface UnboundEvent

public interface KeyedEvent {
    public val id: Long
}

public interface SuspendEvent<K> {
    public val id: Long
}
