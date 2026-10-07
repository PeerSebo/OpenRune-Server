package org.rsmod.api.account.saver.request

/** Invoked asynchronously, outside the requesting thread; implementations must be thread-safe. */
public fun interface AccountSaveCallback {
    public operator fun invoke(response: AccountSaveResponse)
}
