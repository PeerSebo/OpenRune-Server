package org.rsmod.api.account.saver

import java.util.IdentityHashMap
import org.rsmod.api.account.saver.request.AccountSaveRequest

internal class AccountSaveQueue(private val capacity: Int) {
    private val pending = ArrayDeque<AccountSaveRequest>()
    private val active = IdentityHashMap<AccountSaveRequest, Session>()
    private val sessions = HashMap<Session, Int>()
    private val committed = HashMap<Session, Long>()
    private var reservations = 0

    init { require(capacity > 0) }

    @Synchronized fun reserve(): Boolean {
        if (active.size + reservations >= capacity) return false
        reservations++
        return true
    }

    @Synchronized fun cancelReservation() {
        check(reservations > 0)
        reservations--
    }

    @Synchronized fun addReserved(request: AccountSaveRequest) {
        check(reservations > 0)
        check(!active.containsKey(request)) { "Save request is already queued" }
        val session = Session(request.characterId, request.saveGeneration)
        reservations--
        active[request] = session
        sessions.merge(session, 1, Int::plus)
        pending.addLast(request)
    }

    @Synchronized fun retry(request: AccountSaveRequest) {
        check(active.containsKey(request))
        pending.addLast(request)
    }

    @Synchronized fun poll(): AccountSaveRequest? = pending.removeFirstOrNull()

    @Synchronized fun isEmpty(): Boolean = pending.isEmpty()

    @Synchronized fun snapshot(): List<AccountSaveRequest> = pending.toList()

    @Synchronized fun outstanding(): Int = active.size + reservations

    @Synchronized fun trackedSessions(): Int = sessions.size

    @Synchronized fun isCommitted(request: AccountSaveRequest): Boolean =
        request.sequence <= (committed[Session(request.characterId, request.saveGeneration)] ?: 0)

    @Synchronized fun markCommitted(request: AccountSaveRequest) {
        val session = checkNotNull(active[request])
        committed.merge(session, request.sequence, ::maxOf)
    }

    @Synchronized fun complete(request: AccountSaveRequest) {
        val session = active.remove(request) ?: return
        val remaining = checkNotNull(sessions[session]) - 1
        if (remaining == 0) {
            sessions.remove(session)
            committed.remove(session)
        } else {
            sessions[session] = remaining
        }
    }

    private data class Session(val characterId: Int, val generation: String?)
}
