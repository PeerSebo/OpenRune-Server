package org.rsmod.api.utils.system

import kotlin.concurrent.thread
import kotlin.system.exitProcess

public object SafeServiceExit {
    private const val TERMINATION_THREAD_NAME = "SystemReboot"

    /**
     * Exit from a separate daemon thread so JVM shutdown hooks cannot deadlock by waiting on
     * the initiating service worker.
     */
    public fun terminate(status: Int = 0) {
        thread(name = TERMINATION_THREAD_NAME, isDaemon = true) { exitProcess(status) }
    }
}
