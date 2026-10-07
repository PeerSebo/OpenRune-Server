package org.rsmod.api.account.saver

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.rsmod.api.account.saver.request.AccountSaveRequest
import org.rsmod.game.entity.PlayerAccountCommit

public class AtomicAccountSave internal constructor(
    internal val requests: List<AccountSaveRequest>,
    internal val audit: Map<String, Any?>,
) : PlayerAccountCommit {
    internal val id: String = UUID.randomUUID().toString()
    private val result = AtomicReference<Boolean?>()
    override val succeeded: Boolean? get() = result.get()
    internal fun complete(success: Boolean) { result.compareAndSet(null, success) }

    override fun release() {
        check(succeeded == true) { "An unresolved joint save must remain fenced" }
        check(requests.all { it.player.pendingAccountCommit === this })
        for (request in requests) {
            request.player.pendingAccountCommit = null
        }
    }
}
