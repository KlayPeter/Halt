package com.klaypeter.halt.updater

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val notes: String
)

/**
 * 从 GitHub Release 检查更新。
 *
 * 没有后端，直接读仓库的 latest release。6 小时内不重复请求，
 * 失败就静默 —— 网络不好不该影响 App 正常用。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/KlayPeter/Halt/releases/latest"
    private const val PREFS = "app"
    private const val KEY_CHECKED_AT = "update_checked_at"
    private const val KEY_CACHED_TAG = "update_cached_tag"
    private const val KEY_CACHED_URL = "update_cached_url"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    fun cachedVersion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CACHED_TAG, null)
    }

    fun cachedUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CACHED_URL, null)
    }

    /** 返回 null 表示「没有更新」或「查不到」。 */
    suspend fun check(context: Context, currentVersion: String): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_CHECKED_AT, 0L)

        var tag: String?
        var url: String?

        if (now - lastChecked < CHECK_INTERVAL_MS) {
            tag = prefs.getString(KEY_CACHED_TAG, null)
            url = prefs.getString(KEY_CACHED_URL, null)
        } else {
            val info = fetch()
            prefs.edit()
                .putLong(KEY_CHECKED_AT, now)
                .putString(KEY_CACHED_TAG, info?.version)
                .putString(KEY_CACHED_URL, info?.downloadUrl)
                .apply()
            tag = info?.version
            url = info?.downloadUrl
        }

        if (tag.isNullOrBlank() || url.isNullOrBlank()) return null
        return if (isNewer(tag, currentVersion)) UpdateInfo(tag, url, "") else null
    }

    private fun fetch(): UpdateInfo? = runCatching {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Halt-Android")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        val tag = json.optString("tag_name")
        if (tag.isBlank()) return null

        var download = json.optString("html_url")
        val assets = json.optJSONArray("assets")
        if (assets != null && assets.length() > 0) {
            val first = assets.getJSONObject(0).optString("browser_download_url")
            if (first.isNotBlank()) download = first
        }
        UpdateInfo(version = tag, downloadUrl = download, notes = json.optString("body"))
    }.getOrNull()

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
