package com.automattic.simplenote

import androidx.annotation.MainThread
import com.automattic.simplenote.repositories.AccountRepository
import com.automattic.simplenote.repositories.AccountVerificationUpdate
import com.automattic.simplenote.utils.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

class AccountVerificationCoordinator @JvmOverloads constructor(
    private val accountRepository: AccountRepository,
    private val accountEmailProvider: AccountEmailProvider,
    private val renderer: Renderer,
    private val dismissRenderer: DismissRenderer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val processNonce: String = UUID.randomUUID().toString(),
) {
    private var collectionJob: Job? = null
    private var dismissPending = false
    private var lastAcceptedUpdate: AccountVerificationUpdate? = null
    private var pendingPresentation: Presentation? = null
    private var presentationRevision = 0L
    private var sessionGeneration = 0

    @MainThread
    fun start() {
        if (collectionJob == null) {
            sessionGeneration++
            startCollection()
        }
    }

    @MainThread
    fun onAuthenticationSessionChanging() {
        sessionGeneration++
        dismissPending = true
        lastAcceptedUpdate = null
        pendingPresentation = null
        collectionJob?.cancel()
        startCollection()
        deliverPendingEffects()
    }

    @MainThread
    fun onHostAvailable() {
        if (collectionJob?.isActive != true) {
            startCollection()
        }
        deliverPendingEffects()
    }

    @MainThread
    fun isCurrentSession(email: String?, generation: Int): Boolean {
        val activeEmail = accountEmailProvider.getEmail()
        return generation == sessionGeneration && email != null && activeEmail != null &&
                email.equals(activeEmail, ignoreCase = true)
    }

    @MainThread
    fun isCurrentPresentation(email: String?, generation: Int, revision: Long, nonce: String?): Boolean =
        nonce == processNonce && revision == presentationRevision && isCurrentSession(email, generation)

    private fun startCollection() {
        val generation = sessionGeneration
        collectionJob = scope.launch {
            try {
                accountRepository.verificationStatusChanges().collect { update ->
                    if (generation != sessionGeneration || !isForActiveAccount(update)) {
                        return@collect
                    }
                    if (isDuplicateOfLastAcceptedUpdate(update)) {
                        return@collect
                    }

                    lastAcceptedUpdate = update
                    presentationRevision++
                    pendingPresentation = Presentation(update, generation, presentationRevision, processNonce)
                    deliverPendingEffects()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                AppLog.add(
                    AppLog.Type.SYNC,
                    "Unable to observe account verification (${exception.javaClass.simpleName})",
                )
            }
        }
    }

    private fun deliverPendingEffects() {
        if (dismissPending) {
            if (!dismissRenderer.dismiss()) {
                return
            }
            dismissPending = false
        }

        val presentation = pendingPresentation ?: return
        if (!isCurrentPresentation(
                presentation.update.email,
                presentation.sessionGeneration,
                presentation.revision,
                presentation.processNonce,
            )
        ) {
            pendingPresentation = null
            return
        }

        if (renderer.render(presentation)) {
            pendingPresentation = null
        }
    }

    private fun isForActiveAccount(update: AccountVerificationUpdate): Boolean {
        val activeEmail = accountEmailProvider.getEmail()
        return activeEmail != null && update.email.equals(activeEmail, ignoreCase = true)
    }

    private fun isDuplicateOfLastAcceptedUpdate(update: AccountVerificationUpdate): Boolean {
        val previous = lastAcceptedUpdate ?: return false
        return previous.status == update.status && previous.email.equals(update.email, ignoreCase = true)
    }

    fun interface AccountEmailProvider {
        fun getEmail(): String?
    }

    fun interface Renderer {
        fun render(presentation: Presentation): Boolean
    }

    fun interface DismissRenderer {
        fun dismiss(): Boolean
    }

    data class Presentation(
        val update: AccountVerificationUpdate,
        val sessionGeneration: Int,
        val revision: Long,
        val processNonce: String,
    )
}
