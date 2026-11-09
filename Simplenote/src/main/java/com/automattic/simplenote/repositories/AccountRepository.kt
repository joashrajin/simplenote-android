package com.automattic.simplenote.repositories

import com.simperium.client.User
import kotlinx.coroutines.flow.Flow

/** The session surface SessionManager used to expose; unchanged apart from its new home. */
sealed class UserSession {
    object UnauthorizedUser : UserSession()
    data class AuthorizedUser(val user: User) : UserSession()
}

/**
 * The account's email-verification state. [Unverified.hasSentEmail] carries the legacy
 * AccountVerificationWatcher SENT_EMAIL/UNVERIFIED split; [Unknown] is the one-shot read's
 * result when the account object or the session email is not available to decide from.
 */
sealed class AccountStatus {
    object Verified : AccountStatus()
    data class Unverified(val hasSentEmail: Boolean) : AccountStatus()
    object Unknown : AccountStatus()
}

interface AccountRepository {
    fun session(): UserSession

    fun isLoggedIn(): Boolean

    fun userEmail(): String?

    /**
     * Authorizes the Simperium user with an already-obtained token and persists the session the
     * way Simplenote.loginWithToken did. Runs its body on the main dispatcher under
     * NonCancellable: entered from the main thread it completes before control returns to an
     * eager caller (see AccountStreams.logInWithToken), and once begun it always finishes.
     */
    suspend fun loginWithToken(email: String?, token: String?)

    /**
     * The data core of logout: Simperium deauth, reset and stop of all four buckets in the
     * legacy order, and the WordPress-token and per-account preference clears. Runs on the main
     * dispatcher under NonCancellable, so a logout that has begun can never be abandoned
     * half-way. UI side effects (analytics reset, passcode clear, widget refresh) stay with the
     * caller.
     */
    suspend fun logout()

    /**
     * Verification-status updates derived from the account bucket's network changes: the
     * replacement for AccountVerificationWatcher. Indeterminate changes (wrong change type, no
     * session email, missing account object) emit nothing, consecutive duplicates are
     * de-duplicated, and the stream is conflated to the latest state. Never emits [AccountStatus.Unknown].
     */
    fun accountChanges(): Flow<AccountStatus>

    /** One-shot read of the verification state ReviewAccountVerifyEmailFragment polls. */
    suspend fun verificationStatus(): AccountStatus
}
