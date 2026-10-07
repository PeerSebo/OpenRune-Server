package org.rsmod.server.services

public interface ListenerService : Service {
    /**
     * Runs after every [Service.startup] completes. Startup awaits all listener signals; propagate
     * failures so [ServiceManager] can report them and clean up.
     */
    public suspend fun signalStartup()

    /**
     * Prepares shutdown before [Service.shutdown] runs. Failures are collected by [ServiceManager]
     * without preventing the remaining shutdown phases.
     */
    public suspend fun signalShutdown()
}
