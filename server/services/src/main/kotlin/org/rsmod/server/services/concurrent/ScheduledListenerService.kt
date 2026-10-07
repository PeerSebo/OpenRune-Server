package org.rsmod.server.services.concurrent

public interface ScheduledListenerService : ScheduledService {
    /**
     * Runs after all services finish [startup], on the same dispatcher as [setup] and [run].
     * Propagate errors: a failure or timeout aborts startup and triggers service cleanup.
     */
    public suspend fun signalStartup()

    /**
     * Prepares shutdown before worker cancellation and [shutdown], on the same dispatcher as [setup]
     * and [run]. There is no parallel mutation by [run]. The manager awaits this with a timeout and
     * reports failures while continuing shutdown.
     */
    public suspend fun signalShutdown()
}
