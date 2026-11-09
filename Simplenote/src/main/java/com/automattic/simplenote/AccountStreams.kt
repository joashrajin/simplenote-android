package com.automattic.simplenote

import android.content.Context
import android.util.Log
import androidx.core.util.Consumer
import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The coroutine seam between the Java auth entry points — Simplenote, AuthUtils, WordPressUtils,
 * SimplenoteAuthenticationActivity, ReviewAccountVerifyEmailFragment — and [AccountRepository].
 *
 * [logInWithToken] and [logOut] bridge callers that were synchronous on the main thread to the
 * suspend repository with the eager Main.immediate shape: launched from the main thread the
 * coroutine starts undispatched, the repository's `withContext(NonCancellable + Main.immediate)`
 * continues inline, and the body has no suspension points, so the whole data sequence completes
 * before the bridge returns — the legacy synchronous guarantee. A caller arriving off the main
 * thread (none exists today) would dispatch to the main looper instead, with NonCancellable
 * still guaranteeing the begun sequence runs to completion exactly once.
 */
object AccountStreams {

    @JvmStatic
    fun accountRepository(context: Context): AccountRepository = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AccountRepositoryEntryPoint::class.java,
    ).accountRepository()

    @JvmStatic
    fun logInWithToken(repository: AccountRepository, email: String?, token: String?) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            repository.loginWithToken(email, token)
        }
    }

    @JvmStatic
    fun logOut(repository: AccountRepository) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            repository.logout()
        }
    }

    /**
     * Collects [AccountRepository.accountChanges] for the life of the process, replacing the
     * never-removed AccountVerificationWatcher bucket listener. The collection runs on the main
     * thread, so [onStatus] may touch fragments — the legacy listener fired on Simperium's
     * network callback thread.
     */
    @JvmStatic
    fun watchAccountStatus(repository: AccountRepository, onStatus: Consumer<AccountStatus>) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            repository.accountChanges().collect { status ->
                try {
                    onStatus.accept(status)
                } catch (exception: Exception) {
                    // Bucket.notifyListeners swallowed per-listener exceptions; a throwing
                    // consumer must not kill the process-lifetime watcher either.
                    Log.e(Simplenote.TAG, "Error handling account status $status", exception)
                }
            }
        }
    }

    /** One-shot [AccountRepository.verificationStatus] read delivered on the caller's scope. */
    @JvmStatic
    fun verificationStatus(scope: CoroutineScope, repository: AccountRepository, onResult: Consumer<AccountStatus>) {
        scope.launch {
            onResult.accept(repository.verificationStatus())
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountRepositoryEntryPoint {
    fun accountRepository(): AccountRepository
}
