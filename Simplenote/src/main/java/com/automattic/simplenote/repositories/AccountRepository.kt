package com.automattic.simplenote.repositories

import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    /** Reads the locally stored verification object once, or returns null when it is missing. */
    suspend fun verificationStatus(email: String): AccountVerificationStatus?

    /**
     * Observes server-confirmed verification changes without emitting an initial local snapshot.
     *
     * The flow is cold, attaches a listener for each collector, and deduplicates status only for
     * the same account within that collection. Every emitted update carries the callback-time
     * email and is emitted only while that account remains active. Removing the verification
     * object emits an account-keyed unverified update without reading the object. Superseded,
     * signed-out, missing, and unreadable snapshots are skipped while later changes remain
     * observable. Consumers must still compare the update email with the active account before
     * performing UI side effects and restart collection when the authentication session changes.
     */
    fun verificationStatusChanges(): Flow<AccountVerificationUpdate>
}

data class AccountVerificationUpdate(
    val email: String,
    val status: AccountVerificationStatus,
)

enum class AccountVerificationStatus {
    SENT_EMAIL,
    UNVERIFIED,
    VERIFIED,
}
