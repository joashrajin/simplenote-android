package com.automattic.simplenote.utils

interface AccountVerificationEmailHandler {
    fun onSuccess(url: String)
    fun onFailure(e: Exception, url: String)
}
