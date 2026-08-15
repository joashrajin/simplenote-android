package com.automattic.simplenote

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountVerificationStatus
import com.automattic.simplenote.utils.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccountVerificationResumeCheck(
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
) {
    fun start(lifecycle: Lifecycle, email: String, onVerified: Runnable) {
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val status = try {
                    accountRepository.verificationStatus(email)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    AppLog.add(
                        AppLog.Type.SYNC,
                        "Unable to check account verification (${exception.javaClass.simpleName})",
                    )
                    null
                }

                if (isActive && status == AccountVerificationStatus.VERIFIED) {
                    onVerified.run()
                }
            }
        }
    }
}
