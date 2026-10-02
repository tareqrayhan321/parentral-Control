package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.AppPolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPolicyDao {

    @Query("SELECT * FROM app_policy ORDER BY displayName ASC")
    fun observeAllPolicies(): Flow<List<AppPolicyEntity>>

    @Query("SELECT * FROM app_policy")
    suspend fun getAllPolicies(): List<AppPolicyEntity>

    @Query("SELECT * FROM app_policy WHERE packageName = :packageName LIMIT 1")
    suspend fun getPolicyForPackage(packageName: String): AppPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(policy: AppPolicyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(policies: List<AppPolicyEntity>)

    @Query("UPDATE app_policy SET isSuspended = :isSuspended WHERE packageName = :packageName")
    suspend fun updateSuspensionStatus(packageName: String, isSuspended: Boolean)

    @Query("DELETE FROM app_policy WHERE packageName NOT IN (:keepPackageNames)")
    suspend fun deleteOrphanedPolicies(keepPackageNames: List<String>)

    @Query("DELETE FROM app_policy")
    suspend fun clearAll()
}
