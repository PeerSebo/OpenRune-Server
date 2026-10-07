package org.rsmod.api.net.central.embed

import dev.or2.central.CentralApplication
import dev.or2.central.embed.OpenRuneCentralEmbeddedServer
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicReference

/**
 * Central 2.0.1 resolves its cleanup resources at ApplicationStopped, after Koin 4.0.2 has
 * closed their container. Run that exact existing handler at Koin's stop-preparing event instead.
 * Its own shutdownOnce makes the later ApplicationStopped invocation harmless.
 *
 * The public Central wrapper exposes no application or cleanup hook. This bridge reads only the
 * pinned wrapper/registration shape; it neither changes library fields nor emits lifecycle events.
 */
internal class CentralShutdownBridge private constructor(
    private val failure: AtomicReference<Throwable?>,
) {
    fun throwIfFailed() {
        failure.get()?.let { throw IllegalStateException("Embedded Central resource shutdown failed", it) }
    }

    companion object {
        fun install(server: OpenRuneCentralEmbeddedServer): CentralShutdownBridge {
            val type = CentralApplication::class.java
            val actual = type.getResourceAsStream("CentralApplication.class").use { input ->
                checkNotNull(input) { "Cannot inspect pinned Central shutdown implementation" }
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))
            }
            check(actual == "96683c6692f24e0a29ad3c52e90de0d0875a5621a856ba5827549355019b8022") {
                "Unsupported Central shutdown implementation; review CentralShutdownBridge before upgrading"
            }
            val delegate = field(server, "delegate", "dev.or2.central.embed.CentralEmbeddedServer")
            val engine = field(delegate, "server", "io.ktor.server.engine.EmbeddedServer")
            val application = engine.javaClass.getMethod("getApplication").invoke(engine)
            val monitor = engine.javaClass.getMethod("getMonitor").invoke(engine)
            check(monitor.javaClass.name == "io.ktor.events.Events")
            val stopped = event("io.ktor.server.application.DefaultApplicationEventsKt", "getApplicationStopped")
            val registrations = monitor.javaClass.getDeclaredField("handlers").apply { isAccessible = true }.get(monitor)
            check(registrations.javaClass.name == "io.ktor.util.collections.CopyOnWriteHashMap")
            val head = checkNotNull(registrations.javaClass.getMethod("get", Any::class.java).invoke(registrations, stopped)) {
                "Central did not register its shutdown handler"
            }
            check(head.javaClass.name == "io.ktor.util.internal.LockFreeLinkedListHead")
            val next = head.javaClass.getMethod("getNextNode")
            val matches = mutableListOf<Any>()
            var node = next.invoke(head)
            var visited = 0
            while (node !== head) {
                check(++visited <= 128) { "Unsupported Ktor event registration list" }
                check(node.javaClass.name == "io.ktor.events.Events\$HandlerRegistration")
                val handler = node.javaClass.getDeclaredField("handler").apply { isAccessible = true }.get(node)
                if (handler.javaClass.name.startsWith("dev.or2.central.CentralApplication\$")) matches += handler
                node = next.invoke(node)
            }
            check(matches.size == 1) { "Expected exactly one pinned Central shutdown handler; found ${matches.size}" }
            @Suppress("UNCHECKED_CAST")
            val original = matches.single() as (Any) -> Unit
            val failure = AtomicReference<Throwable?>()
            val preparing = event("org.koin.ktor.plugin.KoinApplicationEventsKt", "getKoinApplicationStopPreparing")
            val callback: (Any) -> Unit = {
                try {
                    original(application)
                } catch (cause: Throwable) {
                    failure.compareAndSet(null, cause)
                    throw cause
                }
            }
            monitor.javaClass.getMethod("subscribe", Class.forName("io.ktor.events.EventDefinition"), Function1::class.java)
                .invoke(monitor, preparing, callback)
            return CentralShutdownBridge(failure)
        }

        private fun field(owner: Any, name: String, expectedType: String): Any {
            val field = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
            check(field.type.name == expectedType) { "Unsupported Central field ${owner.javaClass.name}.$name" }
            return checkNotNull(field.get(owner)) { "Central has not initialized $name" }
        }

        private fun event(owner: String, getter: String): Any = Class.forName(owner).getMethod(getter).invoke(null)
    }
}
