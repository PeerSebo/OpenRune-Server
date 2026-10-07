package org.rsmod.server.services

/**
 * A lifecycle participant managed by [ServiceManager]. Startup completes before application work
 * begins. Shutdown releases resources even when startup failed partway through.
 *
 * Implementations must handle exceptions from any threads they create; the manager handles errors
 * from the lifecycle methods themselves.
 */
public interface Service {
    /** Shared resources remain available until all workers have finished their shutdown work. */
    public val shutdownPhase: ShutdownPhase
        get() = ShutdownPhase.WORKERS

    /**
     * Initializes resources in the lifecycle caller's coroutine. Startup waits for every service;
     * propagate failures so [ServiceManager] can clean up initialized services and report the cause.
     */
    public suspend fun startup()

    /**
     * Releases resources on shutdown, including after a failed [startup]. Called whenever [startup]
     * was invoked. Failures are reported by [ServiceManager] without skipping other services.
     */
    public suspend fun shutdown()

    public enum class ShutdownPhase {
        WORKERS,
        RESOURCES,
    }
}
