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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 一条「额度」：给某个应用手动填的可用时长。
 * usedMs 由守护服务累加，budgetMs 用户自己填。
 */
@Entity(tableName = "allowance")
data class Allowance(
    @PrimaryKey val packageName: String,
    val label: String,
    /** 每天的基础额度。 */
    val budgetMs: Long,
    /** 今天已用。由系统使用情况数据回填，不是自己干算。 */
    val usedMs: Long,
    /** 一次性加时（输了密码才给），跨天清零。 */
    val grantedMs: Long = 0L,
    /** usedMs / grantedMs 属于哪一天，用来做跨天重置。 */
    val dayKey: String = ""
) {
    /** 今天真正能用的总量 = 基础额度 + 加时。 */
    val allowedMs: Long get() = budgetMs + grantedMs

    val exhausted: Boolean get() = usedMs >= allowedMs

    val remainingMs: Long get() = (allowedMs - usedMs).coerceAtLeast(0)
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

    @Query("UPDATE allowance SET usedMs = :usedMs WHERE packageName = :pkg")
    suspend fun setUsed(pkg: String, usedMs: Long)

    /** 输密码解锁：加一段可用时间。 */
    @Query("UPDATE allowance SET grantedMs = grantedMs + :delta WHERE packageName = :pkg")
    suspend fun grant(pkg: String, delta: Long)

    /** 跨天：已用和加时都清零，并记下新的日期。 */
    @Query("UPDATE allowance SET usedMs = 0, grantedMs = 0, dayKey = :day WHERE packageName = :pkg")
    suspend fun rollover(pkg: String, day: String)

    @Query("SELECT * FROM allowance")
    suspend fun all(): List<Allowance>

    @Query("SELECT COUNT(*) FROM allowance")
    suspend fun count(): Int
}

@Database(entities = [Allowance::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun allowanceDao(): AllowanceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2：加时与跨天标记。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE allowance ADD COLUMN grantedMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE allowance ADD COLUMN dayKey TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "screentime.db"
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
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
