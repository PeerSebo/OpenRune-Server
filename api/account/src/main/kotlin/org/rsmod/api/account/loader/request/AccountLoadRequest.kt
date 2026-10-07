package org.rsmod.api.account.loader.request

public sealed class AccountLoadRequest {
    public abstract val auth: AccountLoadAuth
    public abstract val accountName: String
    public abstract val callback: AccountLoadCallback

    /** Read-only requests may run concurrently; do not use this request type for writes. */
    public sealed class ReadOnly : AccountLoadRequest()

    public data class StrictSearch(
        override val auth: AccountLoadAuth,
        override val accountName: String,
        override val callback: AccountLoadCallback,
    ) : WriteRequired()

    /** Writes must use this request type to avoid concurrent read-only dispatch. */
    public sealed class WriteRequired : AccountLoadRequest()

    public data class SearchOrCreateWithPassword(
        public val hashedPassword: () -> String,
        override val auth: AccountLoadAuth,
        override val accountName: String,
        override val callback: AccountLoadCallback,
    ) : WriteRequired()
}
