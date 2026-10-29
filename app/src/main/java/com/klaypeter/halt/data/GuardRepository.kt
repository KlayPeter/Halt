package com.klaypeter.halt.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class GuardRepository(context: Context) {

    private val dao = AppDatabase.get(context).allowanceDao()

    fun observeAll(): Flow<List<Allowance>> = dao.observeAll()

    suspend fun upsert(allowance: Allowance) = dao.upsert(allowance)

    suspend fun delete(packageName: String) = dao.delete(packageName)

    suspend fun addUsed(packageName: String, deltaMs: Long) = dao.addUsed(packageName, deltaMs)

    suspend fun resetUsed(packageName: String) = dao.resetUsed(packageName)
}
