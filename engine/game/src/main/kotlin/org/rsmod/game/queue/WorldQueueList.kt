package org.rsmod.game.queue

public class WorldQueueList {
    private var activeSize: Int = 0
    private val deferred = mutableListOf<Queue>()

    public val size: Int
        get() = activeSize + deferred.size

    private val iterator = QueueIterator()

    private var first: Node? = null
    private var last: Node? = null

    public val isEmpty: Boolean
        get() = size == 0

    public val isNotEmpty: Boolean
        get() = size > 0

    public fun add(remainingCycles: Int, action: () -> Unit) {
        val queue = Queue(action, remainingCycles)
        val node = Node(queue, prev = last)
        add(node)
    }

    /** Starts counting on the next queue pass, including when called by another queue. */
    public fun addDeferred(remainingCycles: Int, action: () -> Unit) {
        require(remainingCycles > 0)
        deferred += Queue(action, remainingCycles)
    }

    private fun add(node: Node) {
        if (activeSize == 0) {
            check(first == null)
            check(last == null)
            first = node
        }
        last?.next = node
        last = node
        activeSize++
    }

    private fun remove(node: Node) {
        val prev = node.prev
        val next = node.next

        if (prev == null) {
            first = next
        } else {
            prev.next = next
        }

        if (next == null) {
            last = prev
        } else {
            next.prev = prev
        }

        check(activeSize > 0)
        activeSize--
    }

    override fun toString(): String = "WorldQueueList(size=$size, first=$first, last=$last)"

    public data class Node(val queue: Queue, var prev: Node?, var next: Node? = null)

    public data class Queue(val action: () -> Unit, var remainingCycles: Int)

    public fun iterator(): QueueIterator {
        for (queue in deferred) {
            add(queue.remainingCycles, queue.action)
        }
        deferred.clear()
        iterator.reset(first)
        return iterator
    }

    // We are assuming that world queues have the same "speed-up" mechanic as player and npc
    // queues.
    public inner class QueueIterator {
        private var next: Node? = null
        private var curr: Node? = null

        public fun hasNext(): Boolean = next != null

        public fun next(): Queue {
            val node = next ?: throw NoSuchElementException()
            curr = node
            next = node.next
            return node.queue
        }

        public fun remove() {
            val node = curr ?: error("`next` must be called before any `remove`.")
            remove(node)
        }

        public fun cleanUp() {
            this.next = null
            this.curr = null
        }

        internal fun reset(first: Node?) {
            this.next = first
            this.curr = null
        }
    }
}
