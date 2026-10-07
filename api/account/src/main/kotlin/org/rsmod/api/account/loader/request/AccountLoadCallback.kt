package org.rsmod.api.account.loader.request

/** Invoked asynchronously, outside the requesting thread; implementations must be thread-safe. */
public fun interface AccountLoadCallback {
    public operator fun invoke(response: AccountLoadResponse)
}
