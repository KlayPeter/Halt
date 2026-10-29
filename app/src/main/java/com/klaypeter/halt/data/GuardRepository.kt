package com.klaypeter.halt.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class GuardRepository(context: Context) {

    private val dao = AppDatabase.get(context).allowanceDao()

    fun observeAll(): Flow<List<Allowance>> = dao.observeAll()

    suspend fun all(): List<Allowance> = dao.all()

    suspend fun upsert(allowance: Allowance) = dao.upsert(allowance)

    suspend fun delete(packageName: String) = dao.delete(packageName)

    suspend fun addUsed(packageName: String, deltaMs: Long) = dao.addUsed(packageName, deltaMs)

    /** 用系统统计到的时长覆盖，比自算更可信。 */
    suspend fun setUsed(packageName: String, usedMs: Long) = dao.setUsed(packageName, usedMs)

    /** 输密码后加一段可用时间。 */
    suspend fun grant(packageName: String, deltaMs: Long) = dao.grant(packageName, deltaMs)

    /** 跨天重置。 */
    suspend fun rollover(packageName: String, day: String) = dao.rollover(packageName, day)
}