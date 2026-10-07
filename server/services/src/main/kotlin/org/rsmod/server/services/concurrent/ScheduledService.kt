package org.rsmod.server.services.concurrent

import java.util.concurrent.ExecutorService
import org.rsmod.server.services.Service

/**
 * A service with one worker coroutine managed by [org.rsmod.server.services.ServiceManager].
 * [startup] and [shutdown] run on the lifecycle caller; [setup] runs once on the owned executor,
 * then the same coroutine repeatedly invokes [run].
 *
 * Work must suspend cooperatively to avoid a tight loop. Uncaught worker errors trigger application
 * shutdown and are reported to the caller. [createExecutor] must return a fresh, unshared executor
 * each time; the manager owns and closes it. Use [Service] with a separately managed executor when
 * concurrent worker tasks are required.
 */
public interface ScheduledService : Service {
    public suspend fun run()

    /**
     * Returns a fresh executor for [setup] and the [run] coroutine, normally a single-thread executor.
     * Never return a cached/shared instance: the manager shuts it down when this service stops.
     */
    public fun createExecutor(): ExecutorService

    public suspend fun setup()
}
