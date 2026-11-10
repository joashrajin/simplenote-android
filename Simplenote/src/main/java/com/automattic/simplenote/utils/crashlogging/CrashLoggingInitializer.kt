package com.automattic.simplenote.utils.crashlogging

import com.automattic.android.tracks.crashlogging.CrashLogging
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes Sentry as part of building the singleton graph. DataModule.providesSimperium takes
 * this as an ordering-only dependency, so crash logging is live before the Simperium client and
 * buckets are constructed (the simperium-store SQLite open, BucketNameInvalid, the sync-time
 * preferences cast) — preserving the legacy order, where initialize() in onCreate preceded all
 * bucket work and so reported exactly those startup crashes. Being a singleton makes the
 * initialization run once no matter how many bindings request it.
 */
@Singleton
class CrashLoggingInitializer @Inject constructor(crashLogging: CrashLogging) {
    init {
        crashLogging.initialize()
    }
}
