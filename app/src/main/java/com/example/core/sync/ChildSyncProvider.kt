package com.example.core.sync

import android.content.Context
import com.example.child.admin.AndroidDeviceOwnerManager
import com.example.child.enforcement.DefaultPolicyEnforcementManager
import com.example.core.apps.AndroidInstalledAppsProvider
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.enrollment.DefaultEnrollmentManager

/** One process-wide ChildSyncController, shared by the foreground service, boot receiver and UI. */
object ChildSyncProvider {
    @Volatile private var instance: ChildSyncController? = null

    fun get(context: Context): ChildSyncController {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: build(app).also { instance = it }
        }
    }

    private fun build(app: Context): ChildSyncController {
        val repository = RoomPolicyRepository(ChildDatabase.getInstance(app))
        val deviceOwner = AndroidDeviceOwnerManager(app)
        val enforcement = DefaultPolicyEnforcementManager(app, repository)
        return ChildSyncController(
            context = app,
            gateway = FirebaseSyncGateway(app),
            repository = repository,
            installedApps = AndroidInstalledAppsProvider(app),
            enrollmentManager = DefaultEnrollmentManager(app, repository),
            isDeviceOwner = { deviceOwner.isDeviceOwner() },
            enforcePolicy = { enforcement.enforceCurrentPolicy() }
        )
    }
}
