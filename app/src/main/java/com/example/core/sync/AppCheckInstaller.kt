package com.example.core.sync

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Installs the App Check provider. Called from FirebaseSyncGateway so it also happens when the
 * process was started by the foreground service or the boot receiver (no Activity involved);
 * otherwise those requests would carry no App Check token once enforcement is switched on.
 * Debug builds use the debug provider; release builds use Play Integrity. Safe to call repeatedly.
 */
object AppCheckInstaller {
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        if (FirebaseApp.getApps(context).isEmpty()) return   // Firebase intentionally not configured
        runCatching {
            val factory = if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
            installed = true
        }.onFailure { Log.w("AppCheckInstaller", "Firebase App Check could not be initialized", it) }
    }
}
