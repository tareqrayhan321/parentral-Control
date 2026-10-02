package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.database.dao.AppPolicyDao
import com.example.core.database.dao.ChildDeviceDao
import com.example.core.database.dao.DailyUsageDao
import com.example.core.database.dao.ScheduleDao
import com.example.core.database.dao.SyncAuditDao
import com.example.core.database.entity.AppPolicyEntity
import com.example.core.database.entity.ChildDeviceEntity
import com.example.core.database.entity.DailyUsageEntity
import com.example.core.database.entity.ScheduleEntity
import com.example.core.database.entity.SyncAuditEntity

@Database(
    entities = [
        ChildDeviceEntity::class,
        AppPolicyEntity::class,
        ScheduleEntity::class,
        DailyUsageEntity::class,
        SyncAuditEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ChildDatabase : RoomDatabase() {

    abstract fun childDeviceDao(): ChildDeviceDao
    abstract fun appPolicyDao(): AppPolicyDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun syncAuditDao(): SyncAuditDao

    companion object {
        private const val DATABASE_NAME = "parental_control_child.db"

        @Volatile
        private var INSTANCE: ChildDatabase? = null

        fun getInstance(context: Context): ChildDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChildDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
