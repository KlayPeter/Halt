package com.klaypeter.halt.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 一条「额度」：给某个应用手动填的可用时长。
 * usedMs 由守护服务累加，budgetMs 用户自己填。
 */
@Entity(tableName = "allowance")
data class Allowance(
    @PrimaryKey val packageName: String,
    val label: String,
    val budgetMs: Long,
    val usedMs: Long
) {
    val exhausted: Boolean get() = usedMs >= budgetMs
    val remainingMs: Long get() = (budgetMs - usedMs).coerceAtLeast(0)
}

@Dao
interface AllowanceDao {

    @Query("SELECT * FROM allowance ORDER BY label")
    fun observeAll(): Flow<List<Allowance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allowance: Allowance)

    @Query("DELETE FROM allowance WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("UPDATE allowance SET usedMs = usedMs + :delta WHERE packageName = :pkg")
    suspend fun addUsed(pkg: String, delta: Long)

    @Query("UPDATE allowance SET usedMs = 0 WHERE packageName = :pkg")
    suspend fun resetUsed(pkg: String)

    @Query("SELECT COUNT(*) FROM allowance")
    suspend fun count(): Int
}

@Database(entities = [Allowance::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun allowanceDao(): AllowanceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "screentime.db"
            ).build().also { instance = it }
        }
    }
}

/** 手机里一个可以启动的应用。 */
data class InstalledApp(val packageName: String, val label: String)

/** 列出可以启动的应用，用于让用户选一个来管控。 */
fun listLaunchableApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val resolved = pm.queryIntentActivities(intent, 0)
    return resolved.asSequence()
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map {
            InstalledApp(
                packageName = it.packageName,
                label = runCatching { pm.getApplicationLabel(it).toString() }
                    .getOrDefault(it.packageName)
            )
        }
        .sortedBy { it.label }
        .toList()
}
