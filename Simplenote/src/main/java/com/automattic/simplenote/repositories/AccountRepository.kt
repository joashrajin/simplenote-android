package com.automattic.simplenote.repositories

import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    /** Reads the locally stored verification object once, or returns null when it is missing. */
    suspend fun verificationStatus(email: String): AccountVerificationStatus?

    /**
     * Observes server-confirmed verification changes without emitting an initial local snapshot.
     *
     * The flow is cold, attaches a listener for each collector, and deduplicates states only within
     * that collection. Removing the verification object emits an unverified state immediately.
     * Other qualifying changes read the latest local object only while the callback-time account
     * remains active, so superseded, missing, unreadable, and stale-account snapshots are skipped
     * while later changes remain observable.
     */
    fun verificationStatusChanges(): Flow<AccountVerificationStatus>
}

enum class AccountVerificationStatus {
    SENT_EMAIL,
    UNVERIFIED,
    VERIFIED,
}
