package com.klaypeter.halt.updater

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val notes: String
)

/**
 * 检查更新。
 *
 * 默认读 GitHub 的 latest release。国内网络经常连不上 github.com，
 * 所以留了一个「自建镜像」的位置：填上 MIRROR_MANIFEST_URL 就优先走它。
 *
 * 镜像清单格式（一个静态 JSON 就行，放哪都可以）：
 * {
 *   "version": "0.1.6",
 *   "apk": "https://your-host/Halt-v0.1.6.apk",
 *   "notes": "可选"
 * }
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API = "https://api.github.com/repos/KlayPeter/Halt/releases/latest"

    /** 留空 = 只用 GitHub。填了就先试镜像，失败再回落 GitHub。 */
    private const val MIRROR_MANIFEST_URL = ""

    private const val PREFS = "app"
    private const val KEY_CHECKED_AT = "update_checked_at"
    private const val KEY_CACHED_TAG = "update_cached_tag"
    private const val KEY_CACHED_URL = "update_cached_url"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun cachedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_TAG, null)

    /** 返回 null 表示「没有更新」或「查不到」。 */
    suspend fun check(context: Context, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastChecked = prefs.getLong(KEY_CHECKED_AT, 0L)

            val tag: String?
            val url: String?

            if (now - lastChecked < CHECK_INTERVAL_MS) {
                tag = prefs.getString(KEY_CACHED_TAG, null)
                url = prefs.getString(KEY_CACHED_URL, null)
            } else {
                val info = fetch()
                if (info != null) {
                    // 只有成功才记「已检查」，失败了下次启动会重试
                    prefs.edit()
                        .putLong(KEY_CHECKED_AT, now)
                        .putString(KEY_CACHED_TAG, info.version)
                        .putString(KEY_CACHED_URL, info.downloadUrl)
                        .apply()
                }
                tag = info?.version
                url = info?.downloadUrl
            }

            if (tag.isNullOrBlank() || url.isNullOrBlank()) {
                Log.d(TAG, "没有可用的版本信息")
                return@withContext null
            }

            val newer = isNewer(tag, currentVersion)
            Log.d(TAG, "线上=$tag 当前=$currentVersion 有更新=$newer")
            if (newer) UpdateInfo(tag, url, "") else null
        }

    private fun fetch(): UpdateInfo? {
        if (MIRROR_MANIFEST_URL.isNotBlank()) {
            fetchFromMirror(MIRROR_MANIFEST_URL)?.let {
                Log.d(TAG, "从镜像拿到版本 ${it.version}")
                return it
            }
            Log.d(TAG, "镜像不可用，回落 GitHub")
        }
        return fetchFromGitHub()
    }

    private fun fetchFromMirror(manifestUrl: String): UpdateInfo? = try {
        val body = request(manifestUrl)
        val json = JSONObject(body)
        val version = json.optString("version")
        val apk = json.optString("apk")
        if (version.isBlank() || apk.isBlank()) {
            null
        } else {
            UpdateInfo(version, apk, json.optString("notes"))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "镜像清单读取失败: ${t.javaClass.simpleName}")
        null
    }

    private fun fetchFromGitHub(): UpdateInfo? = try {
        val json = JSONObject(request(GITHUB_API))
        val tag = json.optString("tag_name")
        if (tag.isBlank()) {
            null
        } else {
            var download = json.optString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null && assets.length() > 0) {
                val first = assets.getJSONObject(0).optString("browser_download_url")
                if (first.isNotBlank()) download = first
            }
            UpdateInfo(version = tag, downloadUrl = download, notes = json.optString("body"))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "检查更新失败: ${t.javaClass.simpleName} ${t.message}")
        null
    }

    private fun request(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Halt-Android")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** "v0.1.3" vs "0.1.2" —— 按数字逐段比较。 */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim()
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.toIntOrNull() }

        val a = parts(latest)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}