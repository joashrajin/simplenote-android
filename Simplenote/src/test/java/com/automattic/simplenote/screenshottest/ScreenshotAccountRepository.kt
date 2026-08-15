package com.automattic.simplenote.screenshottest

import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountVerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class ScreenshotAccountRepository : AccountRepository {
    override suspend fun verificationStatus(email: String): AccountVerificationStatus? = null

    override fun verificationStatusChanges(): Flow<AccountVerificationStatus> = emptyFlow()
}
