package com.klaypeter.halt.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：自己下载 APK，然后唤起系统安装器。
 *
 * 不再把用户丢去浏览器 —— 点「立即更新」就一路走完。
 * 需要「安装未知应用」权限，没有就先跳设置。
 */
object UpdateInstaller {

    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 是否已经被允许安装未知应用。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳到「安装未知应用」开关。 */
    fun openInstallSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 下载到应用私有目录。onProgress 回调百分比（拿不到总大小时是 -1）。
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, "halt-update.apk")
        if (target.exists()) target.delete()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Halt-Android")
        conn.connect()

        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    done += read
                    val percent = if (total > 0) ((done * 100) / total).toInt() else -1
                    onProgress(percent)
                }
                output.flush()
            }
        }
        conn.disconnect()
        target
    }

    /** 唤起系统安装器。 */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, MIME_APK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
